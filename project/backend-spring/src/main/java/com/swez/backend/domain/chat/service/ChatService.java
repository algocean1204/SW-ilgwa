/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.chat.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.repository.UserRepository;
import com.swez.backend.domain.chat.dto.request.ChatRequest;
import com.swez.backend.domain.chat.dto.response.ChatHistoryResponse;
import com.swez.backend.domain.chat.dto.response.ChatResponse;
import com.swez.backend.domain.chat.entity.ChatMessage;
import com.swez.backend.domain.chat.exception.ChatErrorCode;
import com.swez.backend.domain.chat.mapper.ChatMapper;
import com.swez.backend.domain.chat.repository.ChatMessageRepository;
import com.swez.backend.domain.course.entity.Chapter;
import com.swez.backend.domain.course.entity.Course;
import com.swez.backend.domain.course.repository.ChapterRepository;
import com.swez.backend.domain.course.repository.CourseRepository;
import com.swez.backend.domain.tutor.entity.Tutor;
import com.swez.backend.domain.tutor.repository.TutorRepository;
import com.swez.backend.global.exception.CustomException;

import lombok.extern.slf4j.Slf4j;

/** DB 컨텍스트를 붙여 AI 채팅을 FastAPI로 프록시하고, 채팅 히스토리를 저장·조회한다. */
@Slf4j
@Service
@Transactional
public class ChatService {

  private final ChatMessageRepository chatMessageRepository;
  private final CourseRepository courseRepository;
  private final ChapterRepository chapterRepository;
  private final TutorRepository tutorRepository;
  private final UserRepository userRepository;
  private final ChatMapper chatMapper;
  private final RestTemplate restTemplate;

  /** FastAPI 베이스 URL (application.yml의 app.fastapi.base-url) */
  @Value("${app.fastapi.base-url}")
  private String fastapiBaseUrl;

  public ChatService(
      ChatMessageRepository chatMessageRepository,
      CourseRepository courseRepository,
      ChapterRepository chapterRepository,
      TutorRepository tutorRepository,
      UserRepository userRepository,
      ChatMapper chatMapper,
      @Qualifier("fastapiRestTemplate") RestTemplate restTemplate) {
    this.chatMessageRepository = chatMessageRepository;
    this.courseRepository = courseRepository;
    this.chapterRepository = chapterRepository;
    this.tutorRepository = tutorRepository;
    this.userRepository = userRepository;
    this.chatMapper = chatMapper;
    this.restTemplate = restTemplate;
  }

  /** 히스토리 조회 기본 페이지 크기 */
  private static final int HISTORY_DEFAULT_SIZE = 50;

  /** FastAPI 컨텍스트에 포함할 최근 QA 건수 */
  private static final int RECENT_QA_COUNT = 10;

  /**
   * FastAPI scope_guard의 OUT_OF_SCOPE_REPLY 상수와 동일한 거부 식별 어구들.
   *
   * <p>이 어구가 하나라도 들어간 AI 답변은 recent_qa 컨텍스트에서 뺀다. 그래야 LLM이 거부문을 정상 답변으로
   * 알고 따라 쓰지 않는다. FastAPI scope_guard._REFUSAL_MARKERS와 함께 고쳐야 한다.
   */
  private static final List<String> REFUSAL_MARKERS = List.of("다루지 않는", "강의에서는", "확인해 보시");

  /** AI 채팅을 처리한다. DB에서 컨텍스트를 채워 FastAPI에 프록시한다. */
  @Transactional
  public ChatResponse chat(String userId, String courseId, ChatRequest request) {
    log.debug("[ChatService] chat() - START | userId: {}, courseId: {}", userId, courseId);

    Course course = findCourseForUser(courseId, userId);

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new CustomException(ChatErrorCode.CHAT_002));

    /* lessonId는 chapterId에 대응한다. 없으면 chapter는 null이다. */
    Chapter chapter = null;
    if (request.lessonId() != null) {
      chapter = chapterRepository.findById(request.lessonId()).orElse(null);
    }

    Map<String, Object> requestBody = buildFastapiRequestBody(user, course, chapter, request);

    /*
        사용자 메시지 엔티티는 지금 만들고 저장은 FastAPI 성공 뒤에 한다.
        FastAPI가 실패했을 때 답 없는 메시지가 남지 않게 save()를 뒤로 미룬다.
    */
    ChatMessage userMessage =
        chatMapper.toUserMessage(
            user,
            course,
            chapter,
            request.slideId(),
            request.slideIdx(),
            request.message(),
            request.playbackSec());

    Map<String, Object> fastapiResponse = callFastapi(requestBody);

    String responseText = extractString(fastapiResponse, "response_text");
    List<String> citations = extractStringList(fastapiResponse, "citations");

