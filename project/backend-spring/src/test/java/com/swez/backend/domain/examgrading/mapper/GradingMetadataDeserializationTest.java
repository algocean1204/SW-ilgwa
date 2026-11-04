/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.domain.examgrading.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swez.backend.domain.examgrading.mapper.ExamGradingPersistenceMapper.GradingMetadata;

/**
 * GradingMetadata의 @JsonProperty 누락을 잡는 회귀 테스트.
 *
 * <p>DB에는 snake_case로 저장된 JSON(ordering_items, blank_positions 등)이 GradingMetadata record로 역직렬화될 때
 * 각 필드에 @JsonProperty가 붙어 있어야 non-null 값을 얻는다. 이 테스트가 실패하면 @JsonProperty가 빠진 것이다.
 */
@DisplayName("GradingMetadata snake_case JSON 역직렬화 단위 테스트")
class GradingMetadataDeserializationTest {

  private ObjectMapper objectMapper;
  private ExamGradingPersistenceMapper mapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    mapper = new ExamGradingPersistenceMapper(objectMapper);
  }

  // 순서형(ordering) 문항 역직렬화

  @Nested
  @DisplayName("순서형(ordering) 문항 grading_metadata_json")
  class OrderingQuestion {

    @Test
    @DisplayName("ordering_items 필드가 non-null 리스트로 역직렬화된다")
    void orderingItems_deserializedAsNonNullList() {
      // given: DB에 저장된 snake_case JSON 형식 재현
      String json =
          """
          {
            "ordering_items": ["1단계: 요구사항 분석", "2단계: 설계", "3단계: 구현", "4단계: 테스트"],
            "correct_ordering": ["1단계: 요구사항 분석", "2단계: 설계", "3단계: 구현", "4단계: 테스트"],
            "blank_positions": [],
            "blank_answers": [],
            "matching_pairs": []
          }
          """;

      // when
      GradingMetadata meta = mapper.parseGradingMetadataForPublic(json);

      // then: @JsonProperty("ordering_items") 가 있어야 non-null
      assertThat(meta.orderingItems())
          .as("ordering_items 필드는 non-null 리스트이어야 한다")
          .isNotNull()
          .hasSize(4)
          .contains("1단계: 요구사항 분석", "2단계: 설계");

      assertThat(meta.correctOrdering())
          .as("correct_ordering 필드는 non-null 리스트이어야 한다")
          .isNotNull()
          .hasSize(4);
    }
  }

  // 빈칸형(fill-blank) 문항 역직렬화

  @Nested
  @DisplayName("빈칸형(fill-blank) 문항 grading_metadata_json")
  class FillBlankQuestion {

    @Test
    @DisplayName("blank_positions 필드가 non-null 리스트로 역직렬화된다")
    void blankPositions_deserializedAsNonNullList() {
      // given: DB에 저장된 snake_case JSON 형식 재현
      String json =
          """
          {
            "ordering_items": [],
            "correct_ordering": [],
            "blank_positions": [3, 7],
            "blank_answers": ["CPU", "프로세스"],
            "matching_pairs": []
          }
          """;

      // when
      GradingMetadata meta = mapper.parseGradingMetadataForPublic(json);

      // then: @JsonProperty("blank_positions") 가 있어야 non-null
      assertThat(meta.blankPositions())
          .as("blank_positions 필드는 non-null 리스트이어야 한다")
          .isNotNull()
          .containsExactly(3, 7);

      assertThat(meta.blankAnswers())
          .as("blank_answers 필드는 non-null 리스트이어야 한다")
          .isNotNull()
          .containsExactly("CPU", "프로세스");
    }
  }

  // 연결형(matching) 문항 역직렬화

  @Nested
  @DisplayName("연결형(matching) 문항 grading_metadata_json")
  class MatchingQuestion {

    @Test
    @DisplayName("matching_pairs 필드가 non-null 리스트로 역직렬화된다")
    void matchingPairs_deserializedAsNonNullList() {
      // given: DB에 저장된 snake_case JSON 형식 재현
      String json =
          """
          {
            "matching_pairs": [
              {"left": "TCP", "right": "연결 지향"},
              {"left": "UDP", "right": "비연결형"}
            ],
            "ordering_items": [],
            "correct_ordering": [],
            "blank_positions": [],
            "blank_answers": []
          }
          """;

      // when
      GradingMetadata meta = mapper.parseGradingMetadataForPublic(json);

      // then: @JsonProperty("matching_pairs") 가 있어야 non-null
      assertThat(meta.matchingPairs())
          .as("matching_pairs 필드는 non-null 리스트이어야 한다")
          .isNotNull()
          .hasSize(2);

      assertThat(meta.matchingPairs().get(0).left()).isEqualTo("TCP");
      assertThat(meta.matchingPairs().get(0).right()).isEqualTo("연결 지향");
    }
  }

  // null·빈 JSON 방어

  @Nested
  @DisplayName("null·빈 JSON 방어 케이스")
  class EmptyAndNull {

    @Test
    @DisplayName("null JSON이면 empty 인스턴스를 반환한다")
    void nullJson_returnsEmptyInstance() {
      GradingMetadata meta = mapper.parseGradingMetadataForPublic(null);

      assertThat(meta.orderingItems()).isNotNull().isEmpty();
      assertThat(meta.blankPositions()).isNotNull().isEmpty();
      assertThat(meta.matchingPairs()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("blank JSON이면 empty 인스턴스를 반환한다")
    void blankJson_returnsEmptyInstance() {
      GradingMetadata meta = mapper.parseGradingMetadataForPublic("  ");

      assertThat(meta.orderingItems()).isNotNull().isEmpty();
      assertThat(meta.blankPositions()).isNotNull().isEmpty();
    }
  }
}
