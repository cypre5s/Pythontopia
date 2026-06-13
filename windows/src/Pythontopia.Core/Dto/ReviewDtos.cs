namespace Pythontopia.Core.Dto;

/// <summary>
/// GET /api/learner/twin/weekly — 数字孪生周报: this week's memory-stream brief, reflective
/// insights, mastery deltas and the AI team's resource runs. Read-only, current学生 only.
/// </summary>
public sealed record WeeklyReportDto(
    string? PeriodStart,
    string? PeriodEnd,
    WeeklyMemoryBriefDto? MemoryBrief,
    IReadOnlyList<WeeklyInsightDto> Insights,
    IReadOnlyList<WeeklyMasteryDeltaDto> MasteryDeltas,
    IReadOnlyList<WeeklyResourceRunDto> ResourceRuns);

/// <summary>记忆流摘要（事件计数 + 高频 KC + 高光）。</summary>
public sealed record WeeklyMemoryBriefDto(
    int TotalEvents,
    int FailedPractices,
    int SucceededPractices,
    IReadOnlyList<string>? TopKcCodes,
    IReadOnlyList<string>? Highlights);

/// <summary>单条反思洞察（简版）。</summary>
public sealed record WeeklyInsightDto(
    long Id,
    string? Text,
    string? DimensionKey,
    IReadOnlyList<string>? Tags,
    string? CreatedAt);

/// <summary>单 KC 掌握度变化与最近事件原因。</summary>
public sealed record WeeklyMasteryDeltaDto(
    string KcCode,
    double CurrentScore,
    double Confidence,
    IReadOnlyList<string>? RecentEventReasons);

/// <summary>AI 团队资源运行摘要。</summary>
public sealed record WeeklyResourceRunDto(
    string? AgentName,
    string? Scenario,
    string? Status,
    int EvidencePageCount,
    string? CreatedAt);

/// <summary>
/// GET /api/learner/oracle/risk — per-KC forgetting/failure risk prediction with the
/// dependency path and evidence that drove it.
/// </summary>
public sealed record RiskPredictionDto(
    string KcCode,
    double Risk,
    IReadOnlyList<long>? EvidenceEventIds,
    string? Reason,
    IReadOnlyList<string>? DependencyPath);
