package com.everystudy.auth.web;

import com.everystudy.common.error.BusinessException;
import com.everystudy.common.error.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 보안 필터에서 난 거절을 다른 오류와 같은 형식(problem details, API 명세 1장)으로 응답한다.
 * 필터는 컨트롤러 밖이라 {@code GlobalExceptionHandler} 가 직접 받지 못하므로, 같은 처리기로 넘긴다.
 *
 * <ul>
 *   <li>로그인하지 않았거나 액세스 토큰이 만료·위조됨 → {@code 401 UNAUTHENTICATED}</li>
 *   <li>CSRF 토큰이 없거나 맞지 않음 → {@code 403 CSRF_INVALID}</li>
 *   <li>로그인했지만 권한이 없음 → {@code 403 FORBIDDEN}</li>
 * </ul>
 */
@Component
class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final HandlerExceptionResolver resolver;

    SecurityProblemHandler(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        respond(request, response, CommonErrorCode.UNAUTHENTICATED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        respond(request, response,
                ex instanceof CsrfException ? CommonErrorCode.CSRF_INVALID : CommonErrorCode.FORBIDDEN);
    }

    private void respond(HttpServletRequest request, HttpServletResponse response, CommonErrorCode code)
            throws IOException {
        if (resolver.resolveException(request, response, null, new BusinessException(code)) == null) {
            // 처리기가 응답을 쓰지 못한 경우에도 상태 코드는 지킨다.
            response.sendError(code.status().value());
        }
    }
}
