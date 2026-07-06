namespace Pythontopia.Core.Dto;

/// <summary>
/// GET /api/learner/path/active — the learner's active personalized path
/// (404 PATH_NOT_FOUND if none yet). Status is ACTIVE / ARCHIVED / DRAFT.
/// </summary>
public sealed record LearningPathDto(
    long? Id,
    long? UserId,
    long? PackId,
    int Version,
    string? Status,
    string? GoalSummary,
    DateTimeOffset? GeneratedAt,
    DateTimeOffset? NextReviewAt,
    long? SourceRunId,
    IReadOnlyList<LearningPathNodeDto> Nodes);

/// <summary>One node in the learning path (a per-day, ordered KC step with reasoning).</summary>
public sealed record LearningPathNodeDto(
    long? Id,
    long? PathId,
    int DayIndex,
    int OrderInDay,
    string? KcCode,
    string? NodeType,
    string? ReasonSummary,
    string? EvidenceSummary,
    string? SuggestedResourceType,
    long? SuggestedProblemId,
    long? SourceClassroomQuestId);

/// <summary>POST /api/learner/path/plan — (re)plan result with the new active path + run metadata.</summary>
public sealed record PlanPathResponse(
    LearningPathDto? Path,
    IReadOnlyList<long>? ArchivedPathIds,
    int GeneratedQuestCount,
    string? RunStatus,
    string? GroundingMessage);
