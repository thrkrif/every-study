package com.everystudy.attendance.web;

import com.everystudy.attendance.service.AttendanceCorrectionService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 운영자 개별 출석 정정 본문 (API 7장). 사유는 필수다. */
record CorrectionRequest(@NotNull AttendanceCorrectionService.Result result, @NotBlank @Size(max = 200) String note) {
}
