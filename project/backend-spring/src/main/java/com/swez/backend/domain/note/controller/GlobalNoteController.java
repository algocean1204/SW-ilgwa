/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.note.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.swez.backend.domain.note.dto.response.NoteListResponse;
import com.swez.backend.domain.note.dto.response.TelegramSendResponse;
import com.swez.backend.global.auth.UserPrincipal;
import com.swez.backend.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 과외/강의 컨텍스트 밖에서 동작하는 전체 노트 API */
@Tag(name = "Note", description = "전체 노트 API")
@SecurityRequirement(name = "bearerAuth")
public interface GlobalNoteController {

  @Operation(summary = "전체 노트 목록 조회", description = "과목별 필터, 검색, 별표 필터를 지원하는 페이징된 전체 노트 목록을 조회합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = NoteListResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content)
  })
  @GetMapping("/notes")
  ResponseEntity<ApiResponse<Page<NoteListResponse>>> getAllNotes(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "과목 필터") @RequestParam(required = false) String subject,
      @Parameter(description = "검색어 (제목+본문)") @RequestParam(required = false) String search,
      @Parameter(description = "별표 필터") @RequestParam(required = false) Boolean starred,
      @PageableDefault(size = 20) Pageable pageable);

  @Operation(summary = "텔레그램으로 노트 전송", description = "노트 내용을 텔레그램으로 전송합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "전송 성공",
        content = @Content(schema = @Schema(implementation = TelegramSendResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "텔레그램 미연동 (NTE_003)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "노트 없음 (NTE_001)",
        content = @Content),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "502",
        description = "텔레그램 전송 실패 (NTE_004)",
        content = @Content)
  })
  @PostMapping("/notes/{noteId}/telegram-send")
  ResponseEntity<ApiResponse<TelegramSendResponse>> sendToTelegram(
      @AuthenticationPrincipal UserPrincipal principal,
      @Parameter(description = "노트 ID", required = true) @PathVariable String noteId);
}
