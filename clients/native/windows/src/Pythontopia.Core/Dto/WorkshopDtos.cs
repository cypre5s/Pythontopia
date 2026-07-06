using System.Text.Json;

namespace Pythontopia.Core.Dto;

/// <summary>
/// POST /api/workshop/resources (and /stream) body. <see cref="Type"/> is one of HANDOUT / MINDMAP /
/// PRACTICE / CODE_LAB / READING / STORYBOARD. At least one (≤ 10) KC code is required;
/// <see cref="FocusHint"/> (≤ 512 chars) is an optional one-line generation focus.
/// </summary>
public sealed record ResourceCreateRequest(
    IReadOnlyList<string> KcCodes,
    string Type,
    string? FocusHint = null);

/// <summary>
/// POST /api/workshop/resources response. <see cref="Status"/> mirrors the agent run status
/// (e.g. SUCCEEDED / PARTIAL); on success <see cref="ResourceId"/> is fetchable via GET .../{id}.
/// </summary>
public sealed record ResourceCreateResponse(long? RunId, long? ResourceId, string? Status);

/// <summary>
/// GET /api/workshop/resources/{id} — a generated learning resource. <see cref="ContentFormat"/> is
/// one of MARKDOWN / MERMAID / STRUCT_JSON; <see cref="Content"/> is the freeform generated body.
/// <see cref="SafetyStatus"/> / <see cref="GroundingStatus"/> are PASSED / FAILED / NO_EVIDENCE;
/// <see cref="FeedbackStatus"/> is NONE / LIKED / DISLIKED.
/// </summary>
public sealed record LearningResourceDto(
    long Id,
    long? UserId,
    long? PackId,
    IReadOnlyList<string>? KcCodes,
    long? ChapterId,
    string? Type,
    string? Title,
    string? ContentFormat,
    JsonElement? Content,
    IReadOnlyList<long>? EvidencePageIds,
    string? Summary,
    string? SafetyStatus,
    string? GroundingStatus,
    string? FeedbackStatus,
    string? FeedbackNote,
    DateTimeOffset? CreatedAt);

/// <summary>GET /api/workshop/resources?type=&amp;kcCode= — a generated-resource list row.</summary>
public sealed record ResourceListItemDto(
    long Id,
    string? Type,
    string? Title,
    string? Summary,
    IReadOnlyList<string>? KcCodes,
    string? GroundingStatus,
    string? FeedbackStatus,
    DateTimeOffset? CreatedAt);
