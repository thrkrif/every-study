package com.everystudy.chat.web;

import jakarta.validation.constraints.Min;

/** 읽음 위치 (API 6장). */
record ReadRequest(@Min(0) long lastReadMessageId) {
}
