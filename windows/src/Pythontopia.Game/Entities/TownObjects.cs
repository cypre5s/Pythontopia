using Microsoft.Xna.Framework;

namespace Pythontopia.Game.Entities;

/// <summary>An interactable NPC parsed from a Tiled object of type <c>npc</c>.</summary>
public sealed class TownNpc
{
    public required string Name { get; init; }   // ASCII object name, e.g. npc_mayor_py
    public string? Key { get; init; }            // "key" property, e.g. mayor_py
    public string? Sprite { get; init; }         // "sprite" property, e.g. npc_mayor
    public string? Label { get; init; }          // localized label (not drawn by the ASCII HUD font)
    public Point Tile { get; init; }
}

/// <summary>A building / landmark footprint parsed from a Tiled object.</summary>
public sealed class TownBuilding
{
    public required string Name { get; init; }
    public string? Route { get; init; }          // "route" property, e.g. /profile-room
    public string? Label { get; init; }
    public Rectangle TileBounds { get; init; }   // bounds in tile coordinates
}
