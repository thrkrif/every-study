package com.everystudy.common.ratelimit;

import com.everystudy.common.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * HTTP 요청의 호출 제한 (API 1장·9장, PLAN D-5·T-064). 대상은 체크인 코드 입력, 참여 신청, 신고(회원 기준)와 비회원의 스터디
 * 목록·상세 조회(IP 기준)다. 채팅 전송은 REST·STOMP 가 같은 서비스를 쓰므로 서비스에서 센다. 보안 필터가 인증한 뒤에 돌므로
 * 회원을 알 수 있다. 운영에서는 {@code forward-headers-strategy: native} 가 프록시 뒤의 실제 IP 를 준다.
 */
class RateLimitInterceptor implements HandlerInterceptor {

    private static final AntPathMatcher PATHS = new AntPathMatcher();
    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final Duration HOUR = Duration.ofHours(1);

    private final ObjectProvider<RateLimiter> limiters;
    private final ObjectProvider<RateLimitProperties> settings;

    /** 웹 계층만 띄우는 슬라이스 테스트에는 제한기가 없으므로 요청 때 찾고, 없으면 제한하지 않는다. */
    RateLimitInterceptor(ObjectProvider<RateLimiter> limiters, ObjectProvider<RateLimitProperties> settings) {
        this.limiters = limiters;
        this.settings = settings;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        RateLimiter limiter = limiters.getIfAvailable();
        RateLimitProperties properties = settings.getIfAvailable();
        if (limiter == null || properties == null) {
            return true;
        }
        String method = request.getMethod();
        String path = request.getRequestURI();
        Long userId = currentUserId();
        if (HttpMethod.POST.matches(method)) {
            if (PATHS.match("/api/v1/studies/*/checkins/code", path)) {
                limitUser(limiter, "checkin-code", userId, request, properties.checkinCodePerMinute(), MINUTE);
            } else if (PATHS.match("/api/v1/studies/*/members", path)) {
                limitUser(limiter, "join", userId, request, properties.joinPerMinute(), MINUTE);
            } else if (PATHS.match("/api/v1/reports", path)) {
                limitUser(limiter, "report", userId, request, properties.reportPerHour(), HOUR);
            }
        } else if (HttpMethod.GET.matches(method) && userId == null
                && (PATHS.match("/api/v1/studies", path) || PATHS.match("/api/v1/studies/*", path))) {
            limiter.check("public-read", request.getRemoteAddr(), properties.publicReadPerMinute(), MINUTE);
        }
        return true;
    }

    private void limitUser(RateLimiter limiter, String bucket, Long userId, HttpServletRequest request, int limit, Duration window) {
        // 인증이 필요한 경로라 보통 회원이 있다. 없으면 IP 로 센다.
        limiter.check(bucket, userId != null ? "u" + userId : "ip" + request.getRemoteAddr(), limit, window);
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser user) {
            return user.userId();
        }
        return null;
    }
}
