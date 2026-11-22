package com.everystudy.trust.service;

import static com.everystudy.trust.domain.AttendanceEventType.ATTENDED;
import static com.everystudy.trust.domain.AttendanceEventType.EARLY_CANCEL;
import static com.everystudy.trust.domain.AttendanceEventType.LATE;
import static com.everystudy.trust.domain.AttendanceEventType.NO_SHOW;
import static com.everystudy.trust.domain.AttendanceEventType.NO_SHOW_CANDIDATE;
import static com.everystudy.trust.domain.AttendanceEventType.VOIDED;
import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.trust.domain.AttendanceEventType;
import com.everystudy.trust.service.SanctionJudgement.Decision;
import com.everystudy.trust.service.SanctionJudgement.Trigger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SanctionJudgementTest {

    private static final TrustProperties PROPERTIES = new TrustProperties(3, 180, 1, 10, 5, 5, 10, 3, 2, 4, 7, 14, 7);
    private static final Instant T = Instant.parse("2026-12-01T00:00:00Z");

    /** 최근 것부터 판정을 나열한다. 시각은 모두 제한 시작보다 앞선 것으로 둔다. */
    private static List<SessionVerdict> verdicts(AttendanceEventType... types) {
        List<SessionVerdict> list = new ArrayList<>();
        for (AttendanceEventType type : types) {
            list.add(new SessionVerdict(type, T.minusSeconds(3600)));
        }
        return list;
    }

    private static Decision decide(Trigger trigger, List<SessionVerdict> verdicts, Instant restrictStartedAt) {
        return SanctionJudgement.decide(trigger, verdicts, restrictStartedAt, PROPERTIES);
    }

    @Test
    @DisplayName("노쇼 1회만 있으면 경고다")
    void singleNoShowWarns() {
        assertThat(decide(Trigger.NO_SHOW, verdicts(NO_SHOW, ATTENDED, ATTENDED), null)).isEqualTo(Decision.WARN);
    }

    @Test
    @DisplayName("지각만으로는 최근 5회 중 3회에서 경고까지만 받는다")
    void lateOnlyWarnsAtThree() {
        assertThat(decide(Trigger.LATE, verdicts(LATE, LATE, ATTENDED, ATTENDED, ATTENDED), null))
                .isEqualTo(Decision.NONE);
        assertThat(decide(Trigger.LATE, verdicts(LATE, LATE, LATE, ATTENDED, ATTENDED), null))
                .isEqualTo(Decision.WARN);
        // 5번째 밖의 지각은 세지 않는다
        assertThat(decide(Trigger.LATE, verdicts(LATE, LATE, ATTENDED, ATTENDED, ATTENDED, LATE), null))
                .isEqualTo(Decision.NONE);
    }

    @Test
    @DisplayName("최근 5회 중 노쇼 1 + 지각 3(환산 노쇼 2)이면 참여 신청 제한이다")
    void noShowPlusThreeLatesRestricts() {
        assertThat(decide(Trigger.LATE, verdicts(LATE, LATE, LATE, NO_SHOW, ATTENDED), null))
                .isEqualTo(Decision.JOIN_RESTRICT);
    }

    @Test
    @DisplayName("최근 5회 중 노쇼 2회면 제한이고, 5회 창 밖의 노쇼는 세지 않아 경고에 그친다")
    void restrictBoundaryAtFiveSessions() {
        assertThat(decide(Trigger.NO_SHOW, verdicts(NO_SHOW, ATTENDED, ATTENDED, ATTENDED, NO_SHOW), null))
                .isEqualTo(Decision.JOIN_RESTRICT);
        assertThat(decide(Trigger.NO_SHOW, verdicts(NO_SHOW, ATTENDED, ATTENDED, ATTENDED, ATTENDED, NO_SHOW), null))
                .isEqualTo(Decision.WARN);
    }

    @Test
    @DisplayName("노쇼 후보·무효·사전 취소는 최근 5회 창을 차지하지 않는다")
    void uncountedVerdictsDoNotTakeWindow() {
        assertThat(decide(Trigger.NO_SHOW,
                verdicts(NO_SHOW_CANDIDATE, VOIDED, EARLY_CANCEL, NO_SHOW, ATTENDED, ATTENDED, ATTENDED, NO_SHOW),
                null)).isEqualTo(Decision.JOIN_RESTRICT);
    }

    @Test
    @DisplayName("최근 10회 중 환산 노쇼 4회면 제한을 받은 적이 없어도 정지다")
    void suspendAtFourInTen() {
        List<SessionVerdict> spread = verdicts(NO_SHOW, ATTENDED, ATTENDED, ATTENDED, ATTENDED, NO_SHOW, NO_SHOW,
                NO_SHOW, ATTENDED, ATTENDED);
        assertThat(decide(Trigger.NO_SHOW, spread, null)).isEqualTo(Decision.SUSPEND);
        List<SessionVerdict> three = verdicts(NO_SHOW, ATTENDED, ATTENDED, ATTENDED, ATTENDED, NO_SHOW, NO_SHOW,
                ATTENDED, ATTENDED, ATTENDED);
        assertThat(decide(Trigger.NO_SHOW, three, null)).isEqualTo(Decision.WARN);
    }

    @Test
    @DisplayName("제한 시작 이후 확정된 노쇼 1회는 재발이라 정지다. 같은 시각에 확정된 것은 세지 않는다")
    void recurrenceByNoShowAfterRestrictStart() {
        List<SessionVerdict> after = List.of(new SessionVerdict(NO_SHOW, T.plusSeconds(1)),
                new SessionVerdict(NO_SHOW, T), new SessionVerdict(NO_SHOW, T.minusSeconds(86400)));
        assertThat(decide(Trigger.NO_SHOW, after, T)).isEqualTo(Decision.SUSPEND);

        List<SessionVerdict> sameInstant = List.of(new SessionVerdict(NO_SHOW, T),
                new SessionVerdict(NO_SHOW, T.minusSeconds(86400)));
        assertThat(decide(Trigger.NO_SHOW, sameInstant, T)).isEqualTo(Decision.WARN);
    }

    @Test
    @DisplayName("제한 시작 이후 지각 3회가 쌓이면 정지, 2회까지는 경고도 없다")
    void recurrenceByLatesAfterRestrictStart() {
        List<SessionVerdict> three = List.of(new SessionVerdict(LATE, T.plusSeconds(3)),
                new SessionVerdict(LATE, T.plusSeconds(2)), new SessionVerdict(LATE, T.plusSeconds(1)));
        assertThat(decide(Trigger.LATE, three, T)).isEqualTo(Decision.SUSPEND);
        assertThat(decide(Trigger.LATE, three.subList(1, 3), T)).isEqualTo(Decision.NONE);
    }

    @Test
    @DisplayName("이미 제한을 받았고 재발이 없으면 제한을 다시 걸지 않는다")
    void doesNotRestrictTwice() {
        List<SessionVerdict> both = List.of(new SessionVerdict(NO_SHOW, T),
                new SessionVerdict(NO_SHOW, T.minusSeconds(86400)));
        assertThat(decide(Trigger.NO_SHOW, both, T)).isNotEqualTo(Decision.JOIN_RESTRICT);
    }
}
