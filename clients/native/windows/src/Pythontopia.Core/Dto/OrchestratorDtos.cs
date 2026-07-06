namespace Pythontopia.Core.Dto;

/// <summary>One message in the 学习总管 (orchestrator) conversation history.</summary>
public sealed record OrchestratorTurnMessage(string? Role, string? Content);

/// <summary>
/// POST /api/orchestrator/turn/stream body (学习总管 SSE). Only <see cref="Message"/> is required
/// (&lt;= 4000 chars); the backend server-side injects the FSRS-due <c>decaying_kcs</c>, so the client
/// neither sends nor needs them. The remaining fields are optional learning context.
/// </summary>
public sealed record OrchestratorTurnRequest(
    string Message,
    string? SessionId = null,
    IReadOnlyList<OrchestratorTurnMessage>? History = null,
    int? CurrentChapter = null,
    IReadOnlyList<string>? RecentKcCodes = null,
    IReadOnlyList<string>? RecentFailures = null,
    string? ProfileBrief = null,
    IReadOnlyList<string>? CourseKcCodes = null);
