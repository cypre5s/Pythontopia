using System.Text.Json;

namespace Pythontopia.Core.Dto;

/// <summary>World/calendar snapshot from POST /api/game/bootstrap.</summary>
public sealed record WorldSnapshot(
    long? WorldSessionId,
    int DayIndex,
    int MonthIndex,
    string? Weekday,
    string? Season,
    string? TimeOfDay,
    string? Weather,
    int InGameMinutes,
    DateTimeOffset? StartedAt,
    DateTimeOffset? LastActiveAt);

/// <summary>Persisted player position/avatar. Direction is one of up/down/left/right.</summary>
public sealed record PlayerState(
    long? PlayerStateId,
    string? AvatarKey,
    string? MapKey,
    int? TileX,
    int? TileY,
    string? Direction);

/// <summary>Per-NPC dialog progress.</summary>
public sealed record NpcDialogState(
    string NpcKey,
    string? CurrentNodeId,
    IReadOnlyList<string>? CompletedNodeIds,
    DateTimeOffset? LastTalkedAt,
    JsonElement? Flags);

/// <summary>RPG save slot metadata.</summary>
public sealed record SaveSlot(
    int SlotIndex,
    string? DisplayName,
    bool IsActive,
    DateTimeOffset? CreatedAt,
    DateTimeOffset? LastPlayedAt,
    JsonElement? Snapshot,
    string? SceneName,
    IReadOnlyList<string>? UnlockedBuildings,
    long AccumulatedPlaySeconds,
    JsonElement? InventoryItems);

/// <summary>
/// Aggregate payload returned by POST /api/game/bootstrap. The learning-path,
/// skill and pending-event collections are kept as raw <see cref="JsonElement"/>
/// items because the desktop MVP does not yet render them (no shape guessing).
/// </summary>
public sealed record GameBootstrapResponse(
    WorldSnapshot? World,
    PlayerState? Player,
    LearnerProfileDto? Profile,
    IReadOnlyList<QuestDto>? Quests,
    IReadOnlyList<MasterySnapshotDto>? Mastery,
    IReadOnlyList<JsonElement>? Path,
    IReadOnlyList<JsonElement>? Skills,
    IReadOnlyList<NpcDialogState>? NpcStates,
    SaveSlot? ActiveSlot,
    IReadOnlyList<JsonElement>? PendingEvents);
