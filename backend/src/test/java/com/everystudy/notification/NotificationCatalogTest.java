package com.everystudy.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.everystudy.notification.service.NotificationType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 알림 종류와 문구의 규칙 점검 (PLAN T-039). 알림은 늦게 받거나 두 번 받을 수 있으므로(ADR-0005) 문구가 "방금"처럼 받은 시각에
 * 기대는 표현을 쓰지 않아야 하고, 모든 종류가 API 8장 표에 생성 지점과 함께 적혀 있어야 한다.
 */
class NotificationCatalogTest {

    /** 받은 시각을 기준으로 하는 상대 표현. 늦게 받으면 사실과 달라진다. */
    private static final List<String> RELATIVE_WORDS = List.of("방금", "조금 전", "아까", "지금 막", "몇 분 전");

    @Test
    @DisplayName("알림을 만드는 소스의 문구에 받은 시각에 기대는 상대 표현이 없다")
    void noRelativeTimeWordsInNotificationTexts() throws IOException {
        Path root = Path.of("src/main/java");
        assumeTrue(Files.isDirectory(root), "소스 디렉터리를 찾을 수 없음");
        try (Stream<Path> files = Files.walk(root)) {
            List<String> offenders = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> read(path).contains("NotificationRequest"))
                    .flatMap(path -> {
                        String code = withoutComments(read(path));
                        return RELATIVE_WORDS.stream().filter(code::contains)
                                .map(word -> path.getFileName() + ": " + word);
                    })
                    .toList();
            assertThat(offenders).isEmpty();
        }
    }

    @Test
    @DisplayName("모든 알림 종류가 API 8장 표에 적혀 있다")
    void everyTypeIsDocumented() throws IOException {
        Path api = Path.of("../docs/API.md");
        assumeTrue(Files.isRegularFile(api), "API 문서를 찾을 수 없음");
        String section = Files.readString(api);
        section = section.substring(section.indexOf("## 8. 서버가 내보내는 이벤트와 알림"));
        section = section.substring(0, section.indexOf("## 9."));
        for (NotificationType type : NotificationType.values()) {
            // `CHECKIN_REMINDER_DAY`, `_HOUR` 처럼 줄여 적은 경우를 위해 접두어까지만 본다.
            String name = type.name().startsWith("CHECKIN_REMINDER_") ? "CHECKIN_REMINDER_" : type.name();
            assertThat(section).as("API 8장에 %s 가 없음", type).contains(name);
        }
    }

    /** 문구는 문자열 리터럴에 있으므로 설명 주석은 뺀다. */
    private static String withoutComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)^\\s*//.*$", "");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
