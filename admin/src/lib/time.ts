const DEFAULT_TIME_ZONE = "Asia/Shanghai";
const HOUR = 3_600_000;
interface WallClock {
  year: number;
  month: number;
  day: number;
  hour: number;
  minute: number;
  second: number;
  millisecond: number;
}
export function resolveTimeZone(configured?: string): string {
  const zone = configured?.trim() || DEFAULT_TIME_ZONE;
  try {
    if (/^[+-]/.test(zone)) throw new Error("IANA identifier required");
    new Intl.DateTimeFormat("en-US", { timeZone: zone }).format(0);
    return zone;
  } catch {
    throw new Error(
      `无效时区“${zone}”，请将 VITE_TIME_ZONE 设置为有效的 IANA 时区，例如 Asia/Shanghai。`,
    );
  }
}
export const TIME_ZONE = resolveTimeZone(import.meta.env.VITE_TIME_ZONE);
function utcScalar(value: WallClock): number {
  const date = new Date(0);
  date.setUTCFullYear(value.year, value.month - 1, value.day);
  date.setUTCHours(value.hour, value.minute, value.second, value.millisecond);
  return date.getTime();
}
function parseLocal(value: string): WallClock {
  const match =
    /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,3}))?)?$/.exec(
      value,
    );
  if (!match) throw new Error("请输入有效的日期和时间。");
  const result = {
    year: Number(match[1]),
    month: Number(match[2]),
    day: Number(match[3]),
    hour: Number(match[4]),
    minute: Number(match[5]),
    second: Number(match[6] ?? 0),
    millisecond: Number((match[7] ?? "").padEnd(3, "0")),
  };
  const instant = new Date(utcScalar(result));
  if (
    result.year < 1 ||
    result.month < 1 ||
    result.month > 12 ||
    result.day < 1 ||
    result.hour > 23 ||
    result.minute > 59 ||
    result.second > 59 ||
    instant.getUTCFullYear() !== result.year ||
    instant.getUTCMonth() + 1 !== result.month ||
    instant.getUTCDate() !== result.day
  )
    throw new Error("日期或时间超出有效范围。");
  return result;
}
function formatter(zone: string) {
  return new Intl.DateTimeFormat("en-US", {
    timeZone: zone,
    calendar: "iso8601",
    numberingSystem: "latn",
    era: "short",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hourCycle: "h23",
  });
}
function wallAt(instant: number, format: Intl.DateTimeFormat): WallClock {
  const parts = Object.fromEntries(
    format.formatToParts(instant).map((part) => [part.type, part.value]),
  );
  const year = Number(parts.year);
  return {
    year: parts.era === "BC" ? 1 - year : year,
    month: Number(parts.month),
    day: Number(parts.day),
    hour: Number(parts.hour),
    minute: Number(parts.minute),
    second: Number(parts.second),
    millisecond: ((instant % 1000) + 1000) % 1000,
  };
}
function sameWall(a: WallClock, b: WallClock) {
  return (
    a.year === b.year &&
    a.month === b.month &&
    a.day === b.day &&
    a.hour === b.hour &&
    a.minute === b.minute &&
    a.second === b.second &&
    a.millisecond === b.millisecond
  );
}
/** Interpret the input in the chosen IANA zone, independently of browser time zone. */
export function localDateTimeToIso(
  value: string,
  timeZone = TIME_ZONE,
): string {
  const zone = resolveTimeZone(timeZone),
    wall = parseLocal(value),
    nominal = utcScalar(wall),
    format = formatter(zone),
    offsets = new Set<number>();
  // Sample both sides of a possible transition, then strictly round-trip every candidate.
  for (let hours = -24; hours <= 24; hours++) {
    const sample = nominal + hours * HOUR;
    offsets.add(
      utcScalar({ ...wallAt(sample, format), millisecond: 0 }) -
        Math.floor(sample / 1000) * 1000,
    );
  }
  const candidates = [...offsets]
    .map((offset) => nominal - offset)
    .filter((instant) => sameWall(wallAt(instant, format), wall));
  if (candidates.length === 0)
    throw new Error(
      `该当地时间在 ${zone} 不存在（可能处于夏令时跳变），请选择其他时间。`,
    );
  if (candidates.length > 1)
    throw new Error(
      `该当地时间在 ${zone} 有歧义（可能处于夏令时回拨），请选择没有歧义的时间。`,
    );
  return new Date(candidates[0]!).toISOString();
}
export function formatTimestamp(value: string, timeZone = TIME_ZONE): string {
  if (!/T.+(?:Z|[+-]\d{2}:\d{2})$/i.test(value))
    throw new Error("服务端时间戳必须包含时区。");
  const instant = new Date(value);
  if (Number.isNaN(instant.getTime())) throw new Error("无效时间戳。");
  return new Intl.DateTimeFormat("zh-CN", {
    dateStyle: "short",
    timeStyle: "short",
    timeZone: resolveTimeZone(timeZone),
  }).format(instant);
}

/** Keep the release editor in the same configured zone as its displayed timestamp. */
export function isoToLocalDateTime(value: string, timeZone = TIME_ZONE): string {
  if (!/T.+(?:Z|[+-]\d{2}:\d{2})$/i.test(value))
    throw new Error("服务端时间戳必须包含时区。");
  const instant = new Date(value).getTime();
  if (Number.isNaN(instant)) throw new Error("无效时间戳。");
  const wall = wallAt(instant, formatter(resolveTimeZone(timeZone)));
  const part = (number: number, size = 2) => String(number).padStart(size, "0");
  return `${part(wall.year, 4)}-${part(wall.month)}-${part(wall.day)}T${part(wall.hour)}:${part(wall.minute)}`;
}
