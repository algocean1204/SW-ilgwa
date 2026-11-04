/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swez.backend.domain.user.entity.UserSettings;

public interface UserSettingsRepository extends JpaRepository<UserSettings, String> {

  /** userId 로 사용자 설정 단건 조회. */
  Optional<UserSettings> findByUserId(String userId);
}
