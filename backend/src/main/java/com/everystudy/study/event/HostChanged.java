package com.everystudy.study.event;

/** 주최자가 승계로 바뀌었다. 승계와 같은 트랜잭션에서 발행된다. 채팅이 새 주최자 안내를 남긴다. */
public record HostChanged(long studyId, long previousHostId, long newHostId) {
}
