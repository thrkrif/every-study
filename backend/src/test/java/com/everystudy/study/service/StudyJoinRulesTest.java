package com.everystudy.study.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.everystudy.common.error.BusinessException;
import com.everystudy.study.domain.Study;
import com.everystudy.study.domain.StudyStatus;
import com.everystudy.trust.service.AttendanceStanding;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StudyJoinRulesTest {

    private static final StudyProperties PROPERTIES =
            new StudyProperties(2, 6, 14, 10, 10, 30, List.of(50, 60, 70, 80, 90, 100), 10, 30);
    private static final Instant START = Instant.parse("2026-11-20T10:00:00Z");
    /** 체크인 오픈(09:50) 전. */
    private static final Instant NOW = Instant.parse("2026-11-20T09:00:00Z");
    private static final Instant RESTRICTED_UNTIL = Instant.parse("2026-11-25T00:00:00Z");

    /** 모든 검사를 통과하는 사실. 테스트는 여기서 하나씩 어긋나게 바꾼다. */
    private static Facts passing() {
        return new Facts();
    }

    /** {@link StudyJoinRules.JoinFacts} 를 만드는 테스트용 빌더. 기본값은 모든 검사를 통과한다. */
    private static final class Facts {

        private StudyStatus status = StudyStatus.ACTIVE;
        private int memberCount = 2;
        private Instant restrictedUntil;
        private AttendanceStanding standing = AttendanceStanding.rated(new BigDecimal("80.00"));
        private Study.EntryCriteria entryCriteria = new Study.EntryCriteria(70, true);
        private Set<String> targetJobCodes = Set.of("IT");
        private String myJobCategory = "IT";
        private boolean alreadyMember;

        Facts withStatus(StudyStatus value) {
            status = value;
            return this;
        }

        Facts withMemberCount(int value) {
            memberCount = value;
            return this;
        }

        Facts withRestrictedUntil(Instant value) {
            restrictedUntil = value;
            return this;
        }

        Facts withStanding(AttendanceStanding value) {
            standing = value;
            return this;
        }

        Facts withEntryCriteria(Study.EntryCriteria value) {
            entryCriteria = value;
            return this;
        }

        Facts withTargetJobCodes(Set<String> value) {
            targetJobCodes = value;
            return this;
        }

        Facts withMyJobCategory(String value) {
            myJobCategory = value;
            return this;
        }

        Facts withAlreadyMember(boolean value) {
            alreadyMember = value;
            return this;
        }

        /** 정원 4, 시작 {@link #START}. */
        StudyJoinRules.JoinFacts build() {
            return new StudyJoinRules.JoinFacts(status, START, 4, memberCount, restrictedUntil, standing,
                    entryCriteria, targetJobCodes, myJobCategory, alreadyMember);
        }
    }

    @Test
    @DisplayName("모든 조건을 만족하면 통과한다")
    void passes() {
        assertThatCode(() -> StudyJoinRules.check(passing().build(), NOW, PROPERTIES)).doesNotThrowAnyException();
    }

    @Nested
    @DisplayName("스터디 상태")
    class Status {

        @ParameterizedTest(name = "{0}")
        @CsvSource({"ENDED", "CANCELLED"})
        @DisplayName("진행 중이 아니면 STUDY_NOT_RECRUITING")
        void notActive(StudyStatus status) {
            Facts facts = passing().withStatus(status);

            assertThat(codeOf(facts, NOW)).isEqualTo("STUDY_NOT_RECRUITING");
        }

        @Test
        @DisplayName("체크인 오픈 순간부터 STUDY_NOT_RECRUITING, 1초 전까지는 신청할 수 있다")
        void checkinOpened() {
            assertThat(codeOf(passing(), Instant.parse("2026-11-20T09:50:00Z"))).isEqualTo("STUDY_NOT_RECRUITING");
            assertThatCode(() -> StudyJoinRules.check(passing().build(), Instant.parse("2026-11-20T09:49:59Z"), PROPERTIES))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("참여자 수가 정원에 닿았으면 STUDY_FULL")
    void full() {
        assertThat(codeOf(passing().withMemberCount(4), NOW)).isEqualTo("STUDY_FULL");
    }

    @Test
    @DisplayName("이용 제한 중이면 USER_RESTRICTED 와 해제 시각")
    void restricted() {
        BusinessException ex = rejection(passing().withRestrictedUntil(RESTRICTED_UNTIL), NOW);

        assertThat(ex.errorCode().name()).isEqualTo("USER_RESTRICTED");
        assertThat(ex.properties()).containsEntry("until", RESTRICTED_UNTIL);
    }

    @Nested
    @DisplayName("입장 기준")
    class EntryCriteria {

        @Test
        @DisplayName("신규 불허 스터디에 신규가 신청하면 ENTRY_CRITERIA_NOT_MET")
        void newcomerNotAllowed() {
            Facts facts = passing()
                    .withStanding(AttendanceStanding.NEWCOMER)
                    .withEntryCriteria(new Study.EntryCriteria(null, false));

            assertThat(codeOf(facts, NOW)).isEqualTo("ENTRY_CRITERIA_NOT_MET");
        }

        @Test
        @DisplayName("신규 허용이면 최소 출석률이 있어도 신규는 통과한다")
        void newcomerAllowedIgnoresRate() {
            Facts facts = passing()
                    .withStanding(AttendanceStanding.NEWCOMER)
                    .withEntryCriteria(new Study.EntryCriteria(90, true));

            assertThatCode(() -> StudyJoinRules.check(facts.build(), NOW, PROPERTIES)).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "출석률 {0}%, 기준 {1}% → 통과 {2}")
        @CsvSource({"70.00, 70, true", "69.99, 70, false", "100.00, 100, true", "0.00, 50, false"})
        @DisplayName("신규가 아니면 출석률이 최소 출석률 이상이어야 한다")
        void minimumRate(String rate, int minimum, boolean passes) {
            Facts facts = passing()
                    .withStanding(AttendanceStanding.rated(new BigDecimal(rate)))
                    .withEntryCriteria(new Study.EntryCriteria(minimum, false));

            if (passes) {
                assertThatCode(() -> StudyJoinRules.check(facts.build(), NOW, PROPERTIES)).doesNotThrowAnyException();
            } else {
                assertThat(codeOf(facts, NOW)).isEqualTo("ENTRY_CRITERIA_NOT_MET");
            }
        }

        @Test
        @DisplayName("최소 출석률이 없으면 신규가 아닌 회원은 출석률과 관계없이 통과한다")
        void noMinimum() {
            Facts facts = passing()
                    .withStanding(AttendanceStanding.rated(BigDecimal.ZERO))
                    .withEntryCriteria(new Study.EntryCriteria(null, false));

            assertThatCode(() -> StudyJoinRules.check(facts.build(), NOW, PROPERTIES)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("희망 직군")
    class TargetJobs {

        @Test
        @DisplayName("내 대분류가 희망 직군에 없으면 JOB_NOT_ALLOWED")
        void notAllowed() {
            assertThat(codeOf(passing().withMyJobCategory("PUBLIC"), NOW)).isEqualTo("JOB_NOT_ALLOWED");
        }

        @Test
        @DisplayName("희망 직군이 없으면 직군 무관이다")
        void anyJob() {
            Facts facts = passing().withTargetJobCodes(Set.of()).withMyJobCategory("PUBLIC");

            assertThatCode(() -> StudyJoinRules.check(facts.build(), NOW, PROPERTIES)).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("이미 참여 중이면 ALREADY_JOINED")
    void alreadyJoined() {
        assertThat(codeOf(passing().withAlreadyMember(true), NOW)).isEqualTo("ALREADY_JOINED");
    }

    @Test
    @DisplayName("여러 조건이 함께 어긋나면 PRD 4.4 순서(상태 → 정원 → 이용 제한 → 입장 기준 → 희망 직군 → 중복)상 앞선 오류가 나온다")
    void firstViolationInPrdOrderWins() {
        Facts all = passing()
                .withStatus(StudyStatus.ENDED)
                .withMemberCount(4)
                .withRestrictedUntil(RESTRICTED_UNTIL)
                .withStanding(AttendanceStanding.NEWCOMER)
                .withEntryCriteria(new Study.EntryCriteria(null, false))
                .withMyJobCategory("PUBLIC")
                .withAlreadyMember(true);

        assertThat(codeOf(all, NOW)).isEqualTo("STUDY_NOT_RECRUITING");
        Facts afterStatus = all.withStatus(StudyStatus.ACTIVE);
        assertThat(codeOf(afterStatus, NOW)).isEqualTo("STUDY_FULL");
        Facts afterCapacity = afterStatus.withMemberCount(2);
        assertThat(codeOf(afterCapacity, NOW)).isEqualTo("USER_RESTRICTED");
        Facts afterRestriction = afterCapacity.withRestrictedUntil(null);
        assertThat(codeOf(afterRestriction, NOW)).isEqualTo("ENTRY_CRITERIA_NOT_MET");
        Facts afterCriteria = afterRestriction.withEntryCriteria(new Study.EntryCriteria(null, true));
        assertThat(codeOf(afterCriteria, NOW)).isEqualTo("JOB_NOT_ALLOWED");
        Facts afterJob = afterCriteria.withMyJobCategory("IT");
        assertThat(codeOf(afterJob, NOW)).isEqualTo("ALREADY_JOINED");
    }

    private static String codeOf(Facts facts, Instant now) {
        return rejection(facts, now).errorCode().name();
    }

    private static BusinessException rejection(Facts facts, Instant now) {
        StudyJoinRules.JoinFacts built = facts.build();
        BusinessException ex = catchThrowableOfType(BusinessException.class,
                () -> StudyJoinRules.check(built, now, PROPERTIES));
        assertThat(ex).as("거절되어야 한다").isNotNull();
        return ex;
    }
}
