/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.auth;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.swez.backend.domain.auth.enums.UserRole;
import com.swez.backend.domain.auth.enums.UserStatus;

import lombok.Builder;
import lombok.Getter;

/**
 * SecurityContext 에 저장되는 인증 주체 DTO. JwtAuthenticationFilter 가 토큰 검증 후 Authentication 의 principal 로
 * 주입한다.
 */
@Getter
@Builder
public class UserPrincipal implements UserDetails {

  private final String userId;
  private final String email;
  private final UserRole role;
  private final UserStatus status;

  /** 이메일 로그인 검증 시에만 세팅. JWT 기반 요청에서는 null */
  private final String password;

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    UserRole resolvedRole = role == null ? UserRole.USER : role;
    return List.of(new SimpleGrantedAuthority("ROLE_" + resolvedRole.name()));
  }

  @Override
  public String getPassword() {
    return password;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return status != UserStatus.SUSPENDED;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
