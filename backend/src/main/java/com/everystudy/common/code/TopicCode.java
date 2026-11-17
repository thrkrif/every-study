package com.everystudy.common.code;

import java.util.Arrays;
import java.util.Optional;

/** 스터디 주제 (DATA-MODEL 1장 고정 목록, PRD 4.2). 값 추가는 코드 변경이다. */
public enum TopicCode {

    JOB_PREP("취업준비"),
    CERTIFICATE("자격증"),
    LANGUAGE("어학"),
    CIVIL_SERVICE("공무원"),
    DEVELOPMENT("개발"),
    ETC("기타");

    private final String label;

    TopicCode(String label) {
        this.label = label;
    }

    public static Optional<TopicCode> find(String code) {
        return Arrays.stream(values()).filter(topic -> topic.name().equals(code)).findFirst();
    }

    public String label() {
        return label;
    }
}
