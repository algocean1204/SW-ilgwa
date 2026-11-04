/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.swez.backend.domain.subscription.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, String> {

  /** 특정 사용자의 결제 이력을 최신 순으로 페이징 조회한다. 소프트 삭제된 결제는 뺀다. */
  Page<Payment> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
      String userId, Pageable pageable);
}
