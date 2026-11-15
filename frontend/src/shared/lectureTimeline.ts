export type LectureSegment = { pageNo: number; start: number; seconds: number };

export function lectureTimeline(pages: number[], durations: Record<number, number>): LectureSegment[] {
  let start = 0;
  const segments: LectureSegment[] = [];
  for (const pageNo of pages) {
    const seconds = durations[pageNo];
    // Never invent timestamps from target speaking lengths or unknown audio metadata.
    if (!seconds || !Number.isFinite(seconds) || seconds <= 0) return [];
    segments.push({ pageNo, start, seconds });
    start += seconds;
  }
  return segments;
}

export function lectureTarget(segments: LectureSegment[], time: number) {
  const last = segments.at(-1);
  if (!last) return undefined;
  const safe = Math.max(0, Math.min(time, last.start + last.seconds));
  const segment = segments.find(item => safe < item.start + item.seconds) ?? last;
  return { pageNo: segment.pageNo, offset: safe - segment.start };
}

export function lectureStamp(seconds: number) {
  const total = Math.max(0, Math.floor(seconds || 0));
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
}
