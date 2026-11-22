package com.everystudy.attendance.service;

import com.everystudy.attendance.domain.Appeal;
import com.everystudy.attendance.domain.AppealStatus;
import com.everystudy.attendance.repository.AppealRepository;
import com.everystudy.common.audit.AuditAction;
import com.everystudy.common.audit.AuditLogger;
import com.everystudy.common.error.BusinessException;
import com.everystudy.common.error.CommonErrorCode;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.notification.service.NotificationRecorder;
import com.everystudy.notification.service.NotificationRequest;
import com.everystudy.notification.service.NotificationType;
import com.everystudy.study.service.StudySessions;
import com.everystudy.trust.service.AttendanceEventRecorder;
import com.everystudy.trust.service.AttendanceLedger;
import com.everystudy.trust.service.AttendanceLedger.Verdict;
import com.everystudy.trust.service.ParticipantSanctionService;
import com.everystudy.user.service.PublicUser;
import com.everystudy.user.service.UserDirectory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자의 이의 최종 결정 (PRD 4.9, API 7장, PLAN T-056). 주최자 1차 의견은 참고일 뿐 결정은 운영자가 한다.
 *
 * <ul>
 *   <li><b>인정</b>: 직전 판정을 {@code NO_SHOW_CLEARED} 로 무효화하고, 지각이었으면 출석으로 센다 ({@code ATTENDED}
 *       정정, PLAN X-12). 집계를 다시 만들고, 그 판정 때문에 걸린 자동 제재 중 근거를 잃은 것을 해제한다.</li>
 *   <li><b>기각</b>: 노쇼 후보는 이의 기간을 기다리지 않고 바로 {@code NO_SHOW} 로 확정하고 참여자 제재를 판정한다
 *       ({@code ParticipantSanctionService} 재사용). 지각은 판정이 그대로 유지된다.</li>
 * </ul>
 *
 * <p>이의 행을 잠그고 한 번만 결정한다. 결과는 신청자에게 알리고 감사 로그에 남긴다.
 */
@Service
public class AppealDecisionService {

    /** 운영자 목록의 이의 한 건. */
    public record AdminView(long id, long studyId, String studyTitle, long userId, String applicantNickname,
            String verdictType, String reason, String hostOpinion, String status, Instant dueAt, Instant createdAt,
            Instant decidedAt) {
    }

    /** @param nextCursor 다음 쪽이 없으면 {@code null} */
    public record Page(List<AdminView> items, String nextCursor) {
    }

    private final AppealRepository appeals;
    private final AttendanceLedger ledger;
    private final AttendanceEventRecorder events;
    private final ParticipantSanctionService sanctions;
    private final NotificationRecorder notifications;
    private final AuditLogger audit;
    private final StudySessions sessions;
    private final UserDirectory users;
    private final AttendanceProperties properties;
    private final TimeProvider time;

    AppealDecisionService(AppealRepository appeals, AttendanceLedger ledger, AttendanceEventRecorder events,
            ParticipantSanctionService sanctions, NotificationRecorder notifications, AuditLogger audit,
            StudySessions sessions, UserDirectory users, AttendanceProperties properties, TimeProvider time) {
        this.appeals = appeals;
        this.ledger = ledger;
        this.events = events;
        this.sanctions = sanctions;
        this.notifications = notifications;
        this.audit = audit;
        this.sessions = sessions;
        this.users = users;
        this.properties = properties;
        this.time = time;
    }

