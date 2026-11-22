package com.everystudy.attendance.service;

import com.everystudy.attendance.repository.CheckinRepository;
import com.everystudy.common.audit.AuditAction;
import com.everystudy.common.audit.AuditLogger;
import com.everystudy.common.scheduling.SchedulerProperties;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.notification.service.NotificationRecorder;
import com.everystudy.notification.service.NotificationRequest;
import com.everystudy.notification.service.NotificationType;
import com.everystudy.study.service.StudySessions;
import com.everystudy.study.service.StudySessions.JudgementTarget;
import com.everystudy.trust.service.AttendanceEventRecorder;
import com.everystudy.trust.service.HostPenaltyRecorder;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 체크인 마감 뒤의 자동 출석 판정 (PRD 4.6, ARCHITECTURE 4.4·4.7, PLAN T-031). 체크인 마감 시각이 지난 스터디를 하나씩
 * 판정한다.
 *
 * <ul>
 *   <li><b>파투</b>(주최자 포기·운영자 강제 종료): 참여자마다 {@code VOIDED} (지표에 영향 없음).</li>
 *   <li><b>주최자 미체크인</b>: 세션 무효. 주최자를 뺀 참여자마다 {@code VOIDED}, 주최자는 영향받는 참여자가 있을 때만
 *       {@code ABSENT} 감점. 감사 로그 {@code SESSION_VOID}.</li>
 *   <li><b>그 밖</b>: 체크인하지 않은 참여자마다 {@code NO_SHOW_CANDIDATE} 와 통지.</li>
 * </ul>
 *
 * <p>스터디마다 별도 트랜잭션에서 {@code study} 행 락을 잡고 조건을 다시 확인한다 (PLAN X-15). 체크인도 같은 락 안에서
 * 서버 시각으로 시간창을 검사하므로, 마감 직전 체크인과 판정이 겹쳐도 한쪽이 먼저 끝난 뒤 다른 쪽이 본다. 판정을 끝내면
 * {@code judged_at} 을 남겨 재실행해도 이벤트·알림이 늘지 않는다.
 */
@Service
public class AttendanceJudgementService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceJudgementService.class);

    private final StudySessions sessions;
    private final CheckinRepository checkins;
    private final AttendanceEventRecorder events;
    private final HostPenaltyRecorder penalties;
    private final NotificationRecorder notifications;
    private final AuditLogger audit;
    private final AttendanceProperties properties;
    private final SchedulerProperties scheduler;
    private final TimeProvider time;
    private final TransactionTemplate tx;

    AttendanceJudgementService(StudySessions sessions, CheckinRepository checkins, AttendanceEventRecorder events,
            HostPenaltyRecorder penalties, NotificationRecorder notifications, AuditLogger audit,
            AttendanceProperties properties, SchedulerProperties scheduler, TimeProvider time,
            PlatformTransactionManager transactionManager) {
        this.sessions = sessions;
        this.checkins = checkins;
        this.events = events;
        this.penalties = penalties;
        this.notifications = notifications;
        this.audit = audit;
        this.properties = properties;
        this.scheduler = scheduler;
        this.time = time;
        // 체크인과 같은 격리 수준이다 (CheckinService). 락을 잡은 뒤의 값만 믿는다.
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /** @return 이번에 판정을 끝낸 스터디 수. 한 번에 {@code batchLimit} 건까지만 처리한다 */
    public int judgeDueSessions() {
        Instant now = time.now();
        int judged = 0;
        for (long id : sessions.findJudgementDueIds(now, scheduler.batchLimit())) {
            try {
                if (Boolean.TRUE.equals(tx.execute(status -> judgeOne(id)))) {
                    judged++;
                }
            } catch (DataIntegrityViolationException e) {
                // 이벤트 유일 제약(PLAN X-9): 이미 처리된 판정이다. 오류로 보지 않고 다음 스터디를 계속 처리한다.
                log.warn("스터디 {} 판정은 이미 처리되어 건너뜀", id, e);
            } catch (RuntimeException e) {
                log.error("스터디 {} 판정 실패. 다음 스터디를 계속 처리한다", id, e);
            }
        }
        return judged;
    }

    private boolean judgeOne(long studyId) {
        Instant now = time.now();
        JudgementTarget target = sessions.lockForJudgement(studyId, now).orElse(null);
        if (target == null) {
            return false;
        }
        boolean voided = false;
        if (target.cancelled()) {
            target.activeMemberIds().forEach(userId -> events.recordVoided(studyId, userId, now));
        } else if (checkins.findByStudyIdAndUserId(studyId, target.hostId()).isEmpty()) {
            voided = true;
            voidSession(target, now);
        } else {
            markNoShowCandidates(target, now);
        }
        sessions.markJudged(studyId, voided, now);
        return true;
    }

    /** 주최자 미도착: 주최자를 뺀 참여자는 노쇼가 아니라 무효, 주최자만 감점한다 (PRD 4.6·4.13B). */
    private void voidSession(JudgementTarget target, Instant now) {
        long hostId = target.hostId();
        List<Long> others = target.activeMemberIds().stream().filter(id -> id != hostId).toList();
        others.forEach(userId -> events.recordVoided(target.studyId(), userId, now));
        boolean penalized = !others.isEmpty() && penalties.recordAbsent(target.studyId(), hostId, now);
        audit.record(null, AuditAction.SESSION_VOID, "STUDY", target.studyId(),
                Map.of("hostId", hostId, "affectedMembers", others.size(), "hostPenalized", penalized));
    }

    /** 주최자는 체크인했다. 체크인하지 않은 참여자를 노쇼 후보로 기록하고 알린다 (PRD 4.6). */
    private void markNoShowCandidates(JudgementTarget target, Instant now) {
        Set<Long> checkedIn = checkins.findByStudyId(target.studyId()).stream()
                .map(checkin -> checkin.getUserId())
                .collect(Collectors.toSet());
        Instant expiresAt = now.plus(Duration.ofHours(properties.appealWindowHours()));
        for (long userId : target.activeMemberIds()) {
            if (checkedIn.contains(userId) || !events.recordNoShowCandidate(target.studyId(), userId, now)) {
                continue;
            }
            notifications.record(NotificationRequest.allChannels(userId, NotificationType.NO_SHOW_NOTICE,
                    target.studyId(), "출석 기록이 없어 노쇼 후보로 표시됐어요",
                    "'" + abbreviate(target.title()) + "' 모임에서 체크인 기록을 찾지 못했어요. 잘못된 판정이면 "
                            + properties.appealWindowHours() + "시간 안에 이의를 신청해 주세요.",
                    "NO_SHOW_NOTICE:" + target.studyId() + ":" + userId, now, expiresAt));
        }
    }

    private static String abbreviate(String title) {
        return title.length() <= 60 ? title : title.substring(0, 59) + "…";
    }
}
