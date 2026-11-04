/*
 * Copyright (c) EZ TEAM
 */
package com.swez.backend.domain.examgrading.event;

/**
 * 응시 채점 완료 도메인 이벤트.
 *
 * <p>ExamAttemptSubmitService가 채점 결과·AI 총평 저장을 마친 뒤 발행한다. mockexam 도메인 리스너가
 * AFTER_COMMIT으로 수신해 mock_exam_submission(analysis 포함)을 복제 저장한다. ChapterStudio 약점 집계가
 * 이 테이블의 analysis(weakTopics JSON)를 읽기 때문이다.
 *
 * <p>examgrading은 mockexam을 import하지 않는다(도메인 격리). 이벤트는 examgrading이 소유하고
 * mockexam이 구독하는 단방향 의존만 발생한다.
 */
public record ExamAttemptGradedEvent(String attemptId, String userId) {}
