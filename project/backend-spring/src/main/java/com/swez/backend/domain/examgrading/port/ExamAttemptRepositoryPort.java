/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.port;

import java.util.List;
import java.util.Optional;

import com.swez.backend.domain.examgrading.entity.ExamAttempt;
import com.swez.backend.domain.examgrading.entity.ExamAttemptQuestion;
import com.swez.backend.domain.examgrading.model.ExamAttemptSnapshot;
import com.swez.backend.domain.examgrading.model.ExamGradeResult;
import com.swez.backend.domain.examgrading.model.QuestionReviewData;
import com.swez.backend.domain.examgrading.model.SubmittedAnswer;

/** 채점·응시 상세·제출·결과 조회의 DB 접근을 추상화한 응시 저장소 포트. */
public interface ExamAttemptRepositoryPort {

  /** 채점 서버 전용 필드(정답·해설 등)를 모두 포함한 채점 스냅샷을 로드한다. */
  Optional<ExamAttemptSnapshot> findGradingSnapshot(String attemptId);

  /** 채점 결과를 저장하고 ExamAttempt 상태를 전환한다. */
  void saveGradingResult(ExamGradeResult result);

  /** 소유권을 검증해 응시 엔티티를 조회한다. 해당 userId가 소유한 attempt이면 반환하고, 아니면 empty. */
  Optional<ExamAttempt> findAttemptByOwner(String attemptId, String userId);

  /**
   * 비관적 쓰기 락(SELECT ... FOR UPDATE)으로 소유권 검증 후 응시 엔티티를 조회한다.
   *
   * <p>제출 트랜잭션 진입 시 attempt 행을 잠가, 같은 attempt에 대한 동시 제출(더블클릭/재제출)을 직렬화한다. 두 번째 트랜잭션은 첫
   * 트랜잭션이 SUBMITTED로 전이·커밋할 때까지 대기한 뒤 멱등 경로로 빠진다. 반드시 활성 트랜잭션 안에서 호출해야 한다.
   *
   * <p>기본 구현은 락 없는 {@link #findAttemptByOwner}로 위임한다. 실제 락은 JPA 어댑터가 FOR UPDATE 쿼리로 오버라이드해
   * 제공하고, 인메모리 테스트 더블은 락이 필요 없어 기본 위임으로 충분하다.
   *
   * @return 소유자가 일치하면 락을 획득한 응시 엔티티 Optional, 아니면 empty
   */
  default Optional<ExamAttempt> findAttemptByOwnerForUpdate(String attemptId, String userId) {
    return findAttemptByOwner(attemptId, userId);
  }

  /** 응시에 속한 문항 목록을 displayOrder 오름차순으로 조회한다. */
  List<ExamAttemptQuestion> findQuestionsByAttemptId(String attemptId);

  /** 제출 답안을 exam_submitted_answer 테이블에 일괄 저장하고 attempt 상태를 SUBMITTED로 전환한다. */
  void saveSubmittedAnswers(String attemptId, List<SubmittedAnswer> answers);

  /** 채점 완료된 응시의 결과(집계 + 문항별)를 조회한다. */
  Optional<ExamGradeResult> findGradeResult(String attemptId);

  /**
   * 채점 결과에 AI 총평 JSON을 부착한다.
   *
   * <p>채점 저장(saveGradingResult) 이후 분석이 성공한 경우에만 호출한다. 분석은 부가 기능이라 채점 흐름을 깨지 않도록
   * 결과 행이 없으면 무시한다.
   *
   * @param analysisJson {@code {"summary","weakTopics"}} JSON 문자열
   */
  void saveAnalysisJson(String attemptId, String analysisJson);

  /** 저장된 AI 총평 JSON을 조회한다. 분석 미생성/실패 시 empty. */
  Optional<String> findAnalysisJson(String attemptId);

  /**
   * 복습 모드용 데이터를 조회한다.
   *
   * <p>exam_attempt_question(스냅샷, displayOrder 오름차순) + exam_submitted_answer를 결합하여
   * 문항별 {스냅샷, 제출답안} 쌍 목록을 반환한다. 미응답 문항의 submittedAnswerJson은 null.
   *
   * <p>이 메서드는 채점 완료(GRADED) 상태 복습 경로에서만 호출한다. 상태 검증은 서비스 레이어에서 한다.
   *
   * @return 문항별 복습 데이터 목록 (displayOrder 오름차순)
   */
  List<QuestionReviewData> findReviewData(String attemptId);
}
