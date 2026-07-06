using System.Text;
using Microsoft.Xna.Framework;
using Microsoft.Xna.Framework.Graphics;
using Microsoft.Xna.Framework.Input;
using Pythontopia.Core.Api;
using Pythontopia.Core.Config;
using Pythontopia.Core.Dto;
using Pythontopia.Game.Entities;
using Pythontopia.Game.Rendering;
using Pythontopia.Game.Tiled;

namespace Pythontopia.Game;

/// <summary>
/// The pixel-town MVP: loads the real Tiled <c>town_hub</c> map + Kenney tileset,
/// renders the ground / buildings / decoration layers with a camera that follows a
/// tile-grid player, parses NPC / building objects for proximity interaction, and
/// draws a HUD. When a JWT is supplied it overlays the live world snapshot
/// (day / weather / quest count / saved position) from POST /api/game/bootstrap.
/// </summary>
public sealed class TownGame : Microsoft.Xna.Framework.Game
{
    private const int Zoom = 3;

    private readonly GraphicsDeviceManager _graphics;
    private readonly AppConfig _config;
    private readonly string? _token;
    private readonly object _sync = new();

    private SpriteBatch _batch = null!;
    private Texture2D _pixel = null!;
    private Texture2D _tileset = null!;
    private PixelFont _font = null!;
    private TiledMap _map = null!;
    private Player _player = null!;

    private int _tile = 16;
    private List<TiledLayer> _tileLayers = new();
    private TiledLayer? _buildingsLayer;
    private List<TownNpc> _npcs = new();
    private List<TownBuilding> _buildings = new();
    private bool[,] _blocked = new bool[1, 1];

    // Interaction state (main thread only).
    private TownNpc? _nearNpc;
    private TownBuilding? _nearBuilding;
    private string? _prompt;
    private string? _dialog;
    private bool _ePrev;

    // Backend overlay (guarded by _sync).
    private bool _offline = true;
    private string _worldLine = string.Empty;
    private int _questCount;
    private Point? _pendingTeleport;

    public TownGame(AppConfig config, string? token)
    {
        _config = config;
        _token = token;
        _graphics = new GraphicsDeviceManager(this)
        {
            PreferredBackBufferWidth = 960,
            PreferredBackBufferHeight = 720,
            SynchronizeWithVerticalRetrace = true,
        };
        Content.RootDirectory = "Assets";
        IsFixedTimeStep = true;
        IsMouseVisible = true;
    }

    protected override void Initialize()
    {
        Window.Title = "Pythontopia 小镇 · Town Hub";
        Window.AllowUserResizing = true;
        base.Initialize();
    }

    protected override void LoadContent()
    {
        _batch = new SpriteBatch(GraphicsDevice);
        _pixel = new Texture2D(GraphicsDevice, 1, 1);
        _pixel.SetData(new[] { Color.White });
        _font = new PixelFont(_pixel);

        var baseDir = AppContext.BaseDirectory;
        var mapPath = Path.Combine(baseDir, "Assets", "maps", "town_hub.json");
        var tilesetPath = Path.Combine(baseDir, "Assets", "tilesets", "tilemap_packed.png");

        _map = TiledMap.LoadFromFile(mapPath);
        _tile = _map.TileWidth <= 0 ? 16 : _map.TileWidth;

        using (var fs = File.OpenRead(tilesetPath))
        {
            _tileset = Texture2D.FromStream(GraphicsDevice, fs);
        }

        _tileLayers = _map.Layers.Where(l => l.IsTileLayer && l.Visible && l.Data is not null).ToList();
        _buildingsLayer = _map.FindTileLayer("buildings");
        BuildObjectsAndCollision();

        _player = new Player(FindSpawnTile(), _tile);

        if (_token is not null)
        {
            _ = Task.Run(LoadBackendOverlayAsync);
        }

        base.LoadContent();
    }

