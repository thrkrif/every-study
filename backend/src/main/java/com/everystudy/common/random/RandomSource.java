package com.everystudy.common.random;

/**
 * 무작위 선택을 얻는 한 곳 (ARCHITECTURE 4.3). 승계 추첨처럼 서버가 무작위로 고르는 곳은 {@code Random} 을 직접 쓰지 않고
 * 이 인터페이스를 주입받는다. 테스트에서는 고정 구현을 넣어 결과를 고정한다.
 */
public interface RandomSource {

    /** {@code 0} 이상 {@code bound} 미만의 정수 하나. */
    int nextInt(int bound);
}
