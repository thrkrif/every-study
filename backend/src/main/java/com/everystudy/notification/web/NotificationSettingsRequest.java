package com.everystudy.notification.web;

/** 알림 수신 설정 변경. 보내지 않은(null) 채널은 그대로 둔다. */
record NotificationSettingsRequest(Boolean push, Boolean email) {
}
