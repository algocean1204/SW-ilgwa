/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.note.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.swez.backend.domain.note.dto.request.CreateBookmarkRequest;
import com.swez.backend.domain.note.dto.request.CreateNoteRequest;
import com.swez.backend.domain.note.dto.request.UpdateNoteRequest;
import com.swez.backend.domain.note.dto.response.BookmarkResponse;
import com.swez.backend.domain.note.dto.response.NoteResponse;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 노트·북마크 API */
@Tag(name = "Note", description = "노트·북마크 API")
@RequestMapping("/tutoring/{courseId}/lessons/{chapterId}")
@SecurityRequirement(name = "bearerAuth")
public interface NoteController {

  @Operation(
      summary = "노트 생성",
      description = "강의 시청 중 사용자 메모 노트를 생성합니다. type은 항상 user_memo로 저장됩니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "생성 성공",
        content = @Content(schema = @Schema(implementation = NoteResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "유효성 검증 실패",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "과외 또는 강의 없음 (CRS_001, CRS_002)",
        content = @Content)
  })
  @PostMapping("/notes")
  ResponseEntity<ApiResponse<NoteResponse>> createNote(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과외 ID", required = true) @PathVariable String courseId,
      @Parameter(description = "강의 ID", required = true) @PathVariable String chapterId,
      @Valid @RequestBody CreateNoteRequest request);

  @Operation(summary = "노트 별표 토글", description = "노트의 별표 상태를 토글합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "토글 성공",
        content = @Content(schema = @Schema(implementation = NoteResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "노트 없음 (NTE_001)",
        content = @Content)
  })
  @PutMapping("/notes/{noteId}/star")
  ResponseEntity<ApiResponse<NoteResponse>> toggleStar(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과외 ID", required = true) @PathVariable String courseId,
      @Parameter(description = "강의 ID", required = true) @PathVariable String chapterId,
      @Parameter(description = "노트 ID", required = true) @PathVariable String noteId);

  @Operation(
      summary = "북마크 생성·갱신",
      description = "슬라이드 북마크를 생성합니다. 동일 슬라이드 재호출 시 memo만 업데이트됩니다 (upsert).")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "생성·갱신 성공",
        content = @Content(schema = @Schema(implementation = BookmarkResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "유효성 검증 실패",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "과외 또는 강의 없음 (CRS_001, CRS_002)",
        content = @Content)
  })
  @PostMapping("/bookmark")
  ResponseEntity<ApiResponse<BookmarkResponse>> createBookmark(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과외 ID", required = true) @PathVariable String courseId,
      @Parameter(description = "강의 ID", required = true) @PathVariable String chapterId,
      @Valid @RequestBody CreateBookmarkRequest request);

  @Operation(summary = "강의별 노트 목록 조회", description = "특정 강의에 속하는 노트 목록을 조회합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = NoteResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "과외 또는 강의 없음 (CRS_001, CRS_002)",
        content = @Content)
  })
  @GetMapping("/notes")
  ResponseEntity<ApiResponse<List<NoteResponse>>> getNotesByChapter(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과외 ID", required = true) @PathVariable String courseId,
      @Parameter(description = "강의 ID", required = true) @PathVariable String chapterId);

  @Operation(summary = "노트 수정", description = "노트의 제목·내용을 수정합니다. AI 생성 노트도 편집 가능합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "수정 성공",
        content = @Content(schema = @Schema(implementation = NoteResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "본인 노트만 수정 가능 (NTE_002)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "노트 없음 (NTE_001)",
        content = @Content)
  })
  @PutMapping("/notes/{noteId}")
  ResponseEntity<ApiResponse<NoteResponse>> updateNote(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과외 ID", required = true) @PathVariable String courseId,
      @Parameter(description = "강의 ID", required = true) @PathVariable String chapterId,
      @Parameter(description = "노트 ID", required = true) @PathVariable String noteId,
      @Valid @RequestBody UpdateNoteRequest request);

  @Operation(summary = "노트 삭제", description = "노트를 삭제합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "204",
        description = "노트 삭제 성공"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "본인 노트만 삭제 가능 (NTE_002)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "노트 없음 (NTE_001)",
        content = @Content)
  })
  @DeleteMapping("/notes/{noteId}")
  ResponseEntity<Void> deleteNote(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과외 ID", required = true) @PathVariable String courseId,
      @Parameter(description = "강의 ID", required = true) @PathVariable String chapterId,
      @Parameter(description = "노트 ID", required = true) @PathVariable String noteId);
}
