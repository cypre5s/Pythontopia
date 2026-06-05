using System.Text.Json;

namespace Pythontopia.Core.Dto;

/// <summary>
/// A learner quest (GET /api/quests, /api/quests/today and the start/complete/skip results).
/// Backend enums (source / nodeType / status) are surfaced as strings; freeform JSON
/// payloads are exposed as <see cref="JsonElement"/> so the UI can read them lazily.
/// </summary>
public sealed record QuestDto(
    long Id,
    long? UserId,
    string? Source,
    long? SourceRefId,
    string Title,
    string? Description,
    string? KcCode,
    string? NodeType,
    JsonElement? Payload,
    string? DueDate,
    int Priority,
    string Status,
    string? RequiredEvidenceType,
    decimal? RequiredMasteryDelta,
    IReadOnlyList<long>? TargetProblemIds,
    long? TargetResourceId,
    string? DeepLinkRoute,
    JsonElement? DeepLinkParams,
    DateTimeOffset? CreatedAt,
    DateTimeOffset? UpdatedAt);
