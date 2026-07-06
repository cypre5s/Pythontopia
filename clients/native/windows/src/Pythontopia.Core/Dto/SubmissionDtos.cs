using System.Text.Json;

namespace Pythontopia.Core.Dto;

/// <summary>
/// POST /api/submissions body. <see cref="Source"/> defaults to "TRAINING" (one of
/// TRAINING / PATH_QUEST / BUG_MINE_RETRY / CLASSROOM). Code is capped at 8192 chars by the server.
/// </summary>
public sealed record SubmissionCreateRequest(
    long ProblemId,
    string Code,
    string Source = "TRAINING",
    long? QuestId = null);

/// <summary>
/// A submission record (POST /api/submissions returns id+status; GET returns the full row).
/// <see cref="Status"/> is one of PENDING / RUNNING / PASSED / FAILED / ERROR.
/// </summary>
public sealed record SubmissionDto(
    long Id,
    long? UserId,
    long? ProblemId,
    long? QuestId,
    string? Source,
    string? Language,
    string? Code,
    DateTimeOffset? SubmittedAt,
    string? Status,
    int? RuntimeMs,
    int? MemoryKb);

/// <summary>
/// Judge outcome. <see cref="Verdict"/> is one of AC / WA / RE / TLE / CE / MLE / SYSTEM_ERROR.
/// </summary>
public sealed record JudgeResultDto(
    long? Id,
    long? SubmissionId,
    string? Verdict,
    int PassedCases,
    int TotalCases,
    string? FailureReason,
    JsonElement? Trace,
    DateTimeOffset? CreatedAt);

/// <summary>GET /api/submissions/{id} — submission plus its judge result (null until judged).</summary>
public sealed record SubmissionDetailDto(SubmissionDto Submission, JudgeResultDto? JudgeResult);
