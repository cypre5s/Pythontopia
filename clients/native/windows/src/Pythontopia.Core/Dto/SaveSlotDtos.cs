using System.Text.Json;

namespace Pythontopia.Core.Dto;

/// <summary>
/// GET /api/world/saves — an RPG save slot (0-5). <see cref="Snapshot"/> and
/// <see cref="InventoryItems"/> are freeform JSON; activation is POST .../{slotIndex}/activate.
/// </summary>
public sealed record SaveSlotDto(
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
