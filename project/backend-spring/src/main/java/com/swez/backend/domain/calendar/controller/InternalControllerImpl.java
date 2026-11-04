/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.swez.backend.domain.calendar.dto.request.AiResultCallbackRequest;
import com.swez.backend.domain.calendar.dto.request.CreateSubmissionRequest;
import com.swez.backend.domain.calendar.dto.response.AiResultAckResponse;
import com.swez.backend.domain.calendar.dto.response.SubmissionResponse;
import com.swez.backend.domain.calendar.service.InternalService;
import com.swez.backend.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/** 내부 콜백 요청을 InternalService 에 위임한다. */
@RestController
@RequiredArgsConstructor
public class InternalControllerImpl implements InternalController {

  private final InternalService internalService;

  @Override
  public ResponseEntity<ApiResponse<SubmissionResponse>> createSubmission(
      @Valid @RequestBody CreateSubmissionRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(internalService.createSubmission(request)));
  }

  @Override
  public ResponseEntity<ApiResponse<AiResultAckResponse>> receiveAiResult(
      @PathVariable String submissionId, @Valid @RequestBody AiResultCallbackRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(internalService.receiveAiResult(submissionId, request)));
  }
}
