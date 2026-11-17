package com.everystudy.study;

import static com.everystudy.auth.FakeSocialProvider.kakaoUser;
import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.auth.FakeSocialProvider;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.support.IntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 스터디 목록·상세·참여자 목록 (PLAN T-009, API 4장). 스터디는 시각을 마음대로 정하려고 SQL 로 직접 넣는다.
 * 모든 시각은 실제 현재 시각에서 떨어진 거리로 만든다.
 */
@Import(FakeSocialProvider.class)
class StudyQueryIntegrationTest extends IntegrationTestSupport {

    private static final String STUDIES = "/api/v1/studies";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private long hostId;
    private long memberId;
    private long outsiderId;
    private Cookie hostCookie;
    private Cookie memberCookie;
    private Cookie outsiderCookie;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        hostCookie = login(1001L);
        memberCookie = login(1002L);
        outsiderCookie = login(1003L);
        hostId = userIdOf(1001L);
        memberId = userIdOf(1002L);
        outsiderId = userIdOf(1003L);
        onboard(hostId, "주최자", "IT");
        onboard(memberId, "참여자", "IT_BACKEND");
        onboard(outsiderId, "행정준비생", "PUBLIC_ADMIN");
    }

    @Nested
    @DisplayName("목록")
    class List_ {

        @Test
        @DisplayName("기본은 모집 중만. 마감 보기를 켜면 시작 전 마감 스터디도 나오고, 시작했거나 끝난 스터디는 어느 쪽에도 없다")
        void recruitingByDefaultAndClosedOnRequest() {
            long open = study("모집중", "SUWON_JANGAN", inHours(48), 4, List.of());
            long full = study("정원참", "SUWON_JANGAN", inHours(48), 2, List.of());
            join(full, memberId);
            long checkinOpened = study("체크인열림", "SUWON_JANGAN", now.plus(Duration.ofMinutes(5)), 4, List.of());
            study("시작함", "SUWON_JANGAN", now.minus(Duration.ofMinutes(30)), 4, List.of());
            long ended = study("끝남", "SUWON_JANGAN", inHours(48), 4, List.of());
            jdbc.update("UPDATE study SET status = 'ENDED' WHERE id = ?", ended);
            long cancelled = study("파투", "SUWON_JANGAN", inHours(48), 4, List.of());
            jdbc.update("UPDATE study SET status = 'CANCELLED' WHERE id = ?", cancelled);

            assertThat(ids(mvc.get().uri(STUDIES).exchange())).containsExactly(open);
            assertThat(ids(mvc.get().uri(STUDIES).param("includeClosed", "true").exchange()))
                    .containsExactly(checkinOpened, full, open);
        }

        @Test
        @DisplayName("카드에 참여자 수, 모집 여부, 희망 직군, 입장 기준, 주최자를 담는다")
        void summaryFields() {
            long id = study("모각코", "SUWON_YEONGTONG", inHours(30), 4, List.of("PUBLIC", "IT"));
            join(id, memberId);

            MvcTestResult result = mvc.get().uri(STUDIES).exchange();

            assertThat(result).hasStatusOk().bodyJson().isStrictlyEqualTo(String.format("""
                    {"nextCursor": null, "items": [{"id": %d, "title": "모각코", "topicCode": "DEVELOPMENT", "districtCode": "SUWON_YEONGTONG",
                     "startAt": "%s", "durationMin": 120, "capacity": 4, "memberCount": 2, "recruiting": true,
                     "targetJobCodes": ["IT", "PUBLIC"],
                     "entryCriteria": {"minAttendanceRate": 70, "allowNewcomer": true},
                     "host": {"userId": %d, "nickname": "주최자",
                              "stats": {"hostedCount": 0, "leftWithinWindowCount": 0, "showCountOnly": true}}}]}
                    """, id, inHours(30), hostId));
        }

        @Test
        @DisplayName("지역은 시·도·시·구 어느 단계로든 거른다")
        void regionFilter() {
            long jangan = study("장안", "SUWON_JANGAN", inHours(30), 4, List.of());
            long paldal = study("팔달", "SUWON_PALDAL", inHours(31), 4, List.of());

            assertThat(ids(mvc.get().uri(STUDIES).param("regionCode", "SUWON_PALDAL").exchange()))
                    .containsExactly(paldal);
            assertThat(ids(mvc.get().uri(STUDIES).param("regionCode", "SUWON").exchange()))
                    .containsExactly(paldal, jangan);
            assertThat(ids(mvc.get().uri(STUDIES).param("regionCode", "GYEONGGI").exchange()))
                    .containsExactly(paldal, jangan);
        }

        @Test
        @DisplayName("직군 필터는 그 직군이 명시된 스터디만, 고르지 않으면 전체다")
        void jobFilter() {
            long anyone = study("누구나", "SUWON_JANGAN", inHours(30), 4, List.of());
            long it = study("IT", "SUWON_JANGAN", inHours(31), 4, List.of("IT"));
            long publicOnly = study("공무원", "SUWON_JANGAN", inHours(32), 4, List.of("PUBLIC"));

            assertThat(ids(mvc.get().uri(STUDIES).param("jobCode", "IT").exchange())).containsExactly(it);
            assertThat(ids(mvc.get().uri(STUDIES).exchange())).containsExactly(publicOnly, it, anyone);
        }

        @Test
        @DisplayName("내 직군 필터는 내 대분류가 신청할 수 있는 스터디(직군 무관 포함)만 보여 준다")
        void myJobFilter() {
            long anyone = study("누구나", "SUWON_JANGAN", inHours(30), 4, List.of());
            long it = study("IT", "SUWON_JANGAN", inHours(31), 4, List.of("IT", "DESIGN"));
            study("공무원", "SUWON_JANGAN", inHours(32), 4, List.of("PUBLIC"));

            // 참여자의 직군은 세부 직무(IT_BACKEND)지만 대분류(IT)로 판단한다.
            assertThat(ids(mvc.get().uri(STUDIES).param("myJob", "true").cookie(memberCookie).exchange()))
                    .containsExactly(it, anyone);
        }

        @Test
        @DisplayName("내 직군 필터는 로그인이 필요하고, 온보딩 전이면 403 ONBOARDING_REQUIRED 다")
        void myJobRequiresLoginAndJob() {
            jdbc.update("UPDATE user SET job_code = NULL WHERE id = ?", outsiderId);

            assertThat(mvc.get().uri(STUDIES).param("myJob", "true"))
                    .hasStatus(401).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
            assertThat(mvc.get().uri(STUDIES).param("myJob", "true").cookie(outsiderCookie))
                    .hasStatus(403).bodyJson().extractingPath("$.code").isEqualTo("ONBOARDING_REQUIRED");
        }

        @Test
        @DisplayName("주제로 거른다")
        void topicFilter() {
            study("개발", "SUWON_JANGAN", inHours(30), 4, List.of());
            long language = study("어학", "SUWON_JANGAN", inHours(31), 4, List.of());
            jdbc.update("UPDATE study SET topic_code = 'LANGUAGE' WHERE id = ?", language);

            assertThat(ids(mvc.get().uri(STUDIES).param("topicCode", "LANGUAGE").exchange())).containsExactly(language);
        }

        @Test
        @DisplayName("최신순은 개설 순의 역순, 마감임박순은 시작이 빠른 순이고 커서로 끝까지 이어진다")
        void sortAndCursor() {
            long a = study("A", "SUWON_JANGAN", inHours(50), 4, List.of());
            long b = study("B", "SUWON_JANGAN", inHours(30), 4, List.of());
            long c = study("C", "SUWON_JANGAN", inHours(40), 4, List.of());

            assertThat(allPages("latest")).containsExactly(c, b, a);
            assertThat(allPages("deadline")).containsExactly(b, c, a);
        }

        @Test
        @DisplayName("시작 시각이 같으면 마감임박순은 ID 순으로 이어지고 빠지거나 겹치는 항목이 없다")
        void deadlineCursorWithTies() {
            Instant same = inHours(30);
            long a = study("A", "SUWON_JANGAN", same, 4, List.of());
            long b = study("B", "SUWON_JANGAN", same, 4, List.of());
            long c = study("C", "SUWON_JANGAN", same, 4, List.of());

            assertThat(allPages("deadline")).containsExactly(a, b, c);
        }

        @Test
        @DisplayName("잘못된 필터·정렬·크기·커서는 400 VALIDATION_FAILED 다")
        void invalidParameters() {
            study("A", "SUWON_JANGAN", inHours(30), 4, List.of());
            study("B", "SUWON_JANGAN", inHours(31), 4, List.of());
            String cursor = nextCursor(mvc.get().uri(STUDIES).param("size", "1").exchange());

            assertValidationFailed(mvc.get().uri(STUDIES).param("regionCode", "SEOUL").exchange(), "regionCode");
            assertValidationFailed(mvc.get().uri(STUDIES).param("jobCode", "IT_BACKEND").exchange(), "jobCode");
            assertValidationFailed(mvc.get().uri(STUDIES).param("sort", "popular").exchange(), "sort");
            assertValidationFailed(mvc.get().uri(STUDIES).param("size", "51").exchange(), "size");
            assertValidationFailed(mvc.get().uri(STUDIES).param("size", "0").exchange(), "size");
            assertValidationFailed(mvc.get().uri(STUDIES).param("sort", "deadline").param("cursor", cursor).exchange(),
                    "cursor");
        }

        private List<Long> allPages(String sort) {
            List<Long> all = new ArrayList<>();
            String cursor = null;
            int guard = 0;
            do {
                var request = mvc.get().uri(STUDIES).param("sort", sort).param("size", "2");
                if (cursor != null) {
                    request = request.param("cursor", cursor);
                }
                MvcTestResult page = request.exchange();
                assertThat(page).hasStatusOk();
                all.addAll(ids(page));
                cursor = nextCursor(page);
            } while (cursor != null && ++guard < 10);
            return all;
        }
    }

    @Nested
    @DisplayName("상세")
    class Detail {

        @Test
        @DisplayName("비회원도 보고, 장소·체크인 시각·참여자(지표는 신규)·내 상태 NONE 을 받는다. 대기 인원은 없다")
        void anonymous() {
            Instant start = inHours(30);
            long id = study("모각코", "SUWON_JANGAN", start, 4, List.of("IT"));
            join(id, memberId);

            MvcTestResult result = mvc.get().uri(STUDIES + "/" + id).exchange();

            assertThat(result).hasStatusOk().bodyJson().isLenientlyEqualTo(String.format("""
                    {"id": %d, "title": "모각코", "description": "같이 공부해요. 조용히 집중하는 모임입니다.",
                     "place": {"name": "스터디 카페", "address": "경기 수원시 장안구", "latitude": 37.3, "longitude": 127.01},
                     "startAt": "%s", "status": "ACTIVE", "recruiting": true, "capacity": 4, "memberCount": 2,
                     "targetJobCodes": ["IT"], "myState": "NONE", "scheduleEditable": false,
                     "checkinOpensAt": "%s", "checkinClosesAt": "%s",
                     "host": {"userId": %d, "nickname": "주최자"},
                     "members": [
                       {"userId": %d, "nickname": "주최자",
                        "trust": {"attendanceRate": null, "windowCount": 0, "isNewcomer": true}},
                       {"userId": %d, "nickname": "참여자", "trust": {"isNewcomer": true}}]}
                    """, id, start, start.minus(Duration.ofMinutes(10)), start.plus(Duration.ofMinutes(30)),
                    hostId, hostId, memberId));
            assertThat(result).bodyJson().doesNotHavePath("$.waitingCount");
        }

        @Test
        @DisplayName("주최자는 HOST 와 대기 인원을, 참여자는 MEMBER 를, 다른 회원은 NONE 을 받는다")
        void myStateByViewer() {
            long id = study("모각코", "SUWON_JANGAN", inHours(30), 4, List.of());
            join(id, memberId);

            assertThat(mvc.get().uri(STUDIES + "/" + id).cookie(hostCookie)).bodyJson()
                    .isLenientlyEqualTo("{\"myState\": \"HOST\", \"waitingCount\": 0}");
            assertThat(mvc.get().uri(STUDIES + "/" + id).cookie(memberCookie)).bodyJson()
                    .extractingPath("$.myState").isEqualTo("MEMBER");
            MvcTestResult outsider = mvc.get().uri(STUDIES + "/" + id).cookie(outsiderCookie).exchange();
            assertThat(outsider).bodyJson().extractingPath("$.myState").isEqualTo("NONE");
            assertThat(outsider).bodyJson().doesNotHavePath("$.waitingCount");
        }

        @Test
        @DisplayName("주최자 혼자일 때만 일정을 고칠 수 있다고 알려 준다")
        void scheduleEditable() {
            long id = study("모각코", "SUWON_JANGAN", inHours(30), 4, List.of());

            assertThat(mvc.get().uri(STUDIES + "/" + id)).bodyJson()
                    .extractingPath("$.scheduleEditable").isEqualTo(true);
            join(id, memberId);
            assertThat(mvc.get().uri(STUDIES + "/" + id)).bodyJson()
                    .extractingPath("$.scheduleEditable").isEqualTo(false);
        }

        @Test
        @DisplayName("끝난 스터디도 주소로 열면 보이고 모집 중이 아니다")
        void endedStudyIsVisible() {
            long id = study("끝남", "SUWON_JANGAN", inHours(30), 4, List.of());
            jdbc.update("UPDATE study SET status = 'ENDED' WHERE id = ?", id);

            assertThat(mvc.get().uri(STUDIES + "/" + id)).hasStatusOk().bodyJson()
                    .isLenientlyEqualTo("{\"status\": \"ENDED\", \"recruiting\": false}");
        }

        @Test
        @DisplayName("탈퇴한 주최자는 닉네임 없이 보인다")
        void withdrawnHost() {
            long id = study("모각코", "SUWON_JANGAN", inHours(30), 4, List.of());
            jdbc.update("UPDATE user SET status = 'WITHDRAWN' WHERE id = ?", hostId);

            assertThat(mvc.get().uri(STUDIES + "/" + id)).bodyJson()
                    .extractingPath("$.host.nickname").isNull();
        }

        @Test
        @DisplayName("없는 스터디는 404 NOT_FOUND 다")
        void notFound() {
            assertThat(mvc.get().uri(STUDIES + "/999999"))
                    .hasStatus(404).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("참여자 목록")
    class Members {

        @Test
        @DisplayName("참여자(주최자 포함)는 참여 순서대로 본다")
        void membersCanSee() {
            long id = study("모각코", "SUWON_JANGAN", inHours(30), 4, List.of());
            join(id, memberId);

            assertThat(mvc.get().uri(STUDIES + "/" + id + "/members").cookie(memberCookie))
                    .hasStatusOk().bodyJson().extractingPath("$[*].userId").asArray()
                    .containsExactly((int) hostId, (int) memberId);
            assertThat(mvc.get().uri(STUDIES + "/" + id + "/members").cookie(hostCookie)).hasStatusOk();
        }

        @Test
        @DisplayName("참여자가 아니면 403 FORBIDDEN, 비로그인은 401, 나간 참여자도 403 이다")
        void othersCannotSee() {
            long id = study("모각코", "SUWON_JANGAN", inHours(30), 4, List.of());
            join(id, memberId);
            jdbc.update("UPDATE study_member SET status = 'LEFT' WHERE user_id = ?", memberId);

            assertThat(mvc.get().uri(STUDIES + "/" + id + "/members").cookie(outsiderCookie))
                    .hasStatus(403).bodyJson().extractingPath("$.code").isEqualTo("FORBIDDEN");
            assertThat(mvc.get().uri(STUDIES + "/" + id + "/members").cookie(memberCookie)).hasStatus(403);
            assertThat(mvc.get().uri(STUDIES + "/" + id + "/members")).hasStatus(401);
        }
    }

    // ---- 준비 도구

    private Instant inHours(int hours) {
        return now.plus(Duration.ofHours(hours)).truncatedTo(ChronoUnit.MINUTES);
    }

    private Cookie login(long kakaoId) {
        return FakeSocialProvider.login(mvc, "kakao", kakaoUser(kakaoId, null)).getCookie("access_token");
    }

    private long userIdOf(long kakaoId) {
        return jdbc.queryForObject("SELECT user_id FROM social_account WHERE provider_user_id = ?", Long.class,
                Long.toString(kakaoId));
    }

    private void onboard(long userId, String nickname, String jobCode) {
        jdbc.update("UPDATE user SET nickname = ?, job_code = ?, district_code = 'SUWON_JANGAN' WHERE id = ?",
                nickname, jobCode, userId);
    }

    /** 주최자가 연 스터디 하나와 주최자 참여 행을 넣는다. */
    private long study(String title, String district, Instant startAt, int capacity, List<String> targetJobs) {
        LocalDateTime start = LocalDateTime.ofInstant(startAt, ZoneOffset.UTC);
        LocalDateTime created = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO study (host_id, kind, title, description, topic_code, district_code, place_name,
                    place_address, latitude, longitude, start_at, duration_min, session_date, capacity,
                    min_attendance_rate, allow_newcomer, status, created_at, updated_at)
                VALUES (?, 'OFFLINE', ?, '같이 공부해요. 조용히 집중하는 모임입니다.', 'DEVELOPMENT', ?, '스터디 카페',
                    '경기 수원시 장안구', 37.300000, 127.010000, ?, 120, ?, ?, 70, TRUE, 'ACTIVE', ?, ?)
                """, hostId, title, district, start,
                startAt.atZone(TimeProvider.KOREA).toLocalDate(), capacity, created, created);
        long id = jdbc.queryForObject("SELECT MAX(id) FROM study", Long.class);
        join(id, hostId);
        targetJobs.forEach(job -> jdbc.update("INSERT INTO study_target_job (study_id, job_code) VALUES (?, ?)", id, job));
        return id;
    }

    private void join(long studyId, long userId) {
        jdbc.update("INSERT INTO study_member (study_id, user_id, status, joined_at) VALUES (?, ?, 'ACTIVE', ?)",
                studyId, userId, LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
    }

    private static List<Long> ids(MvcTestResult result) {
        assertThat(result).hasStatusOk();
        return JsonPath.<List<Number>>read(body(result), "$.items[*].id").stream().map(Number::longValue).toList();
    }

    private static String nextCursor(MvcTestResult result) {
        return JsonPath.read(body(result), "$.nextCursor");
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void assertValidationFailed(MvcTestResult result, String field) {
        assertThat(result).hasStatus(400).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray().contains(field);
    }
}
