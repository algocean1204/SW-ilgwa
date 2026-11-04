/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swez.backend.domain.examgrading.entity.ExamAttempt;
import com.swez.backend.domain.examgrading.enums.AttemptStatus;

/** exam_attempt 테이블 CRUD + 조회 리포지토리 */
public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, String> {

  /**
   * 채점 스냅샷을 로드한다. ExamAttemptRepositoryPort.findGradingSnapshot 구현에 쓰며 User와 Course를 즉시 로딩해
   * N+1을 막는다.
   */
  @Query(
      """
      SELECT ea FROM ExamAttempt ea
      LEFT JOIN FETCH ea.user
      LEFT JOIN FETCH ea.course
      WHERE ea.attemptId = :attemptId
      """)
  Optional<ExamAttempt> findWithUserAndCourseByAttemptId(@Param("attemptId") String attemptId);

  /** 특정 사용자의 응시 목록을 생성 시각 내림차순으로 조회한다. */
  List<ExamAttempt> findByUserIdOrderByCreatedAtDesc(String userId);

  /** 특정 상태의 응시 목록을 조회한다. 채점 파이프라인 배치 처리용. */
  List<ExamAttempt> findByStatus(AttemptStatus status);

  /** 특정 시험 ID에 속한 모든 응시를 조회한다. */
  List<ExamAttempt> findByExamId(String examId);

  /**
   * 지정 시각 이전에 생성된 GENERATING 응시를 조회한다.
   *
   * <p>timeout 스케줄러(ExamAttemptTimeoutScheduler)가 FastAPI 생성 콜백이 영영 도착하지 않아 GENERATING으로 고착된 응시를
   * FAILED로 전이하는 데 사용한다. (CourseRepository.findStuckGeneratingCourses와 동일 패턴)
   */
  @Query(
      """
      SELECT ea FROM ExamAttempt ea
      WHERE ea.status = com.swez.backend.domain.examgrading.enums.AttemptStatus.GENERATING
      AND ea.createdAt < :cutoff
      """)
  List<ExamAttempt> findStuckGeneratingAttempts(@Param("cutoff") java.time.OffsetDateTime cutoff);

  /**
   * 연결된 모의고사 카드 ID로 가장 최근 응시를 조회한다.
   *
   * <p>MockExamService cutover 분기에서 mock_exam_id를 기준으로 대응하는 ExamAttempt를 찾는 데 사용한다. 동일한 mockExamId에
   * 응시가 여러 개 존재할 경우 가장 최근 생성된 것을 반환한다.
   */
  @Query(
      """
      SELECT ea FROM ExamAttempt ea
      WHERE ea.mockExamId = :mockExamId
      ORDER BY ea.createdAt DESC
      LIMIT 1
      """)
  Optional<ExamAttempt> findLatestByMockExamId(@Param("mockExamId") String mockExamId);

  /**
   * 제출 처리용 비관적 쓰기 락(SELECT ... FOR UPDATE)으로 응시 행을 조회한다.
   *
   * <p>같은 attempt에 대한 동시 제출(더블클릭·재제출 레이스)을 직렬화하기 위한 행 락이다. 첫 트랜잭션이 attempt를 SUBMITTED로 전이하고
   * 커밋할 때까지 두 번째 트랜잭션은 이 조회에서 대기한다. 락 해제 후 두 번째 트랜잭션은 SUBMITTED 상태를 읽어 멱등 경로(기존 결과 반환)로 빠지므로
   * 중복 채점과 exam_grade_result PK 충돌이 생기지 않는다.
   * {@code CoinWalletRepository.findByUserIdForUpdate}와 같은 락 패턴이다.
   *
   * <p>fetch join 없이 루트 행만 잠근다(PostgreSQL에서 join + FOR UPDATE의 잠금 범위 모호성을 피한다). 소유권·과목명 해석은 호출자가 별도 조회로
   * 처리한다.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT ea FROM ExamAttempt ea WHERE ea.attemptId = :attemptId")
  Optional<ExamAttempt> findByAttemptIdForUpdate(@Param("attemptId") String attemptId);
}
