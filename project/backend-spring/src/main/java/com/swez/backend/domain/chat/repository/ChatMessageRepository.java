/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.chat.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.swez.backend.domain.chat.entity.ChatMessage;

/** chat_message 테이블 JPA 리포지토리 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {

  /**
   * 특정 과외의 특정 사용자 채팅 히스토리를 최신순으로 조회한다.
   *
   * @param pageable 페이징 정보 (기본 최근 50건)
   */
  List<ChatMessage> findByCourseIdAndUserIdOrderByCreatedAtDesc(
      String courseId, String userId, Pageable pageable);

  /**
   * FastAPI 컨텍스트 구성에 쓸 특정 과외·사용자의 최근 QA 히스토리를 조회한다.
   *
   * @param pageable 페이징 정보 (최근 N건)
   */
  List<ChatMessage> findByCourseIdAndUserIdOrderByCreatedAtAsc(
      String courseId, String userId, Pageable pageable);
}
