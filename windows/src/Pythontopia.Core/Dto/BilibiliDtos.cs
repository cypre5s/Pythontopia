namespace Pythontopia.Core.Dto;

/// <summary>
/// GET /api/videos/bilibili?keyword= — an external B 站 (Bilibili) explainer video card.
/// Served via the Python microservice proxy; the backend emits camelCase keys.
/// </summary>
public sealed record BilibiliVideoDto(
    string? Bvid,
    string? Title,
    string? Author,
    string? Description,
    string? AiSummary,
    string? CoverUrl,
    int DurationSec,
    long Play,
    long Danmaku,
    long Pubdate,
    string? Url);
