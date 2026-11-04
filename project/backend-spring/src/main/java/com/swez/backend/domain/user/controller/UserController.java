/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.user.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.swez.backend.domain.user.dto.request.OnboardingRequest;
import com.swez.backend.domain.user.dto.request.UpdateProfileRequest;
import com.swez.backend.domain.user.dto.request.UpdateSettingsRequest;
import com.swez.backend.domain.user.dto.request.UpdateTelegramPreferencesRequest;
import com.swez.backend.domain.user.dto.response.OnboardingResponse;
import com.swez.backend.domain.user.dto.response.SettingsResponse;
import com.swez.backend.domain.user.dto.response.TelegramLinkResponse;
import com.swez.backend.domain.user.dto.response.TelegramPreferencesResponse;
import com.swez.backend.domain.user.dto.response.TelegramStatusResponse;
import com.swez.backend.domain.user.dto.response.UserProfileImageResponse;
import com.swez.backend.domain.user.dto.response.UserProfileResponse;
import com.swez.backend.domain.user.dto.response.UserStatsResponse;
import com.swez.backend.global.auth.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 사용자 프로필·온보딩·텔레그램·설정 API. */
@Tag(name = "User", description = "사용자 관련 API")
@RequestMapping("/users")
@SecurityRequirement(name = "bearerAuth")
public interface UserController {

