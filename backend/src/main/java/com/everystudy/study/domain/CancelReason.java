package com.everystudy.study.domain;

/** 스터디가 파투(CANCELLED)된 이유 (DATA-MODEL 4장 {@code study.cancel_reason}). */
public enum CancelReason {
    /** 주최자만 남은 상태에서 주최자가 나갔다 (PRD 4.13A). */
    HOST_LEFT,
    /** 운영자가 강제 종료했다. */
    OPERATOR
}
