using System.Text.Json;

namespace Pythontopia.Core.Dto;

/// <summary>GET /api/classrooms/mine and POST /api/classrooms/join result.</summary>
public sealed record ClassroomDto(
    long? ClassroomId,
    string? Name,
    string? Description,
    long? TeacherId,
    long? PackId,
    string? Status,
    string? JoinCode);

/// <summary>POST /api/classrooms/join body.</summary>
public sealed record JoinClassroomRequest(string JoinCode);

/// <summary>GET /api/classroom/quests/mine — a class quest with the student's claim status.</summary>
public sealed record ClassroomQuestDto(
    long? Id,
    long? ClassroomId,
    string? Title,
    string? Description,
    IReadOnlyList<string>? KcCodes,
    string? NodeType,
    JsonElement? Payload,
    string? DueDate,
    long? CreatedBy,
    DateTimeOffset? CreatedAt,
    string? Status,
    long? TargetProblemId,
    string? TargetResourceType,
    long? StudentQuestId,
    string? StudentQuestStatus,
    string? RequiredEvidenceType,
    IReadOnlyList<long>? TargetProblemIds,
    string? DeepLinkRoute,
    JsonElement? DeepLinkParams);
