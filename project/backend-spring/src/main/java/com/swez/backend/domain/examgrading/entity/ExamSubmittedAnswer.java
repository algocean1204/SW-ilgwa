/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * exam_submitted_answer 테이블. 응시자가 제출한 답안 원본.
 *
 * <p>answer_json은 문항 유형별로 구조가 다르다: - 객관식: {"label": "A"} - 서술형: {"text": "응시자 서술 내용"} - 순서형:
 * {"order": ["A", "C", "B"]} - 연결형: {"pairs": [{"left": "X", "right": "1"}]} - 빈칸형: {"blanks":
 * ["답1", "답2"]} 빈 제출({"blank": true})은 전체 미응답 0점으로 처리한다.
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "exam_submitted_answer")
public class ExamSubmittedAnswer {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 소속 응시. 응시 삭제 시 CASCADE DELETE */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "attempt_id", nullable = false)
  private ExamAttempt attempt;

  /** 답안이 속한 문항 ID */
  @Column(name = "question_id", length = 80, nullable = false)
  private String questionId;

  /** 제출 답안 JSONB. 구조는 문항 유형별로 다르고 null이면 미응답으로 본다(빈 제출 허용 정책). */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "answer_json", columnDefinition = "jsonb")
  private String answerJson;

  /** 답안 제출 시각 */
  @Column(name = "submitted_at", nullable = false)
  @Builder.Default
  private OffsetDateTime submittedAt = OffsetDateTime.now();
}
