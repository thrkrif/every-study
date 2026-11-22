package com.everystudy.attendance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 스터디(세션) 하나의 일회용 체크인 코드 (DATA-MODEL 5장). 재발급하면 같은 행의 값이 바뀌어 이전 코드는 무효가 된다. */
@Entity
@Table(name = "checkin_code")
public class CheckinCode {

    @Id
    @Column(name = "study_id")
    private Long studyId;

    @Column(nullable = false, length = 6, columnDefinition = "CHAR(6)")
    private String code;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected CheckinCode() {
    }

    public static CheckinCode issue(long studyId, String code, Instant issuedAt, Instant expiresAt) {
        CheckinCode entity = new CheckinCode();
        entity.studyId = studyId;
        entity.reissue(code, issuedAt, expiresAt);
        return entity;
    }

    public void reissue(String code, Instant issuedAt, Instant expiresAt) {
        this.code = code;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    /** 코드가 맞고 만료 시각(그 시각 포함)이 지나지 않았는가. */
    public boolean accepts(String input, Instant now) {
        return code.equals(input) && !now.isAfter(expiresAt);
    }

    public String getCode() {
        return code;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
