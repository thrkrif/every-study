package com.everystudy.chat.web;

import java.security.Principal;

/** STOMP 세션의 사용자. 이름이 회원 id 라서 {@code /user/queue/...} 로 그 회원에게 보낼 수 있다. */
record ChatPrincipal(long userId) implements Principal {

    @Override
    public String getName() {
        return Long.toString(userId);
    }
}
