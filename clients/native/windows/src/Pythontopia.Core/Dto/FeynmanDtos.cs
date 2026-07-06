using System.Text.Json;

namespace Pythontopia.Core.Dto;

/// <summary>POST /api/feynman/sessions body — start a teach-back session for a KC.</summary>
public sealed record FeynmanStartRequest(string KcCode);

/// <summary>POST /api/feynman/sessions/{id}/teach body — the learner's explanation turn.</summary>
public sealed record FeynmanTeachRequest(string Explanation);

/// <summary>A 费曼学弟 (teach-back) session. <see cref="TuteeState"/> is freeform JSON.</summary>
public sealed record FeynmanSessionDto(
    long Id,
    long UserId,
    string? KcCode,
    string? Status,
    JsonElement? TuteeState);

/// <summary>One conversation turn (the tutee's reply, with an optional correction).</summary>
public sealed record FeynmanTurnDto(long Id, string? Role, string? Content, string? TaughtFix);

/// <summary>The teach-back verdict (POST .../judge).</summary>
public sealed record FeynmanVerdictDto(long SessionId, bool Passed, string? Message);
