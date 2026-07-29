package com.pythontopia.common.sse;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Predicate;

/** WHATWG-compatible decoder for complete Server-Sent Events frames. */
public final class SseFrameDecoder {

  private static final char UTF8_BOM = '\ufeff';

  private SseFrameDecoder() {}

  /** Why decoding returned to its caller. */
  public enum DecodeResult {
    /** The input ended between frames. */
    END_OF_STREAM,
    /** The frame handler requested an early stop. */
    STOPPED,
    /** The input ended after data fields but before the dispatching blank line. */
    TRUNCATED
  }

  /** One dispatched SSE event. */
  public record SseFrame(String event, String data) {}

  /**
   * Decodes frames until EOF or until {@code handler} returns {@code false}.
   *
   * <p>Multiple {@code data:} fields are joined with LF, comment and unknown fields are ignored,
   * and pending data at EOF is discarded. The decoder only interprets wire frames; terminal-event
   * policy and transport errors remain the caller's responsibility.
   */
  public static DecodeResult decode(
      BufferedReader reader, Predicate<SseFrame> handler) throws IOException {
    Objects.requireNonNull(reader, "reader");
    Objects.requireNonNull(handler, "handler");

    String event = "";
    StringBuilder data = new StringBuilder();
    boolean hasData = false;
    boolean firstLine = true;
    String line;
    while ((line = reader.readLine()) != null) {
      if (firstLine) {
        firstLine = false;
        if (!line.isEmpty() && line.charAt(0) == UTF8_BOM) {
          line = line.substring(1);
        }
      }

      if (line.isEmpty()) {
        if (hasData) {
          String eventName = event.isEmpty() ? "message" : event;
          if (!handler.test(new SseFrame(eventName, data.toString()))) {
            return DecodeResult.STOPPED;
          }
        }
        event = "";
        data.setLength(0);
        hasData = false;
        continue;
      }
      if (line.charAt(0) == ':') {
        continue;
      }

      int colon = line.indexOf(':');
      String field = colon >= 0 ? line.substring(0, colon) : line;
      String value = colon >= 0 ? line.substring(colon + 1) : "";
      if (!value.isEmpty() && value.charAt(0) == ' ') {
        value = value.substring(1);
      }
      if ("event".equals(field)) {
        event = value;
      } else if ("data".equals(field)) {
        if (hasData) {
          data.append('\n');
        }
        data.append(value);
        hasData = true;
      }
    }
    return hasData ? DecodeResult.TRUNCATED : DecodeResult.END_OF_STREAM;
  }
}
