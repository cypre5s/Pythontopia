import { isoToLocalDateTime, localDateTimeToIso, TIME_ZONE } from "./time";

export interface JudgeDates { start: string; end: string }

function shiftDay(value: string, days: number) {
  const date = new Date(`${value}T12:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

/** Inclusive calendar dates in the workbench's configured zone, not the browser zone. */
export function recentJudgeDates(days: number, now = new Date(), zone = TIME_ZONE): JudgeDates {
  const end = isoToLocalDateTime(now.toISOString(), zone).slice(0, 10);
  return { start: shiftDay(end, 1 - days), end };
}

export function judgeDateQuery(dates: JudgeDates | null, zone = TIME_ZONE) {
  if (dates === null) return "";
  if (!/^\d{4}-\d{2}-\d{2}$/.test(dates.start) || !/^\d{4}-\d{2}-\d{2}$/.test(dates.end))
    throw new Error("请选择完整的开始日期和结束日期。");
  if (dates.start > dates.end) throw new Error("结束日期不能早于开始日期。");
  // Validate both original dates before deriving the exclusive next-day boundary.
  const since = localDateTimeToIso(`${dates.start}T00:00`, zone);
  localDateTimeToIso(`${dates.end}T00:00`, zone);
  const until = localDateTimeToIso(`${shiftDay(dates.end, 1)}T00:00`, zone);
  return `&${new URLSearchParams({ since, until })}`;
}
