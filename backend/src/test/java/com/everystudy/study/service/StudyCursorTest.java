package com.everystudy.study.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.everystudy.common.error.InvalidInputException;
import com.everystudy.study.repository.StudySearchCondition.Cursor;
import com.everystudy.study.repository.StudySearchCondition.Sort;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StudyCursorTest {

    private static final Instant START = Instant.parse("2026-11-20T10:00:00Z");

    @Test
    @DisplayName("정렬마다 필요한 값을 담고 그대로 풀린다")
    void roundTrip() {
        assertThat(StudyCursor.decode(StudyCursor.encode(Sort.LATEST, 42L, START), Sort.LATEST))
                .isEqualTo(new Cursor(42L, null));
        assertThat(StudyCursor.decode(StudyCursor.encode(Sort.DEADLINE, 42L, START), Sort.DEADLINE))
                .isEqualTo(new Cursor(42L, START));
    }

    @Test
    @DisplayName("커서가 없으면 첫 페이지다")
    void emptyCursorIsFirstPage() {
        assertThat(StudyCursor.decode(null, Sort.LATEST)).isNull();
        assertThat(StudyCursor.decode("", Sort.DEADLINE)).isNull();
    }

    @Test
    @DisplayName("다른 정렬의 커서는 거절한다")
    void rejectsCursorOfOtherSort() {
        String latest = StudyCursor.encode(Sort.LATEST, 42L, START);

        assertThatThrownBy(() -> StudyCursor.decode(latest, Sort.DEADLINE)).isInstanceOf(InvalidInputException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-base64!", "TFxhYmM", "RHxub3QtYS10aW1lfDQy"})
    @DisplayName("망가진 커서는 거절한다")
    void rejectsGarbage(String cursor) {
        assertThatThrownBy(() -> StudyCursor.decode(cursor, Sort.DEADLINE)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> StudyCursor.decode(cursor, Sort.LATEST)).isInstanceOf(InvalidInputException.class);
    }
}
