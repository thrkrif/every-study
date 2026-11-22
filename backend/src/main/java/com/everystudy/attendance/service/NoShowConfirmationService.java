package com.everystudy.attendance.service;

import com.everystudy.attendance.domain.AppealStatus;
import com.everystudy.attendance.repository.AppealRepository;
import com.everystudy.common.audit.AuditAction;
import com.everystudy.common.audit.AuditLogger;
import com.everystudy.common.scheduling.SchedulerProperties;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.trust.service.AttendanceEventRecorder;
import com.everystudy.trust.service.AttendanceLedger;
import com.everystudy.trust.service.AttendanceLedger.Verdict;
import com.everystudy.trust.service.ParticipantSanctionService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 이의 기간이 지난 노쇼 후보를 {@code NO_SHOW} 로 확정하고 참여자 제재를 판정한다 (PRD 4.6·4.8, PLAN T-035a).
 *
 * <ul>
 *   <li>후보가 난 뒤 {@code appealWindowHours} 가 <b>지나야</b> 확정한다. 정확히 기한인 시각에는 이의를 아직 받으므로
 *       확정하지 않는다 (PLAN X-30).</li>
 *   <li>열린 이의(결정 전)가 있으면 확정하지 않고 둔다. 운영자 결정(T-056)으로 정정되거나 기각된 뒤에 처리된다.</li>
 *   <li>후보마다 별도 트랜잭션에서 현재 판정인지와 열린 이의를 다시 확인하고, 확정 이벤트·집계·제재·감사 로그·알림을
 *       한 번에 남긴다. 다시 실행해도 같은 후보는 한 번만 확정된다(멱등).</li>
 * </ul>
 */
@Service
public class NoShowConfirmationService {

    private static final Logger log = LoggerFactory.getLogger(NoShowConfirmationService.class);

    private final AttendanceLedger ledger;
    private final AppealRepository appeals;
    private final AttendanceEventRecorder events;
    private final ParticipantSanctionService sanctions;
    private final AuditLogger audit;
    private final AttendanceProperties properties;
    private final SchedulerProperties scheduler;
    private final TimeProvider time;
    private final TransactionTemplate tx;

    NoShowConfirmationService(AttendanceLedger ledger, AppealRepository appeals, AttendanceEventRecorder events,
            ParticipantSanctionService sanctions, AuditLogger audit, AttendanceProperties properties,
            SchedulerProperties scheduler, TimeProvider time, PlatformTransactionManager transactionManager) {
        this.ledger = ledger;
        this.appeals = appeals;
        this.events = events;
        this.sanctions = sanctions;
        this.audit = audit;
        this.properties = properties;
        this.scheduler = scheduler;
        this.time = time;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** @return 이번에 확정한 노쇼 수. 한 번에 {@code batchLimit} 건까지만 확정한다 */
    public int confirmDueNoShows() {
        Instant now = time.now();
        Instant cutoff = now.minus(Duration.ofHours(properties.appealWindowHours()));
        int confirmed = 0;
        long afterId = 0;
        // 열린 이의가 있는 후보는 건너뛰므로 id 를 따라 다음 쪽을 이어 본다. 건너뛴 후보가 쌓여도 뒤의 후보가 굶지 않는다.
        while (confirmed < scheduler.batchLimit()) {
            List<Verdict> page = ledger.confirmableNoShowCandidates(cutoff, afterId, scheduler.batchLimit());
            if (page.isEmpty()) {
                break;
            }
            for (Verdict candidate : page) {
                afterId = candidate.id();
                if (confirmed < scheduler.batchLimit() && confirmSafely(candidate)) {
                    confirmed++;
                }
            }
        }
        return confirmed;
    }

    private boolean confirmSafely(Verdict candidate) {
        try {
            return Boolean.TRUE.equals(tx.execute(status -> confirm(candidate)));
        } catch (DataIntegrityViolationException e) {
            // 확정 이벤트 유일 제약(PLAN X-9): 이미 확정된 후보다. 오류로 보지 않는다.
            log.warn("노쇼 후보 {} 는 이미 확정되어 건너뜀", candidate.id(), e);
        } catch (RuntimeException e) {
            log.error("노쇼 후보 {} 확정 실패. 다음 후보를 계속 처리한다", candidate.id(), e);
        }
        return false;
    }

    private boolean confirm(Verdict candidate) {
        if (appeals.existsByAttendanceEventIdAndStatus(candidate.id(), AppealStatus.OPEN)
                || !ledger.isCurrent(candidate.id(), candidate.studyId(), candidate.userId())) {
            return false;
        }
        Instant now = time.now();
        if (!events.recordNoShow(candidate.studyId(), candidate.userId(), now)) {
            return false;
        }
        audit.record(null, AuditAction.NO_SHOW_CONFIRM, "STUDY", candidate.studyId(),
                Map.of("userId", candidate.userId(), "candidateEventId", candidate.id()));
        sanctions.judgeAfterNoShow(candidate.userId(), candidate.studyId(), now);
        return true;
    }
}
