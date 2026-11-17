package com.everystudy.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/**
 * 소셜 토큰을 어디에도 보관하지 않는다 (ADR-0002 결정 1).
 * 기본 구현은 로그인한 사용자의 소셜 액세스 토큰을 서버 메모리에 계속 들고 있는데, 우리는 로그인 순간의 사용자 식별에만 쓴다.
 */
class NoOpAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {

    @Override
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
            String clientRegistrationId, Authentication principal, HttpServletRequest request) {
        return null;
    }

    @Override
    public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal,
            HttpServletRequest request, HttpServletResponse response) {
        // 저장하지 않는다.
    }

    @Override
    public void removeAuthorizedClient(String clientRegistrationId, Authentication principal,
            HttpServletRequest request, HttpServletResponse response) {
        // 저장한 것이 없다.
    }
}
