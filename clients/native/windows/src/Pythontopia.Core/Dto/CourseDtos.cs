using System.Text.Json;

namespace Pythontopia.Core.Dto;

/// <summary>GET /api/course/pack — the Python语言基础 course pack summary (packId 43).</summary>
public sealed record CoursePackDto(
    long? PackId,
    string? Name,
    string? PrimaryLanguage,
    int? DocumentCount,
    int? PageCount,
    int? ChapterCount,
    int? KcCount,
    int? ExampleCount,
    int? ProblemCount);

/// <summary>GET /api/course/chapters.</summary>
public sealed record ChapterDto(long? ChapterId, int? OrderIndex, string? Title, string? SummaryMarkdown);

/// <summary>GET /api/course/kcs?chapterId= — a knowledge component.</summary>
public sealed record KcDto(
    long? KcId,
    string KcCode,
    string? KcName,
    long? ChapterId,
    int? ChapterOrder,
    string? Difficulty,
    string? SummaryMarkdown);

/// <summary>GET /api/course/pages?kcCode= — a courseware page (image at /api/course/pages/{id}/image).</summary>
public sealed record PageDto(
    long? PageId,
    long? DocumentId,
    long? ChapterId,
    int? PageNo,
    string? Title,
    string? ContentMarkdown,
    string? Excerpt);

/// <summary>GET /api/course/videos?chapterId= — manifest entry (stream at /api/course/videos/{id}).</summary>
public sealed record CourseVideoDto(
    long Id,
    int Chapter,
    string? Title,
    int? DurationSec,
    IReadOnlyList<string>? KcCodes,
    bool Available);

/// <summary>GET /api/course/problems?kcCode= — a course-pack problem summary for the practice list.</summary>
public sealed record ProblemDto(
    long? ProblemId,
    string? KcCode,
    string? Title,
    string? StatementMarkdown,
    string? InputDescription,
    string? OutputDescription,
    string? Difficulty,
    string? SourceLabel);

/// <summary>GET /api/problems/{id}/public — the student-facing problem statement + samples.</summary>
public sealed record PublicProblemDto(
    long Id,
    long? PackId,
    string? KcCode,
    string? Title,
    string? StatementMarkdown,
    string? InputDescription,
    string? OutputDescription,
    string? Difficulty,
    JsonElement? Samples);