    protected override void Update(GameTime gameTime)
    {
        var dt = (float)gameTime.ElapsedGameTime.TotalSeconds;
        var keyboard = Keyboard.GetState();

        if (keyboard.IsKeyDown(Keys.Escape))
        {
            Exit();
            return;
        }

        ApplyPendingTeleport();

        var delta = Point.Zero;
        if (keyboard.IsKeyDown(Keys.W) || keyboard.IsKeyDown(Keys.Up))
        {
            delta = new Point(0, -1);
        }
        else if (keyboard.IsKeyDown(Keys.S) || keyboard.IsKeyDown(Keys.Down))
        {
            delta = new Point(0, 1);
        }
        else if (keyboard.IsKeyDown(Keys.A) || keyboard.IsKeyDown(Keys.Left))
        {
            delta = new Point(-1, 0);
        }
        else if (keyboard.IsKeyDown(Keys.D) || keyboard.IsKeyDown(Keys.Right))
        {
            delta = new Point(1, 0);
        }

        if (delta != Point.Zero && !_player.IsMoving)
        {
            _player.TryStartMove(delta, _tile, IsBlocked);
            _dialog = null;
        }

        _player.Update(dt);
        UpdateProximity();

        var ePressed = keyboard.IsKeyDown(Keys.E);
        if (ePressed && !_ePrev)
        {
            Interact();
        }
        _ePrev = ePressed;

        base.Update(gameTime);
    }

    protected override void Draw(GameTime gameTime)
    {
        GraphicsDevice.Clear(new Color(28, 36, 28));

        _batch.Begin(
            samplerState: SamplerState.PointClamp,
            blendState: BlendState.NonPremultiplied,
            transformMatrix: ComputeCamera());
        DrawTileLayers();
        DrawBuildingOutlines();
        DrawNpcs();
        DrawPlayer();
        _batch.End();

        _batch.Begin(samplerState: SamplerState.PointClamp, blendState: BlendState.NonPremultiplied);
        DrawHud();
        _batch.End();

        base.Draw(gameTime);
    }

    // ----------------------------------------------------------- world build ----

    private void BuildObjectsAndCollision()
    {
        _blocked = new bool[_map.Width, _map.Height];

        if (_buildingsLayer?.Data is { } buildingData)
        {
            for (var i = 0; i < buildingData.Length; i++)
            {
                if (Gid(buildingData[i]) == 0)
                {
                    continue;
                }
                MarkBlocked(i % _map.Width, i / _map.Width);
            }
        }

        _npcs = new List<TownNpc>();
        _buildings = new List<TownBuilding>();

        var group = _map.FindObjectGroup();
        if (group?.Objects is not { } objects)
        {
            return;
        }

        foreach (var obj in objects)
        {
            switch (obj.Type?.ToLowerInvariant())
            {
                case "npc":
                {
                    var tile = new Point((int)(obj.X / _tile), (int)(obj.Y / _tile));
                    _npcs.Add(new TownNpc
                    {
                        Name = obj.Name ?? "npc",
                        Key = obj.GetProperty("key"),
                        Sprite = obj.GetProperty("sprite"),
                        Label = obj.GetProperty("label"),
                        Tile = tile,
                    });
                    MarkBlocked(tile.X, tile.Y);
                    break;
                }

                case "building":
                {
                    var rect = TileRect(obj);
                    _buildings.Add(new TownBuilding
                    {
                        Name = obj.Name ?? "building",
                        Route = obj.GetProperty("route"),
                        Label = obj.GetProperty("label"),
                        TileBounds = rect,
                    });
                    BlockRect(rect);
                    break;
                }

                case "landmark":
                case "low_obstacle":
                    BlockRect(TileRect(obj));
                    break;
            }
        }
    }

    private Rectangle TileRect(TiledObject obj)
    {
        var left = (int)MathF.Floor(obj.X / _tile);
        var top = (int)MathF.Floor(obj.Y / _tile);
        var right = (int)MathF.Ceiling((obj.X + Math.Max(obj.Width, _tile)) / _tile);
        var bottom = (int)MathF.Ceiling((obj.Y + Math.Max(obj.Height, _tile)) / _tile);

        left = Math.Clamp(left, 0, _map.Width - 1);
        top = Math.Clamp(top, 0, _map.Height - 1);
        right = Math.Clamp(right, left + 1, _map.Width);
        bottom = Math.Clamp(bottom, top + 1, _map.Height);

        return new Rectangle(left, top, right - left, bottom - top);
    }

