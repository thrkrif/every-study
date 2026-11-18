package com.everystudy.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.everystudy.common.error.InvalidInputException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationCursorTest {

    @Test
    @DisplayName("보인 시각과 ID 를 그대로 되돌린다")
    void roundTrips() {
        Instant at = Instant.parse("2026-11-24T10:00:00.123456Z");

        assertThat(NotificationCursor.decode(NotificationCursor.encode(at, 42L)))
                .isEqualTo(new NotificationCursor.Position(at, 42L));
    }

    @Test
    @DisplayName("없으면 첫 페이지, 망가졌거나 다른 목록의 커서면 VALIDATION_FAILED")
    void rejectsBrokenCursor() {
        String studyCursor = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("L|42".getBytes(StandardCharsets.UTF_8));

        assertThat(NotificationCursor.decode(null)).isNull();
        assertThat(NotificationCursor.decode(" ")).isNull();
        assertThatThrownBy(() -> NotificationCursor.decode("!!")).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> NotificationCursor.decode(studyCursor)).isInstanceOf(InvalidInputException.class);
    }
}
