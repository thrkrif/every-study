package com.everystudy.notification.service;

import com.everystudy.common.scheduling.SchedulerProperties;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.notification.domain.Notification;
import com.everystudy.notification.domain.NotificationOutbox;
import com.everystudy.notification.domain.OutboxStatus;
import com.everystudy.notification.repository.NotificationOutboxRepository;
import com.everystudy.notification.repository.NotificationRepository;
import com.everystudy.notification.service.ChannelSender.ChannelSendException;
import com.everystudy.notification.service.ChannelSender.OutgoingNotification;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림 발송 워커의 본체 (ADR-0005, ARCHITECTURE 4.6, PLAN T-038). {@code PENDING} 이고 시도 시각이 지난 아웃박스 건을
 * 한 번에 {@code batchLimit} 건까지 읽어 채널에 맞는 {@link ChannelSender} 로 보낸다.
 *
 * <ul>
 *   <li>유효 기한({@code expires_at})이 지난 건은 보내지 않고 {@code CANCELLED} 로 바꾼다.</li>
 *   <li>실패하면 지수 백오프로 다시 시도하고, 최대 횟수에 이르거나 다시 해도 소용없는 실패면 {@code FAILED} 로 남긴다.</li>
 *   <li>보낼 구현이 아직 없는 채널의 건은 건드리지 않는다. 유효 기한이 지나면 위 규칙대로 취소된다.</li>
 * </ul>
 *
 * <p>건마다 별도 트랜잭션에서 그 행을 잠그고 상태를 다시 확인한다. 그래서 재실행·동시 실행에 안전하고 {@code SENT} 건은
 * 다시 보내지 않으며, 한 건의 실패가 다른 건을 막지 않는다. 보낸 직후 커밋 전에 서버가 멈추면 한 번 더 보낼 수 있다(적어도
 * 한 번 전달). 알림 문장이 중복을 견디게 쓰인 이유다 (ADR-0005). 수신 설정이 꺼진 채널의 건은 보내지 않고 {@code SKIPPED} 로 남긴다 (T-043).
 */
@Service
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

    private final NotificationOutboxRepository outbox;
    private final NotificationRepository notifications;
    private final NotificationSettingService settings;
    private final Map<NotificationChannel, ChannelSender> senders = new EnumMap<>(NotificationChannel.class);
    private final NotificationProperties properties;
    private final SchedulerProperties scheduler;
    private final TimeProvider time;
    private final TransactionTemplate tx;

    NotificationDispatchService(NotificationOutboxRepository outbox, NotificationRepository notifications,
            NotificationSettingService settings, List<ChannelSender> senders, NotificationProperties properties, SchedulerProperties scheduler,
            TimeProvider time, TransactionTemplate tx) {
        this.outbox = outbox;
        this.notifications = notifications;
        this.settings = settings;
        senders.forEach(sender -> this.senders.put(sender.channel(), sender));
        this.properties = properties;
        this.scheduler = scheduler;
        this.time = time;
        this.tx = tx;
    }

    /** @return 이번에 처리를 마친 건수(발송·실패·재시도 예약·취소). 한 번에 {@code batchLimit} 건까지만 읽는다 */
    public int dispatchDue() {
        Instant now = time.now();
        List<String> channels = senders.keySet().stream().map(NotificationChannel::name).collect(Collectors.toList());
        // 빈 목록을 IN 에 넣을 수 없어 존재하지 않는 채널 이름 하나를 둔다. 보낼 구현이 없어도 만료 취소는 해야 한다.
        if (channels.isEmpty()) {
            channels.add("NONE");
        }
        int processed = 0;
        for (long id : outbox.findDueIds(now, channels, Limit.of(scheduler.batchLimit()))) {
            try {
                if (Boolean.TRUE.equals(tx.execute(status -> dispatchOne(id)))) {
                    processed++;
                }
            } catch (RuntimeException e) {
                log.error("알림 발송 건 {} 처리 실패. 다음 건을 계속 처리한다", id, e);
            }
        }
        return processed;
    }

    private boolean dispatchOne(long id) {
        Instant now = time.now();
        NotificationOutbox row = outbox.findByIdForUpdate(id).orElse(null);
        if (row == null || row.getStatus() != OutboxStatus.PENDING || row.getNextAttemptAt().isAfter(now)) {
            return false;
        }
        if (row.getExpiresAt().isBefore(now)) {
            row.cancelExpired("유효 기한이 지나 보내지 않음");
            return true;
        }
        NotificationChannel channel = NotificationChannel.valueOf(row.getChannel());
        ChannelSender sender = senders.get(channel);
        Notification notification = notifications.findById(row.getNotificationId()).orElse(null);
        if (sender == null || notification == null) {
            return false;
        }
        // 수신 설정은 발송 시점에 읽는다. 예약해 둔 알림도 그 사이 설정을 끄면 나가지 않는다 (PRD 4.5, D-6).
        if (!settings.isEnabled(notification.getUserId(), channel)) {
            row.skipByPreference();
            return true;
        }
        OutgoingNotification message = new OutgoingNotification(row.getId(), notification.getUserId(),
                NotificationType.valueOf(notification.getType()), notification.getStudyId(), notification.getTitle(),
                notification.getBody());
        try {
            sender.send(message);
            row.markSent(now);
        } catch (ChannelSendException e) {
            handleFailure(row, e.getMessage(), e.retryable(), now);
        } catch (RuntimeException e) {
            // 구현이 예기치 못한 오류를 던져도 건을 잃지 않는다. 메시지에 연락처가 섞일 수 있어 종류만 남긴다.
            log.warn("알림 발송 건 {} 채널 {} 예기치 못한 오류", row.getId(), row.getChannel(), e);
            handleFailure(row, "예기치 못한 발송 오류: " + e.getClass().getSimpleName(), true, now);
        }
        return true;
    }

    private void handleFailure(NotificationOutbox row, String error, boolean retryable, Instant now) {
        int attemptsAfter = row.getAttempts() + 1;
        if (!retryable || attemptsAfter >= properties.maxAttempts()) {
            row.fail(error);
            log.warn("알림 발송 건 {} 채널 {} 실패로 종료 (시도 {}회)", row.getId(), row.getChannel(), attemptsAfter);
            return;
        }
        row.retryLater(error, now.plus(properties.retryDelayAfter(attemptsAfter)));
    }
}
