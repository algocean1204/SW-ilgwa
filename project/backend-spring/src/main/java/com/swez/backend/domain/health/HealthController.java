/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swez.backend.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Health", description = "서비스 상태 확인 API")
@RestController
public class HealthController {

  @Operation(summary = "헬스 체크", description = "서비스가 정상 동작 중인지 확인한다.")
  @GetMapping("/health")
  public ApiResponse<HealthResponse> health() {
    return ApiResponse.success(new HealthResponse("ok", "spring"), "서비스가 정상 동작 중입니다.");
  }

  public record HealthResponse(String status, String service) {}
}
