using System.Text.Json;

namespace Pythontopia.Core.Dto;

/// <summary>GET /api/world/calendar/today — the in-game date/season/weather snapshot.</summary>
public sealed record CalendarTodayDto(
    int DayIndex,
    int MonthIndex,
    string? Weekday,
    string? Season,
    string? TimeOfDay,
    string? Weather,
    int InGameMinutes);

/// <summary>
/// A pending world event (GET /api/world/calendar/events). <see cref="Payload"/> is freeform JSON;
/// <see cref="DateIso"/> is an ISO date string (e.g. 2026-06-13).
/// </summary>
public sealed record WorldEventDto(
    long? Id,
    string? DateIso,
    string? EventType,
    JsonElement? Payload,
    bool IsProcessed,
    DateTimeOffset? CreatedAt);
