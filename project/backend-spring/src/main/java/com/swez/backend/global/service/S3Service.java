/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.swez.backend.global.config.properties.S3Properties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** AWS S3 파일 업로드/삭제 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

  private final S3Client s3Client;
  private final S3Properties s3Properties;

  /**
   * S3에 파일을 업로드하고 접근 URL을 반환한다.
   *
   * @param directory S3 키 디렉토리 (예: voice-samples)
   * @param contentType 확장자 기반으로 확정된 Content-Type (클라이언트 헤더 대신 서버에서 결정)
   */
  public String upload(String directory, String fileName, MultipartFile file, String contentType) {
    String key = directory + "/" + fileName;

    PutObjectRequest putRequest =
        PutObjectRequest.builder()
            .bucket(s3Properties.getBucket())
            .key(key)
            .contentType(contentType)
            .build();

    try {
      s3Client.putObject(
          putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
    } catch (Exception e) {
      log.error("[S3Service] 파일 업로드 실패 | key: {}, error: {}", key, e.getMessage());
      throw new RuntimeException("S3 파일 업로드 실패", e);
    }

    String url = publicUrlPrefix() + key;

    log.info("[S3Service] 파일 업로드 완료 | key: {}", key);
    return url;
  }

  /** S3에서 파일을 삭제한다. URL에서 키를 추출해 삭제 요청을 보낸다. */
  public void delete(String fileUrl) {
    String key = extractKeyFromUrl(fileUrl);

    DeleteObjectRequest deleteRequest =
        DeleteObjectRequest.builder().bucket(s3Properties.getBucket()).key(key).build();

    try {
      s3Client.deleteObject(deleteRequest);
      log.info("[S3Service] 파일 삭제 완료 | key: {}", key);
    } catch (Exception e) {
      log.error("[S3Service] 파일 삭제 실패 | key: {}, error: {}", key, e.getMessage());
      throw new RuntimeException("S3 파일 삭제 실패", e);
    }
  }

  /**
   * S3 URL에서 오브젝트 키를 추출한다.
   *
   * <p>현재 설정의 공개 URL prefix(publicUrlBase 또는 AWS 가상호스트 형식)와 AWS 기본 형식을 둘 다 허용한다. 설정을
   * 바꾸기(로컬 MinIO ↔ AWS) 전에 저장된 URL 도 삭제할 수 있어야 하기 때문이다.
   *
   * <p>예: https://bucket.s3.region.amazonaws.com/voice-samples/file.mp3 → voice-samples/file.mp3
   */
  private String extractKeyFromUrl(String fileUrl) {
    String configuredPrefix = publicUrlPrefix();
    if (fileUrl.startsWith(configuredPrefix)) {
      return fileUrl.substring(configuredPrefix.length());
    }
    String awsPrefix = awsUrlPrefix();
    if (fileUrl.startsWith(awsPrefix)) {
      return fileUrl.substring(awsPrefix.length());
    }
    throw new IllegalArgumentException("유효하지 않은 S3 URL: " + fileUrl);
  }

  /**
   * 업로드 파일의 공개 URL prefix를 반환한다 (키 앞까지, trailing slash 포함).
   *
   * <p>publicUrlBase가 설정돼 있으면 (MinIO 등 path-style 스토리지) 그것을 사용하고, 없으면 AWS 가상호스트 URL 형식을 사용한다.
   */
  private String publicUrlPrefix() {
    String base = s3Properties.getPublicUrlBase();
    if (base != null && !base.isBlank()) {
      return base.endsWith("/") ? base : base + "/";
    }
    return awsUrlPrefix();
  }

  /** AWS 가상호스트 형식의 URL prefix를 반환한다. */
  private String awsUrlPrefix() {
    return String.format(
        "https://%s.s3.%s.amazonaws.com/", s3Properties.getBucket(), s3Properties.getRegion());
  }
}
