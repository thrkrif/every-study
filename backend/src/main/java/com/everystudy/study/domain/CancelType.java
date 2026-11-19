package com.everystudy.study.domain;

/** 나갈 때 판정한 취소 종류 (DATA-MODEL 4장 {@code study_member.cancel_type}). 기준 시각은 PRD 4.6. */
public enum CancelType {
    /** 사전 취소. */
    EARLY,
    /** 직전 취소. */
    LATE
}
