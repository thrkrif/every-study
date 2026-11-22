package com.everystudy.trust.repository;

/** 주최자별 감점 건수 집계 한 줄. */
public record HostPenaltyCount(Long hostId, long count) {
}
