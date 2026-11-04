/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.tutor.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.tutor.dto.request.CreateTutorRequest;
import com.swez.backend.domain.tutor.dto.response.CreateTutorResponse;
import com.swez.backend.domain.tutor.dto.response.TutorListResponse;
import com.swez.backend.domain.tutor.dto.response.TutorResponse;
import com.swez.backend.domain.tutor.entity.Tutor;
import com.swez.backend.global.util.IdGenerator;

/** 튜터 도메인 엔티티와 DTO 를 변환한다. */
@Component
public class TutorMapper {

  /** Tutor 엔티티를 TutorResponse 로 변환한다. */
  public TutorResponse toResponse(Tutor tutor) {
    return TutorResponse.builder()
        .tutorId(tutor.getTutorId())
        .name(tutor.getName())
        .tagline(tutor.getTagline())
        .profileImageUrl(tutor.getProfileImageUrl())
        .voiceSampleUrl(tutor.getVoiceSampleUrl())
        .isDefault(tutor.isDefault())
        .toneSlider(tutor.getToneSlider())
        .speedSlider(tutor.getSpeedSlider())
        .depthSlider(tutor.getDepthSlider())
        .questionStyleSlider(tutor.getQuestionStyleSlider())
        .useEmoji(tutor.isUseEmoji())
        .useFormalSpeech(tutor.isUseFormalSpeech())
        .createdAt(tutor.getCreatedAt())
        .updatedAt(tutor.getUpdatedAt())
        .build();
  }

  /** Tutor 엔티티 목록을 TutorListResponse 로 변환한다. */
  public TutorListResponse toListResponse(List<Tutor> tutors) {
    List<TutorResponse> responses = tutors.stream().map(this::toResponse).toList();
    return TutorListResponse.builder().tutors(responses).build();
  }

  /**
   * CreateTutorRequest 와 User 엔티티로 Tutor 엔티티를 생성한다.
   *
   * <p>PK 는 tut_ 프리픽스 + ULID 26자로 구성된다.
   *
   * @return 신규 Tutor 엔티티 (저장 전)
   */
  public Tutor toEntity(CreateTutorRequest request, User user) {
    /* 요청의 슬라이더 값이 null 이면 기본값 50 을 쓴다. */
    short tone = request.toneSlider() != null ? request.toneSlider().shortValue() : 50;
    short speed = request.speedSlider() != null ? request.speedSlider().shortValue() : 50;
    short depth = request.depthSlider() != null ? request.depthSlider().shortValue() : 50;
    short questionStyle =
        request.questionStyleSlider() != null ? request.questionStyleSlider().shortValue() : 50;

    return Tutor.builder()
        .tutorId(IdGenerator.tutor())
        .user(user)
        .name(request.name())
        .tagline(request.tagline())
        .profileImageUrl(request.profileImageUrl())
        .toneSlider(tone)
        .speedSlider(speed)
        .depthSlider(depth)
        .questionStyleSlider(questionStyle)
        .useEmoji(request.useEmoji() != null ? request.useEmoji() : false)
        .useFormalSpeech(request.useFormalSpeech() != null ? request.useFormalSpeech() : true)
        .isDefault(false)
        .build();
  }

  /** 저장된 Tutor 엔티티의 ID를 CreateTutorResponse 로 변환한다. */
  public CreateTutorResponse toCreateResponse(Tutor tutor) {
    return CreateTutorResponse.builder().tutorId(tutor.getTutorId()).build();
  }
}
