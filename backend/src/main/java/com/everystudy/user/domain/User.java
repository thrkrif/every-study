package com.everystudy.user.domain;

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
 * 회원 (DATA-MODEL 3장). 소셜 로그인으로 처음 들어오면 닉네임·직군·지역이 빈 채로 만들어지고,
 * 온보딩에서 셋을 모두 채워야 서비스를 쓸 수 있다.
 */
@Entity
@Table(name = "user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20)
    private String nickname;

    private String email;

    @Column(name = "job_code", length = 30)
    private String jobCode;

    @Column(name = "district_code", length = 20)
    private String districtCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 12)
    private UserStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    protected User() {
    }

    private User(String email, Instant createdAt) {
        this.email = email;
        this.role = UserRole.USER;
        this.status = UserStatus.ACTIVE;
        this.createdAt = createdAt;
    }

    /** 소셜 로그인으로 처음 들어온 회원. 이메일은 공급자가 주지 않으면 {@code null} 이다 (PLAN D-9). */
    public static User registerBySocialLogin(String email, Instant now) {
        return new User(email, now);
    }

    /** 공급자가 확인된 이메일을 주면 그 값으로 맞춘다. 주지 않으면(동의 철회 등) 기존 값을 둔다. */
    public void refreshEmail(String email) {
        if (email != null) {
            this.email = email;
        }
    }

    /**
     * 닉네임·직군·지역을 정한다. 온보딩과 이후 수정이 같은 동작이다.
     * 값의 형식과 닉네임 중복은 호출한 쪽이 검사한다.
     */
    public void updateProfile(String nickname, String jobCode, String districtCode) {
        this.nickname = nickname;
        this.jobCode = jobCode;
        this.districtCode = districtCode;
    }

    /** 온보딩(닉네임·직군·지역 입력)을 마쳤는가. */
    public boolean isOnboarded() {
        return nickname != null && jobCode != null && districtCode != null;
    }

    public Long getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public String getJobCode() {
        return jobCode;
    }

    public String getDistrictCode() {
        return districtCode;
    }

    public String getEmail() {
        return email;
    }

    public UserRole getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
