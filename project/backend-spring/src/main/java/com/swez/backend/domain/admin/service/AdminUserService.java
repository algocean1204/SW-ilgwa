/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.swez.backend.domain.admin.dto.request.SuspendRequest;
import com.swez.backend.domain.admin.dto.request.TelegramChatIdRequest;
import com.swez.backend.domain.admin.dto.response.AdminTelegramResponse;
import com.swez.backend.domain.admin.dto.response.AdminUserListResponse;
import com.swez.backend.domain.admin.dto.response.AdminUserSuspendResponse;
import com.swez.backend.domain.admin.exception.AdminErrorCode;
import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.enums.SubscriptionStatus;
import com.swez.backend.domain.auth.enums.UserStatus;
import com.swez.backend.domain.auth.exception.AuthErrorCode;
import com.swez.backend.domain.auth.repository.UserRepository;
import com.swez.backend.domain.course.entity.Course;
import com.swez.backend.domain.course.enums.CourseStatus;
import com.swez.backend.domain.course.repository.CourseRepository;
import com.swez.backend.domain.user.entity.TelegramAccount;
import com.swez.backend.domain.user.repository.TelegramAccountRepository;
import com.swez.backend.global.exception.CustomException;
import com.swez.backend.global.page.OffsetPageResponse;
import com.swez.backend.global.util.IdGenerator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

  private final UserRepository userRepository;
  private final TelegramAccountRepository telegramAccountRepository;
  private final CourseRepository courseRepository;

  public OffsetPageResponse<AdminUserListResponse> getUsers(
      String keyword, SubscriptionStatus subscriptionStatus, UserStatus status, Pageable pageable) {
    Page<User> users =
        userRepository.findAll(userSpecification(keyword, subscriptionStatus, status), pageable);

    /* N+1을 막으려고 페이지 내 모든 사용자의 ACTIVE 과외를 한 번에 조회한다. */
    List<String> userIds = users.getContent().stream().map(User::getId).toList();
    Map<String, List<Course>> coursesByUserId =
        courseRepository.findByUserIdInAndStatus(userIds, CourseStatus.ACTIVE).stream()
            .collect(Collectors.groupingBy(course -> course.getUser().getId()));

    List<AdminUserListResponse> content =
        users.getContent().stream()
            .map(
                user -> toListResponse(user, coursesByUserId.getOrDefault(user.getId(), List.of())))
            .toList();
    return OffsetPageResponse.of(
        content, users.getTotalElements(), pageable.getPageNumber(), pageable.getPageSize());
  }

  /** 사용자 정지/해제를 토글한다. ACTIVE면 사유와 함께 정지하고, SUSPENDED면 해제한다. */
  @Transactional
  public AdminUserSuspendResponse toggleSuspend(String userId, SuspendRequest request) {
    User user = getUser(userId);
    if (user.getStatus() == UserStatus.ACTIVE) {
      String reason = (request != null) ? request.reason() : null;
      user.suspend(reason);
    } else {
      user.unsuspend();
    }
    return new AdminUserSuspendResponse(
        user.getId(), user.getStatus(), user.getSuspendReason(), user.getSuspendedAt());
  }

  @Transactional
  public AdminTelegramResponse updateTelegramChatId(String userId, TelegramChatIdRequest request) {
    User user = getUser(userId);
    return switch (request.action()) {
      case SET, RELINK -> linkTelegram(user, request.chatId());
      case UNLINK -> unlinkTelegram(user);
    };
  }

  private AdminTelegramResponse linkTelegram(User user, Long chatId) {
    if (chatId == null) {
      throw new CustomException(AdminErrorCode.ADMIN_001);
    }
    validateTelegramChatIdOwner(user.getId(), chatId);

    TelegramAccount account =
        telegramAccountRepository
            .findByUserId(user.getId())
            .orElseGet(
                () ->
                    TelegramAccount.builder()
                        .id(IdGenerator.telegramAccount())
                        .user(user)
                        .telegramChatId(chatId)
                        .build());
    account.link(chatId);
    telegramAccountRepository.save(account);
    user.updateTelegramChatId(chatId);
    return toTelegramResponse(account);
  }

  private AdminTelegramResponse unlinkTelegram(User user) {
    TelegramAccount account = telegramAccountRepository.findByUserId(user.getId()).orElse(null);
    if (account == null) {
      user.updateTelegramChatId(null);
      return new AdminTelegramResponse(false, null, null);
    }
    account.unlink();
    user.updateTelegramChatId(null);
    return new AdminTelegramResponse(false, null, account.getLinkedAt());
  }

  private void validateTelegramChatIdOwner(String userId, Long chatId) {
    telegramAccountRepository
        .findByTelegramChatId(chatId)
        .filter(account -> !account.getUser().getId().equals(userId))
        .ifPresent(
            account -> {
              throw new CustomException(AdminErrorCode.ADMIN_002);
            });
  }

  /** 사용자 정보를 응답 DTO로 변환한다. 과외 정보는 미리 일괄 조회한 목록을 받는다. */
  private AdminUserListResponse toListResponse(User user, List<Course> activeCourses) {
    TelegramAccount telegramAccount =
        telegramAccountRepository.findByUserId(user.getId()).orElse(null);
    boolean linked = telegramAccount != null && Boolean.TRUE.equals(telegramAccount.getIsActive());

    /* 과외가 없으면 평균 진행률은 null이다. */
    Double progress =
        activeCourses.isEmpty()
            ? null
            : activeCourses.stream()
                .mapToDouble(c -> c.getProgressPercent().doubleValue())
                .average()
                .orElse(0.0);

    String activeCourseName =
        activeCourses.stream()
            .max(Comparator.comparing(Course::getUpdatedAt))
            .map(Course::getCourseName)
            .orElse(null);

    return new AdminUserListResponse(
        user.getId(),
        user.getEmail(),
        user.getNickname(),
        user.getProfileImageUrl(),
        user.getSubscriptionStatus(),
        user.getRole(),
        user.getStatus(),
        linked,
        linked ? telegramAccount.getTelegramChatId() : null,
        user.getCreatedAt(),
        progress,
        user.getLastSeenAt(),
        activeCourseName,
        user.getSuspendReason(),
        user.getSuspendedAt());
  }

  private AdminTelegramResponse toTelegramResponse(TelegramAccount account) {
    boolean linked = Boolean.TRUE.equals(account.getIsActive());
    return new AdminTelegramResponse(
        linked, linked ? account.getTelegramChatId() : null, account.getLinkedAt());
  }

  private User getUser(String userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new CustomException(AuthErrorCode.USER_001));
  }

  private Specification<User> userSpecification(
      String keyword, SubscriptionStatus subscriptionStatus, UserStatus status) {
    return (root, query, cb) -> {
      Predicate predicate = cb.conjunction();
      if (StringUtils.hasText(keyword)) {
        String likeKeyword = "%" + keyword.trim().toLowerCase() + "%";
        Predicate emailLike = cb.like(cb.lower(root.get("email")), likeKeyword);
        Predicate nicknameLike = cb.like(cb.lower(root.get("nickname")), likeKeyword);
        predicate = cb.and(predicate, cb.or(emailLike, nicknameLike));
      }
      if (subscriptionStatus != null) {
        predicate = cb.and(predicate, cb.equal(root.get("subscriptionStatus"), subscriptionStatus));
      }
      if (status != null) {
        predicate = cb.and(predicate, cb.equal(root.get("status"), status));
      }
      return predicate;
    };
  }
}
