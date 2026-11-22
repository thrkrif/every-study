package com.everystudy.chat.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 공지 본문 (API 6장). */
record NoticeRequest(@NotBlank @Size(max = 500) String content) {
}
