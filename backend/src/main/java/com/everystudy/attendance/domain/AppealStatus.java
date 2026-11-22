package com.everystudy.attendance.domain;

/** 이의 처리 상태 (DATA-MODEL 5장). 운영자 결정은 T-056 에서 바꾼다. */
public enum AppealStatus {
    OPEN,
    ACCEPTED,
    REJECTED
}
