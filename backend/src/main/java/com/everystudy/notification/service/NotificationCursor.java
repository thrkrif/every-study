package com.everystudy.notification.service;

import com.everystudy.common.error.FieldViolation;
import com.everystudy.common.error.InvalidInputException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;

/**
 * 알림 센터 커서 (API 1장). 클라이언트에게는 뜻 없는 문자열이고, 안에는 직전 페이지 마지막 항목의 보인 시각과 ID 가 있다.
 * 망가진 값은 {@code VALIDATION_FAILED} 다.
 */
final class NotificationCursor {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    record Position(Instant visibleAt, long id) {
    }

    private NotificationCursor() {
    }

    static String encode(Instant visibleAt, long id) {
        return ENCODER.encodeToString(("N|" + visibleAt + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    /** @return 커서가 없으면 {@code null} */
    static Position decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String[] parts = new String(DECODER.decode(cursor), StandardCharsets.UTF_8).split("\\|");
            if (parts.length == 3 && parts[0].equals("N")) {
                return new Position(Instant.parse(parts[1]), Long.parseLong(parts[2]));
            }
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            // 아래에서 같은 오류로 응답한다.
        }
        throw new InvalidInputException(List.of(new FieldViolation("cursor", "올바르지 않은 커서입니다")));
    }
}
