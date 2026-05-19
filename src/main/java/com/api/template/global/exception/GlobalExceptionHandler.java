package com.api.template.global.exception;

import com.api.template.global.exception.dto.FieldErrorResponse;
import com.api.template.global.response.ErrorResponse;
import com.api.template.global.response.code.ErrorResponseCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String VALIDATION_ERROR_MESSAGE = "요청 값 검증에 실패했습니다.";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse<List<FieldErrorResponse>>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e) {
        log.warn("MethodArgumentNotValidException: {}", e.getMessage());
        ErrorResponse<List<FieldErrorResponse>> errorResponse = ErrorResponse.of(
                ErrorResponseCode.INVALID_HTTP_MESSAGE_BODY,
                toFieldErrors(e.getFieldErrors()),
                VALIDATION_ERROR_MESSAGE);
        return ResponseEntity.status(errorResponse.getHttpStatus()).body(errorResponse);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse<List<FieldErrorResponse>>> handleBindException(BindException e) {
        log.warn("BindException: {}", e.getMessage());
        ErrorResponse<List<FieldErrorResponse>> errorResponse = ErrorResponse.of(
                ErrorResponseCode.INVALID_HTTP_MESSAGE_PARAMETER,
                toFieldErrors(e.getFieldErrors()),
                VALIDATION_ERROR_MESSAGE);
        return ResponseEntity.status(errorResponse.getHttpStatus()).body(errorResponse);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse<List<FieldErrorResponse>>> handleConstraintViolationException(
            ConstraintViolationException e) {
        log.warn("ConstraintViolationException: {}", e.getMessage());
        ErrorResponse<List<FieldErrorResponse>> errorResponse = ErrorResponse.of(
                ErrorResponseCode.INVALID_HTTP_MESSAGE_PARAMETER,
                toFieldErrorsFromConstraintViolations(e),
                VALIDATION_ERROR_MESSAGE);
        return ResponseEntity.status(errorResponse.getHttpStatus()).body(errorResponse);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse<?>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("HttpMessageNotReadableException: {}", e.getMessage());
        ErrorResponse<?> errorResponse = ErrorResponse.from(ErrorResponseCode.INVALID_HTTP_MESSAGE_BODY);
        return ResponseEntity.status(errorResponse.getHttpStatus()).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse<?>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("MethodArgumentTypeMismatchException: {}", e.getMessage());
        ErrorResponse<?> errorResponse = ErrorResponse.of(
                ErrorResponseCode.INVALID_HTTP_MESSAGE_PARAMETER,
                "요청 파라미터 '" + e.getName() + "'의 형식이 올바르지 않습니다.");
        return ResponseEntity.status(errorResponse.getHttpStatus()).body(errorResponse);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse<?>> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        log.warn("MissingServletRequestParameterException: {}", e.getMessage());
        ErrorResponse<?> errorResponse = ErrorResponse.of(
                ErrorResponseCode.INVALID_HTTP_MESSAGE_PARAMETER,
                "필수 요청 파라미터 '" + e.getParameterName() + "'이(가) 누락되었습니다.");
        return ResponseEntity.status(errorResponse.getHttpStatus()).body(errorResponse);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse<?>> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("HttpRequestMethodNotSupportedException: {}", e.getMessage());
        ErrorResponse<?> errorResponse = ErrorResponse.from(ErrorResponseCode.UNSUPPORTED_HTTP_METHOD);
        return ResponseEntity.status(errorResponse.getHttpStatus()).body(errorResponse);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse<?>> handleNotFoundEndpoint(Exception e) {
        log.warn("NotFoundEndpoint: {}", e.getMessage());
        ErrorResponse<?> errorResponse = ErrorResponse.from(ErrorResponseCode.NOT_FOUND_ENDPOINT);
        return ResponseEntity.status(errorResponse.getHttpStatus()).body(errorResponse);
    }

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse<?>> handleBaseException(BaseException e) {
        log.warn("BaseException: {}", e.getBaseResponseCode().getMessage());
        ErrorResponse<?> errorResponse = ErrorResponse.from(e.getBaseResponseCode());
        return ResponseEntity.status(errorResponse.getHttpStatus()).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse<?>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        ErrorResponse<?> errorResponse = ErrorResponse.from(ErrorResponseCode.SERVER_ERROR);
        return ResponseEntity.status(errorResponse.getHttpStatus()).body(errorResponse);
    }

    private List<FieldErrorResponse> toFieldErrors(List<FieldError> fieldErrors) {
        return fieldErrors.stream()
                .map(fieldError -> new FieldErrorResponse(
                        fieldError.getField(),
                        toRejectedValue(fieldError.getRejectedValue()),
                        fieldError.getDefaultMessage()))
                .toList();
    }

    private List<FieldErrorResponse> toFieldErrorsFromConstraintViolations(ConstraintViolationException e) {
        return e.getConstraintViolations().stream()
                .map(this::toFieldError)
                .toList();
    }

    private FieldErrorResponse toFieldError(ConstraintViolation<?> violation) {
        return new FieldErrorResponse(
                getLastPathNode(violation),
                toRejectedValue(violation.getInvalidValue()),
                violation.getMessage());
    }

    private String getLastPathNode(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex == -1) {
            return path;
        }
        return path.substring(dotIndex + 1);
    }

    private String toRejectedValue(Object rejectedValue) {
        if (rejectedValue == null) {
            return null;
        }
        return String.valueOf(rejectedValue);
    }
}
