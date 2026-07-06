using System.Text.Json;

namespace Pythontopia.Core.Dto;

/// <summary>
/// POST /api/bug-mine/sessions body — open a mine session from an existing failed submission.
/// </summary>
public sealed record BugMineOpenRequest(long SubmissionId);

/// <summary>POST /api/bug-mine/echoes body — open a 遗忘回声 (memory-vein) session for a KC.</summary>
public sealed record BugMineEchoOpenRequest(string KcCode);

/// <summary>
/// POST /api/bug-mine/sessions/{id}/retry-submit and /variant-submit body. Code is capped at
/// 8192 chars by the server; both endpoints return a <see cref="SubmissionDto"/> to poll.
/// </summary>
public sealed record BugMineRetryRequest(string Code);

/// <summary>The variant problem attached to a mine session (L5 same-KC variant).</summary>
public sealed record BugMineVariantProblemDto(long? Id, string? Title);

/// <summary>
/// One of the 5 progressive depth layers of a mine session. <see cref="Unlocked"/> gates whether
/// the matching card may be revealed at the session's current <c>depthLayer</c>.
/// </summary>
public sealed record BugMineDepthStepDto(
    int Layer,
    string? Key,
    string? Title,
    string? Description,
    bool Unlocked);

/// <summary>
/// A reveal-gated mine card. <see cref="CardType"/> is one of COUNTER_EXAMPLE / VARIABLE_TRACE /
/// WRONG_ASSUMPTION / HINT_LAYER_1 / HINT_LAYER_2 / HINT_LAYER_3; <see cref="Payload"/> is the
/// freeform card body (revealed only after POST .../cards/{cardId}/reveal).
/// </summary>
public sealed record BugMineCardDto(
    long? Id,
    long? SessionId,
    string? CardType,
    JsonElement? Payload,
    bool Revealed,
    DateTimeOffset? CreatedAt);

/// <summary>
/// GET /api/bug-mine/errorbook item — an open (un-closed) mine session in the 错题矿洞 list.
/// <see cref="DecayRisk"/> = 1 − R (FSRS retrievability); <see cref="MinePriority"/> ranks entry order.
/// </summary>
public sealed record BugMineErrorBookItemDto(
    long? SessionId,
    long? SubmissionId,
    long? ProblemId,
    long? QuestId,
    string? KcCode,
    string? ProblemTitle,
    string? Status,
    int RetryCount,
    int DepthLayer,
    int ProfileDepthTarget,
    string? ProfileGuidance,
    decimal? MasteryScore,
    decimal? MinePriority,
    decimal? DecayRisk,
    string? RecommendationReason,
    IReadOnlyList<string>? MistakePatterns,
    bool PatternMatched,
    DateTimeOffset? OpenedAt,
    BugMineVariantProblemDto? VariantProblem,
    IReadOnlyList<BugMineDepthStepDto>? DepthSteps);

/// <summary>
/// GET /api/bug-mine/echoes item — a 遗忘回声 candidate KC recalled by the FSRS-lite engine
/// (only currently-due items). <see cref="DecayRisk"/> = 1 − R; <see cref="MinePriority"/> ranks them.
/// </summary>
public sealed record BugMineEchoCandidateDto(
    string KcCode,
    long? ProblemId,
    string? ProblemTitle,
    decimal? MasteryScore,
    decimal? Confidence,
    decimal? DecayRisk,
    decimal? MinePriority,
    string? RecommendationReason);

/// <summary>
/// GET /api/bug-mine/sessions/{id} (and the open/close/echo responses) — a full mine session with
/// its 5 depth steps, reveal-gated cards and the problem being debugged (<see cref="Problem"/>).
/// <see cref="Status"/> is one of OPEN / RESOLVED / ABANDONED.
/// </summary>
public sealed record BugMineSessionDto(
    long? Id,
    long? UserId,
    long? SubmissionId,
    long? ProblemId,
    long? QuestId,
    string? Status,
    int RetryCount,
    int DepthLayer,
    int ProfileDepthTarget,
    bool MasteryConfirmed,
    long? VariantSubmissionId,
    DateTimeOffset? OpenedAt,
    DateTimeOffset? ClosedAt,
    BugMineVariantProblemDto? VariantProblem,
    string? ProfileGuidance,
    IReadOnlyList<BugMineDepthStepDto>? DepthSteps,
    IReadOnlyList<BugMineCardDto>? Cards,
    PublicProblemDto? Problem);
