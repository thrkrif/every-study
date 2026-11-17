package com.everystudy.study.service;

import com.everystudy.common.error.FieldViolation;
import com.everystudy.common.error.InvalidInputException;
import com.everystudy.study.repository.StudySearchCondition;
import com.everystudy.study.repository.StudySearchCondition.Sort;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * 목록 커서 (API 1장). 클라이언트에게는 뜻 없는 문자열이고, 안에는 정렬 기준과 직전 페이지 마지막 항목이 들어 있다.
 * 다른 정렬의 커서나 망가진 값은 {@code VALIDATION_FAILED} 다.
 */
final class StudyCursor {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private StudyCursor() {
    }

    static String encode(Sort sort, long id, Instant startAt) {
        String raw = sort == Sort.LATEST ? "L|" + id : "D|" + startAt + "|" + id;
        return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** @return 커서가 없으면 {@code null} */
    static StudySearchCondition.Cursor decode(String cursor, Sort sort) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String[] parts = new String(DECODER.decode(cursor), StandardCharsets.UTF_8).split("\\|");
            if (sort == Sort.LATEST && parts.length == 2 && parts[0].equals("L")) {
                return new StudySearchCondition.Cursor(Long.parseLong(parts[1]), null);
            }
            if (sort == Sort.DEADLINE && parts.length == 3 && parts[0].equals("D")) {
                return new StudySearchCondition.Cursor(Long.parseLong(parts[2]), Instant.parse(parts[1]));
            }
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException ex) {
            // 아래에서 같은 오류로 응답한다.
        }
        throw new InvalidInputException(List.of(new FieldViolation("cursor", "올바르지 않은 커서입니다")));
    }
}
