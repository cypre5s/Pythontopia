using Pythontopia.Core.Api;
using Xunit;

namespace Pythontopia.Core.Tests;

public class SseParserTests
{
    [Fact]
    public void Parse_SingleFrame_WithEventAndData()
    {
        var frame = Assert.Single(SseParser.Parse("event: token\ndata: hello\n\n"));
        Assert.Equal("token", frame.Event);
        Assert.Equal("hello", frame.Data);
    }

    [Fact]
    public void Parse_DataWithoutEvent_DefaultsToMessage()
    {
        var frame = Assert.Single(SseParser.Parse("data: payload\n\n"));
        Assert.Equal("message", frame.Event);
        Assert.Equal("payload", frame.Data);
    }

    [Fact]
    public void Parse_MultipleDataLines_JoinedWithNewline()
    {
        var frame = Assert.Single(SseParser.Parse("data: line1\ndata: line2\n\n"));
        Assert.Equal("line1\nline2", frame.Data);
    }

    [Fact]
    public void Parse_IgnoresCommentHeartbeats()
    {
        var frame = Assert.Single(SseParser.Parse(": keep-alive\nevent: trace\ndata: x\n\n"));
        Assert.Equal("trace", frame.Event);
        Assert.Equal("x", frame.Data);
    }

    [Fact]
    public void Parse_MultipleFrames_PreservesOrder()
    {
        var frames = SseParser.Parse("event: plan\ndata: a\n\nevent: result\ndata: b\n\n");
        Assert.Equal(2, frames.Count);
        Assert.Equal(("plan", "a"), (frames[0].Event, frames[0].Data));
        Assert.Equal(("result", "b"), (frames[1].Event, frames[1].Data));
    }

    [Fact]
    public void Parse_EventWithoutData_EmitsEmptyData()
    {
        var frame = Assert.Single(SseParser.Parse("event: ping\n\n"));
        Assert.Equal("ping", frame.Event);
        Assert.Equal(string.Empty, frame.Data);
    }

    [Fact]
    public void Parse_DataWithoutLeadingSpace_IsAccepted()
    {
        var frame = Assert.Single(SseParser.Parse("data:tight\n\n"));
        Assert.Equal("tight", frame.Data);
    }

    [Fact]
    public void Parse_EmptyOrDanglingInput_EmitsNothing()
    {
        Assert.Empty(SseParser.Parse(string.Empty));
        Assert.Empty(SseParser.Parse("\n\n\n"));
        Assert.Empty(SseParser.Parse("data: no trailing blank line"));
    }

    [Fact]
    public void Push_ReturnsFrameOnlyOnBlankLine()
    {
        var parser = new SseParser();
        Assert.Null(parser.Push("event: token"));
        Assert.Null(parser.Push("data: hi"));

        var frame = parser.Push(string.Empty);

        Assert.NotNull(frame);
        Assert.Equal("token", frame!.Event);
        Assert.Equal("hi", frame.Data);
    }
}
