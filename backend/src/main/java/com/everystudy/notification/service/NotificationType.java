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
    NO_SHOW_NOTICE
}
