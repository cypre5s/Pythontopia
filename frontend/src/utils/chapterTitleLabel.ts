/** 侧栏已显示「第 N 章」时，去掉标题里重复的「第X章：」前缀。 */
export function chapterTitleLabel(fullTitle: string): string {
  const stripped = fullTitle.replace(/^第[一二三四五六七八九十\d]+章[：:]\s*/, '');
  return stripped.trim() || fullTitle;
}
