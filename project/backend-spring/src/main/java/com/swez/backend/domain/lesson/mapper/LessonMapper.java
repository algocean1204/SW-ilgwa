/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.lesson.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swez.backend.domain.lesson.dto.response.QuizResponse;
import com.swez.backend.domain.lesson.dto.response.SlideResponse;
import com.swez.backend.domain.lesson.entity.Quiz;
import com.swez.backend.domain.lesson.entity.Slide;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 학습 실행 도메인 엔티티를 DTO로 변환한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LessonMapper {

  /*
   * FastAPI가 chapter_studio.quiz.choices 컬럼에 JSON 배열로 저장한다.
   * 쉼표 split 대신 Jackson으로 역직렬화해야 보기 텍스트에 쉼표가 있어도 깨지지 않는다.
   */
  private final ObjectMapper objectMapper;

  /** Slide 엔티티를 SlideResponse로 변환한다. */
  public SlideResponse toSlideResponse(Slide slide) {
    return SlideResponse.builder()
        .slideId(slide.getId())
        .slideIdx(slide.getSlideIdx())
        .title(slide.getTitle())
        .content(slide.getContent())
        .audioUrl(slide.getAudioUrl())
        .durationSec(slide.getDurationSec())
        .build();
  }

  /** Quiz 엔티티를 정답을 뺀 QuizResponse로 변환한다. */
  public QuizResponse toQuizResponse(Quiz quiz) {
    return QuizResponse.builder()
        .quizId(quiz.getId())
        .quizIdx(quiz.getQuizIdx())
        .slideId(quiz.getSlide().getId())
        .questionText(quiz.getQuestionText())
        .options(parseOptions(quiz.getOptions()))
        .build();
  }

  /**
   * DB에 저장된 JSON 배열 문자열을 List로 파싱한다.
   *
   * <p>FastAPI는 chapter_studio.quiz.choices 컬럼에 보기를 JSON 배열(JSONB)로 저장한다. 예:
   * '["보기1","보기2, 포함 쉼표","보기3"]'. 보기 텍스트에 쉼표가 있어도 그대로 파싱된다. 데이터가 손상돼 파싱이
   * 실패하면 예외를 로그로 남기고 빈 목록을 반환한다.
   *
   * @param options DB 컬럼 원문 (null·빈값 허용)
   * @return 파싱된 보기 목록 (불변)
   */
  private List<String> parseOptions(String options) {
    if (options == null || options.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(options, new TypeReference<List<String>>() {});
    } catch (Exception e) {
      /* DB 데이터가 예상 형식과 달라도 상위 호출 흐름을 막지 않도록 빈 목록으로 대체한다. */
      log.error(
          "[LessonMapper] quiz options JSON 파싱 실패 — raw: '{}', 원인: {}", options, e.getMessage());
      return List.of();
    }
  }
}
