/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.page;

import java.util.List;

import lombok.Getter;

/**
 * 오프셋 기반 페이지네이션 응답 래퍼
 *
 * <p>관리자 페이지, 검색 결과 등 임의 페이지 이동이 필요한 목록에 사용
 */
@Getter
public class OffsetPageResponse<T> {
  private final List<T> content;
  private final int page;
  private final int size;
  private final long totalElements;
  private final int totalPages;
  private final boolean hasNext;
  private final boolean hasPrevious;

  private OffsetPageResponse(
      List<T> content,
      int page,
      int size,
      long totalElements,
      int totalPages,
      boolean hasNext,
      boolean hasPrevious) {
    this.content = content;
    this.page = page;
    this.size = size;
    this.totalElements = totalElements;
    this.totalPages = totalPages;
    this.hasNext = hasNext;
    this.hasPrevious = hasPrevious;
  }

  /** @param page 현재 페이지 번호 (0부터 시작) */
  public static <T> OffsetPageResponse<T> of(
      List<T> content, long totalElements, int page, int size) {
    int totalPages = size == 0 ? 1 : (int) Math.ceil((double) totalElements / size);
    return new OffsetPageResponse<>(
        content, page, size, totalElements, totalPages, (page + 1) < totalPages, page > 0);
  }
}
