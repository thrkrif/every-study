package com.everystudy.study.event;

/**
 * 스터디가 파투됐다. 파투와 같은 트랜잭션에서 발행된다. 채팅방 폐쇄는 이 이벤트를 받아 T-049 에서 한다 (PLAN D-16).
 *
 * @param reason {@code study.cancel_reason} 값 ({@code HOST_LEFT} 등)
 */
public record StudyCancelled(long studyId, String reason) {
}
