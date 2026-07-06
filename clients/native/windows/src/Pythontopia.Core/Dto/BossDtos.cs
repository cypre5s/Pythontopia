namespace Pythontopia.Core.Dto;

/// <summary>POST /api/world/boss/{chapter}/problem body (only the fields the desktop client sends).</summary>
public sealed record BossProblemRequest(int Stage, string? KcCode = null, int? Difficulty = null);

/// <summary>A boss-battle problem (POST /api/world/boss/{chapter}/problem response).</summary>
public sealed record BossProblemDto(
    string? ProblemId,
    int Chapter,
    int Stage,
    int Difficulty,
    string? KcCode,
    string? Statement,
    IReadOnlyList<string>? Hints,
    string? SampleInput,
    string? SampleOutput,
    int TotalTestCases,
    bool Reviewed,
    bool AiGenerated);

/// <summary>POST /api/world/boss/{chapter}/attempt body.</summary>
public sealed record BossAttemptRequest(
    string ProblemId,
    int Chapter,
    int Stage,
    string Code,
    int ElapsedSec);

/// <summary>Boss attempt evaluation result.</summary>
public sealed record BossAttemptResultDto(
    string? ProblemId,
    bool Passed,
    int PassedTestCases,
    int TotalTestCases,
    string? FailureFirstHint,
    int Score,
    string? Feedback);
