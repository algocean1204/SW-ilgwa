/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.exception;

import java.util.Map;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import com.swez.backend.global.logging.TraceId;
import com.swez.backend.global.response.ApiResponse;
import com.swez.backend.global.response.ErrorResponse;

import lombok.extern.slf4j.Slf4j;

/** 컨트롤러 레이어 예외를 가로채 ApiResponse 포맷으로 변환하는 전역 핸들러 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  /** 비즈니스 예외. errorCode 에 정의된 HTTP 상태와 메시지를 그대로 쓴다. */
  @ExceptionHandler(CustomException.class)
  public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
    MDC.put(TraceId.MDC_ERROR_CODE, e.getErrorCode().getCode());
    // HTTP 상태 코드 기반 로그 레벨 분리
    int status = e.getErrorCode().getHttpStatus().value();
    if (status >= 500) {
      log.error("[GlobalExceptionHandler] 서버 예외 | errorCode: {}", e.getErrorCode().getCode());
    } else if (status == 403 || status == 409) {
      log.warn("[GlobalExceptionHandler] 비즈니스 예외 | errorCode: {}", e.getErrorCode().getCode());
    } else {
      log.info("[GlobalExceptionHandler] 클라이언트 예외 | errorCode: {}", e.getErrorCode().getCode());
    }
    ErrorResponse error = ErrorResponse.of(e.getErrorCode());
    return ResponseEntity.status(e.getErrorCode().getHttpStatus()).body(ApiResponse.error(error));
  }

  /** {@code @Valid} 유효성 검증 실패. 필드별 오류 목록을 details 에 담아 반환한다. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
      MethodArgumentNotValidException e) {
    MDC.put(TraceId.MDC_ERROR_CODE, "VALIDATION_001");
    Map<String, String> details =
        e.getBindingResult().getFieldErrors().stream()
            .collect(
                Collectors.toMap(
                    fieldError -> fieldError.getField(),
                    fieldError ->
                        fieldError.getDefaultMessage() != null
                            ? fieldError.getDefaultMessage()
                            : "유효하지 않은 값입니다.",
                    // 동일 필드에 여러 에러가 있으면 첫 번째 메시지 사용
                    (existing, duplicate) -> existing));
    ErrorResponse error = ErrorResponse.of(GlobalErrorCode.VALIDATION_001, details);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
  }

  /** {@code @Validated} 유효성 검증 실패 (파라미터·경로 변수) */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
      ConstraintViolationException e) {
    MDC.put(TraceId.MDC_ERROR_CODE, "VALIDATION_001");
    Map<String, String> details =
        e.getConstraintViolations().stream()
            .collect(
                Collectors.toMap(
                    violation -> {
                      String path = violation.getPropertyPath().toString();
                      // "methodName.paramName" 형태에서 파라미터명만 추출
                      int dotIndex = path.lastIndexOf('.');
                      return dotIndex >= 0 ? path.substring(dotIndex + 1) : path;
                    },
                    violation -> violation.getMessage(),
                    (existing, duplicate) -> existing));
    ErrorResponse error = ErrorResponse.of(GlobalErrorCode.VALIDATION_001, details);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
  }

  /** 잘못된 쿼리 파라미터 값(예: ?status=active)으로 인한 enum·타입 변환 실패 */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatch(
      MethodArgumentTypeMismatchException e) {
    MDC.put(TraceId.MDC_ERROR_CODE, "VALIDATION_001");
    Map<String, String> details = Map.of(e.getName(), "허용되지 않는 값입니다: '" + e.getValue() + "'");
    ErrorResponse error = ErrorResponse.of(GlobalErrorCode.VALIDATION_001, details);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
  }

  /** 잘못된 JSON 형식 또는 역직렬화 실패 */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(
      HttpMessageNotReadableException e) {
    MDC.put(TraceId.MDC_ERROR_CODE, "VALIDATION_001");
    ErrorResponse error = ErrorResponse.of(GlobalErrorCode.VALIDATION_001);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
  }

  /** 필수 요청 파라미터 누락 */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameter(
      MissingServletRequestParameterException e) {
    MDC.put(TraceId.MDC_ERROR_CODE, "VALIDATION_001");
    Map<String, String> details = Map.of(e.getParameterName(), "필수 파라미터가 누락되었습니다.");
    ErrorResponse error = ErrorResponse.of(GlobalErrorCode.VALIDATION_001, details);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
  }

  /** multipart 업로드 크기 초과 */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(
      MaxUploadSizeExceededException e) {
    MDC.put(TraceId.MDC_ERROR_CODE, "VALIDATION_002");
    ErrorResponse error = ErrorResponse.of(GlobalErrorCode.VALIDATION_002);
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ApiResponse.error(error));
  }

  /** 존재하지 않는 경로 요청 */
  @ExceptionHandler(NoHandlerFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNoHandlerFound(NoHandlerFoundException e) {
    MDC.put(TraceId.MDC_ERROR_CODE, "SYSTEM_005");
    ErrorResponse error = ErrorResponse.of(GlobalErrorCode.SYSTEM_005);
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(error));
  }

  /** 지원하지 않는 HTTP 메서드 */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleHttpRequestMethodNotSupported(
      HttpRequestMethodNotSupportedException e) {
    MDC.put(TraceId.MDC_ERROR_CODE, "SYSTEM_004");
    ErrorResponse error = ErrorResponse.of(GlobalErrorCode.SYSTEM_004);
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiResponse.error(error));
  }

  /** 예상치 못한 예외. 서버 로그에 남기고 SYSTEM_001 을 반환한다. */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
    MDC.put(TraceId.MDC_ERROR_CODE, "SYSTEM_001");
    log.error("[GlobalExceptionHandler] 처리되지 않은 예외 발생", e);
    ErrorResponse error = ErrorResponse.of(GlobalErrorCode.SYSTEM_001);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(error));
  }
}
