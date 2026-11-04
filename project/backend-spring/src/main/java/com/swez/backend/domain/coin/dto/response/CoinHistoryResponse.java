/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.coin.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 코인 거래 이력 응답 DTO */
@Builder
@Schema(description = "코인 거래 이력 페이지 응답")
public record CoinHistoryResponse(
    @Schema(description = "거래 이력 목록") List<CoinHistoryItem> content,
    @Schema(description = "현재 페이지 번호", example = "0") Integer page,
    @Schema(description = "페이지 크기", example = "20") Integer size,
    @Schema(description = "전체 요소 수", example = "42") Long totalElements,
    @Schema(description = "전체 페이지 수", example = "3") Integer totalPages,
    @Schema(description = "다음 페이지 존재 여부") Boolean hasNext) {

  /** 코인 거래 이력 단건 항목 */
  @Builder
  @Schema(description = "코인 거래 이력 항목")
  public record CoinHistoryItem(
      @Schema(description = "거래 ID") String id,
      @Schema(description = "거래 유형", example = "purchase") String type,
      @Schema(description = "거래 수량", example = "10") Integer amount,
      @Schema(description = "거래 후 잔액", example = "60") Integer balanceAfter,
      @Schema(description = "참조 대상 ID") String refId,
      @Schema(description = "비고") String note,
      @Schema(description = "거래 일시") LocalDateTime createdAt) {}
}
