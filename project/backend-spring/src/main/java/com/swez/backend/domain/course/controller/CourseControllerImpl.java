/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.course.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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
import com.swez.backend.domain.course.service.CourseService;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.page.OffsetPageResponse;
import com.swez.backend.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/** 과외 요청을 CourseService 에 위임한다. */
@RestController
@RequiredArgsConstructor
public class CourseControllerImpl implements CourseController {

  private final CourseService courseService;

  @Override
  public ResponseEntity<ApiResponse<OffsetPageResponse<CourseSummaryResponse>>> getMyCourses(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(required = false) CourseStatus status,
      @RequestParam(required = false) String subject,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(
        ApiResponse.success(
            courseService.getMyCourses(principal.getUserId(), status, subject, page, size)));
  }

  @Override
  public ResponseEntity<ApiResponse<CourseDetailResponse>> getCourse(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable String courseId) {
    return ResponseEntity.ok(
        ApiResponse.success(courseService.getCourse(principal.getUserId(), courseId)));
  }

  @Override
  public ResponseEntity<ApiResponse<List<ChapterSummaryResponse>>> getChapters(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable String courseId) {
    return ResponseEntity.ok(
        ApiResponse.success(courseService.getChapters(principal.getUserId(), courseId)));
  }

  @Override
  public ResponseEntity<ApiResponse<ChapterDetailResponse>> getChapter(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String courseId,
      @PathVariable String chapterId) {
    return ResponseEntity.ok(
        ApiResponse.success(courseService.getChapter(principal.getUserId(), courseId, chapterId)));
  }

  @Override
  public ResponseEntity<ApiResponse<LessonGenerationStatusResponse>> getLessonGenerationStatus(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String courseId,
      @PathVariable String chapterId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            courseService.getLessonGenerationStatus(principal.getUserId(), courseId, chapterId)));
  }

  // 과외 생성·수정·삭제

  @Override
  public ResponseEntity<ApiResponse<CourseDetailResponse>> createTutoring(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody CreateTutoringRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(courseService.createTutoring(principal, request)));
  }

  @Override
  public ResponseEntity<ApiResponse<SourcePdfUploadResponse>> uploadSourcePdf(
      @AuthenticationPrincipal UserPrincipal principal, @RequestParam("file") MultipartFile file) {
    return ResponseEntity.ok(
        ApiResponse.success(courseService.uploadSourcePdf(principal.getUserId(), file)));
  }

  @Override
  public ResponseEntity<ApiResponse<CurriculumResponse>> getCurriculum(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable String courseId) {
    return ResponseEntity.ok(ApiResponse.success(courseService.getCurriculum(principal, courseId)));
  }

  @Override
  public ResponseEntity<ApiResponse<CurriculumResponse>> confirmCurriculum(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable String courseId) {
    return ResponseEntity.ok(
        ApiResponse.success(courseService.confirmCurriculum(principal, courseId)));
  }

  @Override
  public ResponseEntity<ApiResponse<CourseDetailResponse>> updateTutoring(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String courseId,
      @RequestBody UpdateTutoringRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(courseService.updateTutoring(principal, courseId, request)));
  }

  @Override
  public ResponseEntity<Void> deleteTutoring(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable String courseId) {
    courseService.deleteTutoring(principal, courseId);
    return ResponseEntity.noContent().build();
  }

  // 대시보드

  @Override
  public ResponseEntity<ApiResponse<RecentTutoringResponse>> getRecentTutoring(
      @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(
        ApiResponse.success(courseService.getRecentTutoring(principal.getUserId())));
  }
}
