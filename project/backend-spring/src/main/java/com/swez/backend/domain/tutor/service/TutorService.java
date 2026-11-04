/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.tutor.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.swez.backend.domain.auth.entity.User;
import com.swez.backend.domain.auth.enums.SubscriptionStatus;
import com.swez.backend.domain.auth.exception.AuthErrorCode;
import com.swez.backend.domain.auth.repository.UserRepository;
import com.swez.backend.domain.tutor.dto.request.CreateTutorRequest;
import com.swez.backend.domain.tutor.dto.request.PreviewTutorFastApiRequest;
import com.swez.backend.domain.tutor.dto.request.PreviewTutorRequest;
import com.swez.backend.domain.tutor.dto.request.UpdateTutorRequest;
import com.swez.backend.domain.tutor.dto.response.CreateTutorResponse;
import com.swez.backend.domain.tutor.dto.response.PreviewTutorResponse;
import com.swez.backend.domain.tutor.dto.response.ProfileImageResponse;
import com.swez.backend.domain.tutor.dto.response.TutorListResponse;
import com.swez.backend.domain.tutor.dto.response.TutorResponse;
import com.swez.backend.domain.tutor.dto.response.VoiceSampleResponse;
import com.swez.backend.domain.tutor.entity.Tutor;
import com.swez.backend.domain.tutor.exception.TutorErrorCode;
import com.swez.backend.domain.tutor.mapper.TutorMapper;
import com.swez.backend.domain.tutor.repository.TutorRepository;
import com.swez.backend.global.exception.CustomException;
import com.swez.backend.global.logging.LogMaskUtil;
import com.swez.backend.global.service.S3Service;

import lombok.extern.slf4j.Slf4j;

/** 튜터 프리셋 조회, 커스텀 CRUD, 미리보기를 처리한다. */
@Slf4j
@Service
@Transactional
public class TutorService {

  private final TutorRepository tutorRepository;
  private final UserRepository userRepository;
  private final TutorMapper tutorMapper;
  private final RestTemplate restTemplate;
  private final S3Service s3Service;

  /** 음성 샘플 허용 Content-Type 목록 */
  private static final Set<String> ALLOWED_AUDIO_TYPES =
      Set.of("audio/wav", "audio/mpeg", "audio/mp3");

  /** 파일 최대 크기 (10MB) */
  private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

  /** 프로필 이미지 허용 Content-Type 목록 */
  private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png");

  /** 프로필 이미지 최대 크기 (5MB) */
  private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;

  /** FastAPI 베이스 URL. application.yml 의 app.fastapi.base-url 에서 주입한다. */
  @Value("${app.fastapi.base-url}")
  private String fastapiBaseUrl;

  public TutorService(
      TutorRepository tutorRepository,
      UserRepository userRepository,
      TutorMapper tutorMapper,
      @Qualifier("fastapiRestTemplate") RestTemplate restTemplate,
      S3Service s3Service) {
    this.tutorRepository = tutorRepository;
    this.userRepository = userRepository;
    this.tutorMapper = tutorMapper;
    this.restTemplate = restTemplate;
    this.s3Service = s3Service;
  }

  /** 기본 프리셋(isDefault=true)과 사용자 커스텀 튜터 목록을 조회한다. */
  @Transactional(readOnly = true)
  public TutorListResponse getMyTutors(String userId) {
    log.debug("[TutorService] getMyTutors() - START | userId: {}", userId);

    List<Tutor> tutors = tutorRepository.findByIsDefaultTrueOrUserId(userId);

    TutorListResponse result = tutorMapper.toListResponse(tutors);

    log.debug("[TutorService] getMyTutors() - END | count: {}", tutors.size());
    return result;
  }

