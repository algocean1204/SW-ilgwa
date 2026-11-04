/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.tutor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.enums.SubscriptionStatus;
import com.swez.backend.domain.auth.repository.UserRepository;
import com.swez.backend.domain.tutor.dto.request.CreateTutorRequest;
import com.swez.backend.domain.tutor.dto.request.PreviewTutorRequest;
import com.swez.backend.domain.tutor.dto.request.UpdateTutorRequest;
import com.swez.backend.domain.tutor.dto.response.PreviewTutorResponse;
import com.swez.backend.domain.tutor.entity.Tutor;
import com.swez.backend.domain.tutor.exception.TutorErrorCode;
import com.swez.backend.domain.tutor.mapper.TutorMapper;
import com.swez.backend.domain.tutor.repository.TutorRepository;
import com.swez.backend.global.exception.CustomException;

/** TutorService 단위 테스트 */
@DisplayName("TutorService 단위 테스트")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TutorServiceTest {

  @Mock private TutorRepository tutorRepository;
  @Mock private UserRepository userRepository;
  @Mock private TutorMapper tutorMapper;
  @Mock private RestTemplate restTemplate;

  @InjectMocks private TutorService tutorService;

  private static final String USER_ID = "usr_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String OTHER_USER_ID = "usr_02BRZ4OEFLUUW5SSGGR70H6GBW";
  private static final String TUTOR_ID = "tut_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String PRESET_TUTOR_ID = "tut_PRESET_OWL_DEFAULT00000";

  @BeforeEach
  void setUp() {
    /* 매퍼 로직까지 검증하도록 TutorMapper 실제 인스턴스를 넣는다 */
    ReflectionTestUtils.setField(tutorService, "tutorMapper", new TutorMapper());
    /* FastAPI 베이스 URL 주입 */
    ReflectionTestUtils.setField(tutorService, "fastapiBaseUrl", "http://localhost:8000");
  }

  // 헬퍼 메서드

  /** 주어진 구독 상태를 가진 테스트 사용자를 생성한다 */
  private User makeUser(SubscriptionStatus status) {
    return User.builder()
        .id(USER_ID)
        .email("test@ezteam.com")
        .nickname("테스터")
        .subscriptionStatus(status)
        .build();
  }

  /** 기본 프리셋 튜터를 생성한다 (user=null, isDefault=true) */
  private Tutor makePresetTutor() {
    return Tutor.builder()
        .tutorId(PRESET_TUTOR_ID)
        .user(null)
        .name("기본 부엉이")
        .tagline("친근한 기본 튜터")
        .profileImageUrl(null)
        .isDefault(true)
        .build();
  }

  /** 주어진 소유자의 커스텀 튜터를 생성한다 (isDefault=false) */
  private Tutor makeCustomTutor(User owner) {
    return Tutor.builder()
        .tutorId(TUTOR_ID)
        .user(owner)
        .name("내 커스텀 튜터")
        .tagline("나만의 튜터")
        .profileImageUrl("https://example.com/image.png")
        .toneSlider((short) 70)
        .speedSlider((short) 60)
        .depthSlider((short) 80)
        .questionStyleSlider((short) 40)
        .useEmoji(true)
        .useFormalSpeech(false)
        .isDefault(false)
        .build();
  }

  // getMyTutors

  @Nested
  @DisplayName("getMyTutors()")
  class GetMyTutors {

    @Test
    @DisplayName("FREE 사용자도 프리셋 + 커스텀 목록 조회 가능")
    void freeUser_canListPresetAndCustomTutors() {
      // given: FREE 사용자, 프리셋 1개 + 커스텀 1개 존재
      User freeUser = makeUser(SubscriptionStatus.FREE);
      Tutor preset = makePresetTutor();
      Tutor custom = makeCustomTutor(freeUser);

      given(tutorRepository.findByIsDefaultTrueOrUserId(USER_ID))
          .willReturn(List.of(preset, custom));

      // when
      var result = tutorService.getMyTutors(USER_ID);

      // then: 프리셋 + 커스텀 모두 포함
      assertThat(result.tutors()).hasSize(2);
      then(tutorRepository).should().findByIsDefaultTrueOrUserId(USER_ID);
    }
  }

  // createTutor

  @Nested
  @DisplayName("createTutor()")
  class CreateTutor {

    private final CreateTutorRequest request =
        new CreateTutorRequest(
            "새 튜터", "멋진 튜터", "https://example.com/fox.png", 60, 70, 80, 90, true, false);

    @Test
    @DisplayName("PRO 사용자: 커스텀 튜터 생성 성공")
    void proUser_createSuccess() {
      // given
      User proUser = makeUser(SubscriptionStatus.PRO);
      given(userRepository.findById(USER_ID)).willReturn(Optional.of(proUser));
      given(tutorRepository.save(any(Tutor.class))).willAnswer(inv -> inv.getArgument(0));

      // when
      var result = tutorService.createTutor(USER_ID, request);

      // then: tutorId 가 생성되어 반환됨
      assertThat(result.tutorId()).isNotBlank();
      then(tutorRepository).should().save(any(Tutor.class));
    }

    @Test
    @DisplayName("PREMIUM 사용자: 커스텀 튜터 생성 성공")
    void premiumUser_createSuccess() {
      // given
      User premiumUser = makeUser(SubscriptionStatus.PREMIUM);
      given(userRepository.findById(USER_ID)).willReturn(Optional.of(premiumUser));
      given(tutorRepository.save(any(Tutor.class))).willAnswer(inv -> inv.getArgument(0));

      // when
      var result = tutorService.createTutor(USER_ID, request);

      // then
      assertThat(result.tutorId()).isNotBlank();
    }

    @Test
    @DisplayName("FREE 사용자: TUT_003 예외 발생")
    void freeUser_throwsTut003() {
      // given
      User freeUser = makeUser(SubscriptionStatus.FREE);
      given(userRepository.findById(USER_ID)).willReturn(Optional.of(freeUser));

      // when & then
      assertThatThrownBy(() -> tutorService.createTutor(USER_ID, request))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(TutorErrorCode.TUT_003));
    }

    @Test
    @DisplayName("EXPIRED 사용자: TUT_003 예외 발생")
    void expiredUser_throwsTut003() {
      // given
      User expiredUser = makeUser(SubscriptionStatus.EXPIRED);
      given(userRepository.findById(USER_ID)).willReturn(Optional.of(expiredUser));

      // when & then
      assertThatThrownBy(() -> tutorService.createTutor(USER_ID, request))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(TutorErrorCode.TUT_003));
    }
  }

  // getTutor

  @Nested
  @DisplayName("getTutor()")
  class GetTutor {

    @Test
    @DisplayName("프리셋 튜터: 누구나 조회 가능")
    void presetTutor_anyoneCanAccess() {
      // given: 프리셋 튜터는 isDefault=true, user=null
      Tutor preset = makePresetTutor();
      given(tutorRepository.findById(PRESET_TUTOR_ID)).willReturn(Optional.of(preset));

      // when
      var result = tutorService.getTutor(USER_ID, PRESET_TUTOR_ID);

      // then
      assertThat(result.tutorId()).isEqualTo(PRESET_TUTOR_ID);
      assertThat(result.isDefault()).isTrue();
    }

    @Test
    @DisplayName("커스텀 튜터: 소유자만 조회 가능")
    void customTutor_ownerCanAccess() {
      // given
      User owner = makeUser(SubscriptionStatus.PRO);
      Tutor custom = makeCustomTutor(owner);
      given(tutorRepository.findById(TUTOR_ID)).willReturn(Optional.of(custom));

      // when
      var result = tutorService.getTutor(USER_ID, TUTOR_ID);

      // then
      assertThat(result.tutorId()).isEqualTo(TUTOR_ID);
      assertThat(result.isDefault()).isFalse();
    }

    @Test
    @DisplayName("다른 사용자의 커스텀 튜터 조회 시 TUT_001 예외")
    void otherUsersCustomTutor_throwsTut001() {
      // given: 튜터 소유자가 다른 사용자
      User otherUser =
          User.builder()
              .id(OTHER_USER_ID)
              .email("other@ezteam.com")
              .nickname("다른사용자")
              .subscriptionStatus(SubscriptionStatus.PRO)
              .build();
      Tutor custom = makeCustomTutor(otherUser);
      given(tutorRepository.findById(TUTOR_ID)).willReturn(Optional.of(custom));

      // when & then: USER_ID 로 접근 시 소유자 불일치
      assertThatThrownBy(() -> tutorService.getTutor(USER_ID, TUTOR_ID))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(TutorErrorCode.TUT_001));
    }
  }

  // updateTutor

  @Nested
  @DisplayName("updateTutor()")
  class UpdateTutor {

    @Test
    @DisplayName("PRO 사용자: 커스텀 튜터 수정 성공, null 필드 기존값 유지")
    void proUser_partialUpdateSuccess() {
      // given: 이름만 변경, 나머지 null
      User proUser = makeUser(SubscriptionStatus.PRO);
      Tutor custom = makeCustomTutor(proUser);
      given(userRepository.findById(USER_ID)).willReturn(Optional.of(proUser));
      given(tutorRepository.findById(TUTOR_ID)).willReturn(Optional.of(custom));

      /* 이름만 바꾸는 부분 수정 요청 */
      UpdateTutorRequest request =
          new UpdateTutorRequest("수정된이름", null, null, null, null, null, null, null, null);

      // when
      var result = tutorService.updateTutor(USER_ID, TUTOR_ID, request);

      // then: 이름은 변경, 나머지는 기존값 유지
      assertThat(result.name()).isEqualTo("수정된이름");
      assertThat(result.tagline()).isEqualTo("나만의 튜터");
      assertThat(result.profileImageUrl()).isEqualTo("https://example.com/image.png");
      assertThat(result.toneSlider()).isEqualTo((short) 70);
      assertThat(result.speedSlider()).isEqualTo((short) 60);
      assertThat(result.depthSlider()).isEqualTo((short) 80);
      assertThat(result.questionStyleSlider()).isEqualTo((short) 40);
      assertThat(result.useEmoji()).isTrue();
      assertThat(result.useFormalSpeech()).isFalse();
    }

    @Test
    @DisplayName("프리셋 튜터 수정 시 TUT_002 예외")
    void presetTutor_throwsTut002() {
      // given
      User proUser = makeUser(SubscriptionStatus.PRO);
      Tutor preset = makePresetTutor();
      given(userRepository.findById(USER_ID)).willReturn(Optional.of(proUser));
      given(tutorRepository.findById(PRESET_TUTOR_ID)).willReturn(Optional.of(preset));

      UpdateTutorRequest request =
          new UpdateTutorRequest("수정시도", null, null, null, null, null, null, null, null);

      // when & then
      assertThatThrownBy(() -> tutorService.updateTutor(USER_ID, PRESET_TUTOR_ID, request))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(TutorErrorCode.TUT_002));
    }

    @Test
    @DisplayName("FREE 사용자: TUT_003 예외")
    void freeUser_throwsTut003() {
      // given
      User freeUser = makeUser(SubscriptionStatus.FREE);
      given(userRepository.findById(USER_ID)).willReturn(Optional.of(freeUser));

      UpdateTutorRequest request =
          new UpdateTutorRequest("수정시도", null, null, null, null, null, null, null, null);

      // when & then
      assertThatThrownBy(() -> tutorService.updateTutor(USER_ID, TUTOR_ID, request))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(TutorErrorCode.TUT_003));
    }
  }

  // deleteTutor

  @Nested
  @DisplayName("deleteTutor()")
  class DeleteTutor {

    @Test
    @DisplayName("PRO 사용자: 커스텀 튜터 삭제 성공")
    void proUser_deleteSuccess() {
      // given
      User proUser = makeUser(SubscriptionStatus.PRO);
      Tutor custom = makeCustomTutor(proUser);
      given(userRepository.findById(USER_ID)).willReturn(Optional.of(proUser));
      given(tutorRepository.findById(TUTOR_ID)).willReturn(Optional.of(custom));

      // when
      tutorService.deleteTutor(USER_ID, TUTOR_ID);

      // then
      then(tutorRepository).should().delete(custom);
    }

    @Test
    @DisplayName("프리셋 튜터 삭제 시 TUT_002 예외")
    void presetTutor_throwsTut002() {
      // given
      User proUser = makeUser(SubscriptionStatus.PRO);
      Tutor preset = makePresetTutor();
      given(userRepository.findById(USER_ID)).willReturn(Optional.of(proUser));
      given(tutorRepository.findById(PRESET_TUTOR_ID)).willReturn(Optional.of(preset));

      // when & then
      assertThatThrownBy(() -> tutorService.deleteTutor(USER_ID, PRESET_TUTOR_ID))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(TutorErrorCode.TUT_002));
    }

    @Test
    @DisplayName("다른 사용자의 커스텀 튜터 삭제 시 TUT_001 예외")
    void otherUsersCustomTutor_throwsTut001() {
      // given: 튜터 소유자가 다른 사용자
      User proUser = makeUser(SubscriptionStatus.PRO);
      User otherUser =
          User.builder()
              .id(OTHER_USER_ID)
              .email("other@ezteam.com")
              .nickname("다른사용자")
              .subscriptionStatus(SubscriptionStatus.PRO)
              .build();
      Tutor custom = makeCustomTutor(otherUser);
      given(userRepository.findById(USER_ID)).willReturn(Optional.of(proUser));
      given(tutorRepository.findById(TUTOR_ID)).willReturn(Optional.of(custom));

      // when & then
      assertThatThrownBy(() -> tutorService.deleteTutor(USER_ID, TUTOR_ID))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(TutorErrorCode.TUT_001));
    }
  }

  // resetTutor

  @Nested
  @DisplayName("resetTutor()")
  class ResetTutor {

    @Test
    @DisplayName("PRO 사용자: 커스텀 튜터 리셋 성공 - 슬라이더 50/50/50/50, useEmoji=false, useFormalSpeech=true")
    void proUser_resetSuccess() {
      // given: 슬라이더 값이 기본값과 다른 커스텀 튜터
      User proUser = makeUser(SubscriptionStatus.PRO);
      Tutor custom = makeCustomTutor(proUser);
      given(userRepository.findById(USER_ID)).willReturn(Optional.of(proUser));
      given(tutorRepository.findById(TUTOR_ID)).willReturn(Optional.of(custom));

      // when
      var result = tutorService.resetTutor(USER_ID, TUTOR_ID);

      // then: 모든 슬라이더/토글이 기본값으로 초기화됨
      assertThat(result.toneSlider()).isEqualTo((short) 50);
      assertThat(result.speedSlider()).isEqualTo((short) 50);
      assertThat(result.depthSlider()).isEqualTo((short) 50);
      assertThat(result.questionStyleSlider()).isEqualTo((short) 50);
      assertThat(result.useEmoji()).isFalse();
      assertThat(result.useFormalSpeech()).isTrue();
    }

    @Test
    @DisplayName("프리셋 튜터 리셋 시 TUT_002 예외")
    void presetTutor_throwsTut002() {
      // given
      User proUser = makeUser(SubscriptionStatus.PRO);
      Tutor preset = makePresetTutor();
      given(userRepository.findById(USER_ID)).willReturn(Optional.of(proUser));
      given(tutorRepository.findById(PRESET_TUTOR_ID)).willReturn(Optional.of(preset));

      // when & then
      assertThatThrownBy(() -> tutorService.resetTutor(USER_ID, PRESET_TUTOR_ID))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(TutorErrorCode.TUT_002));
    }
  }

  // previewTutor

  @Nested
  @DisplayName("previewTutor()")
  class PreviewTutor {

    private final PreviewTutorRequest previewRequest =
        new PreviewTutorRequest(50, 50, 50, 50, false, true, "미분이 뭐예요?");

    @Test
    @DisplayName("PRO 사용자: 소유 커스텀 튜터 미리보기 성공")
    void proUser_previewSuccess() {
      // given
      User proUser = makeUser(SubscriptionStatus.PRO);
      Tutor custom = makeCustomTutor(proUser);
      given(userRepository.findById(USER_ID)).willReturn(Optional.of(proUser));
      given(tutorRepository.findById(TUTOR_ID)).willReturn(Optional.of(custom));

      PreviewTutorResponse fastapiResponse =
          PreviewTutorResponse.builder()
              .previewText("미분은 순간 변화율을 구하는 수학적 도구입니다.")
              .settingTags(List.of("formal", "balanced"))
              .audioUrl("http://localhost:9000/sw-ez-media/tts/preview.wav")
              .build();
      String expectedUrl = "http://localhost:8000/api/tutors/" + TUTOR_ID + "/preview";
      given(restTemplate.postForEntity(eq(expectedUrl), any(), eq(PreviewTutorResponse.class)))
          .willReturn(new ResponseEntity<>(fastapiResponse, HttpStatus.OK));

      // when
      var result = tutorService.previewTutor(USER_ID, TUTOR_ID, previewRequest);

      // then
      assertThat(result.previewText()).isEqualTo("미분은 순간 변화율을 구하는 수학적 도구입니다.");
      assertThat(result.settingTags()).containsExactly("formal", "balanced");
      assertThat(result.audioUrl()).isEqualTo("http://localhost:9000/sw-ez-media/tts/preview.wav");
    }

    @Test
    @DisplayName("존재하지 않는 tutorId: TUT_001 예외")
    void nonExistentTutor_throwsTut001() {
      // given
      User proUser = makeUser(SubscriptionStatus.PRO);
      given(userRepository.findById(USER_ID)).willReturn(Optional.of(proUser));
      given(tutorRepository.findById("tut_NON_EXISTENT_ID")).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(
              () -> tutorService.previewTutor(USER_ID, "tut_NON_EXISTENT_ID", previewRequest))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(TutorErrorCode.TUT_001));
    }
  }
}
