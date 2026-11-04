/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swez.backend.domain.admin.entity.TelegramMessage;

public interface TelegramMessageRepository extends JpaRepository<TelegramMessage, String> {

  List<TelegramMessage> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
      String userId, LocalDateTime from, LocalDateTime to);
}
