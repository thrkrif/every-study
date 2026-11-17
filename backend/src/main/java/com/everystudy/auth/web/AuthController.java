package com.everystudy.auth.web;

import com.everystudy.auth.service.IssuedTokens;
import com.everystudy.auth.service.TokenSessionService;
import com.everystudy.common.error.BusinessException;
import com.everystudy.common.error.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 토큰 갱신과 로그아웃 (API 명세 2장). 둘 다 액세스 토큰 없이 리프레시 쿠키로 대상을 정하고,
 * 상태를 바꾸므로 CSRF 검사를 받는다.
 */
@RestController
@RequestMapping(AuthController.BASE_PATH)
class AuthController {

    static final String BASE_PATH = "/api/v1/auth";

    private final TokenSessionService sessions;
    private final AuthCookies cookies;
    private final CsrfTokenRepository csrfTokens;

    AuthController(TokenSessionService sessions, AuthCookies cookies, CsrfTokenRepository csrfTokens) {
        this.sessions = sessions;
        this.cookies = cookies;
        this.csrfTokens = csrfTokens;
    }

    /** 쓸 수 없는 리프레시 토큰이면 쿠키를 지우고 {@code 401}. 클라이언트는 로그인 화면으로 보낸다. */
    @PostMapping("/refresh")
    ResponseEntity<Void> refresh(
            @CookieValue(name = AuthCookies.REFRESH, required = false) String refreshToken,
            HttpServletResponse response) {
        Optional<IssuedTokens> tokens = sessions.refresh(refreshToken);
        if (tokens.isEmpty()) {
            cookies.clear(response);
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
        }
        cookies.write(response, tokens.get());
        return ResponseEntity.noContent().build();
    }

    /** 리프레시 쿠키가 없거나 이미 무효여도 쿠키를 지우고 {@code 204} 다 (여러 번 불러도 결과가 같다). */
    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            @CookieValue(name = AuthCookies.REFRESH, required = false) String refreshToken,
            HttpServletRequest request, HttpServletResponse response) {
        sessions.logout(refreshToken);
        cookies.clear(response);
        // 로그아웃 뒤에는 새 CSRF 토큰을 받게 한다 (ADR-0002 결정 3).
        csrfTokens.saveToken(null, request, response);
        return ResponseEntity.noContent().build();
    }
}
