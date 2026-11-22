package com.everystudy.attendance.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 이의 신청 본문 (API 5장). 사유는 텍스트만 받는다. */
record AppealRequest(@NotBlank @Size(max = 500) String reason) {
}