    /* FastAPI 성공을 확인한 뒤에만 사용자 메시지와 AI 응답 메시지를 함께 저장한다. */
    chatMessageRepository.save(userMessage);

    ChatMessage assistantMessage =
        chatMapper.toAssistantMessage(
            user,
            course,
            chapter,
            request.slideId(),
            request.slideIdx(),
            responseText,
            citations,
            request.playbackSec());
    chatMessageRepository.save(assistantMessage);

    ChatResponse result =
        chatMapper.toChatResponse(responseText, citations, assistantMessage.getId());

    log.debug("[ChatService] chat() - END | sessionId: {}", assistantMessage.getId());
    return result;
  }

  /** 과외의 최근 채팅 히스토리 50건을 최신순으로 조회한다. */
  @Transactional(readOnly = true)
  public ChatHistoryResponse getHistory(String userId, String courseId) {
    log.debug("[ChatService] getHistory() - START | userId: {}, courseId: {}", userId, courseId);

    findCourseForUser(courseId, userId);

    List<ChatMessage> messages =
        chatMessageRepository.findByCourseIdAndUserIdOrderByCreatedAtDesc(
            courseId, userId, PageRequest.of(0, HISTORY_DEFAULT_SIZE));

    ChatHistoryResponse result = chatMapper.toHistoryResponse(messages);

    log.debug("[ChatService] getHistory() - END | count: {}", messages.size());
    return result;
  }

  // 내부 헬퍼

  /** 과외 소유권을 확인한다. 과외가 없거나 소유자가 아니면 CHAT_002를 던진다. */
  private Course findCourseForUser(String courseId, String userId) {
    Course course =
        courseRepository
            .findById(courseId)
            .orElseThrow(() -> new CustomException(ChatErrorCode.CHAT_002));

    if (!userId.equals(course.getUser().getId())) {
      throw new CustomException(ChatErrorCode.CHAT_002);
    }
    return course;
  }

  /**
   * FastAPI 요청 body를 snake_case Map으로 만든다. 튜터 설정은 과외에 연결된 Tutor를 먼저 쓰고, 없으면
   * 사용자의 기본 튜터를 쓴다.
   */
  private Map<String, Object> buildFastapiRequestBody(
      User user, Course course, Chapter chapter, ChatRequest request) {

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("user_id", user.getId());
    body.put("tutoring_id", course.getId());
    body.put("lesson_id", request.lessonId());
    body.put("slide_id", request.slideId());
    body.put("slide_idx", request.slideIdx());
    body.put("message", request.message());
    body.put("selected_text", request.selectedText());
    body.put("playback_sec", request.playbackSec());

    Map<String, Object> context = buildContext(user, course, chapter);
    body.put("context", context);

    return body;
  }

  /** FastAPI 컨텍스트 블록을 만든다. 튜터 설정 우선순위: 과외의 Tutor > 사용자의 기본 Tutor > 기본값 */
  private Map<String, Object> buildContext(User user, Course course, Chapter chapter) {
    Map<String, Object> context = new LinkedHashMap<>();

    Tutor courseTutor = course.getTutor();
    if (courseTutor != null) {
      context.put("tutor_name", courseTutor.getName());
      context.put("tone_slider", (int) courseTutor.getToneSlider());
      context.put("speed_slider", (int) courseTutor.getSpeedSlider());
      context.put("depth_slider", (int) courseTutor.getDepthSlider());
      context.put("question_style_slider", (int) courseTutor.getQuestionStyleSlider());
      context.put("use_emoji", courseTutor.isUseEmoji());
      context.put("use_formal_speech", courseTutor.isUseFormalSpeech());
    } else {
      fillTutorContext(context, user.getId());
    }

    context.put("course_subject", course.getSubject());
    context.put("lesson_title", chapter != null ? chapter.getChapterName() : null);

    context.put("recent_qa", buildRecentQa(course.getId(), user.getId()));

    return context;
  }

  /** 사용자의 기본 Tutor 정보로 컨텍스트를 채운다. 기본 튜터가 없으면 기본값을 사용한다. */
  private void fillTutorContext(Map<String, Object> context, String userId) {
    List<Tutor> tutors = tutorRepository.findByIsDefaultTrueOrUserId(userId);
    /* 기본 프리셋 튜터 중 첫 번째를 쓴다. */
    Tutor defaultTutor = tutors.stream().filter(Tutor::isDefault).findFirst().orElse(null);

    if (defaultTutor != null) {
      context.put("tutor_name", defaultTutor.getName());
      context.put("tone_slider", (int) defaultTutor.getToneSlider());
      context.put("speed_slider", (int) defaultTutor.getSpeedSlider());
      context.put("depth_slider", (int) defaultTutor.getDepthSlider());
      context.put("question_style_slider", (int) defaultTutor.getQuestionStyleSlider());
      context.put("use_emoji", defaultTutor.isUseEmoji());
      context.put("use_formal_speech", defaultTutor.isUseFormalSpeech());
    } else {
      /* 기본 튜터도 없으면 고정 기본값을 쓴다. */
      context.put("tutor_name", "기본 튜터");
      context.put("tone_slider", 50);
      context.put("speed_slider", 50);
      context.put("depth_slider", 50);
      context.put("question_style_slider", 50);
      context.put("use_emoji", false);
      context.put("use_formal_speech", true);
    }
  }

  /**
   * 최근 QA 히스토리를 question/answer 쌍 리스트로 만든다.
   *
   * <p>최근 메시지를 user→assistant 순서로 짝짓는다. AI 거부 답변(REFUSAL_MARKERS 포함)이 있는 Q&A 쌍은 빼서
   * LLM 컨텍스트(recent_qa)에 거부문이 섞이지 않게 한다. 화면에 보이는 히스토리는 그대로 두고 전송 데이터만 거른다.
   */
  private List<Map<String, String>> buildRecentQa(String courseId, String userId) {
    List<ChatMessage> recentMessages =
        chatMessageRepository.findByCourseIdAndUserIdOrderByCreatedAtDesc(
            courseId, userId, PageRequest.of(0, RECENT_QA_COUNT * 2));

    List<Map<String, String>> qaList = new ArrayList<>();
    /* 최신순 결과를 시간순으로 뒤집어 짝짓는다. */
    List<ChatMessage> chronological = new ArrayList<>(recentMessages);
    java.util.Collections.reverse(chronological);

    for (int i = 0; i < chronological.size() - 1; i++) {
      ChatMessage current = chronological.get(i);
      ChatMessage next = chronological.get(i + 1);
      if ("user".equals(current.getRole()) && "assistant".equals(next.getRole())) {
        /* 거부 답변이 다음 답변에 번지지 않도록 그 쌍은 컨텍스트에서 뺀다. */
        if (!isRefusedAnswer(next.getMessage())) {
          Map<String, String> qa = new LinkedHashMap<>();
          qa.put("question", current.getMessage());
          qa.put("answer", next.getMessage());
          qaList.add(qa);
        }
        i++; // 짝지은 assistant 메시지를 건너뛴다
      }
    }

    /* 최근 RECENT_QA_COUNT(10)쌍만 남긴다. */
    if (qaList.size() > RECENT_QA_COUNT) {
      return qaList.subList(qaList.size() - RECENT_QA_COUNT, qaList.size());
    }
    return qaList;
  }

  /**
   * AI 답변이 강의 범위 밖 거부 응답인지 판별한다.
   *
   * <p>FastAPI scope_guard._REFUSAL_MARKERS와 같은 어구로 확인한다. 하나라도 들어 있으면 거부 답변으로 본다.
   *
   * @return 거부 답변이면 true, 정상 답변이면 false
   */
  private boolean isRefusedAnswer(String answer) {
    if (answer == null || answer.isBlank()) {
      return false;
    }
    return REFUSAL_MARKERS.stream().anyMatch(answer::contains);
  }

  /** FastAPI에 채팅 요청(snake_case Map)을 보내고 응답을 반환한다. */
  private Map<String, Object> callFastapi(Map<String, Object> requestBody) {
    String url = fastapiBaseUrl + "/api/chat";
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

      /* ParameterizedTypeReference로 raw Map.class와 unchecked 캐스트 없이 응답을 받는다. */
      ResponseEntity<Map<String, Object>> response =
          restTemplate.exchange(
              url,
              HttpMethod.POST,
              entity,
              new ParameterizedTypeReference<Map<String, Object>>() {});

      if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
        throw new CustomException(ChatErrorCode.CHAT_001);
      }

      return response.getBody();
    } catch (RestClientException e) {
      log.error("[ChatService] callFastapi() - FastAPI 호출 실패 | error: {}", e.getMessage());
      throw new CustomException(ChatErrorCode.CHAT_001);
    }
  }

  /** Map에서 String 값을 꺼낸다. 값이 없으면 빈 문자열을 반환한다. */
  private String extractString(Map<String, Object> map, String key) {
    Object value = map.get(key);
    return value != null ? value.toString() : "";
  }

  /** Map에서 String 리스트를 꺼낸다. instanceof List<?> 패턴 매칭으로 unchecked 캐스트를 피한다. */
  private List<String> extractStringList(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (value instanceof List<?> list) {
      /* 원소마다 Object::toString을 쓰므로 캐스트가 필요 없다. */
      return list.stream().map(Object::toString).toList();
    }
    return List.of();
  }
}
