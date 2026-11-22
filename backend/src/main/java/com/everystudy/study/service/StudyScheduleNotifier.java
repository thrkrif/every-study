package com.everystudy.study.service;

import com.everystudy.common.time.TimeProvider;
import com.everystudy.notification.service.NotificationRecorder;
import com.everystudy.notification.service.NotificationRequest;
import com.everystudy.notification.service.NotificationType;
import com.everystudy.notification.service.ScheduledNotificationCanceller;
import com.everystudy.study.domain.Study;
import com.everystudy.study.event.MemberJoined;
import com.everystudy.study.event.MemberLeft;
import com.everystudy.study.event.StudyCancelled;
import com.everystudy.study.event.StudyCreated;
import com.everystudy.study.repository.StudyRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 일정 알림(전날·1시간 전·체크인 오픈)을 <b>참여가 확정될 때</b> 그 참여자 몫으로 예약하고, 나가기·파투 때 미발송 건을
 * 취소한다 (PRD 4.5, ARCHITECTURE 4.6, PLAN X-2·T-040). 동기 리스너라 참여·나가기와 함께 커밋되거나 롤백된다.
 *
 * <p>이미 지난 시점은 예약하지 않는다. 각 알림의 유효 기한(PLAN D-18)은 다음 일정 알림 시각이고, 마지막인 체크인 오픈은
 * 체크인 마감이다. 예약 시각은 알림 센터에 보이기 시작하는 시각이기도 하다 (X-10).
 */
@Component
class StudyScheduleNotifier {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREAN).withZone(TimeProvider.KOREA);

    private final StudyRepository studies;
    private final NotificationRecorder notifications;
    private final ScheduledNotificationCanceller canceller;
    private final StudyProperties properties;
    private final ReminderProperties reminders;
    private final TimeProvider time;

    StudyScheduleNotifier(StudyRepository studies, NotificationRecorder notifications,
            ScheduledNotificationCanceller canceller, StudyProperties properties, ReminderProperties reminders,
            TimeProvider time) {
        this.studies = studies;
        this.notifications = notifications;
        this.canceller = canceller;
        this.properties = properties;
        this.reminders = reminders;
        this.time = time;
    }

    @EventListener
    void onCreated(StudyCreated event) {
        schedule(event.studyId(), event.hostId());
    }

    @EventListener
    void onJoined(MemberJoined event) {
        schedule(event.studyId(), event.userId());
    }

    @EventListener
    void onLeft(MemberLeft event) {
        canceller.cancelForUser(event.studyId(), event.userId());
    }

    @EventListener
    void onCancelled(StudyCancelled event) {
        canceller.cancelForStudy(event.studyId());
    }

    /** 주최자 혼자일 때 일정을 바꿨다. 이미 예약한 알림을 지우고 새 시각으로 다시 만든다. */
    void reschedule(Study study, long hostId) {
        canceller.cancelForUser(study.getId(), hostId);
        schedule(study, hostId);
    }

    private void schedule(long studyId, long userId) {
        studies.findById(studyId).ifPresent(study -> schedule(study, userId));
    }

    private void schedule(Study study, long userId) {
        Instant now = time.now();
        Instant start = study.getStartAt();
        Instant dayAt = start.minus(Duration.ofHours(reminders.dayBeforeHours()));
        Instant hourAt = start.minus(Duration.ofMinutes(reminders.hourBeforeMinutes()));
        Instant openAt = StudyTimeline.checkinOpensAt(start, properties);
        Instant closeAt = StudyTimeline.checkinClosesAt(start, properties);
        String title = WaitingNotifier.abbreviate(study.getTitle());
        String when = WHEN.format(start);

        record(userId, study, NotificationType.CHECKIN_REMINDER_DAY, "모임 하루 전 알림이에요",
                "'" + title + "' 모임이 " + when + "에 시작돼요.", dayAt, hourAt, now);
        record(userId, study, NotificationType.CHECKIN_REMINDER_HOUR, "모임 1시간 전 알림이에요",
                "'" + title + "' 모임이 " + when + "에 시작돼요.", hourAt, openAt, now);
        record(userId, study, NotificationType.CHECKIN_OPEN, "체크인이 열렸어요",
                "'" + title + "' 모임 체크인을 할 수 있어요. 장소에 도착하면 체크인해 주세요.", openAt, closeAt, now);
    }

    private void record(long userId, Study study, NotificationType type, String title, String body, Instant at,
            Instant expiresAt, Instant now) {
        if (!at.isAfter(now)) {
            return;
        }
        notifications.record(NotificationRequest.allChannels(userId, type, study.getId(), title, body,
                type.name() + ":" + study.getId() + ":" + userId, at, expiresAt));
    }
}
