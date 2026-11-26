package com.everystudy.meta.service;

import java.util.List;

/**
 * 클라이언트가 화면을 그리는 데 필요한 코드 목록과 설정값 (API 4장 {@code GET /meta/options}).
 *
 * @param topics  스터디 주제
 * @param jobs    직군. 대분류마다 세부 직무를 {@code children} 으로 둔다
 * @param regions 지역. 전국 시·도 한 단계라 children 은 비어 있다
 */
public record MetaOptions(List<Option> topics, List<Option> jobs, List<Option> regions, Study study, Checkin checkin) {

    /** 코드 하나. 아래 단계가 없으면 {@code children} 은 빈 목록이다. */
    public record Option(String code, String name, List<Option> children) {
    }

    /**
     * 개설 화면이 쓰는 값.
     *
     * @param minAttendanceRates 입장 기준 "최소 출석률"로 고를 수 있는 값(%). 기준 없음은 따로 고른다
     */
    public record Study(int capacityMin, int capacityMax, int openableDays, int startMinuteUnit,
            int durationMinuteUnit, int durationMinMinutes, List<Integer> minAttendanceRates) {
    }

    public record Checkin(int radiusMeters) {
    }
}
