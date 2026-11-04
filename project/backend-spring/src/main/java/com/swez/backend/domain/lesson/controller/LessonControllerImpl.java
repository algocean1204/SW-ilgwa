/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.lesson.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.swez.backend.domain.lesson.dto.request.ProgressRequest;
import com.swez.backend.domain.lesson.dto.request.QuizAnswerRequest;
import com.swez.backend.domain.lesson.dto.response.QuizListResponse;
import com.swez.backend.domain.lesson.dto.response.QuizResultResponse;
import com.swez.backend.domain.lesson.dto.response.SlideListResponse;
import com.swez.backend.domain.lesson.service.LessonService;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/** 학습 실행 요청을 LessonService에 위임한다. */
@RestController
@RequiredArgsConstructor
public class LessonControllerImpl implements LessonController {

  private final LessonService lessonService;

  @Override
  public ResponseEntity<ApiResponse<SlideListResponse>> getSlides(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String courseId,
      @PathVariable String chapterId) {
    return ResponseEntity.ok(
        ApiResponse.success(lessonService.getSlides(principal.getUserId(), courseId, chapterId)));
  }

  @Override
  public ResponseEntity<ApiResponse<Void>> recordProgress(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String courseId,
      @PathVariable String chapterId,
      @Valid @RequestBody ProgressRequest request) {
    lessonService.recordProgress(principal.getUserId(), courseId, chapterId, request);
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  @Override
  public ResponseEntity<ApiResponse<QuizListResponse>> getQuizzes(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String courseId,
      @PathVariable String chapterId) {
    return ResponseEntity.ok(
        ApiResponse.success(lessonService.getQuizzes(principal.getUserId(), courseId, chapterId)));
  }

  @Override
  public ResponseEntity<ApiResponse<QuizResultResponse>> getQuizAnswers(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String courseId,
      @PathVariable String chapterId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            lessonService.getQuizAnswers(principal.getUserId(), courseId, chapterId)));
  }

  @Override
  public ResponseEntity<ApiResponse<QuizResultResponse>> submitQuizAnswers(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String courseId,
      @PathVariable String chapterId,
      @Valid @RequestBody QuizAnswerRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            lessonService.submitQuizAnswers(principal.getUserId(), courseId, chapterId, request)));
  }
}
