package com.everystudy.chat.web;

import com.everystudy.common.security.LoginUser;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * 핸드셰이크 요청의 쿠키 인증(ADR-0002)을 STOMP 세션의 사용자로 옮긴다. 쿠키가 없거나 잘못되면 HTTP 보안 필터가 핸드셰이크
 * 전에 {@code 401} 로 끝내므로 여기까지 오지 않는다. STOMP 헤더의 계정 정보는 쓰지 않는다.
 */
class ChatHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler handler,
            Map<String, Object> attributes) {
        if (request.getPrincipal() instanceof Authentication authentication
                && authentication.getPrincipal() instanceof LoginUser user) {
            return new ChatPrincipal(user.userId());
        }
        return null;
    }
}
