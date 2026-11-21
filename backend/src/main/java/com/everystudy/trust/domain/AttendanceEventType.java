package com.everystudy.trust.domain;

/** 출석 이벤트 종류와 출석률 반영 (DATA-MODEL 5장, PRD 4.7). */
public enum AttendanceEventType {
    ATTENDED,
    LATE,
    NO_SHOW_CANDIDATE,
    NO_SHOW,
    NO_SHOW_CLEARED,
    EARLY_CANCEL,
    LATE_CANCEL,
    VOIDED
}
