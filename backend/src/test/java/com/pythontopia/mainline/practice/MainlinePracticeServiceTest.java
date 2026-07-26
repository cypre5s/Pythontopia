package com.pythontopia.mainline.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.error.ResourceNotFoundException;
import com.pythontopia.mainline.MainlineStageCompletionPort;
import com.pythontopia.mainline.MainlineStageRepository;
import com.pythontopia.mainline.domain.ChapterStar;
import com.pythontopia.mainline.domain.MainlineStage;
import com.pythontopia.mainline.practice.dto.PracticeAnswerResultDto;
import com.pythontopia.mainline.practice.dto.StagePracticeQuestionDto;
import com.pythontopia.mainline.star.ChapterStarRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;

/**
 * 「练-客观题」服务单测（纯 Mockito，无 Spring 上下文 / 无真实库）：三题型判分对错、归一化、幂等、
 * stage 完成触发（含 completionPort 缺席降级）、端口聚合方法、归属与存在校验。
 */
@ExtendWith(MockitoExtension.class)
class MainlinePracticeServiceTest {

  private static final long USER = 7L;
  private static final long STAGE_ID = 100L;
  private static final int CH = 2;
  private static final int SI = 3;

  @Mock private MainlinePracticeRepository repository;
  @Mock private MainlineStageRepository stageRepository;
  @Mock private ChapterStarRepository starRepository;
  @Mock private ObjectProvider<MainlineStageCompletionPort> completionProvider;
  @Mock private MainlineStageCompletionPort completionPort;

  private MainlinePracticeService service;

  @BeforeEach
  void setUp() {
    service =
        new MainlinePracticeService(
            repository, stageRepository, starRepository, completionProvider);
    // 本测试的 stage 在第 2 章：默认前置章已达 1★，章解锁 gate 放行（解锁本身另有专测）。
    lenient()
        .when(starRepository.find(USER, CH - 1))
        .thenReturn(Optional.of(new ChapterStar(1L, USER, CH - 1, 1, 70, null, null)));
  }

  // --- 判分：选择 ----------------------------------------------------------

  @Test
  void choiceCorrectMarksProgressAndTriggersCompletion() {
    MainlinePracticeQuestion q = choice(11L, CH, SI, List.of("A 选项", "B 选项"), "A 选项");
    when(stageRepository.findById(STAGE_ID)).thenReturn(Optional.of(stage(STAGE_ID, CH, SI)));
    when(repository.findById(11L)).thenReturn(Optional.of(q));
    when(completionProvider.getIfAvailable()).thenReturn(completionPort);

    PracticeAnswerResultDto result = service.grade(USER, STAGE_ID, 11L, List.of(" A 选项 "));

    assertThat(result.questionId()).isEqualTo(11L);
    assertThat(result.correct()).isTrue();
    assertThat(result.correctAnswers()).containsExactly("A 选项");
    assertThat(result.explanation()).isEqualTo("讲解");
    verify(repository).appendCorrect(USER, STAGE_ID, 11L);
    verify(completionPort).recheckCompletion(USER, STAGE_ID);
  }

  /** 答错不得回传正确答案（否则一次试错即可复制回填，「练-客观题」门槛失效）；讲解仍给，供订正。 */
  @Test
  void choiceWrongWithholdsAnswersButKeepsExplanation() {
    MainlinePracticeQuestion q = choice(11L, CH, SI, List.of("A 选项", "B 选项"), "A 选项");
    when(stageRepository.findById(STAGE_ID)).thenReturn(Optional.of(stage(STAGE_ID, CH, SI)));
    when(repository.findById(11L)).thenReturn(Optional.of(q));

    PracticeAnswerResultDto result = service.grade(USER, STAGE_ID, 11L, List.of("B 选项"));

    assertThat(result.correct()).isFalse();
    assertThat(result.correctAnswers()).isEmpty();
    assertThat(result.explanation()).isEqualTo("讲解");
    verify(repository, never()).appendCorrect(anyLong(), anyLong(), anyLong());
    verify(completionProvider, never()).getIfAvailable();
  }

