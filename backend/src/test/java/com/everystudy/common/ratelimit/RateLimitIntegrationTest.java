package com.everystudy.common.ratelimit;

import static com.everystudy.auth.FakeSocialProvider.kakaoUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.everystudy.auth.FakeSocialProvider;
import com.everystudy.chat.service.ChatMessageService;
import com.everystudy.common.error.BusinessException;
import com.everystudy.common.error.CommonErrorCode;
import com.everystudy.support.IntegrationTestSupport;
import com.everystudy.support.MutableClock;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** 호출 제한 (PLAN T-064, D-5). 한도를 낮추고 시계를 시간 창의 가운데로 고정해 경계에 걸리지 않게 한다. */
@Import({FakeSocialProvider.class, RateLimitIntegrationTest.ClockConfig.class})
@TestPropertySource(properties = {"everystudy.rate-limit.report-per-hour=2",
        "everystudy.rate-limit.public-read-per-minute=2", "everystudy.rate-limit.chat-send-per-minute=2",
        "everystudy.rate-limit.join-per-minute=2", "everystudy.rate-limit.checkin-code-per-minute=2"})
class RateLimitIntegrationTest extends IntegrationTestSupport {

    @TestConfiguration
    static class ClockConfig {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock("2026-11-23T03:30:30Z");
        }
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ChatMessageService messages;

    private Cookie user;
    private Cookie csrf;
    private long userId;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        user = FakeSocialProvider.login(mvc, "kakao", kakaoUser(9101L, null)).getCookie("access_token");
        csrf = mvc.get().uri("/api/v1/me").cookie(user).exchange().getResponse().getCookie("XSRF-TOKEN");
        userId = jdbc.queryForObject("SELECT MAX(id) FROM user", Long.class);
    }

    @Test
    @DisplayName("신고는 시간당 한도(2)까지 처리하고 넘으면 429 RATE_LIMITED 와 Retry-After 를 준다")
    void reportsAreLimited() {
        assertThat(report()).hasStatus(404); // 한도 안: 대상이 없어 404 지만 제한에는 걸리지 않는다
        assertThat(report()).hasStatus(404);

        MvcTestResult limited = report();
        assertThat(limited).hasStatus(429).bodyJson().extractingPath("$.code").isEqualTo("RATE_LIMITED");
        assertThat(limited.getResponse().getHeader("Retry-After")).isEqualTo("1770");
    }

    @Test
    @DisplayName("참여 신청과 체크인 코드 입력도 회원별 분당 한도를 넘으면 429 다")
    void joinAndCheckinCodeAreLimited() {
        for (int i = 0; i < 2; i++) {
            assertThat(post("/api/v1/studies/999999/members", "{}").getResponse().getStatus()).isNotEqualTo(429);
            assertThat(post("/api/v1/studies/999999/checkins/code", "{\"code\":\"123456\"}").getResponse().getStatus())
                    .isNotEqualTo(429);
        }
        assertThat(post("/api/v1/studies/999999/members", "{}")).hasStatus(429);
        assertThat(post("/api/v1/studies/999999/checkins/code", "{\"code\":\"123456\"}")).hasStatus(429);
    }

    @Test
    @DisplayName("비회원의 스터디 목록·상세 조회는 IP 기준 분당 한도(2)를 넘으면 429 이고, 로그인한 회원은 제한하지 않는다")
    void publicReadsAreLimitedForAnonymousOnly() {
        assertThat(mvc.get().uri("/api/v1/studies").exchange()).hasStatus(200);
        assertThat(mvc.get().uri("/api/v1/studies/999999").exchange()).hasStatus(404);
        assertThat(mvc.get().uri("/api/v1/studies").exchange()).hasStatus(429);

        for (int i = 0; i < 5; i++) {
            assertThat(mvc.get().uri("/api/v1/studies").cookie(user).exchange()).hasStatus(200);
        }
    }

    @Test
    @DisplayName("채팅 전송은 회원당 분당 한도(2)를 넘으면 RATE_LIMITED 다")
    void chatSendIsLimited() {
        jdbc.update("INSERT INTO study (host_id, kind, title, description, topic_code, district_code, place_name, "
                + "place_address, latitude, longitude, start_at, duration_min, session_date, capacity, "
                + "min_attendance_rate, allow_newcomer, status, created_at, updated_at) VALUES (?, 'OFFLINE', '모각코', "
                + "'', 'DEVELOPMENT', 'SUWON_JANGAN', '카페', '수원', 37.3, 127.01, '2026-11-30 03:00:00', 120, "
                + "'2026-11-30', 4, NULL, TRUE, 'ACTIVE', NOW(6), NOW(6))", userId);
        long study = jdbc.queryForObject("SELECT MAX(id) FROM study", Long.class);
        jdbc.update("INSERT INTO chat_room (study_id, status, created_at) VALUES (?, 'OPEN', NOW(6))", study);
        jdbc.update("INSERT INTO study_member (study_id, user_id, status, joined_at) VALUES (?, ?, 'ACTIVE', NOW(6))",
                study, userId);

        messages.send(study, userId, "하나");
        messages.send(study, userId, "둘");

        assertThatThrownBy(() -> messages.send(study, userId, "셋"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).errorCode()).isEqualTo(CommonErrorCode.RATE_LIMITED));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_message", Integer.class)).isEqualTo(2);
    }

    private MvcTestResult report() {
        return post("/api/v1/reports", "{\"targetType\":\"STUDY\",\"targetId\":999999,\"reason\":\"ABUSE\"}");
    }

    private MvcTestResult post(String uri, String json) {
        return mvc.post().uri(uri).cookie(user, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json").content(json).exchange();
    }
}
