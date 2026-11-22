package com.everystudy.study.web;

import com.everystudy.common.security.LoginUser;
import com.everystudy.study.repository.MyStudyRepository.Role;
import com.everystudy.study.service.MyStudyQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 내 스터디 (API 3장). 로그인 회원만 부른다. */
@RestController
@RequestMapping("/api/v1/me/studies")
class MyStudyController {

    private final MyStudyQueryService queries;

    MyStudyController(MyStudyQueryService queries) {
        this.queries = queries;
    }

    /**
     * 내가 참여 중인 스터디. {@code role} 을 생략하면 주최·참여 모두, {@code state} 를 생략하면 다가오는 모임이다.
     */
    @GetMapping
    MyStudyQueryService.Page list(
            @AuthenticationPrincipal LoginUser user,
            @RequestParam(required = false) @Pattern(regexp = "host|member",
                    message = "host 또는 member 여야 합니다") String role,
            @RequestParam(defaultValue = "upcoming") @Pattern(regexp = "upcoming|past",
                    message = "upcoming 또는 past 여야 합니다") String state,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return queries.find(user.userId(), new MyStudyQueryService.Query(
                role == null ? null : Role.valueOf(role.toUpperCase(java.util.Locale.ROOT)),
                state.equals("past") ? MyStudyQueryService.State.PAST : MyStudyQueryService.State.UPCOMING,
                cursor, size));
    }
}
