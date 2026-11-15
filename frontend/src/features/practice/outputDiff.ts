/** Bounded, Unicode-aware character diff. Text is rendered by Vue, never as HTML. */
export interface DiffPart { text: string; changed: boolean }
export function outputDiff(actual: string, expected: string): { actual: DiffPart[]; expected: DiffPart[] } {
  const a = Array.from(actual.replace(/\r\n/g, '\n').trimEnd());
  const b = Array.from(expected.replace(/\r\n/g, '\n').trimEnd());
  let start = 0, endA = a.length, endB = b.length;
  while (start < endA && start < endB && a[start] === b[start]) start++;
  while (endA > start && endB > start && a[endA - 1] === b[endB - 1]) { endA--; endB--; }
  const left: DiffPart[] = [], right: DiffPart[] = [];
  const append = (list: DiffPart[], text: string, changed: boolean) => {
    if (!text) return;
    const last = list.at(-1);
    if (last?.changed === changed) last.text += text; else list.push({text, changed});
  };
  append(left, a.slice(0, start).join(''), false); append(right, b.slice(0, start).join(''), false);
  const n = endA - start, m = endB - start;
  // Large unrelated outputs fall back to a changed middle, avoiding quadratic UI work.
  if (n * m > 250000) {
    append(left, a.slice(start, endA).join(''), true); append(right, b.slice(start, endB).join(''), true);
  } else {
    const rows = Array.from({length:n + 1}, () => new Uint32Array(m + 1));
    for (let i = n - 1; i >= 0; i--) for (let j = m - 1; j >= 0; j--)
      rows[i]![j] = a[start + i] === b[start + j] ? rows[i + 1]![j + 1]! + 1 : Math.max(rows[i + 1]![j]!, rows[i]![j + 1]!);
    let i = 0, j = 0;
    while (i < n || j < m) {
      if (i < n && j < m && a[start + i] === b[start + j]) {
        append(left, a[start + i++]!, false); append(right, b[start + j++]!, false);
      } else if (i < n && (j === m || rows[i + 1]![j]! >= rows[i]![j + 1]!)) append(left, a[start + i++]!, true);
      else append(right, b[start + j++]!, true);
    }
  }
  append(left, a.slice(endA).join(''), false); append(right, b.slice(endB).join(''), false);
  return {actual:left, expected:right};
}
export function visibleDiffText(part: DiffPart): string {
  return part.changed ? part.text.replace(/ /g, '·').replace(/\t/g, '⇥').replace(/\n/g, '↵\n') : part.text;
}
