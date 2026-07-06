namespace Pythontopia.Core.Dto;

/// <summary>
/// GET /api/learner/insights — a reflective insight about the learner, with traceable
/// <see cref="EvidenceEventIds"/>. Student-only, current user.
/// </summary>
public sealed record LearnerInsightDto(
    long Id,
    string? Text,
    string? DimensionKey,
    IReadOnlyList<string>? Tags,
    IReadOnlyList<long>? EvidenceEventIds,
    double Confidence,
    string? Source,
    string? CreatedAt);

/// <summary>
/// GET /api/learner/memory — one "agent notebook" memory item with recency / importance /
/// relevance score decomposition. Student-only, current user.
/// </summary>
public sealed record MemoryItemDto(
    long EventId,
    string? EventType,
    string? SummaryZh,
    IReadOnlyList<string>? KcCodes,
    string? CreatedAt,
    int Importance,
    double RecencyScore,
    double ImportanceScore,
    double RelevanceScore,
    double TotalScore);
