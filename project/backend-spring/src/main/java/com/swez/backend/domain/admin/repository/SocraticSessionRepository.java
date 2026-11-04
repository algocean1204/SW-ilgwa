/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swez.backend.domain.admin.entity.SocraticSession;

public interface SocraticSessionRepository extends JpaRepository<SocraticSession, String> {

  List<SocraticSession> findTop20ByUserIdOrderByCreatedAtDesc(String userId);

  /** 대시보드 KPI에 쓰는 응답 미완료(transcript IS NULL) 세션 수 */
  long countByTranscriptIsNull();
}
