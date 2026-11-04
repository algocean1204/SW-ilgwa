/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.lesson.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swez.backend.domain.lesson.entity.Slide;

public interface SlideRepository extends JpaRepository<Slide, String> {

  /** 챕터 내 슬라이드를 slideIdx 오름차순으로 조회한다. */
  List<Slide> findByChapterIdOrderBySlideIdxAsc(String chapterId);

  /** 챕터에 생성된 슬라이드가 하나라도 있으면 true를 반환한다. */
  boolean existsByChapterId(String chapterId);
}
