package com.everystudy.notification.domain;

/** 아웃박스 발송 상태 (DATA-MODEL 7장). 수신 설정으로 건너뛴 건의 표기는 DATA-MODEL 11장 2번에서 정한다. */
public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED,
    CANCELLED
}
