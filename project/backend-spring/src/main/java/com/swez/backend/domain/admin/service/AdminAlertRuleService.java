/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swez.backend.domain.admin.dto.request.UpdateAlertRuleRequest;
import com.swez.backend.domain.admin.dto.response.AlertRuleListResponse;
import com.swez.backend.domain.admin.dto.response.AlertRuleResponse;
import com.swez.backend.domain.admin.entity.AdminAlertRule;
import com.swez.backend.domain.admin.entity.AdminTelegramCredential;
import com.swez.backend.domain.admin.enums.AlertEventType;
import com.swez.backend.domain.admin.enums.AlertSeverity;
import com.swez.backend.domain.admin.exception.AdminErrorCode;
import com.swez.backend.domain.admin.repository.AdminAlertRuleRepository;
import com.swez.backend.domain.admin.repository.AdminTelegramCredentialRepository;
import com.swez.backend.global.exception.CustomException;
import com.swez.backend.global.util.IdGenerator;

import lombok.RequiredArgsConstructor;

/** 관리자 에러 알림 규칙 서비스 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAlertRuleService {

  private final AdminAlertRuleRepository alertRuleRepository;
  private final AdminTelegramCredentialRepository credentialRepository;

  /**
   * 알림 규칙 목록을 조회하고, 누락된 이벤트 유형은 기본값으로 보충(reconcile)한다.
   *
   * <p>규칙이 0개일 때만 기본값을 만들면 새 AlertEventType이 추가돼도 이미 규칙을 가진 관리자는 그 유형
   * 규칙을 받지 못한다. 그래서 기존 규칙을 읽은 뒤 빠진 유형만 추가로 만든다.
   */
  @Transactional
  public AlertRuleListResponse getAlertRules(String adminUserId) {
    AdminTelegramCredential credential = getCredentialByUserId(adminUserId);

    List<AdminAlertRule> rules =
        new ArrayList<>(alertRuleRepository.findByCredentialId(credential.getId()));

    // 누락된 이벤트 유형만 추려 기본 규칙으로 보충한다.
    List<AdminAlertRule> missing = buildMissingRules(credential, rules);
    if (!missing.isEmpty()) {
      rules.addAll(alertRuleRepository.saveAll(missing));
    }

    List<AlertRuleResponse> responses = rules.stream().map(this::toAlertRuleResponse).toList();
    return new AlertRuleListResponse(responses);
  }

  /**
   * 소유권을 확인한 뒤 알림 규칙의 활성 상태 또는 심각도를 수정한다.
   *
   * <p>ruleId만으로 수정하면 다른 관리자의 credential에 속한 규칙을 바꿀 수 있다(IDOR). 요청자와 규칙의
   * credential이 같을 때만 수정하고, 다르면 ADMIN_010으로 거부한다.
   *
   * @param adminUserId 소유권 확인 기준이 되는 관리자 ID
   */
  @Transactional
  public AlertRuleResponse updateAlertRule(
      String adminUserId, String ruleId, UpdateAlertRuleRequest request) {
    AdminTelegramCredential credential = getCredentialByUserId(adminUserId);
    AdminAlertRule rule =
        alertRuleRepository
            .findById(ruleId)
            .orElseThrow(() -> new CustomException(AdminErrorCode.ADMIN_009));
    if (!rule.getCredential().getId().equals(credential.getId())) {
      throw new CustomException(AdminErrorCode.ADMIN_010);
    }
    rule.updateEnabled(request.enabled());
    rule.updateSeverity(request.severity());
    return toAlertRuleResponse(rule);
  }

  /**
   * 이벤트 유형별 기본 표시명과 심각도. 기본값은 여기에서만 정의한다.
   *
   * <p>누락분 보충(buildMissingRules)이 늘 같은 기본값을 써야 해서 한 곳에 둔다. 기존 규칙이 0개면 모든 유형이
   * 누락으로 잡혀 전체 초기화처럼 동작한다.
   */
  private record DefaultRuleSpec(
      AlertEventType eventType, String displayName, AlertSeverity severity) {}

  private static final List<DefaultRuleSpec> DEFAULT_RULE_SPECS =
      List.of(
          new DefaultRuleSpec(AlertEventType.PAYMENT_FAILED, "결제 실패", AlertSeverity.CRITICAL),
          new DefaultRuleSpec(AlertEventType.QUERY_ERROR, "쿼리 오류", AlertSeverity.WARN),
          new DefaultRuleSpec(AlertEventType.AUTH_FAILURE, "인증 실패", AlertSeverity.WARN),
          new DefaultRuleSpec(AlertEventType.SYSTEM_ERROR, "시스템 오류", AlertSeverity.CRITICAL),
          new DefaultRuleSpec(AlertEventType.SUBMISSION_TIMEOUT, "제출 시간 초과", AlertSeverity.INFO),
          new DefaultRuleSpec(AlertEventType.SUBSCRIPTION_EXPIRED, "구독 만료", AlertSeverity.WARN),
          new DefaultRuleSpec(
              AlertEventType.CURRICULUM_GENERATION_FAILED, "커리큘럼 생성 실패", AlertSeverity.CRITICAL),
          new DefaultRuleSpec(
              AlertEventType.EXAM_GENERATION_FAILED, "모의고사 생성 실패", AlertSeverity.CRITICAL));

  /** 기존 규칙에 없는 이벤트 유형만 골라 기본 규칙 엔티티로 만든다. 저장은 호출 측에서 한다. */
  private List<AdminAlertRule> buildMissingRules(
      AdminTelegramCredential credential, List<AdminAlertRule> existingRules) {
    List<AlertEventType> existingTypes =
        existingRules.stream().map(AdminAlertRule::getEventType).toList();
    return DEFAULT_RULE_SPECS.stream()
        .filter(spec -> !existingTypes.contains(spec.eventType()))
        .map(spec -> buildRule(credential, spec.eventType(), spec.displayName(), spec.severity()))
        .toList();
  }

  private AdminAlertRule buildRule(
      AdminTelegramCredential credential,
      AlertEventType eventType,
      String displayName,
      AlertSeverity severity) {
    return AdminAlertRule.builder()
        .id(IdGenerator.adminAlertRule())
        .credential(credential)
        .eventType(eventType)
        .displayName(displayName)
        .severity(severity)
        .build();
  }

  private AlertRuleResponse toAlertRuleResponse(AdminAlertRule rule) {
    return new AlertRuleResponse(
        rule.getId(),
        rule.getEventType().name(),
        rule.getDisplayName(),
        rule.getEnabled(),
        rule.getSeverity().name(),
        rule.getUpdatedAt());
  }

  /** 관리자 사용자의 credential을 조회한다. 없으면 ADMIN_006. */
  private AdminTelegramCredential getCredentialByUserId(String adminUserId) {
    return credentialRepository
        .findByUserId(adminUserId)
        .orElseThrow(() -> new CustomException(AdminErrorCode.ADMIN_006));
  }
}
