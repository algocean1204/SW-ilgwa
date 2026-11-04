/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.course.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 과외 소스 PDF 업로드 응답 DTO */
@Builder
@Schema(description = "과외 소스 PDF 업로드 응답")
public record SourcePdfUploadResponse(
    @Schema(description = "업로드된 PDF URL") String url,
    @Schema(description = "원본 파일명") String originalFileName,
    @Schema(description = "파일 크기(bytes)") long size) {}
