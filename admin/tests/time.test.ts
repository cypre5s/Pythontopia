import { describe, it, expect } from "vitest";
import {
  resolveTimeZone,
  localDateTimeToIso,
  formatTimestamp,
  isoToLocalDateTime,
} from "../src/lib/time";
describe("configurable teacher time zone", () => {
  it("defaults to Shanghai and rejects invalid or offset-only zone configuration", () => {
    expect(resolveTimeZone()).toBe("Asia/Shanghai");
    expect(resolveTimeZone(" UTC ")).toBe("UTC");
    expect(() => resolveTimeZone("Mars/Olympus")).toThrow("无效时区");
    expect(() => resolveTimeZone("+08:00")).toThrow("无效时区");
  });
  it("interprets Shanghai and UTC wall clocks without depending on the browser zone", () => {
    expect(localDateTimeToIso("2026-11-03T08:30", "Asia/Shanghai")).toBe(
      "2026-11-03T00:30:00.000Z",
    );
    expect(localDateTimeToIso("2026-11-03T08:30:12.25", "UTC")).toBe(
      "2026-11-03T08:30:12.250Z",
    );
  });
  it("handles both winter and summer offsets in New York", () => {
    expect(localDateTimeToIso("2026-01-15T09:30", "America/New_York")).toBe(
      "2026-01-15T14:30:00.000Z",
    );
    expect(localDateTimeToIso("2026-07-15T09:30", "America/New_York")).toBe(
      "2026-07-15T13:30:00.000Z",
    );
  });
  it("edits existing timestamps in the configured zone instead of the browser zone", () => {
    expect(isoToLocalDateTime("2026-11-03T00:30:00Z", "Asia/Shanghai")).toBe("2026-11-03T08:30");
    expect(isoToLocalDateTime("2026-11-03T00:30:00Z", "UTC")).toBe("2026-11-03T00:30");
    expect(isoToLocalDateTime("2026-07-15T13:30:00Z", "America/New_York")).toBe("2026-07-15T09:30");
    expect(() => isoToLocalDateTime("2026-11-03T00:30:00", "UTC")).toThrow("包含时区");
  });
  it("rejects spring-forward gaps and fall-back ambiguity instead of silently normalizing", () => {
    expect(() =>
      localDateTimeToIso("2026-03-08T02:30", "America/New_York"),
    ).toThrow("不存在");
    expect(() =>
      localDateTimeToIso("2026-11-01T01:30", "America/New_York"),
    ).toThrow("有歧义");
  });
  it("rejects malformed dates and keeps displayed timestamps in the same configured zone", () => {
    expect(() => localDateTimeToIso("2026-02-30T12:00", "UTC")).toThrow(
      "有效范围",
    );
    expect(() => localDateTimeToIso("2026-11-03T24:00", "UTC")).toThrow(
      "有效范围",
    );
    expect(formatTimestamp("2026-11-03T00:30:00Z", "Asia/Shanghai")).toContain(
      "08:30",
    );
    expect(formatTimestamp("2026-11-03T00:30:00Z", "UTC")).toContain("00:30");
    expect(() => formatTimestamp("2026-11-03T00:30:00", "UTC")).toThrow(
      "包含时区",
    );
  });
});
