/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.enums.UserStatus;

public interface UserRepository
    extends JpaRepository<User, String>, JpaSpecificationExecutor<User> {

  /** 이메일로 사용자 단건 조회. */
  Optional<User> findByEmail(String email);

  /**
   * 이메일 중복 여부 확인.
   *
   * @return 이미 존재하면 true
   */
  boolean existsByEmail(String email);

  /** 특정 상태의 사용자 수를 센다. 대시보드 KPI 에 쓴다. */
  long countByStatus(UserStatus status);
}
