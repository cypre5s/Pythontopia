namespace Pythontopia.Core.Dto;

/// <summary>One turn in the ProfileAgent interview transcript.</summary>
public sealed record ProfileMessageDto(string Role, string Content);

/// <summary>POST /api/learner/profile/interview — opens (or resumes) the画像访谈 session.</summary>
public sealed record ProfileInterviewStartResponse(
    string InterviewSessionId,
    string Status,
    IReadOnlyList<ProfileMessageDto>? History,
    LearnerProfileDto? Draft);

/// <summary>POST .../interview/{id}/turn body — the student's answer plus prior history for memory.</summary>
public sealed record ProfileTurnRequest(string UserMessage, IReadOnlyList<ProfileMessageDto>? History = null);

/// <summary>Safety / grounding gate result attached to an interview turn.</summary>
public sealed record CheckResultDto(bool Passed, IReadOnlyList<string>? Reasons);

/// <summary>An evidence anchor the ProfileAgent cited for a dimension update.</summary>
public sealed record EvidenceAnchorDto(string? Source, string? Ref, string? Summary);

/// <summary>POST .../interview/{id}/turn response — the next question and the live profile draft.</summary>
public sealed record ProfileTurnResponse(
    string? NextQuestion,
    LearnerProfileDto? ProfileDraft,
    bool Done,
    IReadOnlyList<string>? DimensionsFilled,
    IReadOnlyList<EvidenceAnchorDto>? EvidenceAnchors,
    CheckResultDto? SafetyResult,
    CheckResultDto? GroundingResult);
