package com.everystudy.study;

import static com.everystudy.auth.FakeSocialProvider.kakaoUser;
import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.auth.FakeSocialProvider;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.study.event.StudyCreated;
import com.everystudy.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** 스터디 개설 (PLAN T-008, API 4장). 개설·주최자 참여·채팅방 생성이 한 트랜잭션인지 실제 DB 로 본다. */
@Import({FakeSocialProvider.class, StudyCreationIntegrationTest.FailAfterCreationConfig.class})
class StudyCreationIntegrationTest extends IntegrationTestSupport {

    private static final String STUDIES = "/api/v1/studies";

    /** 켜면 개설 이벤트를 받은 뒤(채팅방이 만들어진 뒤) 예외를 던져 트랜잭션을 실패시킨다. */
    static final AtomicBoolean FAIL_AFTER_CREATION = new AtomicBoolean();
    /** 실패시키기 직전에 본 채팅방 수. 채팅방이 실제로 만들어진 뒤 롤백됐는지 확인한다. */
    static final AtomicInteger CHAT_ROOMS_SEEN_BEFORE_FAILURE = new AtomicInteger(-1);

    @TestConfiguration(proxyBeanMethods = false)
    static class FailAfterCreationConfig {

        @Bean
        FailingListener failingListener(JdbcTemplate jdbc) {
            return new FailingListener(jdbc);
        }
    }

    static class FailingListener {

        private final JdbcTemplate jdbc;

