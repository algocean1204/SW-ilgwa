/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swez.backend.domain.auth.entity.OauthAccount;
import com.swez.backend.domain.auth.enums.OauthProvider;

public interface OauthAccountRepository extends JpaRepository<OauthAccount, String> {

  /** 제공자와 제공자 사용자 ID로 OAuth 계정 단건 조회. */
  Optional<OauthAccount> findByProviderAndProviderUserId(
      OauthProvider provider, String providerUserId);
}
