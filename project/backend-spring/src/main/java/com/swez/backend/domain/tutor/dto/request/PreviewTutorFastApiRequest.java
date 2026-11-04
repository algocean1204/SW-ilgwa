/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.tutor.dto.request;

/**
 * FastAPI 미리보기 호출용 내부 요청 DTO. 클라이언트 요청(PreviewTutorRequest)에 서버가 보관 중인 튜터 음성 샘플
 * URL 을 덧붙여 전달한다.
 *
 * <p>FastAPI는 voiceSampleUrl이 있으면 보이스클론(qwen3-tts-modal) 경로를, 없으면 기본 TTS(gemini-tts) 경로를 사용해 미리보기
 * 음성을 합성한다. 클라이언트에 노출되는 DTO가 아니므로 Swagger 스키마는 붙이지 않는다.
 */
public record PreviewTutorFastApiRequest(
    Integer toneSlider,
    Integer speedSlider,
    Integer depthSlider,
    Integer questionStyleSlider,
    Boolean useEmoji,
    Boolean useFormalSpeech,
    String sampleQuestion,
    String voiceSampleUrl) {

  /** 클라이언트 요청 + 튜터의 저장된 음성 샘플 URL로 FastAPI 요청을 구성한다. */
  public static PreviewTutorFastApiRequest of(PreviewTutorRequest request, String voiceSampleUrl) {
    return new PreviewTutorFastApiRequest(
        request.toneSlider(),
        request.speedSlider(),
        request.depthSlider(),
        request.questionStyleSlider(),
        request.useEmoji(),
        request.useFormalSpeech(),
        request.sampleQuestion(),
        voiceSampleUrl);
  }
}
