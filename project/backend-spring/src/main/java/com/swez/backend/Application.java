/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** SW-EZ 백엔드 스프링 애플리케이션 진입점. */
@SpringBootApplication
@EnableAsync // AssignmentGradingTriggerService의 @Async 채점 요청 처리에 필요하다
@EnableScheduling // CurriculumTimeoutScheduler가 오래 멈춘 CURRICULUM_GENERATING 코스를 실패 처리한다
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
