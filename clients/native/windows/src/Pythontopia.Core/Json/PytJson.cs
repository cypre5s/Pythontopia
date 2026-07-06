using System.Text.Json;
using System.Text.Json.Serialization;

namespace Pythontopia.Core.Json;

/// <summary>
/// Centralized <see cref="JsonSerializerOptions"/> matching the Spring Boot backend:
/// camelCase property names, case-insensitive reads and tolerant number parsing.
/// Backend enums (status / verdict / role / source) are surfaced as plain strings
/// on the DTOs, so no enum converter is required.
/// </summary>
public static class PytJson
{
    public static readonly JsonSerializerOptions Default = Create();

    public static JsonSerializerOptions Create()
    {
        return new JsonSerializerOptions
        {
            PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
            DictionaryKeyPolicy = JsonNamingPolicy.CamelCase,
            PropertyNameCaseInsensitive = true,
            DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
            NumberHandling = JsonNumberHandling.AllowReadingFromString,
            ReadCommentHandling = JsonCommentHandling.Skip,
        };
    }
}
