package com.everystudy.attendance.service;

import com.everystudy.attendance.domain.AppealStatus;
import com.everystudy.attendance.repository.AppealRepository;
import com.everystudy.common.audit.AuditAction;
import com.everystudy.common.audit.AuditLogger;
import com.everystudy.common.error.BusinessException;
import com.everystudy.common.error.CommonErrorCode;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.trust.service.AttendanceEventRecorder;
import com.everystudy.trust.service.AttendanceLedger;
import com.everystudy.trust.service.AttendanceLedger.Verdict;
import com.everystudy.trust.service.ParticipantSanctionService;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자 개별 출석 정정 (PRD 9장, API 7장, PLAN X-7·T-057). 서버 장애로 체크인하지 못한 사용자처럼 이의 신청 없이도 운영자가
 * 판정을 바로잡을 때 쓴다. 이의 결정(T-056)과 같은 정정 이벤트를 남기고, 집계를 다시 만들며, 근거를 잃은 자동 제재를 해제한다.
 *
 * <p>정정할 수 있는 판정은 현재 판정이 {@code NO_SHOW_CANDIDATE}·{@code LATE}·{@code NO_SHOW} 인 것이다. 열린 이의가 있으면
 * 이의 결정으로 처리하도록 거부한다.
 */
@Service
public class AttendanceCorrectionService {

    /** 정정 결과. */
    public enum Result {
        /** 출석으로 센다. */
        ATTENDED,
        /** 판정을 무효화한다(지표에서 뺀다). */
        CLEARED
    }

    /** 정정으로 남은 현재 판정. */
    public record Corrected(long eventId, long studyId, long userId, String verdict) {
    }

    private static final Set<String> CORRECTABLE = Set.of("NO_SHOW_CANDIDATE", "LATE", "NO_SHOW");

    private final AttendanceLedger ledger;
    private final AttendanceEventRecorder events;
    private final ParticipantSanctionService sanctions;
    private final AppealRepository appeals;
    private final AuditLogger audit;
    private final TimeProvider time;

    AttendanceCorrectionService(AttendanceLedger ledger, AttendanceEventRecorder events,
            ParticipantSanctionService sanctions, AppealRepository appeals, AuditLogger audit, TimeProvider time) {
        this.ledger = ledger;
        this.events = events;
        this.sanctions = sanctions;
        this.appeals = appeals;
        this.audit = audit;
        this.time = time;
    }

    /**
     * 검사 순서: 판정 없음 {@code NOT_FOUND} → 정정할 수 없는 종류이거나 현재 판정이 아니거나 열린 이의가 있음
     * {@code CORRECTION_NOT_ALLOWED}.
     *
     * @param note 정정 사유. 호출한 쪽이 비어 있지 않고 200자 이하임을 보장한다
     */
    @Transactional
    public Corrected correct(long operatorId, long eventId, Result result, String note) {
        Verdict verdict = ledger.find(eventId).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!CORRECTABLE.contains(verdict.type())
                || !ledger.isCurrent(eventId, verdict.studyId(), verdict.userId())
                || appeals.existsByAttendanceEventIdAndStatus(eventId, AppealStatus.OPEN)) {
            throw new BusinessException(AttendanceErrorCode.CORRECTION_NOT_ALLOWED);
        }
        Instant now = time.now();
        events.recordCorrection(verdict.studyId(), verdict.userId(), result == Result.ATTENDED, now, note);
        sanctions.reassessAfterCorrection(verdict.userId(), verdict.occurredAt(), operatorId, now);
        audit.record(operatorId, AuditAction.ATTENDANCE_CORRECT, "STUDY", verdict.studyId(),
                Map.of("userId", verdict.userId(), "eventId", eventId, "from", verdict.type(),
                        "to", result == Result.ATTENDED ? "ATTENDED" : "NO_SHOW_CLEARED", "note", note));
        return new Corrected(eventId, verdict.studyId(), verdict.userId(),
                result == Result.ATTENDED ? "ATTENDED" : "NO_SHOW_CLEARED");
    }
}
