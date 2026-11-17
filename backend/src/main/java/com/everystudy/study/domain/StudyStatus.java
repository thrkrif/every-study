package com.everystudy.study.domain;

/** 저장하는 스터디 상태 (PRD 4.14). "모집 중·마감"은 저장하지 않고 계산한다 (ARCHITECTURE 4.2). */
public enum StudyStatus {
    ACTIVE,
    ENDED,
    CANCELLED
}
