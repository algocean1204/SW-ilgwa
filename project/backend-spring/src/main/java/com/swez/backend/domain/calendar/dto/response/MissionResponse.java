/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.calendar.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** GET /calendar/missions/today 응답 DTO */
@Builder
@Schema(description = "오늘의 미션 응답")
public record MissionResponse(
    @Schema(description = "날짜") String date,
    @Schema(description = "미션 목록") List<MissionItem> missions) {

  @Builder
  @Schema(description = "미션 아이템")
  public record MissionItem(
      @Schema(description = "미션 ID") String id,
      @Schema(description = "제목") String title,
      @Schema(description = "설명") String description,
      @Schema(description = "유형 (daily/weekly)") String type,
      @Schema(description = "현재 진행량") int current,
      @Schema(description = "목표량") int target,
      @Schema(description = "XP 보상") int xpReward,
      @Schema(description = "완료 여부") boolean isCompleted) {}
}
