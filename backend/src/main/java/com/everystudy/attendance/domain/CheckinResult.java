package com.everystudy.attendance.domain;

/** 체크인 시각으로 정해지는 판정 (PRD 4.6): 시작 10분 이내는 출석, 그 뒤 마감까지는 지각. */
public enum CheckinResult {
    ATTENDED,
    LATE
}