  /** 커스텀 튜터를 생성한다. Pro 이상 구독자만 사용할 수 있다. */
  @Transactional
  public CreateTutorResponse createTutor(String userId, CreateTutorRequest request) {
    log.debug("[TutorService] createTutor() - START | userId: {}", userId);

    /*
        FREE 구독자는 커스텀 튜터를 만들 수 없다(TUT_003).
    */
    User user = findUserById(userId);
    validateProOrAbove(user);

    Tutor tutor = tutorMapper.toEntity(request, user);
    Tutor saved = tutorRepository.save(tutor);

    CreateTutorResponse result = tutorMapper.toCreateResponse(saved);

    log.debug("[TutorService] createTutor() - END | tutorId: {}", saved.getTutorId());
    return result;
  }

  /** 튜터 단건을 조회한다. 기본 프리셋은 누구나, 커스텀은 소유자만 접근할 수 있다(TUT_001). */
  @Transactional(readOnly = true)
  public TutorResponse getTutor(String userId, String tutorId) {
    log.debug("[TutorService] getTutor() - START | userId: {}, tutorId: {}", userId, tutorId);

    Tutor tutor = findTutorForUser(userId, tutorId);

    TutorResponse result = tutorMapper.toResponse(tutor);

    log.debug("[TutorService] getTutor() - END | tutorId: {}", tutorId);
    return result;
  }

  /** 커스텀 튜터 정보를 부분 수정한다. Pro 이상 구독자만 사용할 수 있다. */
  @Transactional
  public TutorResponse updateTutor(String userId, String tutorId, UpdateTutorRequest request) {
    log.debug("[TutorService] updateTutor() - START | userId: {}, tutorId: {}", userId, tutorId);

    User user = findUserById(userId);
    validateProOrAbove(user);

    /*
        프리셋은 누구나, 커스텀은 소유자만 조회할 수 있다(TUT_001).
    */
    Tutor tutor = findTutorForUser(userId, tutorId);

    /*
        기본 제공 튜터(isDefault=true)는 수정할 수 없다(TUT_002).
    */
    if (tutor.isDefault()) {
      throw new CustomException(TutorErrorCode.TUT_002);
    }

    tutor.update(
        request.name(),
        request.tagline(),
        request.profileImageUrl(),
        request.toneSlider(),
        request.speedSlider(),
        request.depthSlider(),
        request.questionStyleSlider(),
        request.useEmoji(),
        request.useFormalSpeech());

    TutorResponse result = tutorMapper.toResponse(tutor);

    log.debug("[TutorService] updateTutor() - END | tutorId: {}", tutorId);
    return result;
  }

  /** 커스텀 튜터를 삭제한다. Pro 이상 구독자만 사용할 수 있으며, isDefault=true 인 튜터는 삭제할 수 없다. */
  @Transactional
  public void deleteTutor(String userId, String tutorId) {
    log.debug("[TutorService] deleteTutor() - START | userId: {}, tutorId: {}", userId, tutorId);

    User user = findUserById(userId);
    validateProOrAbove(user);

    /*
        findById 로 프리셋과 커스텀을 모두 조회한다.
    */
    Tutor tutor =
        tutorRepository
            .findById(tutorId)
            .orElseThrow(() -> new CustomException(TutorErrorCode.TUT_001));

    /*
        기본 제공 튜터(isDefault=true)는 삭제할 수 없다(TUT_002).
    */
    if (tutor.isDefault()) {
      throw new CustomException(TutorErrorCode.TUT_002);
    }

    String ownerId = tutor.getUser() != null ? tutor.getUser().getId() : null;
    if (!userId.equals(ownerId)) {
      throw new CustomException(TutorErrorCode.TUT_001);
    }

    tutorRepository.delete(tutor);

    log.debug("[TutorService] deleteTutor() - END | tutorId: {}", tutorId);
  }

