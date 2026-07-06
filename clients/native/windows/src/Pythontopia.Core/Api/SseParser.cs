using System.Text;

namespace Pythontopia.Core.Api;

/// <summary>
/// Incremental parser for a Server-Sent Events stream. Feed it one line at a time
/// (newline already stripped, as produced by <see cref="TextReader.ReadLineAsync()"/>);
/// it assembles <c>event:</c> / <c>data:</c> fields and emits one <see cref="SseEvent"/>
/// per blank-line frame boundary.
/// </summary>
/// <remarks>
/// Extracted from the HTTP layer so the framing rules are unit-testable without a live
/// socket. The reader is stateful and is <b>not</b> thread-safe; use one instance per stream.
/// Behaviour matches the SSE subset the Pythontopia backend emits:
/// <list type="bullet">
/// <item>A line starting with <c>:</c> is a comment / heartbeat and is ignored.</item>
/// <item>Multiple <c>data:</c> lines in one frame are joined with <c>\n</c>.</item>
/// <item>A frame with only an <c>event:</c> and no data still emits (with empty data).</item>
/// <item>A frame with neither an event name nor data is dropped.</item>
/// <item>Unknown fields (<c>id:</c>, <c>retry:</c>, …) are ignored.</item>
/// </list>
/// </remarks>
public sealed class SseParser
{
    private const string DefaultEventName = "message";

    private readonly StringBuilder _data = new();
    private string? _eventName;

    /// <summary>
    /// Feeds a single line into the parser. Returns a completed <see cref="SseEvent"/> when
    /// <paramref name="line"/> is the blank line that terminates a frame and a frame is pending;
    /// otherwise returns <c>null</c>.
    /// </summary>
    public SseEvent? Push(string line)
    {
        ArgumentNullException.ThrowIfNull(line);

        if (line.Length == 0)
        {
            return Flush();
        }

        if (line[0] == ':')
        {
            return null; // comment / heartbeat
        }

        if (line.StartsWith("event:", StringComparison.Ordinal))
        {
            _eventName = line[6..].Trim();
        }
        else if (line.StartsWith("data:", StringComparison.Ordinal))
        {
            if (_data.Length > 0)
            {
                _data.Append('\n');
            }
            _data.Append(line[5..].TrimStart());
        }

        return null;
    }

    /// <summary>Emits the pending frame (if any) and resets parser state.</summary>
    private SseEvent? Flush()
    {
        if (_data.Length == 0 && _eventName is null)
        {
            return null;
        }

        var frame = new SseEvent(_eventName ?? DefaultEventName, _data.ToString());
        _data.Clear();
        _eventName = null;
        return frame;
    }

    /// <summary>
    /// Parses a complete SSE text block into frames in arrival order. Convenience for tests and
    /// non-streaming callers; a frame without a trailing blank line is not emitted (per the SSE spec).
    /// </summary>
    public static IReadOnlyList<SseEvent> Parse(string text)
    {
        ArgumentNullException.ThrowIfNull(text);

        var parser = new SseParser();
        var frames = new List<SseEvent>();
        using var reader = new StringReader(text);
        while (reader.ReadLine() is { } line)
        {
            if (parser.Push(line) is { } frame)
            {
                frames.Add(frame);
            }
        }
        return frames;
    }
}
