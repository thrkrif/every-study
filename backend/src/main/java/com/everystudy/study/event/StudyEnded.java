package com.everystudy.study.event;

/**
 * 스터디가 종료됐다 (세션 날짜 다음날 00:00 KST 경과). 종료와 같은 트랜잭션에서 발행된다.
 * 채팅방 폐쇄는 이 이벤트를 받아 T-049 에서 한다.
 */
public record StudyEnded(long studyId) {
}
