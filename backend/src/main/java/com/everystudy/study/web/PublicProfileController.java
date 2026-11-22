package com.everystudy.study.web;

import com.everystudy.study.service.PublicProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 공개 프로필 (API 3장). 비회원도 볼 수 있다. */
@RestController
@RequestMapping("/api/v1/users")
class PublicProfileController {

    private final PublicProfileService profiles;

    PublicProfileController(PublicProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/{id}")
    PublicProfileService.View profile(@PathVariable long id) {
        return profiles.find(id);
    }
}
