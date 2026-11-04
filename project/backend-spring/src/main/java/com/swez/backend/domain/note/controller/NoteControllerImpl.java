/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.note.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.swez.backend.domain.note.dto.request.CreateBookmarkRequest;
import com.swez.backend.domain.note.dto.request.CreateNoteRequest;
import com.swez.backend.domain.note.dto.request.UpdateNoteRequest;
import com.swez.backend.domain.note.dto.response.BookmarkResponse;
import com.swez.backend.domain.note.dto.response.NoteResponse;
import com.swez.backend.domain.note.service.NoteService;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/** 노트·북마크 요청을 NoteService에 위임한다. */
@RestController
@RequiredArgsConstructor
public class NoteControllerImpl implements NoteController {

  private final NoteService noteService;

  @Override
  public ResponseEntity<ApiResponse<NoteResponse>> createNote(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String courseId,
      @PathVariable String chapterId,
      @Valid @RequestBody CreateNoteRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                noteService.createNote(principal.getUserId(), courseId, chapterId, request)));
  }

  @Override
  public ResponseEntity<ApiResponse<NoteResponse>> toggleStar(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String courseId,
      @PathVariable String chapterId,
      @PathVariable String noteId) {
    return ResponseEntity.ok(
        ApiResponse.success(noteService.toggleStar(principal.getUserId(), noteId)));
  }

  @Override
  public ResponseEntity<ApiResponse<BookmarkResponse>> createBookmark(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String courseId,
      @PathVariable String chapterId,
      @Valid @RequestBody CreateBookmarkRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                noteService.createBookmark(principal.getUserId(), courseId, chapterId, request)));
  }

  @Override
  public ResponseEntity<ApiResponse<List<NoteResponse>>> getNotesByChapter(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String courseId,
      @PathVariable String chapterId) {
    List<NoteResponse> result =
        noteService.getNotesByChapter(principal.getUserId(), courseId, chapterId, null);
    return ResponseEntity.ok(ApiResponse.success(result));
  }

  @Override
  public ResponseEntity<ApiResponse<NoteResponse>> updateNote(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String courseId,
      @PathVariable String chapterId,
      @PathVariable String noteId,
      @Valid @RequestBody UpdateNoteRequest request) {
    NoteResponse result = noteService.updateNote(principal.getUserId(), noteId, request);
    return ResponseEntity.ok(ApiResponse.success(result));
  }

  @Override
  public ResponseEntity<Void> deleteNote(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String courseId,
      @PathVariable String chapterId,
      @PathVariable String noteId) {
    noteService.deleteNote(principal.getUserId(), noteId);
    return ResponseEntity.noContent().build();
  }
}
