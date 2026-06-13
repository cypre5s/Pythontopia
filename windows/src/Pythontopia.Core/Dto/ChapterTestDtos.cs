using System.Text.Json;

namespace Pythontopia.Core.Dto;

/// <summary>POST .../test/prepare response — generation status (e.g. READY / GENERATING).</summary>
public sealed record TestPrepareResponse(string? Status);

/// <summary>
/// POST .../test/start response — the answer-stripped exam paper (questions only, with the
/// time limit / deadline). Submit answers to .../{testId}/submit.
/// </summary>
public sealed record ChapterTestPaperDto(
    long TestId,
    int Chapter,
    int AttemptNo,
    string? Status,
    int TimeLimitSec,
    DateTimeOffset? DeadlineAt,
    IReadOnlyList<ChapterTestQuestionViewDto> Items);

/// <summary>One answer-stripped exam question (type is CHOICE / JUDGE / FILL / CODE).</summary>
public sealed record ChapterTestQuestionViewDto(
    string? Qid,
    string? Type,
    int Score,
    string? Stem,
    string? Title,
    string? StatementMarkdown,
    IReadOnlyList<string>? Options,
    IReadOnlyList<JsonElement>? Samples);

/// <summary>POST .../{testId}/submit body — answers keyed by question id.</summary>
public sealed record ChapterTestSubmitRequest(IReadOnlyDictionary<string, string> Answers);

/// <summary>
/// GET /api/mainline/chapters/{chapter}/test/latest (and the submit response) — chapter test
/// outcome. Per-question detail (with correct answers + explanations) is only filled in terminal
/// states; an IN_PROGRESS latest leaves <see cref="Items"/> empty (no answer leakage).
/// </summary>
public sealed record ChapterTestResultDto(
    long TestId,
    int Chapter,
    int AttemptNo,
    string? Status,
    int? Score,
    int? MaxScore,
    bool Passed,
    int Stars,
    bool Overtime,
    DateTimeOffset? SubmittedAt,
    IReadOnlyList<ChapterTestQuestionResultDto> Items,
    ChapterTestIntegrityDto? Integrity);

/// <summary>逐题判分（终态才下发，含正确答案与解析）。</summary>
public sealed record ChapterTestQuestionResultDto(
    string? Qid,
    string? Type,
    bool Correct,
    int AwardedScore,
    int MaxScore,
    string? YourAnswer,
    string? CorrectAnswer,
    string? Explain,
    int? PassedCases,
    int? TotalCases);

/// <summary>诚信信号（粘贴拦截 / 失焦次数 / 超时）。</summary>
public sealed record ChapterTestIntegrityDto(int PasteBlocked, int BlurEvents, bool Overtime);