  /** 章解锁 gate：前一章 0★ 时连答题都不该收（服务端写路径必须自己 gate，不能只靠 overview 展示）。 */
  @Test
  void gradeRejectedWhenPreviousChapterHasNoStar() {
    when(stageRepository.findById(STAGE_ID)).thenReturn(Optional.of(stage(STAGE_ID, CH, SI)));
    when(starRepository.find(USER, CH - 1)).thenReturn(Optional.empty()); // 前置章 0★

    assertThatThrownBy(() -> service.grade(USER, STAGE_ID, 11L, List.of("A")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).status())
        .isEqualTo(HttpStatus.CONFLICT);

    verify(repository, never()).findById(anyLong());
    verify(repository, never()).appendCorrect(anyLong(), anyLong(), anyLong());
  }

  @Test
  void choiceEmptySubmissionIsWrong() {
    MainlinePracticeQuestion q = choice(11L, CH, SI, List.of("A", "B"), "A");
    when(stageRepository.findById(STAGE_ID)).thenReturn(Optional.of(stage(STAGE_ID, CH, SI)));
    when(repository.findById(11L)).thenReturn(Optional.of(q));

    assertThat(service.grade(USER, STAGE_ID, 11L, List.of()).correct()).isFalse();
    assertThat(service.grade(USER, STAGE_ID, 11L, null).correct()).isFalse();
    verify(repository, never()).appendCorrect(anyLong(), anyLong(), anyLong());
  }

  // --- 判分：判断 ----------------------------------------------------------

  @Test
  void judgeCorrectIgnoresWhitespace() {
    MainlinePracticeQuestion q = judge(12L, CH, SI, "正确");
    when(stageRepository.findById(STAGE_ID)).thenReturn(Optional.of(stage(STAGE_ID, CH, SI)));
    when(repository.findById(12L)).thenReturn(Optional.of(q));
    when(completionProvider.getIfAvailable()).thenReturn(completionPort);

    assertThat(service.grade(USER, STAGE_ID, 12L, List.of("  正 确 ")).correct()).isTrue();
    verify(repository).appendCorrect(USER, STAGE_ID, 12L);
  }

  @Test
  void judgeWrong() {
    MainlinePracticeQuestion q = judge(12L, CH, SI, "正确");
    when(stageRepository.findById(STAGE_ID)).thenReturn(Optional.of(stage(STAGE_ID, CH, SI)));
    when(repository.findById(12L)).thenReturn(Optional.of(q));

    assertThat(service.grade(USER, STAGE_ID, 12L, List.of("错误")).correct()).isFalse();
    verify(repository, never()).appendCorrect(anyLong(), anyLong(), anyLong());
  }

  // --- 判分：填空 ----------------------------------------------------------

  @Test
  void fillCorrectNormalizesQuotesFullwidthSpaceAndCase() {
    MainlinePracticeQuestion q = fill(13L, CH, SI, List.of("print", "True"));
    when(stageRepository.findById(STAGE_ID)).thenReturn(Optional.of(stage(STAGE_ID, CH, SI)));
    when(repository.findById(13L)).thenReturn(Optional.of(q));
    when(completionProvider.getIfAvailable()).thenReturn(completionPort);

    // 第 1 空：全角空格 + 弯引号包裹；第 2 空：全角字母 + 大小写差异。
    List<String> submitted = List.of("\u3000\u201Cprint\u201D\u3000", "\uFF54\uFF52\uFF55\uFF45");
    assertThat(service.grade(USER, STAGE_ID, 13L, submitted).correct()).isTrue();
    verify(repository).appendCorrect(USER, STAGE_ID, 13L);
  }

  @Test
  void fillWrongOnBlankCountMismatch() {
    MainlinePracticeQuestion q = fill(13L, CH, SI, List.of("a", "b"));
    when(stageRepository.findById(STAGE_ID)).thenReturn(Optional.of(stage(STAGE_ID, CH, SI)));
    when(repository.findById(13L)).thenReturn(Optional.of(q));

    assertThat(service.grade(USER, STAGE_ID, 13L, List.of("a")).correct()).isFalse();
    verify(repository, never()).appendCorrect(anyLong(), anyLong(), anyLong());
  }

