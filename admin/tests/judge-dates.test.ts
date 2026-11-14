import { expect, it } from "vitest";
import { judgeDateQuery, recentJudgeDates } from "../src/lib/judge-dates";

it("uses inclusive calendar days in the workbench zone across month boundaries", () => {
  expect(recentJudgeDates(1, new Date("2026-02-28T16:30:00Z"), "Asia/Shanghai")).toEqual({ start: "2026-03-01", end: "2026-03-01" });
  expect(recentJudgeDates(7, new Date("2026-02-28T16:30:00Z"), "Asia/Shanghai")).toEqual({ start: "2026-02-23", end: "2026-03-01" });
  expect(recentJudgeDates(30, new Date("2026-02-28T16:30:00Z"), "Asia/Shanghai")).toEqual({ start: "2026-01-31", end: "2026-03-01" });
  const query = new URLSearchParams(judgeDateQuery({ start: "2026-11-14", end: "2026-11-14" }, "Asia/Shanghai").slice(1));
  expect(query.get("since")).toBe("2026-11-13T16:00:00.000Z");
  expect(query.get("until")).toBe("2026-11-14T16:00:00.000Z");
});

it("uses the next local midnight rather than adding 24 hours across DST", () => {
  const query = new URLSearchParams(judgeDateQuery({ start: "2026-03-08", end: "2026-03-08" }, "America/New_York").slice(1));
  expect(query.get("since")).toBe("2026-03-08T05:00:00.000Z");
  expect(query.get("until")).toBe("2026-03-09T04:00:00.000Z");
  expect(judgeDateQuery(null)).toBe("");
  expect(() => judgeDateQuery({ start: "", end: "2026-11-14" })).toThrow();
  expect(() => judgeDateQuery({ start: "2026-11-15", end: "2026-11-14" })).toThrow("结束日期");
  expect(() => judgeDateQuery({ start: "2026-02-30", end: "2026-03-01" })).toThrow();
});
