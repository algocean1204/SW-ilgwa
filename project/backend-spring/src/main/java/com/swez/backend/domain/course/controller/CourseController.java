/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.course.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.swez.backend.domain.course.dto.request.CreateTutoringRequest;
import com.swez.backend.domain.course.dto.request.UpdateTutoringRequest;
import com.swez.backend.domain.course.dto.response.ChapterDetailResponse;
import com.swez.backend.domain.course.dto.response.ChapterSummaryResponse;
import com.swez.backend.domain.course.dto.response.CourseDetailResponse;
import com.swez.backend.domain.course.dto.response.CourseSummaryResponse;
import com.swez.backend.domain.course.dto.response.CurriculumResponse;
import com.swez.backend.domain.course.dto.response.LessonGenerationStatusResponse;
import com.swez.backend.domain.course.dto.response.RecentTutoringResponse;
import com.swez.backend.domain.course.dto.response.SourcePdfUploadResponse;
import com.swez.backend.domain.course.enums.CourseStatus;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.page.OffsetPageResponse;
import com.swez.backend.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 과외 CRUD와 강의 조회 API. */
@Validated
@Tag(name = "Tutoring", description = "과외 CRUD 및 강의 조회 API")
@RequestMapping("/tutoring")
@SecurityRequirement(name = "bearerAuth")
public interface CourseController {

