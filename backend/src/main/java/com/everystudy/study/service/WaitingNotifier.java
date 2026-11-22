package com.everystudy.study.service;

import com.everystudy.notification.service.NotificationRecorder;
import com.everystudy.notification.service.NotificationRequest;
import com.everystudy.notification.service.NotificationType;
import com.everystudy.study.domain.Study;
import com.everystudy.study.domain.StudyWaiting;
import com.everystudy.study.repository.StudyWaitingRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 자리가 새로 났다고 대기자에게 알린다 (PRD 4.14, PLAN X-20). 참여자가 나갔을 때와 정원을 늘렸을 때 같은 규칙을 쓴다.
 * 호출한 쪽 트랜잭션(스터디 행 락 안)에서 아웃박스에 기록한다.
 */
@Component
class WaitingNotifier {

    private final StudyWaitingRepository waiting;
    private final ApplicantEligibility eligibility;
    private final NotificationRecorder notifications;
    private final StudyProperties properties;

    WaitingNotifier(StudyWaitingRepository waiting, ApplicantEligibility eligibility,
            NotificationRecorder notifications, StudyProperties properties) {
        this.waiting = waiting;
        this.eligibility = eligibility;
        this.notifications = notifications;
        this.properties = properties;
    }

    /**
     * 알림 시점에 신청 자격을 다시 검사해 지금도 신청할 수 있는 사람에게만 보낸다 (PLAN X-20). 체크인이 열린 뒤에는 신청이
     * 닫혀 있어 보내지 않는다 (X-16). 자격을 잃은 대기자의 행은 지우지 않는다.
     *
     * @param cause 자리가 난 원인. 중복 방지 키에 들어간다(나간 사람 ID, 정원 증액이면 {@code CAPACITY}). 같은 원인이 시각을
     *              달리해 되풀이돼도 새 자리이므로 시각(ms)도 키에 넣는다
     */
    void notifySeatOpened(Study study, String cause, Instant now) {
        Instant applicationsCloseAt = StudyTimeline.checkinOpensAt(study.getStartAt(), properties);
        if (!now.isBefore(applicationsCloseAt)) {
            return;
        }
        String title = "참여하려던 스터디에 자리가 났어요";
        String body = "'" + abbreviate(study.getTitle()) + "' 에 자리가 생겼어요. 먼저 신청하는 분이 참여해요.";
        for (StudyWaiting waiter : waiting.findByStudyId(study.getId())) {
            long waiterId = waiter.getUserId();
            if (!eligibility.isEligible(study, waiterId, now)) {
                continue;
            }
            String key = "WAITING_OPENED:" + study.getId() + ":" + waiterId + ":" + cause + ":" + now.toEpochMilli();
            notifications.record(NotificationRequest.allChannels(waiterId, NotificationType.WAITING_OPENED,
                    study.getId(), title, body, key, now, applicationsCloseAt));
        }
    }

    static String abbreviate(String title) {
        return title.length() <= 60 ? title : title.substring(0, 59) + "…";
    }
}
