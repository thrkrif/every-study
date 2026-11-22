package com.everystudy.trust.service;

import com.everystudy.trust.domain.AttendanceEventType;
import java.time.Instant;
import java.util.List;

/**
 * 참여자 제재 단계 판정 (PRD 4.8, PLAN D-15·D-23). 이벤트를 읽지 않는 순수 계산이다.
 *
 * <ol>
 *   <li><b>정지</b>: 참여 신청 제한을 받은 뒤 <b>제한이 시작된 이후 누적</b>으로 노쇼 1회 또는 지각
 *       {@code lateAsNoShow}회가 더 확정됐거나(재발, D-15), 긴 창의 환산 노쇼가 기준 이상.</li>
 *   <li><b>제한</b>: 아직 제한을 받은 적이 없고 짧은 창의 환산 노쇼가 기준 이상.</li>
 *   <li><b>경고</b>: 노쇼가 확정됐거나, 지각이 짧은 창에서 {@code lateAsNoShow}회에 이름.</li>
 * </ol>
 *
 * <p>환산 노쇼 = 노쇼 + ⌊지각 ÷ {@code lateAsNoShow}⌋. 창은 분모에 드는 최근 세션 기준이다({@link AttendanceTally}).
 */
final class SanctionJudgement {

    /** 판정을 일으킨 사건. */
    enum Trigger {
        /** 노쇼 확정. */
        NO_SHOW,
        /** 지각 체크인. */
        LATE
    }

    /** 판정 결과. 높은 단계가 낮은 단계를 대신한다. */
    enum Decision {
        NONE,
        WARN,
        JOIN_RESTRICT,
        SUSPEND
    }

    private SanctionJudgement() {
    }

    /**
     * @param verdictsNewestFirst 세션마다 현재 판정, 최근 것부터
     * @param restrictStartedAt 해제되지 않은 자동 참여 신청 제한의 가장 늦은 시작 시각. 받은 적 없으면 {@code null}
     */
    static Decision decide(Trigger trigger, List<SessionVerdict> verdictsNewestFirst, Instant restrictStartedAt,
            TrustProperties p) {
        List<AttendanceEventType> types = verdictsNewestFirst.stream().map(SessionVerdict::type).toList();
        AttendanceTally shortWindow = AttendanceTally.of(types, p.sanctionShortWindow(), 1);
        AttendanceTally longWindow = AttendanceTally.of(types, p.sanctionLongWindow(), 1);

        if (restrictStartedAt != null && recurred(verdictsNewestFirst, restrictStartedAt, p)) {
            return Decision.SUSPEND;
        }
        if (converted(longWindow, p) >= p.suspendConvertedNoShows()) {
            return Decision.SUSPEND;
        }
        if (restrictStartedAt == null && converted(shortWindow, p) >= p.restrictConvertedNoShows()) {
            return Decision.JOIN_RESTRICT;
        }
        if (trigger == Trigger.NO_SHOW || shortWindow.late() >= p.lateAsNoShow()) {
            return Decision.WARN;
        }
        return Decision.NONE;
    }

    private static int converted(AttendanceTally tally, TrustProperties p) {
        return tally.noShow() + tally.late() / p.lateAsNoShow();
    }

    /** 제한 시작 이후에 확정된 노쇼가 1회 이상이거나 지각이 {@code lateAsNoShow}회 이상이다 (PLAN D-15). */
    private static boolean recurred(List<SessionVerdict> verdicts, Instant since, TrustProperties p) {
        int noShows = 0;
        int lates = 0;
        for (SessionVerdict verdict : verdicts) {
            if (!verdict.occurredAt().isAfter(since)) {
                continue;
            }
            if (verdict.type() == AttendanceEventType.NO_SHOW) {
                noShows++;
            } else if (verdict.type() == AttendanceEventType.LATE) {
                lates++;
            }
        }
        return noShows >= 1 || lates >= p.lateAsNoShow();
    }
}
