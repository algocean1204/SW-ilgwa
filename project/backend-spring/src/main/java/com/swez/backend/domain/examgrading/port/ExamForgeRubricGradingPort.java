/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.port;

import java.util.List;

import com.swez.backend.domain.examgrading.model.ExamAttemptSnapshot;
import com.swez.backend.domain.examgrading.model.QuestionGrade;
import com.swez.backend.domain.examgrading.model.SubmittedAnswer;

public interface ExamForgeRubricGradingPort {

  List<QuestionGrade> gradeRubricQuestions(
      ExamAttemptSnapshot snapshot, List<SubmittedAnswer> rubricAnswers);
}
