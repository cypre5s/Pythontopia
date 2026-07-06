namespace Pythontopia.Core.Dto;

/// <summary>POST /api/npc/{npcKey}/dialog body.</summary>
public sealed record NpcDialogRequest(string Message);

/// <summary>GET /api/npc/{npcKey}/greeting — the NPC's grounded greeting.</summary>
public sealed record NpcGreetingDto(
    string? Greeting,
    bool Available,
    bool Grounded,
    IReadOnlyList<long>? CitedEventIds,
    bool Degraded,
    bool Cached);

/// <summary>POST /api/npc/{npcKey}/dialog response — the NPC's grounded reply.</summary>
public sealed record NpcDialogResponseDto(
    string? ReplyMd,
    IReadOnlyList<long>? CitedEventIds,
    bool Grounded);
