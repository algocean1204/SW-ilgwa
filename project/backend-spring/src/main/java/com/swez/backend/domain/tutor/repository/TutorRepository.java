/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.tutor.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swez.backend.domain.tutor.entity.Tutor;

/** tutor 테이블 JPA 리포지토리 */
public interface TutorRepository extends JpaRepository<Tutor, String> {

  /** isDefault=true 인 기본 프리셋 튜터 전체 조회 */
  List<Tutor> findByIsDefaultTrue();

  /**
   * isDefault=true 이거나 userId 소유의 커스텀 튜터 조회.
   *
   * <p>프리셋 전체와 해당 사용자 커스텀 튜터를 한 번에 가져온다.
   */
  @Query("SELECT t FROM Tutor t WHERE t.isDefault = true OR t.user.id = :userId")
  List<Tutor> findByIsDefaultTrueOrUserId(@Param("userId") String userId);

  /** tutorId + userId 로 커스텀 튜터 단건 조회. 소유권 검증에 쓴다. */
  Optional<Tutor> findByTutorIdAndUserId(String tutorId, String userId);

  /** tutorId + userId 로 커스텀 튜터를 비관적 락을 걸어 조회한다. 동시 업로드 race condition 을 막는다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT t FROM Tutor t WHERE t.tutorId = :tutorId AND t.user.id = :userId")
  Optional<Tutor> findByTutorIdAndUserIdForUpdate(
      @Param("tutorId") String tutorId, @Param("userId") String userId);

  /** 특정 사용자의 커스텀 튜터(isDefault=false)를 모두 삭제한다. */
  void deleteByUserIdAndIsDefaultFalse(@Param("userId") String userId);
}
