/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.util;

import com.github.f4b6a3.ulid.UlidCreator;

/** 도메인별 ULID prefix 기반 ID 생성 유틸. DB INSERT 전 애플리케이션 레이어에서 호출한다. */
public class IdGenerator {

  private IdGenerator() {}

  /** users 테이블 PK:usr_{ULID 26자} */
  public static String user() {
    return "usr_" + UlidCreator.getUlid().toString();
  }

  /** oauth_account 테이블 PK:oau_{ULID 26자} */
  public static String oauthAccount() {
    return "oau_" + UlidCreator.getUlid().toString();
  }

  /** user_settings 테이블 PK:ust_{ULID 26자} */
  public static String userSettings() {
    return "ust_" + UlidCreator.getUlid().toString();
  }

  /** user_preferences 테이블 PK:prf_{ULID 26자} */
  public static String userPreferences() {
    return "prf_" + UlidCreator.getUlid().toString();
  }

  /** telegram_account 테이블 PK:tga_{ULID 26자} */
  public static String telegramAccount() {
    return "tga_" + UlidCreator.getUlid().toString();
  }

  /** course 테이블 PK:crs_{ULID 26자} */
  public static String course() {
    return "crs_" + UlidCreator.getUlid().toString();
  }

  /** chapter 테이블 PK:chp_{ULID 26자} */
  public static String chapter() {
    return "chp_" + UlidCreator.getUlid().toString();
  }

  /** note 테이블 PK:nte_{ULID 26자} */
  public static String note() {
    return "nte_" + UlidCreator.getUlid().toString();
  }

  /** bookmark 테이블 PK:bkm_{ULID 26자} */
  public static String bookmark() {
    return "bkm_" + UlidCreator.getUlid().toString();
  }

  /** calendar_event 테이블 PK:cev_{ULID 26자} */
  public static String calendarEvent() {
    return "cev_" + UlidCreator.getUlid().toString();
  }

  /** assignment 테이블 PK:asg_{ULID 26자} */
  public static String assignment() {
    return "asg_" + UlidCreator.getUlid().toString();
  }

  /** submission 테이블 PK:sbm_{ULID 26자} */
  public static String submission() {
    return "sbm_" + UlidCreator.getUlid().toString();
  }

  /** tutor 테이블 PK:tut_{ULID 26자} */
  public static String tutor() {
    return "tut_" + UlidCreator.getUlid().toString();
  }

  /** subscription 테이블 PK:sub_{ULID 26자} */
  public static String subscription() {
    return "sub_" + UlidCreator.getUlid().toString();
  }

  /** payment 테이블 PK:pay_{ULID 26자} */
  public static String payment() {
    return "pay_" + UlidCreator.getUlid().toString();
  }

  /** coin_wallet 테이블 PK:cwl_{ULID 26자} */
  public static String coinWallet() {
    return "cwl_" + UlidCreator.getUlid().toString();
  }

  /** coin_transaction 테이블 PK:ctx_{ULID 26자} */
  public static String coinTransaction() {
    return "ctx_" + UlidCreator.getUlid().toString();
  }

  /** chat_message 테이블 PK:cmg_{ULID 26자} */
  public static String chatMessage() {
    return "cmg_" + UlidCreator.getUlid().toString();
  }

  /** slide 테이블 PK:sld_{ULID 26자} */
  public static String slide() {
    return "sld_" + UlidCreator.getUlid().toString();
  }

  /** lesson_progress 테이블 PK:lpg_{ULID 26자} */
  public static String lessonProgress() {
    return "lpg_" + UlidCreator.getUlid().toString();
  }

  /** study_time_log 테이블 PK:stl_{ULID 26자} */
  public static String studyTimeLog() {
    return "stl_" + UlidCreator.getUlid().toString();
  }

  /** quiz 테이블 PK:qz_{ULID 26자} */
  public static String quiz() {
    return "qz_" + UlidCreator.getUlid().toString();
  }

  /** quiz_answer 테이블 PK:qa_{ULID 26자} */
  public static String quizAnswer() {
    return "qa_" + UlidCreator.getUlid().toString();
  }

  /** mock_exam 테이블 PK:mex_{ULID 26자} */
  public static String mockExam() {
    return "mex_" + UlidCreator.getUlid().toString();
  }

  /** mock_exam_question 테이블 PK:mq_{ULID 26자} */
  public static String mockExamQuestion() {
    return "mq_" + UlidCreator.getUlid().toString();
  }

  /** mock_exam_submission 테이블 PK:ms_{ULID 26자} */
  public static String mockExamSubmission() {
    return "ms_" + UlidCreator.getUlid().toString();
  }

  /** admin_telegram_recipient 테이블 PK:atr_{ULID 26자} */
  public static String adminTelegramRecipient() {
    return "atr_" + UlidCreator.getUlid().toString();
  }

  /** admin_alert_rule 테이블 PK:aar_{ULID 26자} */
  public static String adminAlertRule() {
    return "aar_" + UlidCreator.getUlid().toString();
  }

  /** exam_attempt 테이블 PK:eat_{ULID 26자} */
  public static String examAttempt() {
    return "eat_" + UlidCreator.getUlid().toString();
  }
}
