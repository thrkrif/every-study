package com.everystudy.study.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 운영자 강제 종료 본문 (API 7장). 사유는 필수이고 감사 로그에 남는다. */
record ForceEndRequest(@NotBlank @Size(max = 200) String note) {
}
