export interface TeacherChapter {
  id: number;
  title: string;
  order_index: number;
  owner_id: number | null;
}
export interface KnowledgePoint {
  id: number;
  chapter_id: number;
  title: string;
  owner_id: number | null;
}
export function canEditCurriculum(
  role: string | undefined,
  userId: number | undefined,
  ownerId: number | null,
) {
  return role === "admin" || (ownerId !== null && ownerId === userId);
}
export function selectedChapterKnowledgePoints(
  points: KnowledgePoint[],
  chapterId: number,
  selected: number[],
) {
  const allowed = new Set(
    points.filter((p) => p.chapter_id === chapterId).map((p) => p.id),
  );
  return selected.filter((id) => allowed.has(id));
}
