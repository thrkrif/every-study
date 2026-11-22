package com.everystudy.study.event;

/**
 * 참여자가 스터디에서 나갔다(주최자의 나가기 포함, 시작 후 나간 주최자도). 나가기와 같은 트랜잭션에서 발행된다. 채팅이 나감 안내를
 * 남기고 그 사용자의 구독을 끊는다.
 */
public record MemberLeft(long studyId, long userId) {
}
