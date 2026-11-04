/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.assignment.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swez.backend.domain.assignment.dto.request.SubmitAnswerRequest;
import com.swez.backend.domain.assignment.dto.response.AssignmentDetailResponse;
import com.swez.backend.domain.assignment.dto.response.AssignmentListResponse;
import com.swez.backend.domain.assignment.dto.response.GradeResponse;
import com.swez.backend.domain.assignment.dto.response.SubmissionResponse;
import com.swez.backend.domain.assignment.service.AssignmentService;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/** 과제 요청을 AssignmentService에 위임한다. */
@RestController
@RequiredArgsConstructor
public class AssignmentControllerImpl implements AssignmentController {

  private final AssignmentService assignmentService;

  @Override
  public ResponseEntity<ApiResponse<List<AssignmentListResponse>>> getAssignments(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String courseId,
      @RequestParam(required = false) String status) {
    return ResponseEntity.ok(
        ApiResponse.success(
            assignmentService.getAssignments(principal.getUserId(), courseId, status)));
  }

  @Override
  public ResponseEntity<ApiResponse<AssignmentDetailResponse>> getAssignment(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String courseId,
      @PathVariable String assignmentId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            assignmentService.getAssignment(principal.getUserId(), courseId, assignmentId)));
  }

  @Override
  public ResponseEntity<ApiResponse<SubmissionResponse>> submitAssignment(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String courseId,
      @PathVariable String assignmentId,
      @RequestBody SubmitAnswerRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            assignmentService.submitAssignment(
                principal.getUserId(), courseId, assignmentId, request)));
  }

  @Override
  public ResponseEntity<ApiResponse<GradeResponse>> getGrades(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String courseId,
      @PathVariable String assignmentId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            assignmentService.getGrades(principal.getUserId(), courseId, assignmentId)));
  }
}
