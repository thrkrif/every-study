package com.everystudy.common.web;

import com.everystudy.common.error.FieldViolation;
import com.everystudy.common.error.InvalidInputException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/** id 내림차순 목록의 커서 (API 1장). 클라이언트에게는 뜻 없는 문자열이다. 망가진 값은 {@code VALIDATION_FAILED}. */
public final class IdCursor {

    private IdCursor() {
    }

    public static String encode(long lastId) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Long.toString(lastId).getBytes(StandardCharsets.UTF_8));
    }

    /** @return 커서가 없으면 {@link Long#MAX_VALUE} (처음부터) */
    public static long decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Long.MAX_VALUE;
        }
        try {
            long id = Long.parseLong(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
            if (id > 0) {
                return id;
            }
        } catch (IllegalArgumentException e) {
            // 아래에서 같은 오류로 응답한다.
        }
        throw new InvalidInputException(List.of(new FieldViolation("cursor", "올바르지 않은 커서입니다")));
    }
}
