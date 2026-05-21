export interface KcDto {
  kcId: number;
  kcCode: string;
  kcName: string;
  chapterId: number;
  chapterOrder: number;
  difficulty: string | null;
  summaryMarkdown: string | null;
}