    private void BlockRect(Rectangle rect)
    {
        for (var y = rect.Top; y < rect.Bottom; y++)
        {
            for (var x = rect.Left; x < rect.Right; x++)
            {
                MarkBlocked(x, y);
            }
        }
    }

    private void MarkBlocked(int x, int y)
    {
        if (InBounds(x, y))
        {
            _blocked[x, y] = true;
        }
    }

    private Point FindSpawnTile()
    {
        var spawn = new Point(_map.Width / 2, _map.Height / 2);
        var spawnObj = _map.FindObjectGroup()?.Objects?
            .FirstOrDefault(o => string.Equals(o.Type, "spawn", StringComparison.OrdinalIgnoreCase));
        if (spawnObj is not null)
        {
            spawn = new Point((int)(spawnObj.X / _tile), (int)(spawnObj.Y / _tile));
        }
        return NearestFree(spawn);
    }

    private Point NearestFree(Point start)
    {
        if (InBounds(start.X, start.Y) && !_blocked[start.X, start.Y])
        {
            return start;
        }

        var visited = new HashSet<Point> { start };
        var queue = new Queue<Point>();
        queue.Enqueue(start);
        var dirs = new[] { new Point(1, 0), new Point(-1, 0), new Point(0, 1), new Point(0, -1) };

        while (queue.Count > 0)
        {
            var current = queue.Dequeue();
            if (InBounds(current.X, current.Y) && !_blocked[current.X, current.Y])
            {
                return current;
            }

            foreach (var d in dirs)
            {
                var next = new Point(current.X + d.X, current.Y + d.Y);
                if (Math.Abs(next.X - start.X) + Math.Abs(next.Y - start.Y) <= 10 && visited.Add(next))
                {
                    queue.Enqueue(next);
                }
            }
        }

        return start;
    }

    // ----------------------------------------------------------- interaction ----

    private void UpdateProximity()
    {
        _nearNpc = null;
        _nearBuilding = null;
        _prompt = null;

        var pt = _player.Tile;

        foreach (var npc in _npcs)
        {
            if (Chebyshev(pt, npc.Tile) <= 1)
            {
                _nearNpc = npc;
                _prompt = $"PRESS E: TALK TO {npc.Name}";
                return;
            }
        }

        foreach (var building in _buildings)
        {
            if (NearRect(pt, building.TileBounds))
            {
                _nearBuilding = building;
                _prompt = $"PRESS E: ENTER {building.Route ?? building.Name}";
                return;
            }
        }
    }

    private void Interact()
    {
        if (_nearNpc is { } npc)
        {
            _dialog = $"{npc.Name}  SPRITE:{npc.Sprite ?? "-"}";
        }
        else if (_nearBuilding is { } building)
        {
            _dialog = $"{building.Name}  ROUTE:{building.Route ?? "-"}";
        }
        else
        {
            _dialog = null;
        }
    }

    // -------------------------------------------------------------- rendering ----

    private Matrix ComputeCamera()
    {
        var viewWidth = GraphicsDevice.Viewport.Width / (float)Zoom;
        var viewHeight = GraphicsDevice.Viewport.Height / (float)Zoom;
        var center = _player.PixelPosition + new Vector2(_tile / 2f, _tile / 2f);

        var camX = Math.Clamp(center.X - viewWidth / 2f, 0, Math.Max(0, _map.PixelWidth - viewWidth));
        var camY = Math.Clamp(center.Y - viewHeight / 2f, 0, Math.Max(0, _map.PixelHeight - viewHeight));

        if (_map.PixelWidth < viewWidth)
        {
            camX = (_map.PixelWidth - viewWidth) / 2f;
        }
        if (_map.PixelHeight < viewHeight)
        {
            camY = (_map.PixelHeight - viewHeight) / 2f;
        }

        return Matrix.CreateTranslation(-MathF.Round(camX), -MathF.Round(camY), 0f) * Matrix.CreateScale(Zoom);
    }

