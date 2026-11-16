package com.everystudy.common.error;

import org.springframework.http.HttpStatus;

/**
 * 오류 응답의 {@code code} 값. 목록의 원본은 API 명세 1장이다.
 * 공통 코드는 {@link CommonErrorCode}, 모듈 고유 코드는 각 모듈이 이 인터페이스를 구현한 enum 으로 둔다.
 */
public interface ErrorCode {

    /** 클라이언트 분기용 안정 값 (enum 상수 이름). */
    String name();

    HttpStatus status();

    String title();
}
