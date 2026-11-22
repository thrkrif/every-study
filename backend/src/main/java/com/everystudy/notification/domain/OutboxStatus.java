package com.everystudy.notification.domain;

/** 아웃박스 발송 상태 (DATA-MODEL 7장). 수신 설정으로 건너뛴 건은 {@code SKIPPED} 다 (PLAN D-6). */
public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED,
    CANCELLED,
    /** 수신 설정이 꺼져 있어 보내지 않았다 (PLAN D-6). */
    SKIPPED
}
