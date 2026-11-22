package com.everystudy.attendance.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * 위치 체크인 요청 (API 5장). 형식만 본다. 위치는 체크인 순간에만 보내고 서버는 거리만 남긴다 (PRD 4.6·8장).
 *
 * @param accuracyM 브라우저가 알려 주는 GPS 정확도(미터). 소수여도 되고 반올림해 저장한다
 */
record CheckinRequest(
        @NotNull(message = "필수 값입니다") @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull(message = "필수 값입니다") @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @NotNull(message = "필수 값입니다") @DecimalMin(value = "0", message = "0 이상이어야 합니다") Double accuracyM) {

    /** SMALLINT 에 들어가도록 상한을 둔다. 이보다 큰 정확도는 사실상 위치를 모른다는 뜻이다. */
    int accuracyForStorage() {
        return (int) Math.min(Short.MAX_VALUE, Math.round(accuracyM));
    }
}
