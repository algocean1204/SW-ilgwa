/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 모든 엔티티의 생성·수정 시각을 자동 관리하는 공통 상위 클래스. JpaConfig 의 @EnableJpaAuditing 과 함께 동작한다. */
@Getter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

  /** 엔티티가 처음 저장된 시각. 한 번 설정되면 변경되지 않는다 */
  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  /** 엔티티가 마지막으로 수정된 시각 */
  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
