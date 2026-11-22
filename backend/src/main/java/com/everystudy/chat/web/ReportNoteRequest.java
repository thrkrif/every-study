package com.everystudy.chat.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 운영자의 신고 처리 사유 (API 7장). 필수다. */
record ReportNoteRequest(@NotBlank @Size(max = 300) String note) {
}
