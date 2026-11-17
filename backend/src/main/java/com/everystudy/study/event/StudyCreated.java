package com.everystudy.study.event;

/**
 * 스터디가 만들어졌다. 개설과 같은 트랜잭션에서 발행된다.
 * 받는 쪽(채팅방 생성)은 동기 리스너로 받아 함께 커밋되거나 함께 롤백된다 (ARCHITECTURE 3장, PLAN X-8).
 */
public record StudyCreated(long studyId, long hostId) {
}
