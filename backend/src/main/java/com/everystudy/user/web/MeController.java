package com.everystudy.user.web;

import com.everystudy.common.security.LoginUser;
import com.everystudy.user.service.MyProfile;
import com.everystudy.user.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 내 정보와 온보딩·프로필 수정 (API 3장). 로그인 회원만 부른다. */
@RestController
@RequestMapping("/api/v1/me")
class MeController {

    private final ProfileService profiles;

    MeController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    MyProfile me(@AuthenticationPrincipal LoginUser user) {
        return profiles.me(user.userId());
    }

    /** 온보딩과 이후 수정이 같은 경로다. 바뀐 내 정보를 돌려준다. */
    @PutMapping("/profile")
    MyProfile updateProfile(@AuthenticationPrincipal LoginUser user, @Valid @RequestBody ProfileRequest request) {
        return profiles.updateProfile(user.userId(), request.nickname(), request.jobCode(), request.districtCode());
    }
}
