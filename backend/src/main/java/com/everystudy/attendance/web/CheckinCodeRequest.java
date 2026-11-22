package com.everystudy.attendance.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 코드 체크인 요청 (API 5장). 6자리 숫자가 아니면 형식 오류({@code 400})이고 실패 횟수에 세지 않는다. */
record CheckinCodeRequest(
        @NotNull(message = "필수 값입니다") @Pattern(regexp = "\\d{6}", message = "6자리 숫자여야 합니다") String code) {
}
