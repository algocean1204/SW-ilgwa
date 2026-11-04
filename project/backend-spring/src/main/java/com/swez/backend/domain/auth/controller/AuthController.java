/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.swez.backend.domain.auth.dto.request.GoogleLoginRequest;
import com.swez.backend.domain.auth.dto.request.KakaoLoginRequest;
import com.swez.backend.domain.auth.dto.response.LoginResponse;
import com.swez.backend.domain.auth.dto.response.TokenRefreshResponse;
import com.swez.backend.global.auth.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 소셜 로그인(Google·Kakao)과 토큰 갱신 API. */
@Tag(name = "Auth", description = "인증 관련 API")
@RequestMapping("/auth")
public interface AuthController {

  @Operation(
      summary = "Google 소셜 로그인",
      description = "프론트에서 받은 Google ID Token을 검증하고 액세스 토큰과 리프레시 쿠키를 반환합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Google 로그인 성공"),
    @ApiResponse(responseCode = "400", description = "입력값 유효성 검증 실패"),
    @ApiResponse(responseCode = "401", description = "Google 인증 실패"),
    @ApiResponse(responseCode = "409", description = "이미 사용 중인 이메일")
  })
  @PostMapping("/login/google")
  ResponseEntity<com.swez.backend.global.response.ApiResponse<LoginResponse>> loginWithGoogle(
      @Valid @RequestBody GoogleLoginRequest request, HttpServletResponse response);

  @Operation(
      summary = "Kakao 소셜 로그인",
      description = "프론트에서 받은 Authorization Code로 Kakao 토큰을 교환하고 액세스 토큰과 리프레시 쿠키를 반환합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Kakao 로그인 성공"),
    @ApiResponse(responseCode = "400", description = "입력값 유효성 검증 실패"),
    @ApiResponse(responseCode = "401", description = "Kakao 인증 실패"),
    @ApiResponse(responseCode = "409", description = "이미 사용 중인 이메일")
  })
  @PostMapping("/login/kakao")
  ResponseEntity<com.swez.backend.global.response.ApiResponse<LoginResponse>> loginWithKakao(
      @Valid @RequestBody KakaoLoginRequest request, HttpServletResponse response);

  @Operation(
      summary = "로컬 데모 로그인",
      description = "DEV_LOGIN_ENABLED=true 인 로컬 데모 환경에서만 테스트 계정으로 액세스 토큰과 리프레시 쿠키를 반환합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "데모 로그인 성공"),
    @ApiResponse(responseCode = "403", description = "데모 로그인 비활성화")
  })
  @PostMapping("/dev-login")
  ResponseEntity<com.swez.backend.global.response.ApiResponse<LoginResponse>> devLogin(
      HttpServletResponse response);

  @Operation(
      summary = "액세스 토큰 갱신",
      description =
          "HTTP-Only 쿠키(ilkwa_refresh)로 새 액세스 토큰을 발급합니다. Token Rotation으로 리프레시 쿠키도 갱신됩니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "토큰 갱신 성공"),
    @ApiResponse(responseCode = "401", description = "리프레시 토큰이 유효하지 않거나 만료됨")
  })
  @PostMapping("/refresh")
  ResponseEntity<com.swez.backend.global.response.ApiResponse<TokenRefreshResponse>> refresh(
      HttpServletRequest request, HttpServletResponse response);

  @Operation(
      summary = "로그아웃",
      description =
          "리프레시 토큰을 무효화하고 ilkwa_refresh 쿠키를 제거합니다. 만료된 액세스 토큰으로도 호출 가능하며, 쿠키가 없어도 멱등하게 성공 응답을 반환합니다.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "로그아웃 성공")})
  @PostMapping("/logout")
  ResponseEntity<com.swez.backend.global.response.ApiResponse<Void>> logout(
      HttpServletRequest request, HttpServletResponse response);

  @Operation(
      summary = "회원 탈퇴",
      description =
          "인증된 사용자를 탈퇴(소프트 삭제) 처리합니다. 계정 상태를 WITHDRAWN 으로 전이하고 데이터는 보존하되, 재로그인·재가입을 차단합니다. 리프레시 토큰을 무효화하고 ilkwa_refresh 쿠키를 제거합니다.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "회원 탈퇴 성공")})
  @DeleteMapping("/withdraw")
  ResponseEntity<com.swez.backend.global.response.ApiResponse<Void>> withdraw(
      @AuthenticationPrincipal UserPrincipal principal,
      HttpServletRequest request,
      HttpServletResponse response);
}
