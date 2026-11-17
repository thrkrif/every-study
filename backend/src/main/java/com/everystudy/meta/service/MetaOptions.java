package com.everystudy.meta.service;

import java.util.List;

/**
 * 클라이언트가 화면을 그리는 데 필요한 코드 목록과 설정값 (API 4장 {@code GET /meta/options}).
 *
 * @param topics  스터디 주제
 * @param jobs    직군. 대분류마다 세부 직무를 {@code children} 으로 둔다
 * @param regions 지역. 시·도 → 시 → 구
 */
public record MetaOptions(List<Option> topics, List<Option> jobs, List<Option> regions, Study study, Checkin checkin) {

    /** 코드 하나. 아래 단계가 없으면 {@code children} 은 빈 목록이다. */
    public record Option(String code, String name, List<Option> children) {
    }

    public record Study(int capacityMin, int capacityMax, int openableDays) {
    }

    public record Checkin(int radiusMeters) {
    }
}
