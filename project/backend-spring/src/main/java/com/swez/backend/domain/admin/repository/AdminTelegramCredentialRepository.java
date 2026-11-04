/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swez.backend.domain.admin.entity.AdminTelegramCredential;

public interface AdminTelegramCredentialRepository
    extends JpaRepository<AdminTelegramCredential, Long> {

  Optional<AdminTelegramCredential> findByUserId(String userId);
}