    private void DrawTileLayers()
    {
        var tileset = _map.PrimaryTileset;
        if (tileset is null)
        {
            return;
        }

        var columns = tileset.Columns <= 0 ? Math.Max(1, tileset.ImageWidth / _tile) : tileset.Columns;

        foreach (var layer in _tileLayers)
        {
            var data = layer.Data!;
            for (var i = 0; i < data.Length; i++)
            {
                var gid = Gid(data[i]);
                if (gid == 0)
                {
                    continue;
                }

                var tileId = gid - tileset.FirstGid;
                if (tileId < 0)
                {
                    continue;
                }

                var source = new Rectangle(
                    (tileId % columns) * tileset.TileWidth,
                    (tileId / columns) * tileset.TileHeight,
                    tileset.TileWidth,
                    tileset.TileHeight);
                var destination = new Rectangle((i % _map.Width) * _tile, (i / _map.Width) * _tile, _tile, _tile);
                _batch.Draw(_tileset, destination, source, Color.White);
            }
        }
    }

    private void DrawBuildingOutlines()
    {
        var color = new Color(255, 226, 150, 80);
        foreach (var building in _buildings)
        {
            var rect = new Rectangle(
                building.TileBounds.X * _tile,
                building.TileBounds.Y * _tile,
                building.TileBounds.Width * _tile,
                building.TileBounds.Height * _tile);
            DrawOutline(rect, color, 1);
        }
    }

    private void DrawNpcs()
    {
        foreach (var npc in _npcs)
        {
            var x = npc.Tile.X * _tile;
            var y = npc.Tile.Y * _tile;
            _batch.Draw(_pixel, new Rectangle(x + 4, y + 6, _tile - 8, _tile - 7), new Color(60, 130, 200));
            _batch.Draw(_pixel, new Rectangle(x + 5, y + 2, _tile - 10, 5), new Color(250, 220, 170));
            _batch.Draw(_pixel, new Rectangle(x + _tile / 2 - 1, y - 3, 2, 3), new Color(255, 210, 80));
        }
    }

    private void DrawPlayer()
    {
        var x = (int)_player.PixelPosition.X;
        var y = (int)_player.PixelPosition.Y;
        _batch.Draw(_pixel, new Rectangle(x + 4, y + 6, _tile - 8, _tile - 7), new Color(220, 80, 60));
        _batch.Draw(_pixel, new Rectangle(x + 5, y + 2, _tile - 10, 5), new Color(250, 220, 170));

        var nub = _player.Facing switch
        {
            Facing.Up => new Rectangle(x + _tile / 2 - 1, y, 2, 3),
            Facing.Down => new Rectangle(x + _tile / 2 - 1, y + _tile - 3, 2, 3),
            Facing.Left => new Rectangle(x + 2, y + _tile / 2 - 1, 3, 2),
            Facing.Right => new Rectangle(x + _tile - 5, y + _tile / 2 - 1, 3, 2),
            _ => Rectangle.Empty,
        };
        if (nub != Rectangle.Empty)
        {
            _batch.Draw(_pixel, nub, new Color(35, 35, 45));
        }
    }

    private void DrawHud()
    {
        var width = GraphicsDevice.Viewport.Width;
        var height = GraphicsDevice.Viewport.Height;
        const int barHeight = 26;

        _batch.Draw(_pixel, new Rectangle(0, height - barHeight, width, barHeight), new Color(0, 0, 0, 180));

        bool offline;
        string world;
        int quests;
        lock (_sync)
        {
            offline = _offline;
            world = _worldLine;
            quests = _questCount;
        }

        var status = offline ? "OFFLINE (SET PYT_TOKEN)" : $"QUESTS:{quests} {world}".Trim();
        var hud = $"MAP:TOWN_HUB  TILE:{_player.Tile.X},{_player.Tile.Y}  {status}";
        _font.Draw(_batch, hud, new Vector2(8, height - barHeight + 6), Color.White, 2);

        _font.Draw(_batch, "WASD/ARROWS MOVE | E INTERACT | ESC QUIT", new Vector2(8, 8), new Color(200, 255, 200), 2);

        if (_prompt is not null)
        {
            var promptWidth = _font.Measure(_prompt, 2) + 16;
            var promptRect = new Rectangle(8, height - barHeight - 28, promptWidth, 22);
            _batch.Draw(_pixel, promptRect, new Color(20, 20, 40, 205));
            _font.Draw(_batch, _prompt, new Vector2(16, height - barHeight - 22), new Color(255, 230, 120), 2);
        }

        if (_dialog is not null)
        {
            var dialogWidth = _font.Measure(_dialog, 2) + 24;
            var dialogRect = new Rectangle(width / 2 - dialogWidth / 2, height / 2 - 20, dialogWidth, 40);
            _batch.Draw(_pixel, dialogRect, new Color(15, 15, 25, 235));
            DrawOutline(dialogRect, new Color(255, 230, 120), 2);
            _font.Draw(_batch, _dialog, new Vector2(dialogRect.X + 12, dialogRect.Y + 14), Color.White, 2);
        }
    }

