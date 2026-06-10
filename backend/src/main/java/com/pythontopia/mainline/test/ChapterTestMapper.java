package com.pythontopia.mainline.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.pythontopia.mainline.domain.ChapterTestRecord;
import com.pythontopia.mainline.dto.ChapterTestPaperDto;
import com.pythontopia.mainline.dto.ChapterTestPaperDto.QuestionView;
import com.pythontopia.mainline.dto.ChapterTestPaperDto.SampleView;
import com.pythontopia.mainline.dto.ChapterTestResultDto;
import com.pythontopia.mainline.dto.ChapterTestResultDto.IntegrityView;
import com.pythontopia.mainline.dto.ChapterTestResultDto.QuestionResultView;
import com.pythontopia.mainline.test.ChapterTestJudge.JudgeReport;
import com.pythontopia.mainline.test.ChapterTestJudge.QuestionJudgement;
import com.pythontopia.mainline.test.question.QuestionItem;
import com.pythontopia.mainline.test.question.QuestionPaper;
import com.pythontopia.mainline.star.StarFormula;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 章测内部模型 ↔ 对外 DTO 投影。<b>核心安全职责</b>：下发题面时剥离答案/解析/隐藏用例/证据页（§4.2）。
 */
@Component
public class ChapterTestMapper {

  /** 去答案题面：仅保留作答所需字段。 */
  public ChapterTestPaperDto toPaper(ChapterTestRecord record, QuestionPaper paper) {
    List<QuestionView> views = new ArrayList<>();
    for (QuestionItem item : paper.items()) {
      List<SampleView> samples =
          item.safeSamples().stream()
              .map(s -> new SampleView(s.input(), s.output()))
              .toList();
      views.add(
          new QuestionView(
              item.qid(),
              item.type() == null ? null : item.type().name(),
              item.score(),
              item.stem(),
              item.title(),
              item.statementMarkdown(),
              item.type() == com.pythontopia.mainline.test.question.QuestionType.CHOICE
                  ? item.safeOptions()
                  : List.of(),
              samples));
    }
    return new ChapterTestPaperDto(
        record.id(),
        record.chapter(),
        record.attemptNo(),
        record.status().name(),
        record.timeLimitSec(),
        record.deadlineAt(),
        views);
  }

  /** 提交后即时结果（从内存 JudgeReport 构建）。 */
  public ChapterTestResultDto toResult(
      ChapterTestRecord record, JudgeReport report, int stars, boolean overtime) {
    List<QuestionResultView> items =
        report.items().stream()
            .map(this::toResultView)
            .toList();
    Integer score = report.score();
    return new ChapterTestResultDto(
        record.id(),
        record.chapter(),
        record.attemptNo(),
        record.status().name(),
        score,
        report.maxScore(),
        score != null && score >= StarFormula.PASS_SCORE,
        stars,
        overtime,
        record.submittedAt(),
        items,
        integrityView(record.integrity()));
  }

  /**
   * latest 结果（从已落库的 detail 重建）。仅终态（SUBMITTED/EXPIRED）回填逐题；非终态返回空 items 防泄漏答案。
   */
  public ChapterTestResultDto fromStored(ChapterTestRecord record, int stars) {
    boolean terminal =
        record.status() == com.pythontopia.mainline.domain.ChapterTestStatus.SUBMITTED
            || record.status() == com.pythontopia.mainline.domain.ChapterTestStatus.EXPIRED;
    List<QuestionResultView> items = new ArrayList<>();
    JsonNode detail = record.detail();
    if (terminal && detail != null && detail.has("items") && detail.get("items").isArray()) {
      for (JsonNode node : detail.get("items")) {
        items.add(
            new QuestionResultView(
                node.path("qid").asText(""),
                node.path("type").asText(null),
                node.path("correct").asBoolean(false),
                node.path("awardedScore").asInt(0),
                node.path("maxScore").asInt(0),
                node.path("yourAnswer").asText(null),
                node.path("correctAnswer").asText(null),
                node.path("explain").asText(null),
                node.hasNonNull("passedCases") ? node.get("passedCases").asInt() : null,
                node.hasNonNull("totalCases") ? node.get("totalCases").asInt() : null));
      }
    }
    Integer score = record.score();
    Integer maxScore =
        detail != null && detail.has("maxScore") ? detail.get("maxScore").asInt() : null;
    boolean overtime = record.integrity() != null && record.integrity().path("overtime").asBoolean(false);
    return new ChapterTestResultDto(
        record.id(),
        record.chapter(),
        record.attemptNo(),
        record.status().name(),
        score,
        maxScore,
        score != null && score >= StarFormula.PASS_SCORE,
        stars,
        overtime,
        record.submittedAt(),
        items,
        integrityView(record.integrity()));
  }

  private QuestionResultView toResultView(QuestionJudgement j) {
    return new QuestionResultView(
        j.qid(),
        j.type() == null ? null : j.type().name(),
        j.correct(),
        j.awardedScore(),
        j.maxScore(),
        j.yourAnswer(),
        j.correctAnswer(),
        j.explain(),
        j.passedCases(),
        j.totalCases());
  }

  private IntegrityView integrityView(JsonNode integrity) {
    if (integrity == null) {
      return new IntegrityView(0, 0, false);
    }
    return new IntegrityView(
        integrity.path("paste_blocked").asInt(0),
        integrity.path("blur_events").asInt(0),
        integrity.path("overtime").asBoolean(false));
  }
}
