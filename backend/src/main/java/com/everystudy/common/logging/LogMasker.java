package com.everystudy.common.logging;

import java.util.regex.Pattern;

/**
 * 로그 한 줄에서 토큰·이메일·좌표로 보이는 값을 가린다 (ARCHITECTURE 7장).
 *
 * <p>마지막 안전망이다. 이런 값을 로그에 넘기지 않는 것이 규칙이고, 여기서는 실수로 넘어온 값을 모양으로 찾아 가린다.
 * 모양으로 찾기 때문에 모든 경우를 잡지는 못한다.
 */
public final class LogMasker {

    private static final String MASK = "***";

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*");

    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+");

    // 키 이름에 token·secret·password·authorization 이 들어간 "키=값", "키: 값", "\"키\":\"값\"".
    // 키 바로 뒤에 구분자가 와야 한다 ("...TokenRepository : 메시지" 같은 로거 이름은 건드리지 않는다).
    private static final Pattern SECRET_KEY_VALUE = Pattern.compile(
            "(?i)(\\b[\\w-]*(?:token|secret|password|authorization)[\\w-]*[\"']?[=:]\\s*[\"']?)[^\\s,;\"'&}]+");

    // "lat=37.26", "\"lng\":127.02", "latitude: 37.26" 처럼 키가 붙은 좌표.
    private static final Pattern COORDINATE_KEY_VALUE = Pattern.compile(
            "(?i)(\\b(?:lat|lng|lon|latitude|longitude)[\"']?\\s*[=:]\\s*[\"']?)-?\\d{1,3}(?:\\.\\d+)?");

    // "37.263573, 127.028601" 처럼 소수 넷째 자리 이상인 숫자 쌍.
    private static final Pattern COORDINATE_PAIR = Pattern.compile(
            "-?\\d{1,3}\\.\\d{4,}(\\s*,\\s*)-?\\d{1,3}\\.\\d{4,}");

    private LogMasker() {
    }

    public static String mask(String line) {
        if (line == null || line.isEmpty()) {
            return line;
        }
        String masked = EMAIL.matcher(line).replaceAll(MASK + "@" + MASK);
        masked = JWT.matcher(masked).replaceAll(MASK);
        masked = BEARER.matcher(masked).replaceAll("Bearer " + MASK);
        masked = SECRET_KEY_VALUE.matcher(masked).replaceAll("$1" + MASK);
        masked = COORDINATE_KEY_VALUE.matcher(masked).replaceAll("$1" + MASK);
        masked = COORDINATE_PAIR.matcher(masked).replaceAll(MASK + "$1" + MASK);
        return masked;
    }
}
