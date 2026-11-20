package com.everystudy.trust.domain;

/** 제재 종류 (DATA-MODEL 6장 {@code user_sanction.kind}). 기간은 PRD 4.8·4.13C. */
public enum SanctionKind {
    /** 새 스터디 참여 신청 제한. 이미 참여한 스터디는 유지한다. */
    JOIN_RESTRICT,
    /** 이용 정지. 신청·개설을 모두 막는다. */
    SUSPEND,
    /** 스터디 개설 제한. */
    HOST_RESTRICT
}
