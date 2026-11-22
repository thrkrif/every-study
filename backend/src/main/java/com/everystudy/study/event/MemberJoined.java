package com.everystudy.study.event;

/** 참여자가 스터디에 들어왔다(신청 확정, 나갔다가 다시 신청 포함). 참여 확정과 같은 트랜잭션에서 발행된다. 채팅이 입장 안내를 남긴다. */
public record MemberJoined(long studyId, long userId) {
}