  @Operation(summary = "내 프로필 조회", description = "현재 로그인한 사용자의 전체 프로필을 조회합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "프로필 조회 성공"),
    @ApiResponse(responseCode = "401", description = "인증 필요")
  })
  @GetMapping("/me")
  ResponseEntity<com.swez.backend.global.response.ApiResponse<UserProfileResponse>> getProfile(
      @AuthenticationPrincipal UserPrincipal principal);

  @Operation(
      summary = "프로필 수정",
      description = "변경할 필드만 포함하면 됩니다. null 필드는 기존 값 유지 (Partial Update).")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "프로필 수정 성공"),
    @ApiResponse(responseCode = "400", description = "유효성 검증 실패"),
    @ApiResponse(responseCode = "401", description = "인증 필요")
  })
  @PutMapping("/me")
  ResponseEntity<com.swez.backend.global.response.ApiResponse<UserProfileResponse>> updateProfile(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody UpdateProfileRequest request);

  @Operation(
      summary = "프로필 이미지 업로드",
      description = "사용자 프로필 이미지를 업로드합니다. 기존 이미지가 있으면 교체합니다. (jpg, png, webp / 최대 5MB)")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "업로드 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "파일 없음 또는 형식/크기 오류 (USER_009, USER_010, USER_011)"),
    @ApiResponse(responseCode = "401", description = "인증 필요"),
    @ApiResponse(responseCode = "500", description = "S3 업로드 실패 (USER_012)")
  })
  @PostMapping(value = "/me/profile-image", consumes = "multipart/form-data")
  ResponseEntity<com.swez.backend.global.response.ApiResponse<UserProfileImageResponse>>
      uploadProfileImage(
          @AuthenticationPrincipal UserPrincipal principal,
          @RequestParam("file") MultipartFile file);

  @Operation(summary = "프로필 이미지 삭제", description = "사용자 프로필 이미지를 삭제합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "삭제 성공"),
    @ApiResponse(responseCode = "401", description = "인증 필요")
  })
  @DeleteMapping("/me/profile-image")
  ResponseEntity<com.swez.backend.global.response.ApiResponse<Void>> deleteProfileImage(
      @AuthenticationPrincipal UserPrincipal principal);

  @Operation(summary = "온보딩 완료", description = "최초 가입 후 온보딩 정보를 입력합니다. 이미 완료된 사용자는 409.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "온보딩 완료 성공"),
    @ApiResponse(responseCode = "400", description = "필수 필드 누락"),
    @ApiResponse(responseCode = "401", description = "인증 필요"),
    @ApiResponse(responseCode = "409", description = "이미 온보딩 완료")
  })
  @PostMapping("/onboarding")
  ResponseEntity<com.swez.backend.global.response.ApiResponse<OnboardingResponse>>
      completeOnboarding(
          @AuthenticationPrincipal UserPrincipal principal,
          @Valid @RequestBody OnboardingRequest request);

  @Operation(
      summary = "텔레그램 연동 토큰 발급",
      description = "텔레그램 봇 연동용 일회성 토큰을 발급합니다 (Redis TTL 5분). 이미 연동된 계정은 409.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "토큰 발급 성공"),
    @ApiResponse(responseCode = "401", description = "인증 필요"),
    @ApiResponse(responseCode = "409", description = "이미 텔레그램 연동됨")
  })
  @PostMapping("/telegram/link")
  ResponseEntity<com.swez.backend.global.response.ApiResponse<TelegramLinkResponse>>
      generateTelegramLinkToken(@AuthenticationPrincipal UserPrincipal principal);

  @Operation(summary = "텔레그램 연동 상태 확인", description = "텔레그램 연동 여부와 계정 정보를 조회합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "상태 조회 성공"),
    @ApiResponse(responseCode = "401", description = "인증 필요")
  })
  @GetMapping("/telegram/status")
  ResponseEntity<com.swez.backend.global.response.ApiResponse<TelegramStatusResponse>>
      getTelegramStatus(@AuthenticationPrincipal UserPrincipal principal);

  @Operation(
      summary = "텔레그램 자동 전송 옵션 조회",
      description = "암기노트 전송·퀴즈 알림·친구 공유 수신 옵션을 조회합니다. 설정이 없으면 기본값(전부 false)으로 생성됩니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "옵션 조회 성공"),
    @ApiResponse(responseCode = "401", description = "인증 필요")
  })
  @GetMapping("/telegram/preferences")
  ResponseEntity<com.swez.backend.global.response.ApiResponse<TelegramPreferencesResponse>>
      getTelegramPreferences(@AuthenticationPrincipal UserPrincipal principal);

  @Operation(
      summary = "텔레그램 자동 전송 옵션 업데이트",
      description = "텔레그램 자동 전송 옵션을 업데이트합니다. null 필드는 기존 값 유지 (Partial update).")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "옵션 업데이트 성공"),
    @ApiResponse(responseCode = "401", description = "인증 필요")
  })
  @PutMapping("/telegram/preferences")
  ResponseEntity<com.swez.backend.global.response.ApiResponse<TelegramPreferencesResponse>>
      updateTelegramPreferences(
          @AuthenticationPrincipal UserPrincipal principal,
          @RequestBody UpdateTelegramPreferencesRequest request);

  @Operation(
      summary = "설정 업데이트",
      description = "접근성·알림 설정을 업데이트합니다. null 필드는 기존 값 유지 (Partial merge).")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "설정 업데이트 성공"),
    @ApiResponse(responseCode = "401", description = "인증 필요")
  })
  @PutMapping("/settings")
  ResponseEntity<com.swez.backend.global.response.ApiResponse<SettingsResponse>> updateSettings(
      @AuthenticationPrincipal UserPrincipal principal, @RequestBody UpdateSettingsRequest request);

  @Operation(summary = "학습 통계 요약", description = "총 학습 시간·완료 강의 수·연속 학습 일수를 조회합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "통계 조회 성공"),
    @ApiResponse(responseCode = "401", description = "인증 필요")
  })
  @GetMapping("/me/stats")
  ResponseEntity<com.swez.backend.global.response.ApiResponse<UserStatsResponse>> getMyStats(
      @AuthenticationPrincipal UserPrincipal principal);

  @Operation(
      summary = "회원 탈퇴",
      description =
          "현재 로그인한 사용자의 계정과 모든 관련 데이터를 영구 삭제합니다 (하드 삭제). "
              + "S3 프로필 이미지 및 Redis 임시 토큰도 함께 정리됩니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "회원 탈퇴 성공"),
    @ApiResponse(responseCode = "401", description = "인증 필요")
  })
  @DeleteMapping("/me")
  ResponseEntity<com.swez.backend.global.response.ApiResponse<Void>> deleteAccount(
      @AuthenticationPrincipal UserPrincipal principal);
}
