package com.everystudy.study.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 공개 목록 조회 (API 4장 {@code GET /studies}). 조건이 많고 참여자 수를 함께 세야 해서 SQL 로 직접 쓴다.
 * 인덱스 {@code (status, district_code, start_at)} 를 타도록 상태·시작 시각 조건을 항상 넣는다 (DATA-MODEL 4장).
 */
@Repository
public class StudySearchRepository {

    private static final String ACTIVE_MEMBERS =
            "(SELECT COUNT(*) FROM study_member m WHERE m.study_id = s.id AND m.status = 'ACTIVE')";

    private final NamedParameterJdbcTemplate jdbc;

    StudySearchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 조건에 맞는 스터디를 {@code size + 1} 개까지 읽는다 (다음 페이지가 있는지 알기 위해). */
    public List<StudySummaryRow> search(StudySearchCondition condition) {
        StringBuilder sql = new StringBuilder("""
                SELECT s.id, s.host_id, s.title, s.topic_code, s.district_code, s.start_at, s.duration_min,
                       s.capacity, s.min_attendance_rate, s.allow_newcomer,
                """).append(ACTIVE_MEMBERS).append("""
                 AS member_count
                FROM study s
                WHERE s.status = 'ACTIVE' AND s.start_at > :now
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("now", utc(condition.now()))
                .addValue("limit", condition.size() + 1);

        if (!condition.districtCodes().isEmpty()) {
            sql.append(" AND s.district_code IN (:districts)");
            params.addValue("districts", condition.districtCodes());
        }
        if (condition.topicCode() != null) {
            sql.append(" AND s.topic_code = :topic");
            params.addValue("topic", condition.topicCode());
        }
        if (condition.targetJobCategory() != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM study_target_job t WHERE t.study_id = s.id AND t.job_code = :targetJob)");
            params.addValue("targetJob", condition.targetJobCategory());
        }
        if (condition.eligibleJobCategory() != null) {
            sql.append(" AND (NOT EXISTS (SELECT 1 FROM study_target_job t WHERE t.study_id = s.id)")
                    .append(" OR EXISTS (SELECT 1 FROM study_target_job t WHERE t.study_id = s.id AND t.job_code = :myJob))");
            params.addValue("myJob", condition.eligibleJobCategory());
        }
        if (!condition.includeClosed()) {
            sql.append(" AND s.start_at > :recruitingDeadline AND ").append(ACTIVE_MEMBERS).append(" < s.capacity");
            params.addValue("recruitingDeadline", utc(condition.recruitingDeadline()));
        }

        StudySearchCondition.Cursor cursor = condition.cursor();
        if (condition.sort() == StudySearchCondition.Sort.LATEST) {
            if (cursor != null) {
                sql.append(" AND s.id < :cursorId");
                params.addValue("cursorId", cursor.id());
            }
            sql.append(" ORDER BY s.id DESC");
        } else {
            if (cursor != null) {
                sql.append(" AND (s.start_at > :cursorStart OR (s.start_at = :cursorStart AND s.id > :cursorId))");
                params.addValue("cursorStart", utc(cursor.startAt())).addValue("cursorId", cursor.id());
            }
            sql.append(" ORDER BY s.start_at ASC, s.id ASC");
        }
        sql.append(" LIMIT :limit");

        List<StudySummaryRow> rows = jdbc.query(sql.toString(), params, StudySearchRepository::row);
        return withTargetJobs(rows);
    }

    private List<StudySummaryRow> withTargetJobs(List<StudySummaryRow> rows) {
        if (rows.isEmpty()) {
            return rows;
        }
        Map<Long, List<String>> jobs = new HashMap<>();
        jdbc.query("SELECT study_id, job_code FROM study_target_job WHERE study_id IN (:ids) ORDER BY job_code",
                new MapSqlParameterSource("ids", rows.stream().map(StudySummaryRow::id).toList()),
                rs -> {
                    jobs.computeIfAbsent(rs.getLong("study_id"), id -> new ArrayList<>()).add(rs.getString("job_code"));
                });
        return rows.stream()
                .map(r -> new StudySummaryRow(r.id(), r.hostId(), r.title(), r.topicCode(), r.districtCode(),
                        r.startAt(), r.durationMin(), r.capacity(), r.memberCount(), r.minAttendanceRate(),
                        r.allowNewcomer(), jobs.getOrDefault(r.id(), List.of())))
                .toList();
    }

    private static StudySummaryRow row(ResultSet rs, int rowNum) throws SQLException {
        int rate = rs.getInt("min_attendance_rate");
        Integer minAttendanceRate = rs.wasNull() ? null : rate;
        return new StudySummaryRow(
                rs.getLong("id"),
                rs.getLong("host_id"),
                rs.getString("title"),
                rs.getString("topic_code"),
                rs.getString("district_code"),
                rs.getObject("start_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                rs.getInt("duration_min"),
                rs.getInt("capacity"),
                rs.getInt("member_count"),
                minAttendanceRate,
                rs.getBoolean("allow_newcomer"),
                List.of());
    }

    /** DATETIME 에는 UTC 시각을 저장한다 (ARCHITECTURE 5장). JVM 시간대와 상관없이 값 그대로 넘긴다. */
    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
