namespace Pythontopia.Core.Dto;

/// <summary>POST /api/media/image body — generate an illustration from a prompt.</summary>
public sealed record ImageGenerationRequest(string Prompt, string? Label = null, IReadOnlyList<string>? KcCodes = null);

/// <summary>
/// A generated media asset (POST /api/media/image response). <see cref="Url"/> is a relative path
/// (e.g. <c>/api/media/assets/{id}</c>) served anonymously, so it can be loaded directly as an image.
/// <see cref="Degraded"/> is true when a local placeholder was returned (no provider key).
/// </summary>
public sealed record MediaAssetDto(
    long Id,
    string? Kind,
    string? Url,
    string? MimeType,
    bool Degraded,
    string? Source,
    string? Prompt,
    IReadOnlyList<string>? KcCodes);

/// <summary>One scene of a storyboard short video (image prompt + narration + optional voice/duration).</summary>
public sealed record StoryboardSceneSpec(
    string? ImagePrompt,
    string? Narration,
    string? Voice = null,
    int? DurationSeconds = null,
    IReadOnlyList<string>? KcCodes = null);

/// <summary>POST /api/media/storyboard/video body — image sequence + TTS composited into an mp4.</summary>
public sealed record StoryboardVideoRequest(IReadOnlyList<StoryboardSceneSpec> Scenes);

/// <summary>POST /api/media/asr response — speech recognised to text (degraded=true when unavailable).</summary>
public sealed record AsrResponse(string? Text, bool Degraded);

/// <summary>Per-scene progress of an async storyboard-video compose job.</summary>
public sealed record MediaJobProgress(
    string? Phase,
    int? SceneIndex,
    int? SceneTotal,
    string? RoleKey,
    string? Message,
    int? Percent);

/// <summary>POST /api/media/storyboard/video/jobs (202) — created/queued compose job (cached=true returns assetId directly).</summary>
public sealed record MediaJobCreateResponse(long JobId, string Status, bool Cached, long? AssetId);

/// <summary>GET /api/media/jobs/{id} — async compose job status (polling fallback for the SSE stream).</summary>
public sealed record MediaJobDto(
    long JobId,
    string Status,
    MediaJobProgress? Progress,
    long? AssetId,
    string? Url,
    string? ErrorCode);
