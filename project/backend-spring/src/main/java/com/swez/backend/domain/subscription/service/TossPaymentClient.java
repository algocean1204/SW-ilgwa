/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.subscription.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.swez.backend.domain.subscription.config.TossPaymentProperties;
import com.swez.backend.domain.subscription.exception.SubscriptionErrorCode;
import com.swez.backend.global.exception.CustomException;
import com.swez.backend.global.logging.LogMaskUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 토스페이먼츠 REST API 클라이언트. 결제 승인과 취소를 처리한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TossPaymentClient {

  private final RestTemplate restTemplate;
  private final TossPaymentProperties properties;

  /** 토스 confirm API 로 결제를 승인하고 결과 JSON 을 반환한다. */
  public JsonNode confirmPayment(String paymentKey, String orderId, Integer amount) {
    log.debug("[TossPaymentClient] confirmPayment() - START | orderId: {}", orderId);

    String url = properties.baseUrl() + "/v1/payments/confirm";
    HttpHeaders headers = createAuthHeaders();
    Map<String, Object> body =
        Map.of(
            "paymentKey", paymentKey,
            "orderId", orderId,
            "amount", amount);

    try {
      HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
      JsonNode response = restTemplate.postForObject(url, request, JsonNode.class);
      log.debug("[TossPaymentClient] confirmPayment() - END | orderId: {}", orderId);
      return response;
    } catch (RestClientException e) {
      log.error(
          "[TossPaymentClient] confirmPayment() - FAIL | orderId: {}, error: {}",
          orderId,
          e.getMessage());
      throw new CustomException(SubscriptionErrorCode.SUB_001);
    }
  }

  /** 토스 cancel API 로 결제를 취소(환불)한다. */
  public void cancelPayment(String paymentKey, String cancelReason) {
    log.debug(
        "[TossPaymentClient] cancelPayment() - START | paymentKey: {}",
        LogMaskUtil.mask(paymentKey));

    String url = properties.baseUrl() + "/v1/payments/" + paymentKey + "/cancel";
    HttpHeaders headers = createAuthHeaders();
    Map<String, String> body = Map.of("cancelReason", cancelReason);

    try {
      HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
      restTemplate.postForObject(url, request, JsonNode.class);
      log.debug(
          "[TossPaymentClient] cancelPayment() - END | paymentKey: {}",
          LogMaskUtil.mask(paymentKey));
    } catch (RestClientException e) {
      log.error(
          "[TossPaymentClient] cancelPayment() - FAIL | paymentKey: {}, error: {}",
          LogMaskUtil.mask(paymentKey),
          e.getMessage());
      throw new CustomException(SubscriptionErrorCode.SUB_001);
    }
  }

  /** Basic 인증 헤더를 만든다. secretKey + ":" 를 Base64 로 인코딩한다. */
  private HttpHeaders createAuthHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    String encoded =
        Base64.getEncoder()
            .encodeToString((properties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));
    headers.set("Authorization", "Basic " + encoded);
    return headers;
  }
}
