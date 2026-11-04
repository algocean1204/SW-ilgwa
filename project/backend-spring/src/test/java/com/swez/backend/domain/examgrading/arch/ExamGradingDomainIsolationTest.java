/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.arch;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

/**
 * ArchUnit으로 examgrading 도메인 격리 규칙을 검사한다.
 *
 * <p>examgrading·mockexam 패키지와 assignment·calendar·telegram 패키지는 서로 import하지 않는다. 이를 어기면 두 도메인
 * 그룹이 순환 의존으로 얽힌다.
 *
 * <p>검사하는 쌍 (각각 양방향 import 금지):
 *
 * <ul>
 *   <li>examgrading ↔ assignment
 *   <li>examgrading ↔ calendar
 *   <li>examgrading ↔ telegram
 *   <li>mockexam ↔ assignment
 *   <li>mockexam ↔ calendar
 *   <li>mockexam ↔ telegram
 * </ul>
 */
class ExamGradingDomainIsolationTest {

  // 도메인 패키지 전체를 한 번만 로드해 재사용한다
  private static JavaClasses allClasses;

  @BeforeAll
  static void loadClasses() {
    allClasses = new ClassFileImporter().importPackages("com.swez.backend.domain");
  }

  // examgrading ↔ assignment (양방향 import 금지)

  @Test
  @DisplayName("examgrading 패키지는 assignment 패키지를 import해서는 안 된다")
  void examgradingMustNotDependOnAssignment() {
    ArchRule rule =
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..examgrading..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..assignment..")
            .because("examgrading 도메인은 assignment 도메인과 독립적이어야 한다");
    rule.check(allClasses);
  }

  @Test
  @DisplayName("assignment 패키지는 examgrading 패키지를 import해서는 안 된다")
  void assignmentMustNotDependOnExamgrading() {
    ArchRule rule =
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..assignment..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..examgrading..")
            .because("assignment 도메인은 examgrading 도메인과 독립적이어야 한다");
    rule.check(allClasses);
  }

  // examgrading ↔ calendar (양방향 import 금지)

  @Test
  @DisplayName("examgrading 패키지는 calendar 패키지를 import해서는 안 된다")
  void examgradingMustNotDependOnCalendar() {
    ArchRule rule =
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..examgrading..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..calendar..")
            .because("examgrading 도메인은 calendar 도메인과 독립적이어야 한다");
    rule.check(allClasses);
  }

  @Test
  @DisplayName("calendar 패키지는 examgrading 패키지를 import해서는 안 된다")
  void calendarMustNotDependOnExamgrading() {
    ArchRule rule =
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..calendar..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..examgrading..")
            .because("calendar 도메인은 examgrading 도메인과 독립적이어야 한다");
    rule.check(allClasses);
  }

  // examgrading ↔ telegram (양방향 import 금지)

  @Test
  @DisplayName("examgrading 패키지는 telegram 패키지를 import해서는 안 된다")
  void examgradingMustNotDependOnTelegram() {
    ArchRule rule =
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..examgrading..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..telegram..")
            .because("examgrading 도메인은 telegram 도메인과 독립적이어야 한다");
    rule.check(allClasses);
  }

  @Test
  @DisplayName("telegram 패키지는 examgrading 패키지를 import해서는 안 된다")
  void telegramMustNotDependOnExamgrading() {
    // telegram 패키지가 없어도 나중에 생기면 규칙이 적용되게 allowEmptyShould를 켠다
    ArchRule rule =
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..telegram..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..examgrading..")
            .because("telegram 도메인은 examgrading 도메인과 독립적이어야 한다")
            .allowEmptyShould(true);
    rule.check(allClasses);
  }

  // mockexam ↔ assignment (양방향 import 금지)

  @Test
  @DisplayName("mockexam 패키지는 assignment 패키지를 import해서는 안 된다")
  void mockexamMustNotDependOnAssignment() {
    ArchRule rule =
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..mockexam..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..assignment..")
            .because("mockexam 도메인은 assignment 도메인과 독립적이어야 한다");
    rule.check(allClasses);
  }

  @Test
  @DisplayName("assignment 패키지는 mockexam 패키지를 import해서는 안 된다")
  void assignmentMustNotDependOnMockexam() {
    ArchRule rule =
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..assignment..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..mockexam..")
            .because("assignment 도메인은 mockexam 도메인과 독립적이어야 한다");
    rule.check(allClasses);
  }

  // mockexam ↔ calendar (양방향 import 금지)

  @Test
  @DisplayName("mockexam 패키지는 calendar 패키지를 import해서는 안 된다")
  void mockexamMustNotDependOnCalendar() {
    ArchRule rule =
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..mockexam..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..calendar..")
            .because("mockexam 도메인은 calendar 도메인과 독립적이어야 한다");
    rule.check(allClasses);
  }

  @Test
  @DisplayName("calendar 패키지는 mockexam 패키지를 import해서는 안 된다")
  void calendarMustNotDependOnMockexam() {
    ArchRule rule =
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..calendar..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..mockexam..")
            .because("calendar 도메인은 mockexam 도메인과 독립적이어야 한다");
    rule.check(allClasses);
  }

  // mockexam ↔ telegram (양방향 import 금지)

  @Test
  @DisplayName("mockexam 패키지는 telegram 패키지를 import해서는 안 된다")
  void mockexamMustNotDependOnTelegram() {
    ArchRule rule =
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..mockexam..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..telegram..")
            .because("mockexam 도메인은 telegram 도메인과 독립적이어야 한다");
    rule.check(allClasses);
  }

  @Test
  @DisplayName("telegram 패키지는 mockexam 패키지를 import해서는 안 된다")
  void telegramMustNotDependOnMockexam() {
    // telegram 패키지가 없어도 나중에 생기면 규칙이 적용되게 allowEmptyShould를 켠다
    ArchRule rule =
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..telegram..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..mockexam..")
            .because("telegram 도메인은 mockexam 도메인과 독립적이어야 한다")
            .allowEmptyShould(true);
    rule.check(allClasses);
  }
}
