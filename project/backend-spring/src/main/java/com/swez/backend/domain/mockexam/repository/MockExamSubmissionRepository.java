/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.mockexam.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swez.backend.domain.mockexam.entity.MockExamSubmission;

public interface MockExamSubmissionRepository extends JpaRepository<MockExamSubmission, String> {

  /** 모의고사 ID와 사용자 ID로 제출 기록을 조회한다. */
  Optional<MockExamSubmission> findByMockExamIdAndUserId(String mockExamId, String userId);
}
