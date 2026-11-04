/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.user.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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
import com.swez.backend.domain.user.service.UserService;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/** 사용자 요청을 UserService 에 위임한다. */
@RestController
@RequiredArgsConstructor
public class UserControllerImpl implements UserController {

  private final UserService userService;

  @Override
  public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
      @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(ApiResponse.success(userService.getProfile(principal.getUserId())));
  }

  @Override
  public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody UpdateProfileRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(userService.updateProfile(principal.getUserId(), request)));
  }

  @Override
  public ResponseEntity<ApiResponse<UserProfileImageResponse>> uploadProfileImage(
      @AuthenticationPrincipal UserPrincipal principal, @RequestParam("file") MultipartFile file) {
    return ResponseEntity.ok(
        ApiResponse.success(userService.uploadProfileImage(principal.getUserId(), file)));
  }

  @Override
  public ResponseEntity<ApiResponse<Void>> deleteProfileImage(
      @AuthenticationPrincipal UserPrincipal principal) {
    userService.deleteProfileImage(principal.getUserId());
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  @Override
  public ResponseEntity<ApiResponse<OnboardingResponse>> completeOnboarding(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody OnboardingRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(userService.completeOnboarding(principal.getUserId(), request)));
  }

  @Override
  public ResponseEntity<ApiResponse<TelegramLinkResponse>> generateTelegramLinkToken(
      @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(
        ApiResponse.success(userService.generateTelegramLinkToken(principal.getUserId())));
  }

  @Override
  public ResponseEntity<ApiResponse<TelegramStatusResponse>> getTelegramStatus(
      @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(
        ApiResponse.success(userService.getTelegramStatus(principal.getUserId())));
  }

  @Override
  public ResponseEntity<ApiResponse<TelegramPreferencesResponse>> getTelegramPreferences(
      @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(
        ApiResponse.success(userService.getTelegramPreferences(principal.getUserId())));
  }

  @Override
  public ResponseEntity<ApiResponse<TelegramPreferencesResponse>> updateTelegramPreferences(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestBody UpdateTelegramPreferencesRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(userService.updateTelegramPreferences(principal.getUserId(), request)));
  }

  @Override
  public ResponseEntity<ApiResponse<SettingsResponse>> updateSettings(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestBody UpdateSettingsRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(userService.updateSettings(principal.getUserId(), request)));
  }

  @Override
  public ResponseEntity<ApiResponse<UserStatsResponse>> getMyStats(
      @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(ApiResponse.success(userService.getMyStats(principal.getUserId())));
  }

  @Override
  public ResponseEntity<ApiResponse<Void>> deleteAccount(
      @AuthenticationPrincipal UserPrincipal principal) {
    userService.deleteAccount(principal.getUserId());
    return ResponseEntity.ok(ApiResponse.success(null));
  }
}
