package com.everystudy.chat.web;

import com.everystudy.chat.domain.ReportTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 신고 본문 (API 7장). 사유 코드와 상세 규칙은 서비스가 검사한다(코드 외 값은 {@code VALIDATION_FAILED}). */
record ReportRequest(@NotNull ReportTargetType targetType, @NotNull @Positive Long targetId, String reason,
        String detail) {
}
