/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.mockexam.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swez.backend.domain.mockexam.entity.MockExam;

public interface MockExamRepository extends JpaRepository<MockExam, String> {

  /** 특정 과외에 속한 모의고사 목록을 생성일 역순으로 조회한다. */
  List<MockExam> findByCourseIdOrderByCreatedAtDesc(String courseId);
}