  @Test
  void fillWrongOnOneBlankValue() {
    MainlinePracticeQuestion q = fill(13L, CH, SI, List.of("a", "b"));
    when(stageRepository.findById(STAGE_ID)).thenReturn(Optional.of(stage(STAGE_ID, CH, SI)));
    when(repository.findById(13L)).thenReturn(Optional.of(q));

    assertThat(service.grade(USER, STAGE_ID, 13L, List.of("a", "c")).correct()).isFalse();
    verify(repository, never()).appendCorrect(anyLong(), anyLong(), anyLong());
  }

  // --- 幂等 ----------------------------------------------------------------

  @Test
  void repeatedCorrectAnswerRecordsIdempotentAppendEachTime() {
    MainlinePracticeQuestion q = choice(11L, CH, SI, List.of("A", "B"), "A");
    when(stageRepository.findById(STAGE_ID)).thenReturn(Optional.of(stage(STAGE_ID, CH, SI)));
    when(repository.findById(11L)).thenReturn(Optional.of(q));
    when(completionProvider.getIfAvailable()).thenReturn(completionPort);

    assertThat(service.grade(USER, STAGE_ID, 11L, List.of("A")).correct()).isTrue();
    assertThat(service.grade(USER, STAGE_ID, 11L, List.of("A")).correct()).isTrue();

    // 服务每次答对都调并集 UPSERT（DB 层 DISTINCT 去重，不重复计）。
    verify(repository, times(2)).appendCorrect(USER, STAGE_ID, 11L);
    verify(completionPort, times(2)).recheckCompletion(USER, STAGE_ID);
  }

  @Test
  void questionsCompleteDedupsAndIgnoresExtraCorrectIds() {
    when(repository.findByStage(CH, SI))
        .thenReturn(List.of(choice(1L, CH, SI, List.of("A"), "A"), choice(2L, CH, SI, List.of("A"), "A")));
    when(repository.findCorrectQuestionIds(USER, STAGE_ID)).thenReturn(List.of(1L, 2L, 2L, 99L));

    assertThat(service.questionsComplete(USER, STAGE_ID, CH, SI)).isTrue();
  }

  // --- completionPort 缺席降级 --------------------------------------------

  @Test
  void correctButCompletionPortAbsentStillMarksAndSkips() {
    MainlinePracticeQuestion q = choice(11L, CH, SI, List.of("A", "B"), "A");
    when(stageRepository.findById(STAGE_ID)).thenReturn(Optional.of(stage(STAGE_ID, CH, SI)));
    when(repository.findById(11L)).thenReturn(Optional.of(q));
    when(completionProvider.getIfAvailable()).thenReturn(null); // bean 未就绪

    assertThat(service.grade(USER, STAGE_ID, 11L, List.of("A")).correct()).isTrue();
    verify(repository).appendCorrect(USER, STAGE_ID, 11L);
    verify(completionPort, never()).recheckCompletion(anyLong(), anyLong());
  }

  // --- 归属 / 存在校验 ----------------------------------------------------

