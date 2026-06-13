using System.Text.Json;

namespace Pythontopia.Core.Dto;

/// <summary>
/// GET /api/mainline/overview — the 主线 (mainline) chapter/stage map with看/学/练 progress
/// and star totals. Read-only aggregate the desktop client renders as a learning map.
/// </summary>
public sealed record MainlineOverviewDto(
    int TotalStars,
    IReadOnlyList<MainlineChapterDto> Chapters);

/// <summary>
/// One chapter (章) in the mainline map. <see cref="StarBreakdown"/> is a freeform JSON object
/// exposed lazily; <see cref="TestStatus"/> mirrors the chapter test state.
/// </summary>
public sealed record MainlineChapterDto(
    int Chapter,
    string Title,
    bool Unlocked,
    int Stars,
    int? BestTestScore,
    IReadOnlyList<MainlineStageDto> Stages,
    string? TestStatus,
    JsonElement? StarBreakdown,
    MainlineRecoveryDto? Recovery);

/// <summary>One stage (关卡) with看 (video) / 学 (pages) / 练 (examples) progress counters.</summary>
public sealed record MainlineStageDto(
    long Id,
    int Index,
    string Title,
    int VideoWatchedSec,
    int RequiredVideoSec,
    int PagesRead,
    int RequiredPages,
    int ExamplesDone,
    int RequiredExamples,
    bool Completed);

/// <summary>补星进度（错题本 / 无尽模式）。</summary>
public sealed record MainlineRecoveryDto(bool ErrorbookDone, bool EndlessDone);

/// <summary>
/// GET /api/mainline/stages/{id} — full stage (关卡) detail: required看/学/练 thresholds, the
/// concrete videos / examples / practice questions, and the learner's progress ids.
/// </summary>
public sealed record StageDetailDto(
    long Id,
    int Chapter,
    string? ChapterTitle,
    int Index,
    string Title,
    IReadOnlyList<string>? KcCodes,
    string? VideoKeyword,
    IReadOnlyList<long>? CoursewarePageIds,
    IReadOnlyList<long>? ExampleProblemIds,
    int RequiredVideoSec,
    int RequiredPages,
    int RequiredExamples,
    int RequiredQuestions,
    IReadOnlyList<StageVideoDto> Videos,
    IReadOnlyList<StageExampleDto> Examples,
    IReadOnlyList<StagePracticeQuestionDto> Questions,
    int VideoWatchedSec,
    IReadOnlyList<long>? PagesReadIds,
    IReadOnlyList<long>? ExamplesDoneIds,
    IReadOnlyList<long>? QuestionsCorrectIds,
    bool Completed);

/// <summary>A video clip referenced by a stage.</summary>
public sealed record StageVideoDto(long Id, string? Title, int DurationSec);

/// <summary>A worked example referenced by a stage.</summary>
public sealed record StageExampleDto(long Id, string? DisplayNo, string? DisplayName, string? KcCode);

/// <summary>A practice question in a stage (type is CHOICE / JUDGE / FILL / CODE, surfaced as string).</summary>
public sealed record StagePracticeQuestionDto(
    long Id,
    string? KcCode,
    string? Type,
    int Ordinal,
    string? Stem,
    IReadOnlyList<string>? Options,
    int BlankCount);

/// <summary>
/// Updated stage progress returned by the看/学/练 progress endpoints
/// (POST .../stages/{id}/video-progress | page-read | pages-read | example-done).
/// </summary>
public sealed record StageProgressDto(
    long StageId,
    int Chapter,
    int StageIndex,
    int VideoWatchedSec,
    int RequiredVideoSec,
    bool VideoComplete,
    int PagesRead,
    int RequiredPages,
    bool PagesComplete,
    int ExamplesDone,
    int RequiredExamples,
    bool ExamplesComplete,
    bool Completed);

/// <summary>POST .../stages/{id}/video-progress body (deltaSec &gt;= 1).</summary>
public sealed record VideoProgressRequest(int DeltaSec);

/// <summary>POST .../stages/{id}/pages-read body.</summary>
public sealed record PagesReadRequest(IReadOnlyList<long> PageIds);

/// <summary>POST /api/mainline/endless/summary body — report an endless-run result.</summary>
public sealed record EndlessSummaryRequest(int Chapter, int Correct, int Total, int Streak);

/// <summary>Whether a star was awarded (endless summary / recovery responses).</summary>
public sealed record StarAwardResultDto(bool StarAwarded);
