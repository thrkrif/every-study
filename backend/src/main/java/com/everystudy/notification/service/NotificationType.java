package com.everystudy.notification.service;

/**
 * 알림 종류 (DATA-MODEL 7장). 다른 모듈이 알림을 기록할 때 고르므로 공개 패키지에 둔다.
 * 값을 더하면 마이그레이션의 CHECK 제약도 함께 고친다.
 */
public enum NotificationType {
    STUDY_CANCELLED,
    HOST_ASSIGNED,
    WAITING_OPENED,
    CHECKIN_OPEN,
    NO_SHOW_NOTICE,
    /** 참여자 경고: 노쇼 확정 또는 지각 누적 (PRD 4.8). 제재 행은 만들지 않는다. */
    SANCTION_WARNING,
    /** 참여 신청 제한 또는 이용 정지를 부여했다는 안내 (PRD 4.8). */
    SANCTION_APPLIED,
    /** 주최자 경고: 90일 감점 합계가 경고 단계 (PRD 4.13C). */
    HOST_WARNING
}
