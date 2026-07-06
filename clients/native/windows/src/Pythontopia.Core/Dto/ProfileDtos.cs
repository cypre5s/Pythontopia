namespace Pythontopia.Core.Dto;

/// <summary>
/// GET /api/learner/profile (204 No Content when no profile exists yet).
/// Only the scalar fields shared by every backend revision are modelled; the
/// 8 nested dimension objects are intentionally omitted because the dedicated
/// /dimensions endpoint returns the authoritative, display-ready snapshot.
/// </summary>
public sealed record LearnerProfileDto(
    long? ProfileId,
    long? UserId,
    long? PackId,
    string? Major,
    string? NarrativeSummary);

/// <summary>
/// One of the 8 learner-profile dimensions (GET /api/learner/profile/dimensions).
/// <see cref="SelfEditable"/> marks the 4 subjective dimensions a student may self-assess.
/// </summary>
public sealed record ProfileDimensionDto(
    string Key,
    string Label,
    double Score,
    double Confidence,
    int EvidenceCount,
    bool SelfEditable,
    IReadOnlyList<string>? Tags);

/// <summary>A single knowledge-component mastery score (GET /api/learner/mastery).</summary>
public sealed record MasterySnapshotDto(
    string KcCode,
    decimal MasteryScore,
    decimal? Confidence,
    string? LastEventAt,
    string? LatestSource);

/// <summary>PUT /api/learner/profile/dimensions/{key} body. ScoreDelta is clamped to [-1, 1].</summary>
public sealed record DimensionSelfUpdateRequest(double? ScoreDelta);
