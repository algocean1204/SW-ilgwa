/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.mockexam.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swez.backend.domain.mockexam.entity.MockExamQuestion;

public interface MockExamQuestionRepository extends JpaRepository<MockExamQuestion, String> {

  /** 모의고사에 속한 문항 목록을 문항 순서 오름차순으로 조회한다. */
  List<MockExamQuestion> findByMockExamIdOrderByQuestionIdxAsc(String mockExamId);
}
