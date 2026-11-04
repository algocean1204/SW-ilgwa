/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.mockexam.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.swez.backend.domain.mockexam.dto.request.GenerateExamRequest;
import com.swez.backend.domain.mockexam.dto.request.SubmitExamRequest;
import com.swez.backend.domain.mockexam.dto.response.ExamResultResponse;
import com.swez.backend.domain.mockexam.dto.response.MockExamDetailResponse;
import com.swez.backend.domain.mockexam.dto.response.MockExamListResponse;
import com.swez.backend.domain.mockexam.service.MockExamService;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/** 모의고사 요청을 MockExamService 에 위임한다. */
@RestController
@RequiredArgsConstructor
public class MockExamControllerImpl implements MockExamController {

  private final MockExamService mockExamService;

  @Override
  public ResponseEntity<ApiResponse<List<MockExamListResponse>>> getExamList(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable String courseId) {
    return ResponseEntity.ok(
        ApiResponse.success(mockExamService.getExamList(principal.getUserId(), courseId)));
  }

  @Override
  public ResponseEntity<ApiResponse<MockExamListResponse>> generateExam(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String courseId,
      @Valid @RequestBody GenerateExamRequest request) {
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            ApiResponse.success(
                mockExamService.generateExam(principal.getUserId(), courseId, request)));
  }

  @Override
  public ResponseEntity<ApiResponse<MockExamDetailResponse>> getExamDetail(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String courseId,
      @PathVariable String examId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            mockExamService.getExamDetail(principal.getUserId(), courseId, examId)));
  }

  @Override
  public ResponseEntity<ApiResponse<ExamResultResponse>> submitExam(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String courseId,
      @PathVariable String examId,
      @Valid @RequestBody SubmitExamRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            mockExamService.submitExam(principal.getUserId(), courseId, examId, request)));
  }

  @Override
  public ResponseEntity<ApiResponse<ExamResultResponse>> getExamResult(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String courseId,
      @PathVariable String examId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            mockExamService.getExamResult(principal.getUserId(), courseId, examId)));
  }
}
