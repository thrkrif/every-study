package com.everystudy.study.service;

import com.everystudy.study.domain.Study;
import java.time.Instant;
import java.util.Set;

/**
 * 스터디 개설 입력. 형식(길이, 코드 목록, 좌표 범위)은 웹 계층이 이미 검사했다.
 *
 * @param targetJobCodes 희망 직군. 비어 있으면 직군 무관
 */
public record CreateStudyCommand(
        String title,
        String description,
        String topicCode,
        String districtCode,
        Set<String> targetJobCodes,
        Study.Place place,
        Instant startAt,
        int durationMin,
        int capacity,
        Study.EntryCriteria entryCriteria) {

    public CreateStudyCommand {
        targetJobCodes = Set.copyOf(targetJobCodes);
    }
}
