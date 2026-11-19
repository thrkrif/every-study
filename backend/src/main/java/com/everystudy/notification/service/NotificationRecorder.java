package com.everystudy.notification.service;

import com.everystudy.common.time.TimeProvider;
import com.everystudy.notification.domain.Notification;
import com.everystudy.notification.domain.NotificationOutbox;
import com.everystudy.notification.repository.NotificationOutboxRepository;
import com.everystudy.notification.repository.NotificationRepository;
import java.time.Instant;
import java.util.Comparator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림을 기록하는 공개 서비스 (ARCHITECTURE 3장·4.6, PLAN X-8). 다른 모듈은 알림을 직접 보내지 않고 이것을 부른다.
 *
 * <p>알림과 채널별 아웃박스 행을 <b>호출한 쪽 트랜잭션 안에서</b> 기록한다. 그래서 비즈니스 변경과 함께 커밋되거나
 * 함께 롤백된다. 트랜잭션 밖에서 부르면 실패한다({@link Propagation#MANDATORY}). 발송은 워커(PLAN T-038)가 한다.
 */
@Service
public class NotificationRecorder {

    private final NotificationRepository notifications;
    private final NotificationOutboxRepository outbox;
    private final TimeProvider time;

    NotificationRecorder(NotificationRepository notifications, NotificationOutboxRepository outbox,
            TimeProvider time) {
        this.notifications = notifications;
        this.outbox = outbox;
        this.time = time;
    }

    /**
     * 알림 한 건과 채널별 발송 건을 기록한다. 같은 {@code dedupeKey} 의 알림이 이미 있으면 아무것도 하지 않는다.
     *
     * <p>중복 확인 뒤에 기록하므로, 같은 키를 두 트랜잭션이 동시에 기록하면 늦은 쪽이 유일 제약 위반으로 실패한다.
     * 호출하는 쪽은 그 키의 대상(예: {@code study} 행)을 잠근 상태에서 부른다 (ADR-0004).
     *
     * @return 새로 기록했으면 {@code true}, 이미 있어 건너뛰었으면 {@code false}
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean record(NotificationRequest request) {
        if (notifications.existsByDedupeKey(request.dedupeKey())) {
            return false;
        }
        Instant now = time.now();
        Notification notification = notifications.save(Notification.of(request.userId(), request.type().name(),
                request.studyId(), request.title(), request.body(), request.dedupeKey(), request.scheduledAt(), now));
        request.channels().stream().sorted(Comparator.naturalOrder()).forEach(channel ->
                outbox.save(NotificationOutbox.pending(notification.getId(), channel.name(),
                        request.dedupeKey() + ":" + channel.name(), request.scheduledAt(), request.expiresAt(), now)));
        return true;
    }
}
