package com.everystudy.support;

import org.springframework.boot.test.context.SpringBootTest;

/**
 * 실제 포트에서 서버를 띄우는 통합 테스트의 부모. WebSocket 핸드셰이크와 STOMP 는 MockMvc 로 시험할 수 없어 임의 포트로
 * 서버를 연다. 그 밖의 규칙은 {@link IntegrationTestSupport} 와 같다. 컨텍스트가 다르므로 이 클래스를 쓰는 테스트끼리만
 * 컨텍스트를 공유한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class WebSocketIntegrationTestSupport extends IntegrationTestSupport {
}