    /**
     * 이의를 결정한다. 검사 순서: 이의 없음 {@code NOT_FOUND} → 이미 결정됨 또는 대상 판정이 더는 현재 판정이 아님
     * {@code APPEAL_CLOSED}.
     *
     * @param note 결정 사유(운영자 기록·신청자 알림에 쓴다). 호출한 쪽이 비어 있지 않고 200자 이하임을 보장한다
     */
    @Transactional
    public AdminView decide(long operatorId, long appealId, boolean accept, String note) {
        Appeal appeal = appeals.findByIdForUpdate(appealId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        Verdict verdict = ledger.find(appeal.getAttendanceEventId()).orElseThrow();
        if (!appeal.isOpen() || !ledger.isCurrent(verdict.id(), verdict.studyId(), verdict.userId())) {
            throw new BusinessException(AttendanceErrorCode.APPEAL_CLOSED);
        }
        Instant now = time.now();
        boolean wasLate = "LATE".equals(verdict.type());
        if (accept) {
            events.recordAppealAccepted(verdict.studyId(), verdict.userId(), wasLate, now, note);
            sanctions.reassessAfterCorrection(verdict.userId(), verdict.occurredAt(), operatorId, now);
        } else if (!wasLate && events.recordNoShow(verdict.studyId(), verdict.userId(), now)) {
            audit.record(operatorId, AuditAction.NO_SHOW_CONFIRM, "STUDY", verdict.studyId(),
                    Map.of("userId", verdict.userId(), "candidateEventId", verdict.id(), "byAppeal", appealId));
            sanctions.judgeAfterNoShow(verdict.userId(), verdict.studyId(), now);
        }
        appeal.decide(accept, operatorId, now);
        audit.record(operatorId, AuditAction.APPEAL_DECIDE, "APPEAL", appealId,
                Map.of("accept", accept, "verdict", verdict.type(), "studyId", verdict.studyId(),
                        "userId", verdict.userId(), "note", note));
        notify(appeal, verdict, accept, note, now);
        return views(List.of(appeal), Map.of(verdict.id(), verdict)).get(0);
    }

    /**
     * 이의 목록, 최근 신청부터 (커서).
     *
     * @param status {@code OPEN}·{@code ACCEPTED}·{@code REJECTED}. {@code null} 이면 모든 상태
     */
    @Transactional(readOnly = true)
    public Page list(AppealStatus status, String cursor, int size) {
        long before = IdCursor.decode(cursor);
        PageRequest limit = PageRequest.of(0, size + 1);
        List<Appeal> rows = status == null ? appeals.findByIdLessThanOrderByIdDesc(before, limit)
                : appeals.findByStatusAndIdLessThanOrderByIdDesc(status, before, limit);
        boolean hasNext = rows.size() > size;
        List<Appeal> page = hasNext ? rows.subList(0, size) : rows;
        Map<Long, Verdict> verdicts = page.stream()
                .map(a -> ledger.find(a.getAttendanceEventId()).orElseThrow())
                .collect(Collectors.toMap(Verdict::id, v -> v));
        return new Page(views(page, verdicts), hasNext ? IdCursor.encode(page.get(page.size() - 1).getId()) : null);
    }

    private void notify(Appeal appeal, Verdict verdict, boolean accept, String note, Instant now) {
        String study = sessions.titles(Set.of(appeal.getStudyId())).getOrDefault(appeal.getStudyId(), "스터디");
        String shortStudy = study.length() <= 40 ? study : study.substring(0, 39) + "…";
        String what = "LATE".equals(verdict.type()) ? "지각" : "노쇼 후보";
        String title = accept ? "이의가 받아들여졌어요" : "이의가 받아들여지지 않았어요";
        String body = "'" + shortStudy + "' 모임의 " + what + " 판정을 " + (accept ? "취소했어요." : "유지해요.") + " 사유: " + note;
        if (body.length() > NotificationRequest.BODY_MAX) {
            body = body.substring(0, NotificationRequest.BODY_MAX - 1) + "…";
        }
        notifications.record(NotificationRequest.allChannels(appeal.getUserId(), NotificationType.APPEAL_RESULT,
                appeal.getStudyId(), title, body, "APPEAL_RESULT:" + appeal.getId(), now,
                now.plus(Duration.ofDays(properties.appealResultNoticeDays()))));
    }

    private List<AdminView> views(List<Appeal> rows, Map<Long, Verdict> verdicts) {
        Map<Long, String> titles = sessions.titles(rows.stream().map(Appeal::getStudyId).collect(Collectors.toSet()));
        Map<Long, PublicUser> applicants = users.findPublic(
                rows.stream().map(Appeal::getUserId).collect(Collectors.toSet()));
        return rows.stream().map(a -> {
            PublicUser applicant = applicants.get(a.getUserId());
            return new AdminView(a.getId(), a.getStudyId(), titles.getOrDefault(a.getStudyId(), ""), a.getUserId(),
                    applicant == null ? null : applicant.nickname(), verdicts.get(a.getAttendanceEventId()).type(),
                    a.getReason(), a.getHostOpinion(), a.getStatus().name(), a.getDueAt(), a.getCreatedAt(),
                    a.getDecidedAt());
        }).toList();
    }
}
