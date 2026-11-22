package com.everystudy.trust.service;

import static com.everystudy.trust.domain.AttendanceEventType.ATTENDED;
import static com.everystudy.trust.domain.AttendanceEventType.EARLY_CANCEL;
import static com.everystudy.trust.domain.AttendanceEventType.LATE;
import static com.everystudy.trust.domain.AttendanceEventType.LATE_CANCEL;
import static com.everystudy.trust.domain.AttendanceEventType.NO_SHOW;
import static com.everystudy.trust.domain.AttendanceEventType.NO_SHOW_CANDIDATE;
import static com.everystudy.trust.domain.AttendanceEventType.NO_SHOW_CLEARED;
import static com.everystudy.trust.domain.AttendanceEventType.VOIDED;
import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.trust.domain.AttendanceEventType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 출석률 계산 (PRD 4.7). 이벤트 종류별 포함·제외, 신규 경계, 최근 N회 창을 DB 없이 본다. */
class AttendanceTallyTest {

    private static AttendanceTally tally(AttendanceEventType... verdicts) {
        return AttendanceTally.of(List.of(verdicts), 10, 3);
    }

    @Test
    @DisplayName("출석은 분자와 분모에 모두 들어간다")
    void attendedCountsInBoth() {
        AttendanceTally tally = tally(ATTENDED, ATTENDED, ATTENDED);

        assertThat(tally.counted()).isEqualTo(3);
        assertThat(tally.attended()).isEqualTo(3);
        assertThat(tally.rate()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("지각은 분자에 0.5, 분모에 1로 센다")
    void lateIsHalf() {
        AttendanceTally tally = tally(ATTENDED, ATTENDED, LATE);

        assertThat(tally.late()).isEqualTo(1);
        assertThat(tally.rate()).isEqualByComparingTo("83.33");
    }

    @Test
    @DisplayName("최근 10회 중 출석 8, 지각 2면 90%다")
    void prdExample() {
        List<AttendanceEventType> verdicts = new ArrayList<>(Collections.nCopies(8, ATTENDED));
        verdicts.addAll(List.of(LATE, LATE));

        assertThat(AttendanceTally.of(verdicts, 10, 3).rate()).isEqualByComparingTo("90.00");
    }

    @Test
    @DisplayName("노쇼 확정과 직전 취소는 분모에만 들어간다")
    void noShowAndLateCancelCountInDenominatorOnly() {
        AttendanceTally tally = tally(ATTENDED, NO_SHOW, LATE_CANCEL, ATTENDED);

        assertThat(tally.counted()).isEqualTo(4);
        assertThat(tally.noShow()).isEqualTo(1);
        assertThat(tally.lateCancel()).isEqualTo(1);
        assertThat(tally.rate()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("노쇼 후보, 정정으로 취소된 판정, 사전 취소, 세션 무효는 세지 않는다")
    void excludedVerdicts() {
        AttendanceTally tally = tally(ATTENDED, ATTENDED, ATTENDED, NO_SHOW_CANDIDATE, NO_SHOW_CLEARED,
                EARLY_CANCEL, VOIDED);

        assertThat(tally.counted()).isEqualTo(3);
        assertThat(tally.rate()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("집계 대상 세션이 신규 기준 미만이면 출석률이 비고, 기준에 이르면 계산한다")
    void newcomerBoundary() {
        assertThat(tally(ATTENDED, ATTENDED).rate()).isNull();
        assertThat(tally(ATTENDED, ATTENDED, NO_SHOW).rate()).isEqualByComparingTo("66.67");
        assertThat(tally().counted()).isZero();
        assertThat(tally().rate()).isNull();
    }

    @Test
    @DisplayName("제외되는 세션은 최근 N회 창을 차지하지 않고, 창 밖의 오래된 이벤트는 무시한다")
    void recentWindow() {
        List<AttendanceEventType> verdicts = new ArrayList<>();
        verdicts.add(EARLY_CANCEL);
        verdicts.addAll(Collections.nCopies(10, ATTENDED));
        verdicts.add(NO_SHOW);

        AttendanceTally tally = AttendanceTally.of(verdicts, 10, 3);

        assertThat(tally.counted()).isEqualTo(10);
        assertThat(tally.noShow()).isZero();
        assertThat(tally.rate()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("창 크기는 설정값을 따른다")
    void windowFollowsConfig() {
        AttendanceTally tally = AttendanceTally.of(List.of(ATTENDED, ATTENDED, ATTENDED, NO_SHOW), 3, 3);

        assertThat(tally.counted()).isEqualTo(3);
        assertThat(tally.rate()).isEqualByComparingTo("100.00");
    }
}
