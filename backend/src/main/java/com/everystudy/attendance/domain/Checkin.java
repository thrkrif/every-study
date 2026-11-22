package com.everystudy.attendance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 체크인 한 건 (DATA-MODEL 5장). {@code (study_id, user_id)} 당 한 번이다. 원본 좌표는 저장하지 않고 판정에 쓴 거리·정확도·
 * 방식만 남긴다 (PRD 8장).
 */
@Entity
@Table(name = "checkin")
public class Checkin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "study_id", nullable = false)
    private Long studyId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "checked_in_at", nullable = false)
    private Instant checkedInAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private CheckinMethod method;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private CheckinResult result;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "distance_m")
    private Integer distanceM;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "accuracy_m")
    private Integer accuracyM;

    protected Checkin() {
    }

    public static Checkin of(long studyId, long userId, Instant checkedInAt, CheckinMethod method,
            CheckinResult result, Integer distanceM, Integer accuracyM) {
        Checkin checkin = new Checkin();
        checkin.studyId = studyId;
        checkin.userId = userId;
        checkin.checkedInAt = checkedInAt;
        checkin.method = method;
        checkin.result = result;
        checkin.distanceM = distanceM;
        checkin.accuracyM = accuracyM;
        return checkin;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getCheckedInAt() {
        return checkedInAt;
    }

    public CheckinMethod getMethod() {
        return method;
    }

    public CheckinResult getResult() {
        return result;
    }

    public Integer getDistanceM() {
        return distanceM;
    }
}
