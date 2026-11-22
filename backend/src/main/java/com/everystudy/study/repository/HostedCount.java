package com.everystudy.study.repository;

/** 주최자별 스터디 수 집계 한 줄. */
public record HostedCount(Long hostId, long count) {
}
