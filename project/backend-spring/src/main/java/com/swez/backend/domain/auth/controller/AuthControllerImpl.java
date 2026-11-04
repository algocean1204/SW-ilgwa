/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.swez.backend.domain.auth.dto.request.GoogleLoginRequest;
import com.swez.backend.domain.auth.dto.request.KakaoLoginRequest;
import com.swez.backend.domain.auth.dto.response.LoginResponse;
import com.swez.backend.domain.auth.dto.response.TokenRefreshResponse;
import com.swez.backend.domain.auth.service.AuthService;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/** 인증 요청을 비즈니스 로직 없이 AuthService 에 위임한다. */
@RestController
@RequiredArgsConstructor
public class AuthControllerImpl implements AuthController {

  private final AuthService authService;

  @Override
  public ResponseEntity<ApiResponse<LoginResponse>> loginWithGoogle(
      @Valid @RequestBody GoogleLoginRequest request, HttpServletResponse response) {
    return ResponseEntity.ok(ApiResponse.success(authService.loginWithGoogle(request, response)));
  }

  @Override
  public ResponseEntity<ApiResponse<LoginResponse>> loginWithKakao(
      @Valid @RequestBody KakaoLoginRequest request, HttpServletResponse response) {
    return ResponseEntity.ok(ApiResponse.success(authService.loginWithKakao(request, response)));
  }

  @Override
  public ResponseEntity<ApiResponse<LoginResponse>> devLogin(HttpServletResponse response) {
    return ResponseEntity.ok(ApiResponse.success(authService.devLogin(response)));
  }

  @Override
  public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(
      HttpServletRequest request, HttpServletResponse response) {
    return ResponseEntity.ok(ApiResponse.success(authService.refresh(request, response)));
  }

  @Override
  public ResponseEntity<ApiResponse<Void>> logout(
      HttpServletRequest request, HttpServletResponse response) {
    authService.logout(request, response);
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  @Override
  public ResponseEntity<ApiResponse<Void>> withdraw(
      @AuthenticationPrincipal UserPrincipal principal,
      HttpServletRequest request,
      HttpServletResponse response) {
    authService.withdraw(principal.getUserId(), request, response);
    return ResponseEntity.ok(ApiResponse.success(null));
  }
}
