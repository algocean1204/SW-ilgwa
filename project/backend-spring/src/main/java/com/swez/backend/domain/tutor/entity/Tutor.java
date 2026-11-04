/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.tutor.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.swez.backend.domain.auth.entity.User;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** AI 튜터 캐릭터(tutor 테이블). 기본 프리셋과 사용자 커스텀 튜터를 함께 담는다. */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "tutor")
public class Tutor {

  @Id
  @Column(length = 30)
  private String tutorId;

  /** NULL이면 기본 제공 프리셋 튜터 */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  /** 튜터 이름 (최대 20자) */
  @Column(nullable = false, length = 20)
  private String name;

  /** 짧은 소개 문구 (최대 100자) */
  @Column(length = 100)
  private String tagline;

  /** 프로필 이미지 URL. S3 가 설정되지 않았으면 NULL 로 둔다. */
  @Column(name = "profile_image_url")
  private String profileImageUrl;

  /** 음성 클론 샘플 URL. S3 에 업로드된 WAV/MP3 파일을 가리킨다. */
  @Column(name = "voice_sample_url")
  private String voiceSampleUrl;

  /**
   * 말투 슬라이더 값 (0=격식, 100=친근). 기본값 50.
   *
   * <p>short 타입은 -32768 ~ 32767 범위이므로 0~100 슬라이더에 충분하다.
   */
  @Column(nullable = false)
  @Builder.Default
  private short toneSlider = 50;

  /** 설명 속도 슬라이더 값 (0=느림, 100=빠름). 기본값 50. */
  @Column(nullable = false)
  @Builder.Default
  private short speedSlider = 50;

  /** 설명 깊이 슬라이더 값 (0=간단, 100=상세). 기본값 50. */
  @Column(nullable = false)
  @Builder.Default
  private short depthSlider = 50;

  /** 질문 방식 슬라이더 값 (0=직접답변, 100=소크라테스식). 기본값 50. */
  @Column(nullable = false)
  @Builder.Default
  private short questionStyleSlider = 50;

  /** 이모지 사용 여부. 기본값 false. */
  @Column(nullable = false)
  @Builder.Default
  private boolean useEmoji = false;

  /** 존댓말 사용 여부. 기본값 true. */
  @Column(nullable = false)
  @Builder.Default
  private boolean useFormalSpeech = true;

  /** 기본 제공 프리셋 여부. true 이면 삭제할 수 없다. */
  @Column(nullable = false)
  @Builder.Default
  private boolean isDefault = false;

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime updatedAt = LocalDateTime.now();

  /** 튜터 정보를 부분 수정한다. null 필드는 기존 값을 유지한다. */
  public void update(
      String name,
      String tagline,
      String profileImageUrl,
      Integer toneSlider,
      Integer speedSlider,
      Integer depthSlider,
      Integer questionStyleSlider,
      Boolean useEmoji,
      Boolean useFormalSpeech) {
    if (name != null) this.name = name;
    if (tagline != null) this.tagline = tagline;
    if (profileImageUrl != null) this.profileImageUrl = profileImageUrl;
    if (toneSlider != null) this.toneSlider = toneSlider.shortValue();
    if (speedSlider != null) this.speedSlider = speedSlider.shortValue();
    if (depthSlider != null) this.depthSlider = depthSlider.shortValue();
    if (questionStyleSlider != null) this.questionStyleSlider = questionStyleSlider.shortValue();
    if (useEmoji != null) this.useEmoji = useEmoji;
    if (useFormalSpeech != null) this.useFormalSpeech = useFormalSpeech;
    this.updatedAt = LocalDateTime.now();
  }

  /** 음성 클론 샘플 URL을 업데이트한다. */
  public void updateVoiceSampleUrl(String voiceSampleUrl) {
    this.voiceSampleUrl = voiceSampleUrl;
    this.updatedAt = LocalDateTime.now();
  }

  /** 프로필 이미지 URL을 업데이트한다. */
  public void updateProfileImageUrl(String profileImageUrl) {
    this.profileImageUrl = profileImageUrl;
    this.updatedAt = LocalDateTime.now();
  }

  /** 프로필 이미지를 삭제한다 (URL을 null로 설정). */
  public void clearProfileImageUrl() {
    this.profileImageUrl = null;
    this.updatedAt = LocalDateTime.now();
  }

  /**
   * 슬라이더 및 토글 값을 기본값으로 초기화한다.
   *
   * <p>toneSlider=50, speedSlider=50, depthSlider=50, questionStyleSlider=50, useEmoji=false,
   * useFormalSpeech=true
   */
  public void resetToDefaults() {
    this.toneSlider = 50;
    this.speedSlider = 50;
    this.depthSlider = 50;
    this.questionStyleSlider = 50;
    this.useEmoji = false;
    this.useFormalSpeech = true;
    this.updatedAt = LocalDateTime.now();
  }
}
