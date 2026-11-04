/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.admin.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.swez.backend.global.exception.CustomException;
import com.swez.backend.global.exception.GlobalErrorCode;

/** 텔레그램 봇 토큰을 암호화하고 fingerprint를 만든다. */
@Component
public class TelegramCredentialCryptoService {

  private static final String AES_ALGORITHM = "AES";
  private static final String AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int GCM_TAG_BITS = 128;
  private static final int GCM_IV_BYTES = 12;
  private static final int FINGERPRINT_HEX_LENGTH = 12;

  private final SecureRandom secureRandom = new SecureRandom();
  private final SecretKeySpec secretKey;

  public TelegramCredentialCryptoService(
      @Value("${app.telegram.credential-secret:${JWT_SECRET:local-dev-telegram-secret}}")
          String credentialSecret) {
    this.secretKey = new SecretKeySpec(sha256(credentialSecret), AES_ALGORITHM);
  }

  public String encrypt(String plainToken) {
    try {
      byte[] iv = new byte[GCM_IV_BYTES];
      secureRandom.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
      byte[] encrypted = cipher.doFinal(plainToken.getBytes(StandardCharsets.UTF_8));
      ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
      buffer.put(iv);
      buffer.put(encrypted);
      return Base64.getEncoder().encodeToString(buffer.array());
    } catch (GeneralSecurityException e) {
      throw new CustomException(GlobalErrorCode.SYSTEM_001);
    }
  }

  public String decrypt(String encryptedToken) {
    try {
      byte[] payload = Base64.getDecoder().decode(encryptedToken);
      ByteBuffer buffer = ByteBuffer.wrap(payload);
      byte[] iv = new byte[GCM_IV_BYTES];
      buffer.get(iv);
      byte[] encrypted = new byte[buffer.remaining()];
      buffer.get(encrypted);
      Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
      return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException | GeneralSecurityException e) {
      throw new CustomException(GlobalErrorCode.SYSTEM_001);
    }
  }

  public String fingerprint(String plainToken) {
    byte[] digest = sha256(plainToken);
    return HexFormat.of().formatHex(digest).substring(0, FINGERPRINT_HEX_LENGTH) + "...";
  }

  private byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new CustomException(GlobalErrorCode.SYSTEM_001);
    }
  }
}
