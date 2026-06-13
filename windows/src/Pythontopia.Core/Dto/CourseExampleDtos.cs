namespace Pythontopia.Core.Dto;

/// <summary>
/// A worked code example from the course pack (GET /api/course/examples and
/// /api/course/examples/{id}). <see cref="PageRefs"/> cites the courseware pages it came from.
/// </summary>
public sealed record CourseExampleDto(
    long Id,
    long? PackId,
    string? KcCode,
    string? Title,
    string? Code,
    string? ExplanationMarkdown,
    IReadOnlyList<long>? PageRefs);
