package com.everystudy.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.ProbeController.class)
@Import(GlobalExceptionHandlerTest.ProbeController.class)
class GlobalExceptionHandlerTest {

    @RestController
    static class ProbeController {

        record ProbeRequest(@NotBlank String name) {
        }

        @GetMapping("/probe/business")
        void business() {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "스터디 42 없음");
        }

        @PostMapping("/probe/validation")
        void validation(@Valid @RequestBody ProbeRequest request) {
        }

        @GetMapping("/probe/param")
        void param(@RequestParam int size) {
        }

        @GetMapping("/probe/constraint")
        void constraint(@RequestParam @Min(1) int size) {
        }

        @GetMapping("/probe/unexpected")
        void unexpected() {
            throw new IllegalStateException("jdbc:mysql://internal-host/secret-table");
        }
    }

    @Autowired
    MockMvcTester mvc;

    @Test
    @DisplayName("비즈니스 예외는 code 와 traceId 를 담은 problem+json 으로 응답한다")
    void businessExceptionBecomesProblemDetail() {
        assertThat(mvc.get().uri("/probe/business"))
                .hasStatus(404)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .satisfies(json -> {
                    // type 은 기본값(about:blank)이면 응답에서 생략된다 (RFC 9457 3.1.1).
                    assertThat(json).doesNotHavePath("$.type");
                    assertThat(json).extractingPath("$.status").isEqualTo(404);
                    assertThat(json).extractingPath("$.code").isEqualTo("NOT_FOUND");
                    assertThat(json).extractingPath("$.title").asString().isNotBlank();
                    assertThat(json).extractingPath("$.detail").isEqualTo("스터디 42 없음");
                    assertThat(json).extractingPath("$.traceId").asString().isNotBlank();
                });
    }

    @Test
    @DisplayName("본문 검증 실패는 VALIDATION_FAILED 와 필드 오류 목록으로 응답한다")
    void invalidBodyBecomesValidationFailed() {
        assertThat(mvc.post().uri("/probe/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
                .hasStatus(400)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
                    assertThat(json).extractingPath("$.errors[0].field").isEqualTo("name");
                    assertThat(json).extractingPath("$.errors[0].message").asString().isNotBlank();
                    assertThat(json).extractingPath("$.traceId").asString().isNotBlank();
                });
    }

    @Test
    @DisplayName("없는 경로는 NOT_FOUND 로 응답한다")
    void unknownPathBecomesNotFound() {
        assertThat(mvc.get().uri("/probe/nowhere"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("읽을 수 없는 JSON 본문은 VALIDATION_FAILED 와 빈 필드 오류 목록으로 응답한다")
    void malformedBodyBecomesValidationFailed() {
        assertThat(mvc.post().uri("/probe/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":"))
                .hasStatus(400)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
                    assertThat(json).extractingPath("$.errors").asArray().isEmpty();
                    assertThat(json).doesNotHavePath("$.detail");
                });
    }

    @Test
    @DisplayName("형식이 틀린 파라미터는 VALIDATION_FAILED 와 파라미터 이름으로 응답한다")
    void mistypedParameterBecomesValidationFailed() {
        assertThat(mvc.get().uri("/probe/param?size=abc"))
                .hasStatus(400)
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
                    assertThat(json).extractingPath("$.errors[0].field").isEqualTo("size");
                });
    }

    @Test
    @DisplayName("빠진 필수 파라미터는 VALIDATION_FAILED 와 파라미터 이름으로 응답한다")
    void missingParameterBecomesValidationFailed() {
        assertThat(mvc.get().uri("/probe/param"))
                .hasStatus(400)
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
                    assertThat(json).extractingPath("$.errors[0].field").isEqualTo("size");
                });
    }

    @Test
    @DisplayName("파라미터 제약 위반은 VALIDATION_FAILED 와 파라미터 이름으로 응답한다")
    void parameterConstraintViolationBecomesValidationFailed() {
        assertThat(mvc.get().uri("/probe/constraint?size=0"))
                .hasStatus(400)
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
                    assertThat(json).extractingPath("$.errors[0].field").isEqualTo("size");
                    assertThat(json).extractingPath("$.errors[0].message").asString().isNotBlank();
                });
    }

    @Test
    @DisplayName("처리하지 못한 예외는 내부 정보를 숨긴 500 INTERNAL_ERROR 로 응답한다")
    void unexpectedExceptionBecomesInternalError() throws Exception {
        var result = mvc.get().uri("/probe/unexpected").exchange();

        assertThat(result)
                .hasStatus(500)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.code").isEqualTo("INTERNAL_ERROR");
                    assertThat(json).extractingPath("$.traceId").asString().isNotBlank();
                    assertThat(json).doesNotHavePath("$.detail");
                });
        assertThat(result.getResponse().getContentAsString()).doesNotContain("secret-table", "IllegalStateException");
    }

    @Test
    @DisplayName("허용하지 않는 메서드는 code 없이 405 와 traceId 로 응답한다")
    void unsupportedMethodHasNoCode() {
        assertThat(mvc.delete().uri("/probe/business"))
                .hasStatus(405)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).doesNotHavePath("$.code");
                    assertThat(json).extractingPath("$.traceId").asString().isNotBlank();
                });
    }

    @Test
    @DisplayName("요청마다 traceId 가 다르다")
    void traceIdDiffersPerRequest() throws Exception {
        String first = mvc.get().uri("/probe/business").exchange().getResponse().getContentAsString();
        String second = mvc.get().uri("/probe/business").exchange().getResponse().getContentAsString();

        assertThat(first).isNotEqualTo(second);
    }
}
