package com.everystudy.chat.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket 설정 (ADR-0005, ARCHITECTURE 4.8). 단일 서버라 내장 단순 브로커를 쓴다. 서버를 늘릴 때는 외부
 * STOMP 브로커 릴레이로 바꾼다.
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ChatSocketInterceptor interceptor;
    private final String frontendUrl;

    /** 프론트 오리진은 인증 설정(ADR-0002)과 같은 값을 쓴다. 다른 모듈의 설정 객체를 참조하지 않고 값만 읽는다. */
    WebSocketConfig(ChatSocketInterceptor interceptor, @Value("${everystudy.auth.web.frontend-url}") String frontendUrl) {
        this.interceptor = interceptor;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 인증은 핸드셰이크의 쿠키(HTTP 보안 필터)가 하고, 오리진은 프론트 하나만 허용한다 (ADR-0002 결정 5).
        registry.addEndpoint("/ws")
                .setHandshakeHandler(new ChatHandshakeHandler())
                .setAllowedOrigins(frontendUrl);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(interceptor);
    }
}
