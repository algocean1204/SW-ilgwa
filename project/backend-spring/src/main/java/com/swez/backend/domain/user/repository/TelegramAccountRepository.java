/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swez.backend.domain.user.entity.TelegramAccount;

public interface TelegramAccountRepository extends JpaRepository<TelegramAccount, String> {

  /** userId 로 텔레그램 계정 단건 조회. */
  Optional<TelegramAccount> findByUserId(String userId);

  Optional<TelegramAccount> findByTelegramChatId(Long telegramChatId);

  /** 활성 상태인 모든 텔레그램 계정 조회 */
  List<TelegramAccount> findByIsActiveTrue();

  /** 지정한 사용자 ID 목록 중 활성 상태인 텔레그램 계정 조회 */
  List<TelegramAccount> findByUserIdInAndIsActiveTrue(List<String> userIds);
}