  @Operation(summary = "내 과외 목록 조회", description = "내 과외 목록을 조회합니다. status·subject 필터를 지원합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = OffsetPageResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content)
  })
  @GetMapping
  ResponseEntity<ApiResponse<OffsetPageResponse<CourseSummaryResponse>>> getMyCourses(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "상태 필터") @RequestParam(required = false) CourseStatus status,
      @Parameter(description = "과목 필터") @RequestParam(required = false) String subject,
      @Parameter(description = "페이지 번호 (0 이상)", required = true)
          @Min(0)
          @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "페이지 크기 (1 이상)", required = true)
          @Min(1)
          @RequestParam(defaultValue = "20")
          int size);

  @Operation(summary = "과외 상세 조회", description = "과외 상세 정보를 조회합니다. 소유자만 접근할 수 있습니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = CourseDetailResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "과외 없음 (CRS_001)",
        content = @Content)
  })
  @GetMapping("/{courseId}")
  ResponseEntity<ApiResponse<CourseDetailResponse>> getCourse(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과외 ID", required = true) @PathVariable String courseId);

  @Operation(summary = "강의 목록 조회", description = "과외 내 강의(챕터) 목록을 순서대로 조회합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = ChapterSummaryResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "과외 없음 (CRS_001)",
        content = @Content)
  })
  @GetMapping("/{courseId}/lessons")
  ResponseEntity<ApiResponse<List<ChapterSummaryResponse>>> getChapters(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과외 ID", required = true) @PathVariable String courseId);

  @Operation(summary = "강의 상세 조회", description = "강의(챕터) 상세 정보를 조회합니다. 핵심 주제·북마크 목록을 포함합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = ChapterDetailResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "과외 또는 강의 없음 (CRS_001, CRS_002)",
        content = @Content)
  })
  @GetMapping("/{courseId}/lessons/{chapterId}")
  ResponseEntity<ApiResponse<ChapterDetailResponse>> getChapter(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과외 ID", required = true) @PathVariable String courseId,
      @Parameter(description = "강의 ID", required = true) @PathVariable String chapterId);

  @Operation(
      summary = "강의 생성 진행 상태 조회",
      description =
          "AI 강의 생성의 노드 단위 진행 상태를 조회합니다. FastAPI 진행률을 프록시하며, 조회 불가 시 status=unknown 폴백을 반환합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공 (FastAPI 미응답 시 status=unknown)",
        content = @Content(schema = @Schema(implementation = LessonGenerationStatusResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "과외 또는 강의 없음 (CRS_001, CRS_002)",
        content = @Content)
  })
  @GetMapping("/{courseId}/lessons/{chapterId}/generation-status")
  ResponseEntity<ApiResponse<LessonGenerationStatusResponse>> getLessonGenerationStatus(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과외 ID", required = true) @PathVariable String courseId,
      @Parameter(description = "강의 ID", required = true) @PathVariable String chapterId);

  // 과외 생성·수정·삭제

  @Operation(summary = "과외 생성", description = "과외를 생성하고 FastAPI 에 커리큘럼 생성을 요청합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "생성 성공",
        content = @Content(schema = @Schema(implementation = CourseDetailResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "502",
        description = "FastAPI 요청 실패 (CRS_005)",
        content = @Content)
  })
  @PostMapping
  ResponseEntity<ApiResponse<CourseDetailResponse>> createTutoring(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody CreateTutoringRequest request);

  @Operation(
      summary = "과외 소스 PDF 업로드",
      description = "과외 생성 전에 사용할 PDF 파일을 S3에 업로드하고 접근 URL을 반환합니다. (application/pdf, 최대 100MB)")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "업로드 성공",
        content = @Content(schema = @Schema(implementation = SourcePdfUploadResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "파일 없음 또는 형식 오류 (CRS_007, CRS_008)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "413",
        description = "파일 크기 초과 (CRS_009)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "500",
        description = "S3 업로드 실패 (CRS_010)",
        content = @Content)
  })
  @PostMapping(value = "/source-pdfs", consumes = "multipart/form-data")
  ResponseEntity<ApiResponse<SourcePdfUploadResponse>> uploadSourcePdf(
      @AuthenticationPrincipal UserPrincipal principal, @RequestParam("file") MultipartFile file);

  @Operation(summary = "커리큘럼 조회", description = "과외의 커리큘럼(챕터 목록 + 상태)을 조회합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = CurriculumResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "과외 없음 (CRS_001)",
        content = @Content)
  })
  @GetMapping("/{courseId}/curriculum")
  ResponseEntity<ApiResponse<CurriculumResponse>> getCurriculum(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과외 ID", required = true) @PathVariable String courseId);

  @Operation(
      summary = "커리큘럼 확정",
      description = "커리큘럼을 확정하고 ACTIVE 상태로 전환합니다. FastAPI 에 강의 생성을 요청합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "확정 성공",
        content = @Content(schema = @Schema(implementation = CurriculumResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "커리큘럼 아직 생성 중 (CRS_003)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "과외 없음 (CRS_001)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "이미 확정된 커리큘럼 (CRS_004)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "502",
        description = "FastAPI 요청 실패 (CRS_005)",
        content = @Content)
  })
  @PutMapping("/{courseId}/curriculum/confirm")
  ResponseEntity<ApiResponse<CurriculumResponse>> confirmCurriculum(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과외 ID", required = true) @PathVariable String courseId);

  @Operation(summary = "과외 수정", description = "과외 제목·과목·시험일을 부분 수정합니다. null 필드는 무시됩니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "수정 성공",
        content = @Content(schema = @Schema(implementation = CourseDetailResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "과외 없음 (CRS_001)",
        content = @Content)
  })
  @PutMapping("/{courseId}")
  ResponseEntity<ApiResponse<CourseDetailResponse>> updateTutoring(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과외 ID", required = true) @PathVariable String courseId,
      @RequestBody UpdateTutoringRequest request);

  @Operation(summary = "과외 삭제", description = "과외를 소프트 삭제합니다 (status → DELETED).")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "204",
        description = "삭제 성공"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "삭제 불가 상태 (CRS_006)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "과외 없음 (CRS_001)",
        content = @Content)
  })
  @DeleteMapping("/{courseId}")
  ResponseEntity<Void> deleteTutoring(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과외 ID", required = true) @PathVariable String courseId);

  // 대시보드

  @Operation(summary = "최근 학습 과외 목록", description = "최근 접근순으로 상위 5개의 과외 정보를 반환합니다. DELETED 상태 제외.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = RecentTutoringResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content)
  })
  @GetMapping("/recent")
  ResponseEntity<ApiResponse<RecentTutoringResponse>> getRecentTutoring(
      @AuthenticationPrincipal UserPrincipal principal);
}
