package com.everystudy.study.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.common.error.FieldViolation;
import com.everystudy.study.domain.Study;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** 시각은 모두 UTC 로 적는다. KST = UTC + 9시간. */
class StudyCreationRulesTest {

    private static final StudyProperties PROPERTIES =
            new StudyProperties(2, 6, 14, 10, 10, 30, List.of(50, 60, 70, 80, 90, 100));

    /** 2026-11-11 (수) 10:00 KST. 개설 가능 범위의 마지막 날은 2026-11-25 (KST). */
    private static final Instant NOW = Instant.parse("2026-11-11T01:00:00Z");

    private static CreateStudyCommand command(String startAt, int durationMin, int capacity, Integer rate) {
        return new CreateStudyCommand("모각코", "같이 공부해요. 조용히 집중하는 모임입니다.", "DEVELOPMENT", "SUWON_JANGAN",
                Set.of(), new Study.Place("카페", "수원시 장안구", new BigDecimal("37.300000"), new BigDecimal("127.010000")),
                Instant.parse(startAt), durationMin, capacity, new Study.EntryCriteria(rate, true));
    }

    private static List<String> violatedFields(CreateStudyCommand command) {
        return StudyCreationRules.check(command, NOW, PROPERTIES).stream().map(FieldViolation::field).toList();
    }

    @Test
    @DisplayName("규칙에 맞으면 위반이 없다")
    void validCommand() {
        assertThat(violatedFields(command("2026-11-12T10:00:00Z", 120, 4, 70))).isEmpty();
    }

    @Nested
    @DisplayName("시작 시각")
    class StartAt {

        @ParameterizedTest(name = "{0} (KST {1})")
        @CsvSource({
                "2026-11-25T14:50:00Z, '25일 23:50 — 마지막 날'",
                "2026-11-24T15:00:00Z, '25일 00:00 — 마지막 날의 시작'",
                "2026-11-11T01:10:00Z, '오늘 10:10 — 10분 뒤'"})
        @DisplayName("오늘부터 14일 뒤(KST)까지 받는다")
        void acceptsWithinOpenableRange(String startAt, String description) {
            assertThat(violatedFields(command(startAt, 10 * 3, 4, null))).doesNotContain("startAt");
        }

        @Test
        @DisplayName("마지막 날 다음 날(26일 00:00 KST)은 거절한다")
        void rejectsDayAfterLastOpenableDay() {
            assertThat(violatedFields(command("2026-11-25T15:00:00Z", 30, 4, null))).containsExactly("startAt");
        }

        @ParameterizedTest
        @ValueSource(strings = {"2026-11-11T01:00:00Z", "2026-11-11T00:50:00Z", "2026-11-01T01:00:00Z"})
        @DisplayName("현재와 같거나 과거면 거절한다")
        void rejectsNowOrPast(String startAt) {
            assertThat(violatedFields(command(startAt, 30, 4, null))).containsExactly("startAt");
        }

        @ParameterizedTest
        @ValueSource(strings = {"2026-11-12T10:05:00Z", "2026-11-12T10:00:30Z", "2026-11-12T10:00:00.001Z"})
        @DisplayName("10분 단위가 아니거나 초가 있으면 거절한다")
        void rejectsOffUnit(String startAt) {
            assertThat(violatedFields(command(startAt, 30, 4, null))).containsExactly("startAt");
        }

        @Test
        @DisplayName("세션 날짜는 시작 시각의 KST 날짜다 (UTC 로는 전날이어도)")
        void sessionDateIsKstDate() {
            assertThat(StudyCreationRules.sessionDate(Instant.parse("2026-11-12T15:30:00Z")))
                    .isEqualTo(LocalDate.parse("2026-11-13"));
        }
    }

    @Nested
    @DisplayName("진행 시간")
    class Duration {

        @Test
        @DisplayName("자정(24:00 KST)에 끝나는 것까지 받는다")
        void acceptsEndingAtMidnight() {
            // 22:00 KST 시작 + 120분 = 24:00
            assertThat(violatedFields(command("2026-11-12T13:00:00Z", 120, 4, null))).isEmpty();
        }

        @Test
        @DisplayName("자정을 넘기면 거절한다")
        void rejectsCrossingMidnight() {
            assertThat(violatedFields(command("2026-11-12T13:00:00Z", 130, 4, null))).containsExactly("durationMin");
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 20, 35, 45})
        @DisplayName("30분 미만이거나 10분 단위가 아니면 거절한다")
        void rejectsTooShortOrOffUnit(int durationMin) {
            assertThat(violatedFields(command("2026-11-12T01:00:00Z", durationMin, 4, null)))
                    .containsExactly("durationMin");
        }
    }

    @Nested
    @DisplayName("정원과 입장 기준")
    class CapacityAndCriteria {

        @ParameterizedTest
        @CsvSource({"1, true", "2, false", "6, false", "7, true"})
        @DisplayName("정원은 2~6명")
        void capacityRange(int capacity, boolean rejected) {
            List<String> fields = violatedFields(command("2026-11-12T01:00:00Z", 60, capacity, null));
            assertThat(fields.contains("capacity")).isEqualTo(rejected);
        }

        @ParameterizedTest
        @CsvSource({"50, false", "100, false", "40, true", "75, true", "0, true"})
        @DisplayName("최소 출석률은 50~100 중 10 단위 값만")
        void attendanceRateOptions(int rate, boolean rejected) {
            List<String> fields = violatedFields(command("2026-11-12T01:00:00Z", 60, 4, rate));
            assertThat(fields.contains("minAttendanceRate")).isEqualTo(rejected);
        }

        @Test
        @DisplayName("최소 출석률이 없으면(기준 없음) 받는다")
        void noAttendanceCriteria() {
            assertThat(violatedFields(command("2026-11-12T01:00:00Z", 60, 4, null))).isEmpty();
        }
    }

    @Test
    @DisplayName("여러 규칙을 어기면 위반을 모두 알려 준다")
    void reportsAllViolations() {
        assertThat(violatedFields(command("2026-11-01T01:05:00Z", 25, 9, 33)))
                .contains("startAt", "durationMin", "capacity", "minAttendanceRate");
    }
}
