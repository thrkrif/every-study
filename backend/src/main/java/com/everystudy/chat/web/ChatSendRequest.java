package com.everystudy.chat.web;

/** STOMP 전송 본문 `/app/studies/{id}/chat` (API 6장). 길이·공백 검사는 서비스가 한다. */
record ChatSendRequest(String content) {
}
