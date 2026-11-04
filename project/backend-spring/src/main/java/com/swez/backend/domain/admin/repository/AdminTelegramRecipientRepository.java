/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swez.backend.domain.admin.entity.AdminTelegramRecipient;

/** 관리자 텔레그램 수신자 레포지토리 */
public interface AdminTelegramRecipientRepository
    extends JpaRepository<AdminTelegramRecipient, String> {

  /** credential_id로 수신자 목록 조회 */
  List<AdminTelegramRecipient> findByCredentialId(Long credentialId);

  /** credential_id + chatId 중복 확인용 */
  Optional<AdminTelegramRecipient> findByCredentialIdAndChatId(Long credentialId, Long chatId);
}