        FailingListener(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @EventListener
        @Order(Ordered.LOWEST_PRECEDENCE)
        void failAfterChatRoomCreated(StudyCreated event) {
            if (FAIL_AFTER_CREATION.get()) {
                CHAT_ROOMS_SEEN_BEFORE_FAILURE.set(
                        jdbc.queryForObject("SELECT COUNT(*) FROM chat_room", Integer.class));
                throw new IllegalStateException("테스트: 개설 직후 실패");
            }
        }
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    private Cookie access;
    private Cookie csrf;

    @BeforeEach
    void loginAsOnboardedUser() {
        cleanDatabase(jdbc);
        FAIL_AFTER_CREATION.set(false);
        CHAT_ROOMS_SEEN_BEFORE_FAILURE.set(-1);
        access = FakeSocialProvider.login(mvc, "kakao", kakaoUser(1001L, null)).getCookie("access_token");
        csrf = mvc.get().uri("/api/v1/me").cookie(access).exchange().getResponse().getCookie("XSRF-TOKEN");
        jdbc.update("UPDATE user SET nickname = '주최자', job_code = 'IT', district_code = 'SEOUL'");
    }

    @AfterEach
    void resetFailure() {
        FAIL_AFTER_CREATION.set(false);
    }

    @Test
    @DisplayName("개설하면 201 과 주소를 주고, 스터디·희망 직군·주최자 참여·채팅방이 함께 만들어진다")
    void createsStudyWithHostMemberAndChatRoom() {
        ZonedDateTime start = kstAt(2, LocalTime.of(19, 30));

        MvcTestResult result = create(body(start, 120, "[\"IT\", \"PUBLIC\"]"));

        assertThat(result).hasStatus(201);
        long id = jdbc.queryForObject("SELECT id FROM study", Long.class);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/v1/studies/" + id);
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo((int) id);

        Map<String, Object> study = jdbc.queryForMap(
                "SELECT host_id, kind, title, status, capacity, duration_min, session_date, min_attendance_rate, "
                        + "allow_newcomer, latitude FROM study");
        assertThat(study)
                .containsEntry("host_id", userId())
                .containsEntry("kind", "OFFLINE")
                .containsEntry("title", "모각코")
                .containsEntry("status", "ACTIVE")
                .containsEntry("min_attendance_rate", 70)
                .containsEntry("allow_newcomer", true);
        assertThat(((Number) study.get("capacity")).intValue()).isEqualTo(4);
        assertThat(((Number) study.get("duration_min")).intValue()).isEqualTo(120);
        assertThat(study.get("session_date").toString()).isEqualTo(start.toLocalDate().toString());
        assertThat(study.get("latitude").toString()).isEqualTo("37.300000");
        assertThat(jdbc.queryForList("SELECT job_code FROM study_target_job ORDER BY job_code", String.class))
                .containsExactly("IT", "PUBLIC");
        assertThat(jdbc.queryForMap("SELECT study_id, user_id, status FROM study_member"))
                .containsEntry("study_id", id).containsEntry("user_id", userId()).containsEntry("status", "ACTIVE");
        assertThat(jdbc.queryForMap("SELECT study_id, status FROM chat_room"))
                .containsEntry("study_id", id).containsEntry("status", "OPEN");
    }

    @Test
    @DisplayName("세션 날짜는 KST 날짜다 (00:30 KST 시작이면 UTC 로는 전날)")
    void sessionDateIsKst() {
        ZonedDateTime start = kstAt(3, LocalTime.of(0, 30));

        assertThat(create(body(start, 60, "[]"))).hasStatus(201);

        assertThat(jdbc.queryForObject("SELECT session_date FROM study", LocalDate.class))
                .isEqualTo(start.toLocalDate());
    }

    @Test
    @DisplayName("희망 직군을 비우면 행이 없고(직군 무관), 신규 허용은 생략하면 허용이다")
    void emptyTargetJobsMeansAnyone() {
        String body = body(kstAt(2, LocalTime.of(10, 0)), 60, "[]")
                .replace(", \"allowNewcomer\": true", "").replace("\"minAttendanceRate\": 70", "\"minAttendanceRate\": null");

        assertThat(create(body)).hasStatus(201);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM study_target_job", Integer.class)).isZero();
        assertThat(jdbc.queryForMap("SELECT min_attendance_rate, allow_newcomer FROM study"))
                .containsEntry("min_attendance_rate", null).containsEntry("allow_newcomer", true);
    }

    @Test
    @DisplayName("소개는 선택이다. 생략하거나 공백만 쓰면 빈 문자열로 저장하고, 2000자를 넘으면 400 이다")
    void descriptionIsOptional() {
        String base = body(kstAt(2, LocalTime.of(10, 0)), 60, "[]");
        String sample = "\"description\": \"같이 공부해요. 조용히 집중하는 모임입니다.\",";
        assertThat(base).contains(sample); // 바꿔치기가 조용히 빗나가지 않게 한다

        assertThat(create(base.replace(sample, ""))).hasStatus(201);
        assertThat(create(base.replace(sample, "\"description\": \"   \","))).hasStatus(201);
        MvcTestResult tooLong = create(base.replace(sample, "\"description\": \"" + "가".repeat(2001) + "\","));

        assertThat(jdbc.queryForList("SELECT description FROM study", String.class)).containsExactly("", "");
        assertThat(tooLong).hasStatus(400).bodyJson().extractingPath("$.errors[*].field").asArray()
                .containsExactly("description");
    }

    @Test
    @DisplayName("개설 중 실패하면 이미 만든 채팅방까지 모두 롤백된다")
    void failureRollsBackChatRoom() {
        FAIL_AFTER_CREATION.set(true);

        MvcTestResult result = create(body(kstAt(2, LocalTime.of(10, 0)), 60, "[]"));

        assertThat(result).hasStatus(500);
        assertThat(CHAT_ROOMS_SEEN_BEFORE_FAILURE.get()).isEqualTo(1);
        assertThat(count("chat_room")).isZero();
        assertThat(count("study")).isZero();
        assertThat(count("study_member")).isZero();
        assertThat(count("study_target_job")).isZero();
    }

    @Test
    @DisplayName("온보딩 전 회원은 403 ONBOARDING_REQUIRED 이고 아무것도 만들지 않는다")
    void requiresOnboarding() {
        jdbc.update("UPDATE user SET nickname = NULL");

        MvcTestResult result = create(body(kstAt(2, LocalTime.of(10, 0)), 60, "[]"));

        assertThat(result).hasStatus(403).bodyJson().extractingPath("$.code").isEqualTo("ONBOARDING_REQUIRED");
        assertThat(count("study")).isZero();
    }

    @Test
    @DisplayName("시각·정원 규칙과 형식 오류(세부 직무 희망 직군 포함)는 400 VALIDATION_FAILED 이고 필드를 알려 준다")
    void invalidInput() {
        MvcTestResult tooFar = create(body(kstAt(15, LocalTime.of(10, 0)), 60, "[]"));
        MvcTestResult crossesMidnight = create(body(kstAt(2, LocalTime.of(23, 0)), 70, "[]"));
        MvcTestResult badFormat = create(body(kstAt(2, LocalTime.of(10, 0)), 60, "[\"IT_BACKEND\", \"ANY\"]")
                .replace("\"SEOUL\"", "\"SUWON\"").replace("\"모각코\"", "\"가\""));

        assertThat(tooFar).hasStatus(400).bodyJson().extractingPath("$.errors[*].field").asArray()
                .containsExactly("startAt");
        assertThat(crossesMidnight).hasStatus(400).bodyJson().extractingPath("$.errors[*].field").asArray()
                .containsExactly("durationMin");
        assertThat(badFormat).hasStatus(400).bodyJson().extractingPath("$.errors[*].field").asArray()
                .containsExactlyInAnyOrder("title", "districtCode", "targetJobCodes[0]", "targetJobCodes[1]");
        assertThat(count("study")).isZero();
    }

    @Test
    @DisplayName("로그인하지 않으면 401 이다")
    void requiresLogin() {
        assertThat(mvc.post().uri(STUDIES).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(kstAt(2, LocalTime.of(10, 0)), 60, "[]")))
                .hasStatus(401);
    }

    private MvcTestResult create(String body) {
        return mvc.post().uri(STUDIES)
                .cookie(access, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    /** 오늘(KST)부터 {@code days} 일 뒤의 KST 시각. */
    private static ZonedDateTime kstAt(int days, LocalTime time) {
        return LocalDate.now(TimeProvider.KOREA).plusDays(days).atTime(time).atZone(TimeProvider.KOREA);
    }

    private static String body(ZonedDateTime start, int durationMin, String targetJobCodes) {
        return """
                {"title": "모각코", "description": "같이 공부해요. 조용히 집중하는 모임입니다.",
                 "topicCode": "DEVELOPMENT", "districtCode": "SEOUL", "targetJobCodes": %s,
                 "place": {"name": "스터디 카페", "address": "경기 수원시 장안구", "latitude": 37.3, "longitude": 127.0100004},
                 "startAt": "%s", "durationMin": %d, "capacity": 4,
                 "minAttendanceRate": 70, "allowNewcomer": true}
                """.formatted(targetJobCodes, start.toInstant(), durationMin);
    }

    private long userId() {
        return jdbc.queryForObject("SELECT id FROM user", Long.class);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
