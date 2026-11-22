package com.everystudy.common.audit;

import static com.everystudy.auth.FakeSocialProvider.kakaoUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.everystudy.auth.FakeSocialProvider;
import com.everystudy.support.IntegrationTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** 감사 로그 기록 (PLAN T-019, DATA-MODEL 9장). 호출한 쪽 트랜잭션과 함께 커밋·롤백되는지 실제 DB 로 본다. */
@Import(FakeSocialProvider.class)
class AuditLogIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AuditLogger audit;

    @Autowired
    TransactionTemplate tx;

    private long operatorId;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        FakeSocialProvider.login(mvc, "kakao", kakaoUser(3001L, null));
        operatorId = userIdOf(3001L);
    }

    @Test
    @DisplayName("커밋되면 행위자·대상·변경 전후 값이 남는다")
    void recordsDetail() {
        tx.executeWithoutResult(status -> audit.record(operatorId, AuditAction.SANCTION_REVOKE, "USER", 77L,
                Map.of("before", Map.of("endsAt", "2026-12-01T00:00:00Z"), "after", Map.of("revoked", true))));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT actor_id, action, target_type, target_id, detail, created_at FROM audit_log");
        assertThat(row).containsEntry("actor_id", operatorId)
                .containsEntry("action", "SANCTION_REVOKE")
                .containsEntry("target_type", "USER")
                .containsEntry("target_id", 77L);
        assertThat(row.get("created_at")).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> detail = JsonMapper.builder().build().readValue(row.get("detail").toString(), Map.class);
        assertThat(detail).containsKeys("before", "after");
        assertThat(((Map<?, ?>) detail.get("before")).get("endsAt")).isEqualTo("2026-12-01T00:00:00Z");
        assertThat(((Map<?, ?>) detail.get("after")).get("revoked")).isEqualTo(true);
    }

    @Test
    @DisplayName("시스템 행위는 행위자가 NULL 이고 detail 도 비울 수 있다")
    void systemActorWithoutDetail() {
        tx.executeWithoutResult(status -> audit.record(null, AuditAction.HOST_PENALTY, "STUDY", 5L, null));

        Map<String, Object> row = jdbc.queryForMap("SELECT actor_id, detail FROM audit_log");
        assertThat(row).containsEntry("actor_id", null).containsEntry("detail", null);
    }

    @Test
    @DisplayName("호출한 트랜잭션이 롤백되면 감사 로그도 남지 않는다")
    void rolledBackWithCaller() {
        tx.executeWithoutResult(status -> {
            audit.record(null, AuditAction.NO_SHOW_CONFIRM, "STUDY", 5L, Map.of("userId", 1));
            status.setRollbackOnly();
        });

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Long.class)).isZero();
    }

    @Test
    @DisplayName("트랜잭션 밖에서 부르면 기록하지 않고 실패한다")
    void requiresTransaction() {
        assertThatThrownBy(() -> audit.record(null, AuditAction.HOST_PENALTY, "STUDY", 5L, null))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(jdbc.queryForList("SELECT id FROM audit_log")).isEqualTo(List.of());
    }

    private long userIdOf(long kakaoId) {
        return jdbc.queryForObject("SELECT user_id FROM social_account WHERE provider_user_id = ?", Long.class,
                Long.toString(kakaoId));
    }
}
