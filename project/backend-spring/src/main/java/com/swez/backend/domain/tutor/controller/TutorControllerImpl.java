/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.tutor.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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
import com.swez.backend.domain.tutor.service.TutorService;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/** 튜터 요청을 TutorService 에 위임한다. */
@RestController
@RequiredArgsConstructor
public class TutorControllerImpl implements TutorController {

  private final TutorService tutorService;

  @Override
  public ResponseEntity<ApiResponse<TutorListResponse>> getMyTutors(
      @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(ApiResponse.success(tutorService.getMyTutors(principal.getUserId())));
  }

  @Override
  public ResponseEntity<ApiResponse<CreateTutorResponse>> createTutor(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody CreateTutorRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(tutorService.createTutor(principal.getUserId(), request)));
  }

  @Override
  public ResponseEntity<ApiResponse<TutorResponse>> getTutor(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable String tutorId) {
    return ResponseEntity.ok(
        ApiResponse.success(tutorService.getTutor(principal.getUserId(), tutorId)));
  }

  @Override
  public ResponseEntity<ApiResponse<TutorResponse>> updateTutor(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String tutorId,
      @Valid @RequestBody UpdateTutorRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(tutorService.updateTutor(principal.getUserId(), tutorId, request)));
  }

  @Override
  public ResponseEntity<Void> deleteTutor(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable String tutorId) {
    tutorService.deleteTutor(principal.getUserId(), tutorId);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Void> deleteAllCustomTutors(
      @AuthenticationPrincipal UserPrincipal principal) {
    tutorService.deleteAllCustomTutors(principal.getUserId());
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<ApiResponse<TutorResponse>> resetTutor(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable String tutorId) {
    return ResponseEntity.ok(
        ApiResponse.success(tutorService.resetTutor(principal.getUserId(), tutorId)));
  }

  @Override
  public ResponseEntity<ApiResponse<PreviewTutorResponse>> previewTutor(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String tutorId,
      @Valid @RequestBody PreviewTutorRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(tutorService.previewTutor(principal.getUserId(), tutorId, request)));
  }

  @Override
  public ResponseEntity<ApiResponse<VoiceSampleResponse>> uploadVoiceSample(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String tutorId,
      @RequestParam("file") MultipartFile file) {
    return ResponseEntity.ok(
        ApiResponse.success(tutorService.uploadVoiceSample(principal.getUserId(), tutorId, file)));
  }

  @Override
  public ResponseEntity<ApiResponse<ProfileImageResponse>> uploadProfileImage(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String tutorId,
      @RequestParam("file") MultipartFile file) {
    return ResponseEntity.ok(
        ApiResponse.success(tutorService.uploadProfileImage(principal.getUserId(), tutorId, file)));
  }

  @Override
  public ResponseEntity<Void> deleteProfileImage(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable String tutorId) {
    tutorService.deleteProfileImage(principal.getUserId(), tutorId);
    return ResponseEntity.noContent().build();
  }
}
