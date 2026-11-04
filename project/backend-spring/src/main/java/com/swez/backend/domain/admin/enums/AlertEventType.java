/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.enums;

/** 관리자가 알림 규칙으로 켜고 끌 수 있는 알림 이벤트 유형 */
public enum AlertEventType {
  PAYMENT_FAILED,
  QUERY_ERROR,
  AUTH_FAILURE,
  SYSTEM_ERROR,
  SUBMISSION_TIMEOUT,
  SUBSCRIPTION_EXPIRED,
  CURRICULUM_GENERATION_FAILED,
  EXAM_GENERATION_FAILED
}
