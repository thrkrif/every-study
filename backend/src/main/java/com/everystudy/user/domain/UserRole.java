package com.everystudy.user.domain;

/** 역할. 주최자 여부는 역할이 아니라 스터디 단위 관계로 판정한다 (ADR-0002 결정 7). */
public enum UserRole {
    USER,
    ADMIN
}
