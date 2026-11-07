/** Move the former numeric-suffix cache to the current material key, once. */
export function migrateReadingPosition(
  storage: Storage,
  userId: number,
  classroomId: number,
  materialId: number,
) {
  const current = `read:${userId}:${classroomId}:material-${materialId}`;
  const pattern = new RegExp(`^${current}-(\\d+)(:(?:pdf|video))?$`);
  const previous: { key: string; number: number; suffix: string }[] = [];
  for (let index = 0; index < storage.length; index++) {
    const key = storage.key(index);
    const match = key?.match(pattern);
    if (key && match) previous.push({ key, number: Number(match[1]), suffix: match[2] ?? "" });
  }
  previous.sort((a, b) => b.number - a.number);
  for (const entry of previous) {
    const target = current + entry.suffix;
    const value = storage.getItem(entry.key);
    if (storage.getItem(target) === null && value !== null) storage.setItem(target, value);
  }
  for (const entry of previous) storage.removeItem(entry.key);
}

/** Text readers store an array index; reuse its actual page number when PDF arrives. */
export function pdfResumePage(
  pages: { page_no: number }[],
  requested: number | null | undefined,
  savedPdf: string | null,
  textIndex: number,
) {
  const candidates = [requested, savedPdf === null ? undefined : Number(savedPdf), pages[textIndex]?.page_no, 1];
  return candidates.find((page): page is number =>
    typeof page === "number" && Number.isInteger(page) && page > 0 &&
    (!pages.length || pages.some(item => item.page_no === page)),
  ) ?? 1;
}
