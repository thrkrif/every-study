package com.everystudy.study.repository;

import java.time.Instant;
import java.util.Set;

/**
 * 목록 조회 조건. 값의 해석(지역 코드 펼치기, 커서 풀기)은 서비스가 끝낸 상태다.
 *
 * @param districtCodes       이 지역(시·도) 중 하나. 비어 있으면 전체
 * @param topicCode           {@code null} 이면 전체
 * @param targetJobCategory   희망 직군에 이 대분류가 명시된 스터디만. {@code null} 이면 전체
 * @param eligibleJobCategory 이 대분류의 회원이 신청할 수 있는 스터디만 (희망 직군 없음 또는 포함). {@code null} 이면 전체
 * @param includeClosed       {@code false} 면 모집 중만, {@code true} 면 시작 전인 마감 스터디도
 * @param now                 현재 시각. 이미 시작한 스터디는 늘 뺀다
 * @param recruitingDeadline  이 시각 뒤에 시작해야 아직 신청을 받는다 (= 현재 + 체크인 오픈 전 분)
 */
public record StudySearchCondition(
        Set<String> districtCodes,
        String topicCode,
        String targetJobCategory,
        String eligibleJobCategory,
        boolean includeClosed,
        Instant now,
        Instant recruitingDeadline,
        Sort sort,
        Cursor cursor,
        int size) {

    public enum Sort { LATEST, DEADLINE }

    /** 직전 페이지 마지막 항목. 최신순은 {@code id} 만, 마감임박순은 {@code startAt} 과 {@code id} 를 쓴다. */
    public record Cursor(long id, Instant startAt) {
    }
}
