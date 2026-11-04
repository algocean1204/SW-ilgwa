/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.course.exception;

import org.springframework.http.HttpStatus;

import com.swez.backend.global.exception.model.BaseErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CourseErrorCode implements BaseErrorCode {
  CRS_001(HttpStatus.NOT_FOUND, "CRS_001", "과외를 찾을 수 없습니다."),
  CRS_002(HttpStatus.NOT_FOUND, "CRS_002", "강의를 찾을 수 없습니다."),
  CRS_003(HttpStatus.BAD_REQUEST, "CRS_003", "커리큘럼이 아직 생성 중입니다."),
  CRS_004(HttpStatus.CONFLICT, "CRS_004", "이미 확정된 커리큘럼입니다."),
  CRS_005(HttpStatus.BAD_GATEWAY, "CRS_005", "FastAPI 커리큘럼 생성 요청에 실패했습니다."),
  CRS_006(HttpStatus.BAD_REQUEST, "CRS_006", "삭제할 수 없는 상태입니다."),
  CRS_007(HttpStatus.BAD_REQUEST, "CRS_007", "PDF 파일이 비어있습니다."),
  CRS_008(HttpStatus.BAD_REQUEST, "CRS_008", "application/pdf 형식의 PDF 파일만 업로드 가능합니다."),
  CRS_009(HttpStatus.PAYLOAD_TOO_LARGE, "CRS_009", "PDF 파일 크기가 100MB를 초과합니다."),
  CRS_010(HttpStatus.INTERNAL_SERVER_ERROR, "CRS_010", "PDF 파일 업로드에 실패했습니다."),
  /** 커리큘럼 생성에 실패한 과외. CURRICULUM_FAILED 상태에서는 확정할 수 없다. */
  CRS_011(HttpStatus.UNPROCESSABLE_ENTITY, "CRS_011", "커리큘럼 생성에 실패한 과외입니다."),
  CRS_012(HttpStatus.FORBIDDEN, "CRS_012", "현재 플랜의 최대 과외 수를 초과했습니다. 업그레이드해 주세요.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
