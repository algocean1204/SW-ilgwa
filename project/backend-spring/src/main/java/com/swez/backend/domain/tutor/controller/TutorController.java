/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.tutor.controller;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.swez.backend.domain.tutor.dto.request.CreateTutorRequest;
import com.swez.backend.domain.tutor.dto.request.PreviewTutorRequest;
import com.swez.backend.domain.tutor.dto.request.UpdateTutorRequest;
import com.swez.backend.domain.tutor.dto.response.CreateTutorResponse;
import com.swez.backend.domain.tutor.dto.response.PreviewTutorResponse;
import com.swez.backend.domain.tutor.dto.response.ProfileImageResponse;
import com.swez.backend.domain.tutor.dto.response.TutorListResponse;
import com.swez.backend.domain.tutor.dto.response.TutorResponse;
import com.swez.backend.domain.tutor.dto.response.VoiceSampleResponse;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** AI 튜터 프리셋 조회, 커스텀 CRUD, 미리보기 API. */
@Tag(name = "Tutor", description = "AI 튜터 관련 API")
@RequestMapping("/tutors")
@SecurityRequirement(name = "bearerAuth")
public interface TutorController {

  @Operation(summary = "내 튜터 목록 조회", description = "기본 프리셋 + 내 커스텀 튜터 목록을 조회합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = TutorListResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content)
  })
  @GetMapping
  ResponseEntity<ApiResponse<TutorListResponse>> getMyTutors(
      @AuthenticationPrincipal UserPrincipal principal);

  @Operation(summary = "커스텀 튜터 생성", description = "커스텀 AI 튜터를 생성합니다. Pro 이상 구독자만 사용할 수 있습니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "생성 성공",
        content = @Content(schema = @Schema(implementation = CreateTutorResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "유효성 검증 실패",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Pro 이상 구독 필요 (TUT_003)",
        content = @Content)
  })
  @PostMapping
  ResponseEntity<ApiResponse<CreateTutorResponse>> createTutor(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody CreateTutorRequest request);

  @Operation(summary = "튜터 단건 조회", description = "튜터 상세 정보를 조회합니다. 커스텀 튜터는 소유자만 조회할 수 있습니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = TutorResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "튜터 없음 (TUT_001)",
        content = @Content)
  })
  @GetMapping("/{tutorId}")
  ResponseEntity<ApiResponse<TutorResponse>> getTutor(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "튜터 ID", required = true) @PathVariable String tutorId);

  @Operation(
      summary = "커스텀 튜터 수정",
      description = "커스텀 튜터 정보를 수정합니다. null 필드는 기존 값 유지 (Partial Update). Pro 이상 전용.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "수정 성공",
        content = @Content(schema = @Schema(implementation = TutorResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Pro 이상 구독 필요 (TUT_003)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "튜터 없음 (TUT_001)",
        content = @Content)
  })
  @PatchMapping("/{tutorId}")
  ResponseEntity<ApiResponse<TutorResponse>> updateTutor(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "수정할 튜터 ID", required = true) @PathVariable String tutorId,
      @Valid @RequestBody UpdateTutorRequest request);

  @Operation(summary = "커스텀 튜터 삭제", description = "커스텀 튜터를 삭제합니다. 기본 제공 튜터는 삭제할 수 없습니다. Pro 이상 전용.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "204",
        description = "삭제 성공",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "기본 제공 튜터 삭제 불가 (TUT_002) 또는 Pro 이상 구독 필요 (TUT_003)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "튜터 없음 (TUT_001)",
        content = @Content)
  })
  @DeleteMapping("/{tutorId}")
  ResponseEntity<Void> deleteTutor(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "삭제할 튜터 ID", required = true) @PathVariable String tutorId);

  @Operation(
      summary = "커스텀 튜터 전체 삭제",
      description = "사용자의 커스텀 튜터를 모두 삭제합니다 (기본값 복원). 기본 프리셋 튜터는 유지됩니다. Pro 이상 전용.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "204",
        description = "전체 삭제 성공",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Pro 이상 구독 필요 (TUT_003)",
        content = @Content)
  })
  @DeleteMapping("/custom")
  ResponseEntity<Void> deleteAllCustomTutors(@AuthenticationPrincipal UserPrincipal principal);

  @Operation(summary = "튜터 초기화", description = "슬라이더·토글 값을 기본값으로 복원합니다. Pro 이상 전용.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "초기화 성공",
        content = @Content(schema = @Schema(implementation = TutorResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Pro 이상 구독 필요 (TUT_003)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "튜터 없음 (TUT_001)",
        content = @Content)
  })
  @PostMapping("/{tutorId}/reset")
  ResponseEntity<ApiResponse<TutorResponse>> resetTutor(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "초기화할 튜터 ID", required = true) @PathVariable String tutorId);

  @Operation(
      summary = "튜터 미리보기",
      description = "설정값을 바탕으로 AI 응답 샘플을 생성합니다. Pro 이상 전용. FastAPI를 통해 생성됩니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "미리보기 생성 성공",
        content = @Content(schema = @Schema(implementation = PreviewTutorResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "유효성 검증 실패",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Pro 이상 구독 필요 (TUT_003)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "502",
        description = "미리보기 생성 실패 (TUT_004)",
        content = @Content)
  })
  @PostMapping("/{tutorId}/preview")
  ResponseEntity<ApiResponse<PreviewTutorResponse>> previewTutor(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "미리보기 대상 튜터 ID", required = true) @PathVariable String tutorId,
      @Valid @RequestBody PreviewTutorRequest request);

  @Operation(summary = "음성 클론 샘플 업로드", description = "WAV/MP3 형식, 최대 10MB. Pro 이상 전용.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "업로드 성공",
        content = @Content(schema = @Schema(implementation = VoiceSampleResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "파일 비어있음 (TUT_005) / 지원하지 않는 형식 (TUT_006) / 크기 초과 (TUT_007)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "기본 제공 튜터 수정 불가 (TUT_002) 또는 Pro 이상 구독 필요 (TUT_003)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "튜터 없음 (TUT_001)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "500",
        description = "파일 업로드 실패 (TUT_008)",
        content = @Content)
  })
  @PostMapping(value = "/{tutorId}/voice-sample", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<ApiResponse<VoiceSampleResponse>> uploadVoiceSample(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "업로드 대상 튜터 ID", required = true) @PathVariable String tutorId,
      @RequestParam("file") MultipartFile file);

  @Operation(
      summary = "프로필 이미지 업로드",
      description = "JPG/PNG 형식, 최대 5MB. Pro 이상 전용. 기존 이미지는 덮어쓰기됩니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "업로드 성공",
        content = @Content(schema = @Schema(implementation = ProfileImageResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "파일 비어있음 (TUT_009) / 지원하지 않는 형식 (TUT_010) / 크기 초과 (TUT_011)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "기본 제공 튜터 수정 불가 (TUT_002) 또는 Pro 이상 구독 필요 (TUT_003)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "튜터 없음 (TUT_001)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "500",
        description = "파일 업로드 실패 (TUT_008)",
        content = @Content)
  })
  @PostMapping(value = "/{tutorId}/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<ApiResponse<ProfileImageResponse>> uploadProfileImage(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "업로드 대상 튜터 ID", required = true) @PathVariable String tutorId,
      @RequestParam("file") MultipartFile file);

  @Operation(summary = "프로필 이미지 삭제", description = "프로필 이미지를 삭제합니다. Pro 이상 전용.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "204",
        description = "삭제 성공"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "기본 제공 튜터 수정 불가 (TUT_002) 또는 Pro 이상 구독 필요 (TUT_003)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "튜터 없음 (TUT_001)",
        content = @Content)
  })
  @DeleteMapping("/{tutorId}/profile-image")
  ResponseEntity<Void> deleteProfileImage(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "삭제 대상 튜터 ID", required = true) @PathVariable String tutorId);
}
