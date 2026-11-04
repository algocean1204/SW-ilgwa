/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.util.List;
import java.util.Map;
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
import org.springframework.data.domain.Pageable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.enums.SubscriptionStatus;
import com.swez.backend.domain.auth.repository.UserRepository;
import com.swez.backend.domain.chat.dto.request.ChatRequest;
import com.swez.backend.domain.chat.dto.response.ChatHistoryResponse;
import com.swez.backend.domain.chat.dto.response.ChatResponse;
import com.swez.backend.domain.chat.entity.ChatMessage;
import com.swez.backend.domain.chat.exception.ChatErrorCode;
import com.swez.backend.domain.chat.mapper.ChatMapper;
import com.swez.backend.domain.chat.repository.ChatMessageRepository;
import com.swez.backend.domain.course.entity.Course;
import com.swez.backend.domain.course.enums.CourseStatus;
import com.swez.backend.domain.course.repository.ChapterRepository;
import com.swez.backend.domain.course.repository.CourseRepository;
import com.swez.backend.domain.tutor.repository.TutorRepository;
import com.swez.backend.global.exception.CustomException;

/** ChatService 단위 테스트 */
@DisplayName("ChatService 단위 테스트")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatServiceTest {

  @Mock private ChatMessageRepository chatMessageRepository;
  @Mock private CourseRepository courseRepository;
  @Mock private ChapterRepository chapterRepository;
  @Mock private TutorRepository tutorRepository;
  @Mock private UserRepository userRepository;
  @Mock private ChatMapper chatMapper;
  @Mock private RestTemplate restTemplate;

  @InjectMocks private ChatService chatService;

  private static final String USER_ID = "usr_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String COURSE_ID = "crs_01ARZ3NDEKTSV4RRFFQ69G5FAV";
  private static final String FASTAPI_BASE_URL = "http://localhost:8000";
  private static final String ASSISTANT_MSG_ID = "cmg_ASSISTANT_00000000000";

  @BeforeEach
  void setUp() {
    /* 매퍼 로직까지 검증하도록 ChatMapper 실제 인스턴스를 넣는다 */
    ReflectionTestUtils.setField(chatService, "chatMapper", new ChatMapper(new ObjectMapper()));
    /* FastAPI 베이스 URL 주입 */
    ReflectionTestUtils.setField(chatService, "fastapiBaseUrl", FASTAPI_BASE_URL);
  }

  // 헬퍼 메서드

  /** 테스트용 사용자를 생성한다 */
  private User makeUser() {
    return User.builder()
        .id(USER_ID)
        .email("test@ezteam.com")
        .nickname("테스터")
        .subscriptionStatus(SubscriptionStatus.FREE)
        .build();
  }

  /** 주어진 소유자의 과외를 생성한다 */
  private Course makeCourse(User owner) {
    return Course.builder()
        .id(COURSE_ID)
        .user(owner)
        .courseName("수학 기초")
        .subject("수학")
        .status(CourseStatus.ACTIVE)
        .build();
  }

  /** 기본 채팅 요청 DTO를 생성한다 */
  private ChatRequest makeChatRequest() {
    return new ChatRequest(null, null, null, "미분이 뭐야?", null, null);
  }

  /** FastAPI 정상 응답 Map을 생성한다 */
  @SuppressWarnings("unchecked")
  private Map<String, Object> makeFastapiResponse() {
    return Map.of(
        "response_text", "미분은 순간 변화율을 구하는 수학적 도구입니다.", "citations", List.of("교과서 p.42", "참고자료 A"));
  }

  /** 테스트용 ChatMessage 엔티티를 생성한다 */
  private ChatMessage makeChatMessage(String role, User user, Course course) {
    return ChatMessage.builder()
        .id("cmg_" + role.toUpperCase() + "_00000000000")
        .user(user)
        .course(course)
        .role(role)
        .message(role.equals("user") ? "미분이 뭐야?" : "미분은 변화율입니다.")
        .build();
  }

  // chat()

  @Nested
  @DisplayName("chat()")
  class Chat {

    @Test
    @DisplayName("정상 채팅 — FastAPI 호출 성공 시 ChatResponse 반환")
    @SuppressWarnings("unchecked")
    void chat_success() {
      // given
      User user = makeUser();
      Course course = makeCourse(user);
      ChatRequest request = makeChatRequest();

      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
      given(tutorRepository.findByIsDefaultTrueOrUserId(USER_ID)).willReturn(List.of());
      given(
              chatMessageRepository.findByCourseIdAndUserIdOrderByCreatedAtDesc(
                  eq(COURSE_ID), eq(USER_ID), any(Pageable.class)))
          .willReturn(List.of());

      /* FastAPI 정상 응답. 프로덕션이 ParameterizedTypeReference 오버로드를 쓰므로 그 시그니처로 스텁한다 */
      given(
              restTemplate.exchange(
                  eq(FASTAPI_BASE_URL + "/api/chat"),
                  eq(HttpMethod.POST),
                  any(HttpEntity.class),
                  any(ParameterizedTypeReference.class)))
          .willReturn(new ResponseEntity<>(makeFastapiResponse(), HttpStatus.OK));

      given(chatMessageRepository.save(any(ChatMessage.class)))
          .willAnswer(inv -> inv.getArgument(0));

      // when
      ChatResponse result = chatService.chat(USER_ID, COURSE_ID, request);

      // then: AI 응답 텍스트와 인용이 반환됨
      assertThat(result.responseText()).isEqualTo("미분은 순간 변화율을 구하는 수학적 도구입니다.");
      assertThat(result.citations()).containsExactly("교과서 p.42", "참고자료 A");
      assertThat(result.sessionId()).isNotBlank();

      /* 사용자 메시지 + AI 응답 메시지 총 2회 저장 */
      then(chatMessageRepository).should(times(2)).save(any(ChatMessage.class));
    }

    @Test
    @DisplayName("과외 미존재 시 CHAT_002 예외 발생")
    void chat_courseNotFound() {
      // given
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> chatService.chat(USER_ID, COURSE_ID, makeChatRequest()))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(ChatErrorCode.CHAT_002));
    }

    @Test
    @DisplayName("FastAPI 호출 실패 시 CHAT_001 예외 발생")
    @SuppressWarnings("unchecked")
    void chat_fastapiError() {
      // given
      User user = makeUser();
      Course course = makeCourse(user);

      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
      given(tutorRepository.findByIsDefaultTrueOrUserId(USER_ID)).willReturn(List.of());
      given(
              chatMessageRepository.findByCourseIdAndUserIdOrderByCreatedAtDesc(
                  eq(COURSE_ID), eq(USER_ID), any(Pageable.class)))
          .willReturn(List.of());

      /* FastAPI 호출에서 RestClientException이 난다. 프로덕션이 ParameterizedTypeReference 오버로드를 쓰므로 그 시그니처로 스텁한다 */
      given(
              restTemplate.exchange(
                  eq(FASTAPI_BASE_URL + "/api/chat"),
                  eq(HttpMethod.POST),
                  any(HttpEntity.class),
                  any(ParameterizedTypeReference.class)))
          .willThrow(new RestClientException("Connection refused"));

      // when & then
      assertThatThrownBy(() -> chatService.chat(USER_ID, COURSE_ID, makeChatRequest()))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(ChatErrorCode.CHAT_001));
    }
  }

  // getHistory()

  @Nested
  @DisplayName("getHistory()")
  class GetHistory {

    @Test
    @DisplayName("히스토리 조회 성공 — 메시지 목록 반환")
    void getHistory_success() {
      // given
      User user = makeUser();
      Course course = makeCourse(user);
      ChatMessage userMsg = makeChatMessage("user", user, course);
      ChatMessage assistantMsg = makeChatMessage("assistant", user, course);

      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
      given(
              chatMessageRepository.findByCourseIdAndUserIdOrderByCreatedAtDesc(
                  eq(COURSE_ID), eq(USER_ID), any(Pageable.class)))
          .willReturn(List.of(assistantMsg, userMsg));

      // when
      ChatHistoryResponse result = chatService.getHistory(USER_ID, COURSE_ID);

      // then: 메시지 2건이 반환됨
      assertThat(result.messages()).hasSize(2);
      assertThat(result.messages().get(0).role()).isEqualTo("assistant");
      assertThat(result.messages().get(1).role()).isEqualTo("user");
    }

    @Test
    @DisplayName("과외 미존재 시 CHAT_002 예외 발생")
    void getHistory_courseNotFound() {
      // given
      given(courseRepository.findById(COURSE_ID)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> chatService.getHistory(USER_ID, COURSE_ID))
          .isInstanceOf(CustomException.class)
          .satisfies(
              ex ->
                  assertThat(((CustomException) ex).getErrorCode())
                      .isEqualTo(ChatErrorCode.CHAT_002));
    }
  }
}
