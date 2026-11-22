package com.everystudy.chat.domain;

/** 신고 사유 분류 (DATA-MODEL 9장, PLAN D-4). 값이 {@code report.reason} 에 저장되므로 이미 쓰인 이름은 바꾸지 않는다. */
public enum ReportReason {
    /** 광고·홍보. */
    SPAM_AD,
    /** 욕설·비방. */
    ABUSE,
    /** 부적절한 모집. */
    INAPPROPRIATE_RECRUIT,
    /** 괴롭힘·성적 불쾌감. */
    HARASSMENT,
    /** 사기·위장. */
    FRAUD,
    /** 기타. 상세 내용이 필수다. */
    OTHER
}
