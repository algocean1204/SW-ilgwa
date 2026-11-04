/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.page;

import java.util.List;

import lombok.Getter;

/**
 * 커서 기반 페이지네이션 응답 래퍼
 *
 * <p>무한스크롤 목록에 사용. totalElements/totalPages 없이 nextCursor로 다음 페이지를 조회한다
 */
@Getter
public class CursorPageResponse<T> {
  private final List<T> content;

  /** 다음 페이지 요청 시 cursor 파라미터로 전달할 값 (다음 페이지 없으면 null) */
  private final String nextCursor;

  private final boolean hasNext;
  private final int size;

  private CursorPageResponse(List<T> content, String nextCursor, boolean hasNext, int size) {
    this.content = content;
    this.nextCursor = nextCursor;
    this.hasNext = hasNext;
    this.size = size;
  }

  /** @param nextCursor 다음 커서값 (없으면 null) */
  public static <T> CursorPageResponse<T> of(
      List<T> content, String nextCursor, boolean hasNext, int size) {
    return new CursorPageResponse<>(content, nextCursor, hasNext, size);
  }
}
