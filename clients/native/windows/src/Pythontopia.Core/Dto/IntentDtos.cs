namespace Pythontopia.Core.Dto;

/// <summary>
/// POST /api/intent/route body (百晓工坊 intent router). Only <see cref="Message"/> is required
/// (&lt;= 4000 chars); the other fields help the router anchor knowledge components.
/// <see cref="ManualKcCodes"/> are student <c>@</c>-mentions (highest priority);
/// <see cref="CourseKcs"/> is the loaded course KC catalogue used for topic→KC mapping.
/// </summary>
public sealed record IntentRouteRequest(
    string Message,
    IReadOnlyList<string>? ManualKcCodes = null,
    IReadOnlyList<IntentCourseKc>? CourseKcs = null,
    int? CurrentChapter = null,
    IReadOnlyList<string>? RecentKcCodes = null);

/// <summary>A course KC catalogue entry sent to the intent router (<c>code</c> anchors, <c>name</c> maps topic).</summary>
public sealed record IntentCourseKc(string Code, string? Name = null);

/// <summary>
/// POST /api/intent/route response. <see cref="Intent"/> is one of ASK / VIDEO / REVIEW / HANDOUT /
/// MINDMAP / PRACTICE / CODE_LAB / READING / STORYBOARD; the client dispatches accordingly
/// (ASK→tutor SSE, REVIEW→Bug 矿洞, the 6 resource types→workshop). <see cref="KcCodes"/> are the
/// anchored knowledge components and <see cref="FocusHint"/> is a one-line learning focus.
/// </summary>
public sealed record IntentRouteResponse(
    string? Intent,
    IReadOnlyList<string>? KcCodes,
    string? FocusHint,
    double Confidence,
    string? Reason);
