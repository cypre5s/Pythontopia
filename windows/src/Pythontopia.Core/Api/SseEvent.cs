namespace Pythontopia.Core.Api;

/// <summary>
/// A single Server-Sent Event frame: the <see cref="Event"/> name (e.g. <c>plan</c> / <c>trace</c> /
/// <c>token</c> / <c>result</c>) and its raw <c>data:</c> <see cref="Data"/> payload (usually JSON).
/// An <c>error</c> frame is never surfaced as an <see cref="SseEvent"/>; it throws an <see cref="ApiException"/>.
/// </summary>
public sealed record SseEvent(string Event, string Data);
