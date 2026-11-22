package com.everystudy.attendance.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 운영자의 이의 결정 본문 (API 7장). 사유는 필수이고 신청자에게 알림으로 간다. */
record AppealDecisionRequest(@NotNull Boolean accept, @NotBlank @Size(max = 150) String note) {
}
