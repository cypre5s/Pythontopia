namespace Pythontopia.Core.Dto;

/// <summary>
/// POST /api/tutor/ask body (Server-Sent Events response). Only <see cref="Question"/>
/// is required (&lt;= 2000 chars). <see cref="Style"/> may be visual/logical/trial.
/// Image attachments are intentionally not exposed by the desktop MVP.
/// </summary>
public sealed record TutorAskRequest(
    string Question,
    IReadOnlyList<string>? KcCodes = null,
    string? Context = null,
    string? Style = null,
    string? ConversationId = null);
