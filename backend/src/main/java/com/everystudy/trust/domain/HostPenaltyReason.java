package com.everystudy.trust.domain;

/** 주최자 감점 사유 (DATA-MODEL 6장 {@code host_penalty_event.reason}, PRD 4.13B). */
public enum HostPenaltyReason {
    /** 시작 3시간 이내에 주최자가 나갔다. */
    LEFT_WITHIN_WINDOW,
    /** 주최자가 도착하지 않아 세션이 무효가 됐다 (T-031). */
    ABSENT
}