  /** 커스텀 튜터의 슬라이더/토글 값을 기본값으로 복원한다. Pro 이상 구독자만 사용할 수 있다. */
  @Transactional
  public TutorResponse resetTutor(String userId, String tutorId) {
    log.debug("[TutorService] resetTutor() - START | userId: {}, tutorId: {}", userId, tutorId);

    User user = findUserById(userId);
    validateProOrAbove(user);

    /*
        프리셋은 누구나, 커스텀은 소유자만 조회할 수 있다(TUT_001).
    */
    Tutor tutor = findTutorForUser(userId, tutorId);

    /*
        기본 제공 튜터(isDefault=true)는 초기화할 수 없다(TUT_002).
    */
    if (tutor.isDefault()) {
      throw new CustomException(TutorErrorCode.TUT_002);
    }

    tutor.resetToDefaults();

    TutorResponse result = tutorMapper.toResponse(tutor);

    log.debug("[TutorService] resetTutor() - END | tutorId: {}", tutorId);
    return result;
  }

  /** FastAPI를 호출해 튜터 미리보기 텍스트를 생성한다. Pro 이상 구독자만 사용할 수 있다. */
  @Transactional(readOnly = true)
  public PreviewTutorResponse previewTutor(
      String userId, String tutorId, PreviewTutorRequest request) {
    log.debug("[TutorService] previewTutor() - START | userId: {}, tutorId: {}", userId, tutorId);

    User user = findUserById(userId);
    validateProOrAbove(user);

    /*
        프리셋은 누구나, 커스텀은 소유자만 조회할 수 있다.
    */
    Tutor tutor = findTutorForUser(userId, tutorId);

    /*
        POST {base-url}/api/tutors/{tutorId}/preview 를 호출한다.
        서버에 보관한 음성 샘플 URL 을 함께 보내 FastAPI 가 TTS 경로(보이스클론/기본 TTS)를 고르게 한다.
        실패하면 TUT_004 예외를 던진다.
    */
    String url = fastapiBaseUrl + "/api/tutors/" + tutorId + "/preview";
    PreviewTutorFastApiRequest fastapiRequest =
        PreviewTutorFastApiRequest.of(request, tutor.getVoiceSampleUrl());
    try {
      ResponseEntity<PreviewTutorResponse> response =
          restTemplate.postForEntity(url, fastapiRequest, PreviewTutorResponse.class);

      if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
        throw new CustomException(TutorErrorCode.TUT_004);
      }

      PreviewTutorResponse result = response.getBody();
      log.debug("[TutorService] previewTutor() - END | tutorId: {}", tutorId);
      return result;

    } catch (RestClientException e) {
      log.error(
          "[TutorService] previewTutor() - FastAPI 호출 실패 | tutorId: {}, error: {}",
          tutorId,
          e.getMessage());
      throw new CustomException(TutorErrorCode.TUT_004);
    }
  }

  /**
   * 튜터의 음성 클론 샘플을 S3에 업로드한다. Pro 이상 구독자만 사용할 수 있다.
   *
   * <p>S3 업로드 후 트랜잭션이 롤백되면 orphan 파일이 남을 수 있다. 주기적 S3 정리 배치나
   * TransactionalEventListener 를 도입하면 해소할 수 있다.
   *
   * @param file WAV/MP3 음성 파일 (최대 10MB)
   */
  @Transactional
  public VoiceSampleResponse uploadVoiceSample(String userId, String tutorId, MultipartFile file) {
    log.debug(
        "[TutorService] uploadVoiceSample() - START | userId: {}, tutorId: {}", userId, tutorId);

    User user = findUserById(userId);
    validateProOrAbove(user);

    /*
        동시 업로드 race condition 을 막으려고 비관적 락으로 튜터를 조회한다.
    */
    Tutor tutor =
        tutorRepository
            .findByTutorIdAndUserIdForUpdate(tutorId, userId)
            .orElseThrow(() -> new CustomException(TutorErrorCode.TUT_001));
    if (tutor.isDefault()) {
      throw new CustomException(TutorErrorCode.TUT_002);
    }

    validateVoiceSampleFile(file);

    /*
        원본 파일명은 쓰지 않고 UUID + 확장자로 파일명을 만든다.
    */
    String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
    if (ext != null) ext = ext.toLowerCase();
    String fileName = UUID.randomUUID() + "." + ext;
    String resolvedContentType = "wav".equals(ext) ? "audio/wav" : "audio/mpeg";

    String oldUrl = tutor.getVoiceSampleUrl();

    String url;
    try {
      url = s3Service.upload("voice-samples", fileName, file, resolvedContentType);
    } catch (Exception e) {
      log.error(
          "[TutorService] uploadVoiceSample() - S3 업로드 실패 | tutorId: {}, error: {}",
          tutorId,
          e.getMessage());
      throw new CustomException(TutorErrorCode.TUT_008);
    }

    try {
      tutor.updateVoiceSampleUrl(url);
    } catch (Exception e) {
      // DB 저장이 실패하면 올린 S3 파일을 보상 삭제한다.
      try {
        s3Service.delete(url);
      } catch (Exception s3Ex) {
        log.warn(
            "[TutorService] DB 저장 실패 후 S3 보상 삭제에도 실패 (리소스 누수 가능성) | url: {}, error: {}",
            url,
            s3Ex.getMessage());
      }
      throw e;
    }

    /*
        기존 음성 샘플은 best-effort 로 S3 에서 지운다. 실패해도 진행한다.
    */
    if (oldUrl != null && !oldUrl.isBlank()) {
      try {
        s3Service.delete(oldUrl);
      } catch (Exception e) {
        log.warn(
            "[TutorService] 기존 음성 샘플 삭제 실패 (무시) | oldUrl: {}, error: {}",
            LogMaskUtil.mask(oldUrl),
            e.getMessage());
      }
    }

    VoiceSampleResponse result =
        VoiceSampleResponse.builder()
            .tutorId(tutor.getTutorId())
            .voiceSampleUrl(url)
            .uploadedAt(LocalDateTime.now())
            .status("processing")
            .build();

    log.debug(
        "[TutorService] uploadVoiceSample() - END | tutorId: {}, url: {}",
        tutorId,
        LogMaskUtil.mask(url));
    return result;
  }

  /**
   * 튜터의 프로필 이미지를 S3에 업로드한다. Pro 이상 구독자만 사용할 수 있다.
   *
   * @param file JPG/PNG 이미지 파일 (최대 5MB)
   */
  @Transactional
  public ProfileImageResponse uploadProfileImage(
      String userId, String tutorId, MultipartFile file) {
    log.debug(
        "[TutorService] uploadProfileImage() - START | userId: {}, tutorId: {}", userId, tutorId);

    User user = findUserById(userId);
    validateProOrAbove(user);

    Tutor tutor =
        tutorRepository
            .findByTutorIdAndUserIdForUpdate(tutorId, userId)
            .orElseThrow(() -> new CustomException(TutorErrorCode.TUT_001));
    if (tutor.isDefault()) {
      throw new CustomException(TutorErrorCode.TUT_002);
    }

    validateProfileImageFile(file);

    String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
    if (ext != null) ext = ext.toLowerCase();
    String fileName = UUID.randomUUID() + "." + ext;

    String oldUrl = tutor.getProfileImageUrl();

    String url;
    try {
      url = s3Service.upload("profile-images", fileName, file, file.getContentType());
    } catch (Exception e) {
      log.error(
          "[TutorService] uploadProfileImage() - S3 업로드 실패 | tutorId: {}, error: {}",
          tutorId,
          e.getMessage());
      throw new CustomException(TutorErrorCode.TUT_008);
    }

    try {
      tutor.updateProfileImageUrl(url);
    } catch (Exception e) {
      // DB 저장이 실패하면 올린 S3 파일을 보상 삭제한다.
      try {
        s3Service.delete(url);
      } catch (Exception s3Ex) {
        log.warn(
            "[TutorService] DB 저장 실패 후 S3 보상 삭제에도 실패 (리소스 누수 가능성) | url: {}, error: {}",
            url,
            s3Ex.getMessage());
      }
      throw e;
    }

    /*
        기존 프로필 이미지는 best-effort 로 S3 에서 지운다. 실패해도 진행한다.
    */
    if (oldUrl != null && !oldUrl.isBlank()) {
      try {
        s3Service.delete(oldUrl);
      } catch (Exception e) {
        log.warn(
            "[TutorService] 기존 프로필 이미지 삭제 실패 (무시) | oldUrl: {}, error: {}",
            LogMaskUtil.mask(oldUrl),
            e.getMessage());
      }
    }

    ProfileImageResponse result =
        ProfileImageResponse.builder()
            .tutorId(tutor.getTutorId())
            .profileImageUrl(url)
            .uploadedAt(LocalDateTime.now())
            .build();

    log.debug(
        "[TutorService] uploadProfileImage() - END | tutorId: {}, url: {}",
        tutorId,
        LogMaskUtil.mask(url));
    return result;
  }

  /** 튜터의 프로필 이미지를 삭제한다. Pro 이상 구독자만 사용할 수 있다. */
  @Transactional
  public void deleteProfileImage(String userId, String tutorId) {
    log.debug(
        "[TutorService] deleteProfileImage() - START | userId: {}, tutorId: {}", userId, tutorId);

    User user = findUserById(userId);
    validateProOrAbove(user);

    Tutor tutor =
        tutorRepository
            .findByTutorIdAndUserIdForUpdate(tutorId, userId)
            .orElseThrow(() -> new CustomException(TutorErrorCode.TUT_001));
    if (tutor.isDefault()) {
      throw new CustomException(TutorErrorCode.TUT_002);
    }

    String oldUrl = tutor.getProfileImageUrl();
    tutor.clearProfileImageUrl();

    /*
        S3 의 기존 이미지는 best-effort 로 지운다.
    */
    if (oldUrl != null && !oldUrl.isBlank()) {
      try {
        s3Service.delete(oldUrl);
      } catch (Exception e) {
        log.warn(
            "[TutorService] 프로필 이미지 S3 삭제 실패 (무시) | oldUrl: {}, error: {}",
            LogMaskUtil.mask(oldUrl),
            e.getMessage());
      }
    }

    log.debug("[TutorService] deleteProfileImage() - END | tutorId: {}", tutorId);
  }

  /** 사용자의 커스텀 튜터를 모두 삭제한다 (기본값 복원). Pro 이상 구독자만 사용할 수 있다. */
  @Transactional
  public void deleteAllCustomTutors(String userId) {
    log.debug("[TutorService] deleteAllCustomTutors() - START | userId: {}", userId);

    User user = findUserById(userId);
    validateProOrAbove(user);

    tutorRepository.deleteByUserIdAndIsDefaultFalse(userId);

    log.debug("[TutorService] deleteAllCustomTutors() - END | userId: {}", userId);
  }

  // Private Helpers

  /**
   * 음성 샘플 파일 유효성을 검증한다.
   *
   * <p>비어있음(TUT_005), 지원하지 않는 형식(TUT_006), 크기 초과(TUT_007), Magic Bytes 불일치(TUT_006) 를 검사한다.
   */
  private void validateVoiceSampleFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new CustomException(TutorErrorCode.TUT_005);
    }

    String contentType = file.getContentType();
    if (contentType == null || !ALLOWED_AUDIO_TYPES.contains(contentType)) {
      throw new CustomException(TutorErrorCode.TUT_006);
    }

    if (file.getSize() > MAX_FILE_SIZE) {
      throw new CustomException(TutorErrorCode.TUT_007);
    }

    validateMagicBytes(file);
  }

  /**
   * 파일 Magic Bytes 를 검증해 Content-Type 스푸핑을 막는다.
   *
   * <p>WAV: 시작 4바이트 RIFF(52 49 46 46), MP3: ID3 태그(49 44 33) 또는 sync word(FF Fx/Ex)
   */
  private void validateMagicBytes(MultipartFile file) {
    try {
      byte[] header = file.getInputStream().readNBytes(4);
      boolean isWav =
          header.length >= 4
              && header[0] == 0x52
              && header[1] == 0x49
              && header[2] == 0x46
              && header[3] == 0x46;
      boolean isMp3Id3 =
          header.length >= 3 && header[0] == 0x49 && header[1] == 0x44 && header[2] == 0x33;
      boolean isMp3Sync =
          header.length >= 2 && header[0] == (byte) 0xFF && (header[1] & 0xE0) == (byte) 0xE0;
      if (!isWav && !isMp3Id3 && !isMp3Sync) {
        throw new CustomException(TutorErrorCode.TUT_006);
      }
    } catch (CustomException e) {
      throw e;
    } catch (Exception e) {
      throw new CustomException(TutorErrorCode.TUT_005);
    }
  }

  /**
   * 프로필 이미지 파일 유효성을 검증한다.
   *
   * <p>비어있음(TUT_009), 지원하지 않는 형식(TUT_010), 크기 초과(TUT_011), Magic Bytes 불일치(TUT_010) 를 검사한다.
   */
  private void validateProfileImageFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new CustomException(TutorErrorCode.TUT_009);
    }

    String contentType = file.getContentType();
    if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
      throw new CustomException(TutorErrorCode.TUT_010);
    }

    if (file.getSize() > MAX_IMAGE_SIZE) {
      throw new CustomException(TutorErrorCode.TUT_011);
    }

    validateImageMagicBytes(file);
  }

  /**
   * 이미지 파일 Magic Bytes 를 검증해 Content-Type 스푸핑을 막는다.
   *
   * <p>JPEG: FF D8 FF, PNG: 89 50 4E 47 (0x89 'P' 'N' 'G')
   */
  private void validateImageMagicBytes(MultipartFile file) {
    try {
      byte[] header = file.getInputStream().readNBytes(4);
      boolean isJpeg =
          header.length >= 3
              && header[0] == (byte) 0xFF
              && header[1] == (byte) 0xD8
              && header[2] == (byte) 0xFF;
      boolean isPng =
          header.length >= 4
              && header[0] == (byte) 0x89
              && header[1] == 0x50
              && header[2] == 0x4E
              && header[3] == 0x47;
      if (!isJpeg && !isPng) {
        throw new CustomException(TutorErrorCode.TUT_010);
      }
    } catch (CustomException e) {
      throw e;
    } catch (Exception e) {
      throw new CustomException(TutorErrorCode.TUT_009);
    }
  }

  /** userId 로 사용자를 조회한다. 없으면 USER_001 예외를 발생시킨다. */
  private User findUserById(String userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new CustomException(AuthErrorCode.USER_001));
  }

  /**
   * Pro 이상 구독 여부를 검증한다.
   *
   * <p>PRO 또는 PREMIUM 이 아닌 경우 TUT_003 예외를 발생시킨다.
   */
  private void validateProOrAbove(User user) {
    SubscriptionStatus status = user.getSubscriptionStatus();
    if (!SubscriptionStatus.PRO.equals(status) && !SubscriptionStatus.PREMIUM.equals(status)) {
      throw new CustomException(TutorErrorCode.TUT_003);
    }
  }

  /**
   * 튜터를 조회한다. isDefault=true 이면 누구나 접근 가능하고, 커스텀이면 소유자만 접근 가능하다.
   *
   * <p>존재 여부가 드러나지 않게, 없거나 접근 권한이 없으면 똑같이 TUT_001 을 발생시킨다.
   */
  private Tutor findTutorForUser(String userId, String tutorId) {
    Tutor tutor =
        tutorRepository
            .findById(tutorId)
            .orElseThrow(() -> new CustomException(TutorErrorCode.TUT_001));

    if (!tutor.isDefault()) {
      String ownerId = tutor.getUser() != null ? tutor.getUser().getId() : null;
      if (!userId.equals(ownerId)) {
        throw new CustomException(TutorErrorCode.TUT_001);
      }
    }
    return tutor;
  }
}
