/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.swez.backend.domain.admin.entity.AdminAlertRule;
import com.swez.backend.domain.admin.enums.AlertEventType;

/** 관리자 알림 규칙 레포지토리 */
public interface AdminAlertRuleRepository extends JpaRepository<AdminAlertRule, String> {

  /** credential_id로 알림 규칙 목록 조회 */
  List<AdminAlertRule> findByCredentialId(Long credentialId);

  /** credential_id + eventType 중복 확인용 */
  Optional<AdminAlertRule> findByCredentialIdAndEventType(
      Long credentialId, AlertEventType eventType);

  /**
   * 알림 발송 디스패치에서 특정 이벤트 유형의 활성 알림 규칙을 조회한다.
   *
   * <p>디스패치는 @Async 별도 스레드에서 실행된다. 그 안에서 credential을 지연 로딩하면 영속성 컨텍스트가
   * 닫혀 LazyInitializationException이 날 수 있어 @EntityGraph로 credential을 함께 가져온다.
   *
   * @return credential까지 즉시 로딩된 활성 규칙 목록
   */
  @EntityGraph(attributePaths = "credential")
  List<AdminAlertRule> findByEventTypeAndEnabledTrue(AlertEventType eventType);
}
