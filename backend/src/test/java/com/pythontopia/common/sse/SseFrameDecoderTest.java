package com.pythontopia.common.sse;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SseFrameDecoderTest {

  @Test
  void decodesUtf8BomCommentsMultilineDataAndDefaultEvent() throws Exception {
    String stream =
        "\ufeffevent: token\n"
            + ": heartbeat\n"
            + "data:first\n"
            + "data: second  \n"
            + "unknown: ignored\n\n"
            + "data: default\n\n";
    List<SseFrameDecoder.SseFrame> frames = new ArrayList<>();

    SseFrameDecoder.DecodeResult result = decode(stream, frame -> {
      frames.add(frame);
      return true;
    });

    assertThat(result).isEqualTo(SseFrameDecoder.DecodeResult.END_OF_STREAM);
    assertThat(frames)
        .containsExactly(
            new SseFrameDecoder.SseFrame("token", "first\nsecond  "),
            new SseFrameDecoder.SseFrame("message", "default"));
  }

  @Test
  void dispatchesAnExplicitEmptyDataField() throws Exception {
    List<SseFrameDecoder.SseFrame> frames = new ArrayList<>();

    SseFrameDecoder.DecodeResult result = decode("event: empty\ndata:\n\n", frame -> {
      frames.add(frame);
      return true;
    });

    assertThat(result).isEqualTo(SseFrameDecoder.DecodeResult.END_OF_STREAM);
    assertThat(frames).containsExactly(new SseFrameDecoder.SseFrame("empty", ""));
  }

  @Test
  void discardsUnterminatedTrailingFrame() throws Exception {
    List<SseFrameDecoder.SseFrame> frames = new ArrayList<>();

    SseFrameDecoder.DecodeResult result =
        decode("event: result\ndata: {\"ok\":", frame -> {
          frames.add(frame);
          return true;
        });

    assertThat(result).isEqualTo(SseFrameDecoder.DecodeResult.TRUNCATED);
    assertThat(frames).isEmpty();
  }

  @Test
  void stopsImmediatelyWhenHandlerRequestsIt() throws Exception {
    List<SseFrameDecoder.SseFrame> frames = new ArrayList<>();
    String stream = "data: first\n\ndata: second\n\n";

    SseFrameDecoder.DecodeResult result = decode(stream, frame -> {
      frames.add(frame);
      return false;
    });

    assertThat(result).isEqualTo(SseFrameDecoder.DecodeResult.STOPPED);
    assertThat(frames).containsExactly(new SseFrameDecoder.SseFrame("message", "first"));
  }

  private static SseFrameDecoder.DecodeResult decode(
      String stream,
      java.util.function.Predicate<SseFrameDecoder.SseFrame> handler) throws Exception {
    try (BufferedReader reader = new BufferedReader(new StringReader(stream))) {
      return SseFrameDecoder.decode(reader, handler);
    }
  }
}
