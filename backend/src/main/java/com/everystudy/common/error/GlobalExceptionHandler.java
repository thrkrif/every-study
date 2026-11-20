package com.everystudy.common.error;

import com.everystudy.common.web.TraceIdFilter;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 모든 오류를 RFC 9457 problem details 로 응답한다 (형식의 원본: API 명세 1장).
 * Spring MVC 가 던지는 표준 예외는 부모 클래스가 problem details 로 바꾸고, 여기서는 {@code code} 와 {@code traceId} 를 더한다.
 * 입력이 잘못된 경우(본문·파라미터)는 모두 {@code VALIDATION_FAILED}, 처리하지 못한 예외는 {@code INTERNAL_ERROR} 다.
 * 405·415 처럼 {@code status} 만으로 구분되는 표준 오류에는 {@code code} 를 붙이지 않는다.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<Object> handleBusiness(BusinessException ex, WebRequest request) {
        ProblemDetail body = problem(ex.errorCode(), ex.detail());
        ex.properties().forEach(body::setProperty);
        return handleExceptionInternal(ex, body, new HttpHeaders(), ex.errorCode().status(), request);
    }

    /**
     * 비관적 락을 정해진 시간 안에 얻지 못했거나 교착으로 밀려난 경우 (ADR-0004 결정 5). 재시도할 수 있으므로
     * {@code 409 LOCK_TIMEOUT} 으로 응답한다. 원인 메시지(SQL 등)는 응답에 넣지 않는다.
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<Object> handleLockFailure(PessimisticLockingFailureException ex, WebRequest request) {
        logger.warn("락을 얻지 못함: " + ex.getClass().getSimpleName());
        ProblemDetail body = problem(CommonErrorCode.LOCK_TIMEOUT, null);
        return handleExceptionInternal(ex, body, new HttpHeaders(), CommonErrorCode.LOCK_TIMEOUT.status(), request);
    }

    /** 서비스가 검사한 입력 오류 (현재 시각·설정값에 따라 달라지는 규칙). 형식 오류와 같은 응답이다. */
    @ExceptionHandler(InvalidInputException.class)
    ResponseEntity<Object> handleInvalidInput(InvalidInputException ex, WebRequest request) {
        return validationFailed(ex, ex.violations(), new HttpHeaders(), request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        return validationFailed(ex, errors, headers, request);
    }

    /** 읽을 수 없는 본문(깨진 JSON, 타입이 다른 값). 어느 필드인지 특정하지 않으므로 errors 는 비어 있다. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return validationFailed(ex, List.of(), headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String name = ex instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName()
                : ex.getPropertyName();
        return validationFailed(ex, List.of(new FieldViolation(name, "형식이 올바르지 않습니다")), headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = List.of(new FieldViolation(ex.getParameterName(), "필수 값입니다"));
        return validationFailed(ex, errors, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return validationFailed(ex, errors, headers, request);
    }

    /** 처리하지 못한 예외. 원인은 로그에만 남기고 응답에는 넣지 않는다 (traceId 로 로그를 찾는다). */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        logger.error("처리하지 못한 예외", ex);
        ProblemDetail body = problem(CommonErrorCode.INTERNAL_ERROR, null);
        return handleExceptionInternal(ex, body, new HttpHeaders(), CommonErrorCode.INTERNAL_ERROR.status(), request);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = problem(CommonErrorCode.NOT_FOUND, null);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /** 부모가 만든 응답을 포함해 모든 problem details 에 traceId 를 붙인다. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            problem.setProperty("traceId", MDC.get(TraceIdFilter.MDC_KEY));
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private ResponseEntity<Object> validationFailed(
            Exception ex, List<FieldViolation> errors, HttpHeaders headers, WebRequest request) {
        ProblemDetail body = problem(CommonErrorCode.VALIDATION_FAILED, null);
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, CommonErrorCode.VALIDATION_FAILED.status(), request);
    }

    private static ProblemDetail problem(ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatus(code.status());
        problem.setTitle(code.title());
        problem.setDetail(detail);
        problem.setProperty("code", code.name());
        return problem;
    }
}
