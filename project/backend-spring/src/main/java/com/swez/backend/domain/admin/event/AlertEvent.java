/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.event;

import com.swez.backend.domain.admin.enums.AlertEventType;

/**
 * 관리자 알림 발송을 트리거하는 도메인 이벤트.
 *
 * <p>알림 발송은 @Async 별도 스레드에서 처리된다. JPA 엔티티를 넘기면 영속성 컨텍스트가 닫힌 뒤
 * LazyInitializationException이 날 수 있어 불변 원시값(eventType, detail)만 전달한다.
 *
 * <p>심각도(severity)는 이벤트가 아니라 각 알림 규칙에서 결정하므로 여기에 담지 않는다.
 *
 * @param eventType 어떤 규칙들이 발동할지 결정하는 알림 이벤트 유형
 * @param detail 메시지에 포함할 상세 설명 (예: orderId, userId, courseId 등)
 */
public record AlertEvent(AlertEventType eventType, String detail) {}
