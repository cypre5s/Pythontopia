using System.Text.Json;
using System.Text.Json.Serialization;

namespace Pythontopia.Game.Tiled;

/// <summary>
/// Minimal reader for the orthogonal Tiled JSON map exported by the frontend
/// (<c>town_hub.json</c>). Property names are matched case-insensitively, which
/// also covers Tiled's all-lowercase keys (e.g. <c>tilewidth</c> -&gt; <see cref="TileWidth"/>).
/// </summary>
public sealed class TiledMap
{
    private static readonly JsonSerializerOptions Options = new()
    {
        PropertyNameCaseInsensitive = true,
        NumberHandling = JsonNumberHandling.AllowReadingFromString,
    };

    public int Width { get; set; }
    public int Height { get; set; }
    public int TileWidth { get; set; }
    public int TileHeight { get; set; }
    public List<TiledTileset> Tilesets { get; set; } = new();
    public List<TiledLayer> Layers { get; set; } = new();
    public List<TiledProperty>? Properties { get; set; }

    public TiledTileset? PrimaryTileset => Tilesets.Count > 0 ? Tilesets[0] : null;

    public int PixelWidth => Width * TileWidth;
    public int PixelHeight => Height * TileHeight;

    public string? GetProperty(string name) =>
        Properties?.FirstOrDefault(p => string.Equals(p.Name, name, StringComparison.OrdinalIgnoreCase))?.ValueAsString;

    public TiledLayer? FindTileLayer(string name) =>
        Layers.FirstOrDefault(l => l.IsTileLayer && string.Equals(l.Name, name, StringComparison.OrdinalIgnoreCase));

    public TiledLayer? FindObjectGroup() => Layers.FirstOrDefault(l => l.IsObjectGroup);

    public static TiledMap Load(Stream stream) =>
        JsonSerializer.Deserialize<TiledMap>(stream, Options)
        ?? throw new InvalidDataException("Failed to parse Tiled map JSON.");

    public static TiledMap LoadFromFile(string path)
    {
        using var fs = File.OpenRead(path);
        return Load(fs);
    }
}

public sealed class TiledTileset
{
    public int FirstGid { get; set; }
    public string? Name { get; set; }
    public int TileWidth { get; set; }
    public int TileHeight { get; set; }
    public int TileCount { get; set; }
    public int Columns { get; set; }
    public string? Image { get; set; }
    public int ImageWidth { get; set; }
    public int ImageHeight { get; set; }
}

public sealed class TiledLayer
{
    public int Id { get; set; }
    public string? Name { get; set; }
    public string? Type { get; set; }
    public int Width { get; set; }
    public int Height { get; set; }
    public bool Visible { get; set; } = true;
    public long[]? Data { get; set; }
    public List<TiledObject>? Objects { get; set; }

    public bool IsTileLayer => string.Equals(Type, "tilelayer", StringComparison.OrdinalIgnoreCase);
    public bool IsObjectGroup => string.Equals(Type, "objectgroup", StringComparison.OrdinalIgnoreCase);
}

public sealed class TiledObject
{
    public int Id { get; set; }
    public string? Name { get; set; }
    public string? Type { get; set; }
    public float X { get; set; }
    public float Y { get; set; }
    public float Width { get; set; }
    public float Height { get; set; }
    public List<TiledProperty>? Properties { get; set; }

    public string? GetProperty(string name) =>
        Properties?.FirstOrDefault(p => string.Equals(p.Name, name, StringComparison.OrdinalIgnoreCase))?.ValueAsString;
}

public sealed class TiledProperty
{
    public string Name { get; set; } = string.Empty;
    public string? Type { get; set; }
    public JsonElement Value { get; set; }

    /// <summary>Property value as a string regardless of its JSON kind (string/number/bool).</summary>
    public string? ValueAsString => Value.ValueKind switch
    {
        JsonValueKind.String => Value.GetString(),
        JsonValueKind.Number => Value.ToString(),
        JsonValueKind.True => "true",
        JsonValueKind.False => "false",
        _ => null,
    };
}
