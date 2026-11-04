/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.lesson.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swez.backend.domain.lesson.entity.Quiz;

public interface QuizRepository extends JpaRepository<Quiz, String> {

  /** 챕터 내 퀴즈를 quizIdx 오름차순으로 조회한다. */
  List<Quiz> findByChapterIdOrderByQuizIdxAsc(String chapterId);

  /** 지정한 ID 중 특정 챕터에 속한 퀴즈만 조회한다. 챕터 소유권 확인에 쓴다. */
  List<Quiz> findAllByIdInAndChapterId(List<String> ids, String chapterId);
}
