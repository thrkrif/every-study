package com.everystudy.study.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 스터디 = 하루 한 번의 모임 (DATA-MODEL 4장, PRD 4.2). 일정과 장소를 이 행에 둔다.
 * 입력 검증(범위·단위·시각)은 만들기 전에 끝낸다. 이 클래스는 받은 값을 그대로 담는다.
 */
@Entity
@Table(name = "study")
public class Study {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "host_id", nullable = false)
    private Long hostId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private StudyKind kind;

    @Column(nullable = false, length = 60)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "topic_code", nullable = false, length = 30)
    private String topicCode;

    @Column(name = "district_code", nullable = false, length = 20)
    private String districtCode;

    @Column(name = "place_name", nullable = false, length = 100)
    private String placeName;

    @Column(name = "place_address", nullable = false, length = 200)
    private String placeAddress;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "duration_min", nullable = false)
    private int durationMin;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private int capacity;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "min_attendance_rate")
    private Integer minAttendanceRate;

    @Column(name = "allow_newcomer", nullable = false)
    private boolean allowNewcomer;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private StudyStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 희망 직군(대분류). 참여 조건이다. 비어 있으면 직군 무관이다 (PRD 4.2·4.4). */
    @ElementCollection
    @CollectionTable(name = "study_target_job", joinColumns = @JoinColumn(name = "study_id"))
    @Column(name = "job_code", nullable = false, length = 30)
    private Set<String> targetJobCodes = new LinkedHashSet<>();

    protected Study() {
    }

    /** 장소 (카카오맵에서 고른 값). 좌표는 체크인 거리 계산의 기준이다. */
    public record Place(String name, String address, BigDecimal latitude, BigDecimal longitude) {
    }

    /** 입장 기준 (PRD 4.7). 최소 출석률이 {@code null} 이면 기준이 없다. */
    public record EntryCriteria(Integer minAttendanceRate, boolean allowNewcomer) {
    }

    /**
     * 새 스터디를 연다. 상태는 {@code ACTIVE} 이고, 주최자를 참여자로 넣는 것은 호출한 쪽이 한다.
     *
     * @param sessionDate {@code startAt} 의 KST 날짜
     */
    public static Study open(long hostId, String title, String description, String topicCode, String districtCode,
            Set<String> targetJobCodes, Place place, Instant startAt, int durationMin, LocalDate sessionDate,
            int capacity, EntryCriteria entryCriteria, Instant now) {
        Study study = new Study();
        study.hostId = hostId;
        study.kind = StudyKind.OFFLINE;
        study.title = title;
        study.description = description;
        study.topicCode = topicCode;
        study.districtCode = districtCode;
        study.targetJobCodes = new LinkedHashSet<>(targetJobCodes);
        study.placeName = place.name();
        study.placeAddress = place.address();
        study.latitude = place.latitude();
        study.longitude = place.longitude();
        study.startAt = startAt;
        study.durationMin = durationMin;
        study.sessionDate = sessionDate;
        study.capacity = capacity;
        study.minAttendanceRate = entryCriteria.minAttendanceRate();
        study.allowNewcomer = entryCriteria.allowNewcomer();
        study.status = StudyStatus.ACTIVE;
        study.createdAt = now;
        study.updatedAt = now;
        return study;
    }

    public Long getId() {
        return id;
    }

    public Long getHostId() {
        return hostId;
    }
}
