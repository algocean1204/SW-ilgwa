/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.note.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swez.backend.domain.note.dto.response.NoteListResponse;
import com.swez.backend.domain.note.dto.response.TelegramSendResponse;
import com.swez.backend.domain.note.service.NoteService;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/** 전체 노트 요청을 NoteService에 위임한다. */
@RestController
@RequiredArgsConstructor
public class GlobalNoteControllerImpl implements GlobalNoteController {

  private final NoteService noteService;

  @Override
  public ResponseEntity<ApiResponse<Page<NoteListResponse>>> getAllNotes(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(required = false) String subject,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Boolean starred,
      @PageableDefault(size = 20) Pageable pageable) {
    // 클라이언트가 요청한 페이지 크기는 최대 100으로 자른다
    int safeSize = Math.min(pageable.getPageSize(), 100);
    Page<NoteListResponse> result =
        noteService.getAllNotes(
            principal.getUserId(), subject, search, starred, pageable.getPageNumber(), safeSize);
    return ResponseEntity.ok(ApiResponse.success(result));
  }

  @Override
  public ResponseEntity<ApiResponse<TelegramSendResponse>> sendToTelegram(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable String noteId) {
    TelegramSendResponse result = noteService.sendNoteToTelegram(principal.getUserId(), noteId);
    return ResponseEntity.ok(ApiResponse.success(result));
  }
}
