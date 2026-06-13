using System.Text.Json;

namespace Pythontopia.Core.Dto;

/// <summary>GET /api/world/skills — the skill tree plus the learner's unlocked skills.</summary>
public sealed record SkillsResponse(
    IReadOnlyList<SkillNodeDto> Nodes,
    IReadOnlyList<LearnerSkillDto> Unlocked);

/// <summary>A skill tree node. <see cref="UnlockCondition"/> is freeform JSON (KC_MASTERY / ALL / ANY).</summary>
public sealed record SkillNodeDto(
    string SkillKey,
    string? DisplayName,
    string? Description,
    IReadOnlyList<string>? KcCodes,
    JsonElement? UnlockCondition,
    string? IconUrl,
    int Tier);

/// <summary>A skill the learner has unlocked (POST /api/world/skills/{key}/unlock result).</summary>
public sealed record LearnerSkillDto(string SkillKey, DateTimeOffset? UnlockedAt, int Proficiency);
