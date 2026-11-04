/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.adapter;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.swez.backend.domain.examgrading.client.ExamForgeFastApiClient;
import com.swez.backend.domain.examgrading.model.ExamAttemptSnapshot;
import com.swez.backend.domain.examgrading.model.QuestionGrade;
import com.swez.backend.domain.examgrading.model.SubmittedAnswer;
import com.swez.backend.domain.examgrading.port.ExamForgeRubricGradingPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ExamForge AI 루브릭 채점 포트의 어댑터 구현체.
 *
 * <p>포트(도메인 인터페이스)와 HTTP 클라이언트를 분리한다. 실제 HTTP 통신은 {@link ExamForgeFastApiClient}에 위임하고, 이 어댑터는
 * 포트 계약과 클라이언트를 잇기만 한다.
 *
 * <p>FastAPI 응답 검증(ID 집합·점수 범위)은 서비스 계층({@code ExamSubmissionGradingService})에서 하므로 이 어댑터는 추가
 * 검증 없이 클라이언트 결과를 그대로 반환한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.fastapi",
    name = {"base-url", "api-key"})
public class ExamForgeRubricGradingAdapter implements ExamForgeRubricGradingPort {

  /** FastAPI 루브릭 채점 HTTP 클라이언트 */
  private final ExamForgeFastApiClient examForgeFastApiClient;

  /** 서술형·논술 루브릭 문항에 대한 AI 채점을 FastAPI에 위임한다. */
  @Override
  public List<QuestionGrade> gradeRubricQuestions(
      ExamAttemptSnapshot snapshot, List<SubmittedAnswer> rubricAnswers) {
    log.info(
        "[ExamForgeRubricGradingAdapter] gradeRubricQuestions() 위임 시작 | attemptId: {}, 루브릭 문항 수: {}",
        snapshot.attemptId(),
        rubricAnswers.size());

    // 빈 목록이면 HTTP 호출 없이 즉시 반환 (클라이언트와 동일한 조기 반환 정책)
    if (rubricAnswers.isEmpty()) {
      return List.of();
    }

    // 예외 처리와 응답 파싱은 ExamForgeFastApiClient가 한다
    List<QuestionGrade> result =
        examForgeFastApiClient.gradeRubricQuestions(snapshot, rubricAnswers);

    log.info(
        "[ExamForgeRubricGradingAdapter] gradeRubricQuestions() 위임 완료 | attemptId: {}, 결과 수: {}",
        snapshot.attemptId(),
        result.size());
    return result;
  }
}
