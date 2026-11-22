package com.everystudy.notification.service;

import com.everystudy.common.time.TimeProvider;
import com.everystudy.notification.repository.NotificationOutboxRepository;
import com.everystudy.notification.repository.NotificationRepository;
import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예약해 둔 일정 알림을 취소하는 공개 서비스 (PRD 4.5, ARCHITECTURE 4.6). 호출한 쪽 트랜잭션 안에서 처리한다.
 *
 * <ul>
 *   <li>예정 시각이 아직 오지 않은 건(알림 센터에 보이지 않음)은 알림과 함께 지운다. 같은 키로 다시 예약할 수 있다.</li>
 *   <li>예정 시각이 지났지만 아직 안 나간 건은 {@code CANCELLED} 로 바꾼다. 이미 보낸 알림은 건드리지 않는다.</li>
 * </ul>
 */
@Service
public class ScheduledNotificationCanceller {

    /** 일정 알림 종류. */
    static final Set<String> SCHEDULE_TYPES = Set.of(NotificationType.CHECKIN_REMINDER_DAY.name(),
            NotificationType.CHECKIN_REMINDER_HOUR.name(), NotificationType.CHECKIN_OPEN.name());

    private final NotificationRepository notifications;
    private final NotificationOutboxRepository outbox;
    private final TimeProvider time;

    ScheduledNotificationCanceller(NotificationRepository notifications, NotificationOutboxRepository outbox,
            TimeProvider time) {
        this.notifications = notifications;
        this.outbox = outbox;
        this.time = time;
    }

    /** 한 사용자의 이 스터디 일정 알림을 취소한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelForUser(long studyId, long userId) {
        cancel(studyId, userId);
    }

    /** 이 스터디의 모든 참여자의 일정 알림을 취소한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelForStudy(long studyId) {
        notifications.findUserIdsByStudyAndTypes(studyId, SCHEDULE_TYPES).forEach(userId -> cancel(studyId, userId));
    }

    private void cancel(long studyId, long userId) {
        Instant now = time.now();
        outbox.deleteUnseenPending(studyId, userId, SCHEDULE_TYPES, now);
        outbox.cancelPending(studyId, userId, SCHEDULE_TYPES);
        notifications.deleteWithoutOutbox(studyId, userId, SCHEDULE_TYPES);
    }
}
