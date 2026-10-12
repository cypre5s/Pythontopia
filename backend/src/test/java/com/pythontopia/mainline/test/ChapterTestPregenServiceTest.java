package com.pythontopia.mainline.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.mainline.MainlineProperties;
import com.pythontopia.mainline.domain.ChapterAssessmentKind;
import com.pythontopia.mainline.test.ChapterProblemBankRepository.CodingCase;
import com.pythontopia.mainline.test.ChapterProblemBankRepository.PreferredCodingQuestion;
import com.pythontopia.mainline.test.ChapterTestGraphClient.GenerateRequest;
import com.pythontopia.mainline.test.ChapterTestGraphClient.GeneratedPaper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChapterTestPregenServiceTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final ChapterTestRepository repository = mock(ChapterTestRepository.class);
  private final ChapterTestGraphClient graphClient = mock(ChapterTestGraphClient.class);
  private final ChapterProblemBankRepository problemBank =
      mock(ChapterProblemBankRepository.class);
  private final ChapterTestPersonalizationService personalization =
      mock(ChapterTestPersonalizationService.class);
  private final ChapterTestPregenService service =
      new ChapterTestPregenService(
          repository,
          graphClient,
          problemBank,
          personalization,
          new MainlineProperties(1, null, null, 0));

  @BeforeEach
  void defaultPersonalization() {
    when(personalization.build(anyLong(), any()))
        .thenReturn(
            new ChapterTestPersonalizationService.PersonalizationContext(
                mapper.createObjectNode(), mapper.createObjectNode()));
  }

  @Test
  void quizRequestsTenTenTenThreeAndPassesProblemBank() {
    ObjectNode profileBrief = mapper.createObjectNode().put("knowledge_level", 2);
    ObjectNode errorbookBrief = mapper.createObjectNode();
    errorbookBrief.putArray("frequent_error_kcs").add("PY43-KC-016");
    when(personalization.build(eq(7L), any()))
        .thenReturn(
            new ChapterTestPersonalizationService.PersonalizationContext(
                profileBrief, errorbookBrief));
    PreferredCodingQuestion bankQuestion =
        new PreferredCodingQuestion(
            476L,
            "圆面积计算",
            "读入半径并输出面积",
            List.of(new CodingCase("1", "3.1415")),
            List.of(new CodingCase("2", "12.5660")),
            "r=float(input())\nprint(f'{3.1415*r*r:.4f}')",
            "PY43-KC-016");
    when(problemBank.findByKcCodes(any())).thenReturn(List.of(bankQuestion));
    when(graphClient.generate(any()))
        .thenReturn(new GeneratedPaper(paper(10, 10, 10, 3), mapper.createObjectNode()));

    service.pregenerate(9L, 7L, 2, ChapterAssessmentKind.QUIZ, List.of());

    ArgumentCaptor<GenerateRequest> request = ArgumentCaptor.forClass(GenerateRequest.class);
    verify(graphClient).generate(request.capture());
    assertThat(request.getValue().counts())
        .isEqualTo(new GenerateRequest.Counts(10, 10, 10, 3));
    assertThat(request.getValue().preferredCodingQuestions()).containsExactly(bankQuestion);
    assertThat(request.getValue().profileBrief()).isSameAs(profileBrief);
    assertThat(request.getValue().errorbookBrief()).isSameAs(errorbookBrief);
    verify(repository).markReady(eq(9L), any(), any(), any());
  }

  @Test
  void finalRequestsTripleVolumeAndCarriesQuizExclusions() {
    when(problemBank.findByKcCodes(any())).thenReturn(List.of());
    when(graphClient.generate(any()))
        .thenReturn(new GeneratedPaper(paper(30, 30, 30, 9), mapper.createObjectNode()));

    service.pregenerate(
        10L, 7L, 2, ChapterAssessmentKind.FINAL, List.of("小测已经使用的题目"));

    ArgumentCaptor<GenerateRequest> request = ArgumentCaptor.forClass(GenerateRequest.class);
    verify(graphClient).generate(request.capture());
    assertThat(request.getValue().counts())
        .isEqualTo(new GenerateRequest.Counts(30, 30, 30, 9));
    assertThat(request.getValue().excludedQuestions())
        .containsExactly("小测已经使用的题目");
    verify(repository).markReady(eq(10L), any(), any(), any());
  }

  private ObjectNode paper(int choices, int judges, int fills, int coding) {
    ObjectNode paper = mapper.createObjectNode();
    ArrayNode items = paper.putArray("items");
    add(items, "CHOICE", choices);
    add(items, "JUDGE", judges);
    add(items, "FILL", fills);
    add(items, "CODING", coding);
    return paper;
  }

  private void add(ArrayNode items, String type, int count) {
    for (int i = 1; i <= count; i++) {
      ObjectNode item = items.addObject();
      item.put("qid", type.toLowerCase() + i);
      item.put("type", type);
      if ("CODING".equals(type)) {
        item.put("title", "编程题 " + i);
        item.put("statement_markdown", "完成程序 " + i);
      } else {
        item.put("stem", type + " 题目 " + i);
      }
    }
  }
}
