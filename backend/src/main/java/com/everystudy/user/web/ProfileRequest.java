package com.everystudy.user.web;

import com.everystudy.common.code.DistrictCode;
import com.everystudy.common.code.KnownJobCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 온보딩·프로필 수정 요청 (API 3장 {@code PUT /me/profile}).
 *
 * @param nickname     2~20자, 한글(완성형)·영문·숫자·밑줄. 앞뒤 공백은 잘라 낸다
 * @param jobCode      직군 대분류(필수) 또는 그 아래 세부 직무. 세부 직무를 고르면 그 코드를 보낸다
 * @param districtCode 활동 지역. 시·도 코드 ({@code RegionCode})
 */
record ProfileRequest(
        @NotBlank(message = "필수 값입니다")
        @Pattern(regexp = "^[가-힣A-Za-z0-9_]{2,20}$",
                message = "2~20자의 한글, 영문, 숫자, 밑줄(_)만 쓸 수 있습니다")
        String nickname,
        @NotBlank(message = "필수 값입니다") @KnownJobCode String jobCode,
        @NotBlank(message = "필수 값입니다") @DistrictCode String districtCode) {

    ProfileRequest {
        nickname = nickname == null ? null : nickname.strip();
    }
}