    private void DrawOutline(Rectangle rect, Color color, int thickness)
    {
        _batch.Draw(_pixel, new Rectangle(rect.X, rect.Y, rect.Width, thickness), color);
        _batch.Draw(_pixel, new Rectangle(rect.X, rect.Bottom - thickness, rect.Width, thickness), color);
        _batch.Draw(_pixel, new Rectangle(rect.X, rect.Y, thickness, rect.Height), color);
        _batch.Draw(_pixel, new Rectangle(rect.Right - thickness, rect.Y, thickness, rect.Height), color);
    }

    // ----------------------------------------------------------- backend sync ----

    private async Task LoadBackendOverlayAsync()
    {
        try
        {
            var api = ApiClient.Create(_config);
            api.AuthToken = _token;
            var boot = await api.BootstrapGameAsync().ConfigureAwait(false);

            Point? teleport = null;
            if (boot.Player is { } player
                && string.Equals(player.MapKey, "town_hub", StringComparison.OrdinalIgnoreCase)
                && player.TileX.HasValue && player.TileY.HasValue)
            {
                teleport = new Point(player.TileX.Value, player.TileY.Value);
            }

            lock (_sync)
            {
                _offline = false;
                _questCount = boot.Quests?.Count ?? 0;
                _worldLine = BuildWorldLine(boot.World);
                _pendingTeleport = teleport;
            }
        }
        catch (Exception ex)
        {
            lock (_sync)
            {
                _worldLine = "BACKEND ERROR: " + Sanitize(ex.Message);
            }
        }
    }

    private void ApplyPendingTeleport()
    {
        Point? teleport;
        lock (_sync)
        {
            teleport = _pendingTeleport;
            _pendingTeleport = null;
        }
        if (teleport is { } tile)
        {
            _player.Teleport(NearestFree(tile), _tile);
        }
    }

    private static string BuildWorldLine(WorldSnapshot? world)
    {
        if (world is null)
        {
            return string.Empty;
        }
        var season = Sanitize(world.Season);
        var time = Sanitize(world.TimeOfDay);
        var weather = Sanitize(world.Weather);
        return $"DAY:{world.DayIndex} {season} {time} {weather}".Replace("  ", " ").Trim();
    }

    private static string Sanitize(string? value)
    {
        if (string.IsNullOrEmpty(value))
        {
            return string.Empty;
        }

        var builder = new StringBuilder(value.Length);
        foreach (var c in value)
        {
            if (c < 128 && !char.IsControl(c))
            {
                builder.Append(c);
            }
        }
        return builder.ToString();
    }

    // -------------------------------------------------------------- utilities ----

    private bool IsBlocked(Point tile) => !InBounds(tile.X, tile.Y) || _blocked[tile.X, tile.Y];

    private bool InBounds(int x, int y) => x >= 0 && y >= 0 && x < _map.Width && y < _map.Height;

    private static int Gid(long raw) => (int)((uint)raw & 0x1FFFFFFF);

    private static int Chebyshev(Point a, Point b) => Math.Max(Math.Abs(a.X - b.X), Math.Abs(a.Y - b.Y));

    private static bool NearRect(Point p, Rectangle rect)
    {
        var inflated = new Rectangle(rect.X - 1, rect.Y - 1, rect.Width + 2, rect.Height + 2);
        return inflated.Contains(p) && !rect.Contains(p);
    }
}
