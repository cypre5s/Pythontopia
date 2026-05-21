export interface PageDto {
  pageId: number;
  documentId: number;
  chapterId: number | null;
  pageNo: number;
  title: string | null;
  contentMarkdown: string;
  excerpt: string | null;
}
