package com.everystudy.study.repository;

import com.everystudy.study.domain.MyRole;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 내 스터디 목록 조회 (API 3장 {@code GET /me/studies}). 내가 {@code ACTIVE} 참여자인 스터디만 읽는다. 나간 스터디와
 * 대기 중인 스터디는 내 스터디가 아니다. 인덱스 {@code (user_id, status)} 에서 시작해 스터디로 조인한다.
 */
@Repository
public class MyStudyRepository {

    /** @param upcoming {@code true} 면 진행 중({@code ACTIVE}), {@code false} 면 끝난 것(종료·파투) */
    public record Condition(long userId, MyRole role, boolean upcoming, Cursor cursor, int size) {
    }

    /** 직전 페이지의 마지막 항목. */
    public record Cursor(Instant startAt, long id) {
    }

    /** @param role 이 스터디에서 내 역할 */
    public record Row(long id, String title, String topicCode, String districtCode, Instant startAt, int durationMin,
            int capacity, int memberCount, String status, MyRole role) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    MyStudyRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 조건에 맞는 스터디를 {@code size + 1} 개까지 읽는다 (다음 페이지가 있는지 알기 위해). 다가오는 목록은 시작이 빠른
     * 순, 지난 목록은 시작이 늦은 순(최근 것부터)이고 시작이 같으면 {@code id} 로 가른다.
     */
    public List<Row> find(Condition condition) {
        StringBuilder sql = new StringBuilder("""
                SELECT s.id, s.title, s.topic_code, s.district_code, s.start_at, s.duration_min, s.capacity, s.status,
                       CASE WHEN s.host_id = :userId THEN 'HOST' ELSE 'MEMBER' END AS my_role,
                       (SELECT COUNT(*) FROM study_member a WHERE a.study_id = s.id AND a.status = 'ACTIVE')
                           AS member_count
                FROM study_member me
                JOIN study s ON s.id = me.study_id
                WHERE me.user_id = :userId AND me.status = 'ACTIVE'
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", condition.userId())
                .addValue("limit", condition.size() + 1);

        sql.append(condition.upcoming() ? " AND s.status = 'ACTIVE'" : " AND s.status IN ('ENDED', 'CANCELLED')");
        if (condition.role() == MyRole.HOST) {
            sql.append(" AND s.host_id = :userId");
        } else if (condition.role() == MyRole.MEMBER) {
            sql.append(" AND s.host_id <> :userId");
        }
        Cursor cursor = condition.cursor();
        String comparison = condition.upcoming() ? ">" : "<";
        if (cursor != null) {
            sql.append(" AND (s.start_at ").append(comparison).append(" :cursorStart OR (s.start_at = :cursorStart AND s.id ")
                    .append(comparison).append(" :cursorId))");
            params.addValue("cursorStart", utc(cursor.startAt())).addValue("cursorId", cursor.id());
        }
        sql.append(condition.upcoming() ? " ORDER BY s.start_at ASC, s.id ASC" : " ORDER BY s.start_at DESC, s.id DESC");
        sql.append(" LIMIT :limit");
        return jdbc.query(sql.toString(), params, MyStudyRepository::row);
    }

    private static Row row(ResultSet rs, int rowNum) throws SQLException {
        return new Row(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("topic_code"),
                rs.getString("district_code"),
                rs.getObject("start_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                rs.getInt("duration_min"),
                rs.getInt("capacity"),
                rs.getInt("member_count"),
                rs.getString("status"),
                MyRole.valueOf(rs.getString("my_role")));
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
