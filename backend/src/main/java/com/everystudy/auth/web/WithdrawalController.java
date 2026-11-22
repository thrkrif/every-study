package com.everystudy.auth.web;

import com.everystudy.auth.service.WithdrawalService;
import com.everystudy.common.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

/** 회원 탈퇴 (API 3장). 성공하면 로그인 쿠키를 지우고 새 CSRF 토큰을 받게 한다. */
@RestController
class WithdrawalController {

    private final WithdrawalService withdrawal;
    private final AuthCookies cookies;
    private final CsrfTokenRepository csrfTokens;

    WithdrawalController(WithdrawalService withdrawal, AuthCookies cookies, CsrfTokenRepository csrfTokens) {
        this.withdrawal = withdrawal;
        this.cookies = cookies;
        this.csrfTokens = csrfTokens;
    }

    @DeleteMapping("/api/v1/me")
    ResponseEntity<Void> withdraw(@AuthenticationPrincipal LoginUser user, HttpServletRequest request,
            HttpServletResponse response) {
        withdrawal.withdraw(user.userId());
        cookies.clear(response);
        csrfTokens.saveToken(null, request, response);
        return ResponseEntity.noContent().build();
    }
}
