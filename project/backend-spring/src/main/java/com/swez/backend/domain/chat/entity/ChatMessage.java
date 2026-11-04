/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.chat.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.course.entity.Chapter;
import com.swez.backend.domain.course.entity.Course;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자 질문과 AI 응답을 담는 AI 채팅 메시지 (chat_message 테이블) */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "chat_message")
public class ChatMessage {

  @Id
  @Column(length = 30)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "course_id", nullable = false)
  private Course course;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "chapter_id")
  private Chapter chapter;

  @Column(length = 30)
  private String slideId;

  private Integer slideIdx;

  /** 메시지 발화자 역할: 'user' 또는 'assistant' */
  @Column(nullable = false, length = 10)
  private String role;

  /** 메시지 본문 */
  @Column(nullable = false, columnDefinition = "TEXT")
  private String message;

  /** AI 응답의 인용 정보. JSON 배열 문자열 또는 null */
  @Column(columnDefinition = "TEXT")
  private String citations;

  /** 사용자가 질문한 시점의 영상 재생 위치(초) */
  @Column(precision = 10, scale = 2)
  private BigDecimal playbackSec;

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();
}
