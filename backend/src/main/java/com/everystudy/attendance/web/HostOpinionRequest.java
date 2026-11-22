package com.everystudy.attendance.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 주최자 1차 의견 본문 (API 5장). */
record HostOpinionRequest(@NotBlank @Size(max = 500) String opinion) {
}