  @Test
  void gradeUnknownStageThrowsNotFound() {
    when(stageRepository.findById(STAGE_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.grade(USER, STAGE_ID, 11L, List.of("A")))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void gradeUnknownQuestionThrowsNotFound() {
    when(stageRepository.findById(STAGE_ID)).thenReturn(Optional.of(stage(STAGE_ID, CH, SI)));
    when(repository.findById(11L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.grade(USER, STAGE_ID, 11L, List.of("A")))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void gradeQuestionNotInStageThrowsBadRequest() {
    when(stageRepository.findById(STAGE_ID)).thenReturn(Optional.of(stage(STAGE_ID, CH, SI)));
    // 题目属于同章下一小节，不属于本 stage。
    when(repository.findById(11L))
        .thenReturn(Optional.of(choice(11L, CH, SI + 1, List.of("A"), "A")));

    assertThatThrownBy(() -> service.grade(USER, STAGE_ID, 11L, List.of("A")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).status())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    verify(repository, never()).appendCorrect(anyLong(), anyLong(), anyLong());
  }

  // --- 端口聚合方法 -------------------------------------------------------

  @Test
  void questionsForStageStripsAnswers() {
    when(repository.findByStage(CH, SI))
        .thenReturn(List.of(choice(1L, CH, SI, List.of("A", "B"), "A")));

    List<StagePracticeQuestionDto> dtos = service.questionsForStage(CH, SI);

    assertThat(dtos).hasSize(1);
    StagePracticeQuestionDto dto = dtos.get(0);
    assertThat(dto.id()).isEqualTo(1L);
    assertThat(dto.type()).isEqualTo(PracticeQuestionType.CHOICE);
    assertThat(dto.options()).containsExactly("A", "B");
    assertThat(dto.blankCount()).isZero();
    // DTO 不暴露答案字段（record 无 answers 访问器，编译期即保证不泄题）。
  }

  @Test
  void requiredQuestionCountReflectsStageQuestions() {
    when(repository.findByStage(CH, SI))
        .thenReturn(
            List.of(choice(1L, CH, SI, List.of("A"), "A"), choice(2L, CH, SI, List.of("A"), "A")));
    assertThat(service.requiredQuestionCount(CH, SI)).isEqualTo(2);
  }

  @Test
  void noQuestionsMeansCompleteAndZeroRequired() {
    when(repository.findByStage(CH, SI)).thenReturn(List.of());
    assertThat(service.questionsComplete(USER, STAGE_ID, CH, SI)).isTrue();
    assertThat(service.requiredQuestionCount(CH, SI)).isZero();
    verify(repository, never()).findCorrectQuestionIds(anyLong(), anyLong());
  }

  @Test
  void questionsCompleteFalseWhenAnyUnanswered() {
    when(repository.findByStage(CH, SI))
        .thenReturn(
            List.of(choice(1L, CH, SI, List.of("A"), "A"), choice(2L, CH, SI, List.of("A"), "A")));
    when(repository.findCorrectQuestionIds(USER, STAGE_ID)).thenReturn(List.of(1L));
    assertThat(service.questionsComplete(USER, STAGE_ID, CH, SI)).isFalse();
  }

  @Test
  void correctQuestionIdsDelegatesToRepository() {
    when(repository.findCorrectQuestionIds(USER, STAGE_ID)).thenReturn(List.of(1L, 2L));
    assertThat(service.correctQuestionIds(USER, STAGE_ID)).containsExactly(1L, 2L);
  }

  // --- fixtures ------------------------------------------------------------

  private static MainlineStage stage(long id, int chapter, int stageIndex) {
    return new MainlineStage(
        id, chapter, stageIndex, "标题", List.of(), "关键词", List.of(), List.of(), 240, 0, 2);
  }

  private static MainlinePracticeQuestion choice(
      long id, int chapter, int stageIndex, List<String> options, String answer) {
    return new MainlinePracticeQuestion(
        id,
        chapter,
        stageIndex,
        1,
        "PY43-KC-001",
        PracticeQuestionType.CHOICE,
        "题干",
        options,
        List.of(answer),
        0,
        "讲解",
        "通识题（非课件原题）");
  }

  private static MainlinePracticeQuestion judge(
      long id, int chapter, int stageIndex, String answer) {
    return new MainlinePracticeQuestion(
        id,
        chapter,
        stageIndex,
        1,
        "PY43-KC-001",
        PracticeQuestionType.JUDGE,
        "题干",
        List.of(),
        List.of(answer),
        0,
        "讲解",
        "通识题（非课件原题）");
  }

  private static MainlinePracticeQuestion fill(
      long id, int chapter, int stageIndex, List<String> answers) {
    return new MainlinePracticeQuestion(
        id,
        chapter,
        stageIndex,
        1,
        "PY43-KC-001",
        PracticeQuestionType.FILL,
        "题干",
        List.of(),
        answers,
        answers.size(),
        "讲解",
        "通识题（非课件原题）");
  }
}
