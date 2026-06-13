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
