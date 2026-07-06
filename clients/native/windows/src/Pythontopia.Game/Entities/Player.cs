using Microsoft.Xna.Framework;

namespace Pythontopia.Game.Entities;

public enum Facing
{
    Down,
    Up,
    Left,
    Right,
}

/// <summary>
/// Tile-grid player: input requests a one-tile step which is rejected by the
/// collision predicate or animated smoothly to the neighbouring tile.
/// </summary>
public sealed class Player
{
    private const float MoveDurationSeconds = 0.13f;

    private Vector2 _from;
    private Vector2 _to;
    private float _t;

    public Player(Point tile, int tileSize)
    {
        Tile = tile;
        PixelPosition = new Vector2(tile.X * tileSize, tile.Y * tileSize);
    }

    /// <summary>Current logical tile coordinate.</summary>
    public Point Tile { get; private set; }

    /// <summary>Top-left pixel position of the occupied tile, in world space.</summary>
    public Vector2 PixelPosition { get; private set; }

    public Facing Facing { get; private set; } = Facing.Down;

    public bool IsMoving { get; private set; }

    /// <summary>Force the player onto a tile (e.g. from a backend spawn / saved position).</summary>
    public void Teleport(Point tile, int tileSize)
    {
        Tile = tile;
        PixelPosition = new Vector2(tile.X * tileSize, tile.Y * tileSize);
        IsMoving = false;
        _t = 0f;
    }

    /// <summary>
    /// Begins a step in <paramref name="delta"/> (one of the 4 cardinal directions)
    /// unless already moving or the target tile is blocked. The facing always updates.
    /// </summary>
    public void TryStartMove(Point delta, int tileSize, Func<Point, bool> isBlocked)
    {
        if (IsMoving || delta == Point.Zero)
        {
            return;
        }

        Facing = (delta.X, delta.Y) switch
        {
            (0, -1) => Facing.Up,
            (0, 1) => Facing.Down,
            (-1, 0) => Facing.Left,
            (1, 0) => Facing.Right,
            _ => Facing,
        };

        var target = new Point(Tile.X + delta.X, Tile.Y + delta.Y);
        if (isBlocked(target))
        {
            return; // turned to face the obstacle but did not move
        }

        _from = PixelPosition;
        _to = new Vector2(target.X * tileSize, target.Y * tileSize);
        Tile = target;
        _t = 0f;
        IsMoving = true;
    }

    public void Update(float deltaSeconds)
    {
        if (!IsMoving)
        {
            return;
        }

        _t += deltaSeconds / MoveDurationSeconds;
        if (_t >= 1f)
        {
            PixelPosition = _to;
            IsMoving = false;
        }
        else
        {
            PixelPosition = Vector2.Lerp(_from, _to, _t);
        }
    }
}
