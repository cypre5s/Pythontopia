import type { Material } from "./types";
export interface LearningContext {
  page_no?: number;
  video_time?: number;
}
export function learningContext(
  material: Pick<Material, "kind" | "pages"> | undefined,
  pageNo: number | undefined,
  videoTime: number,
): LearningContext {
  if (material?.kind === "video") return { video_time: Math.max(0, videoTime) };
  if (
    pageNo !== undefined &&
    material?.pages.some((page) => page.page_no === pageNo)
  )
    return { page_no: pageNo };
  return {};
}
