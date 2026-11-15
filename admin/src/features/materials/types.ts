export interface MaterialPreview {
  title: string;
  kind: string;
  media_url?: string;
  pages: {
    id?: string;
    page_no?: number;
    title?: string;
    text?: string;
    content?: string;
  }[];
  notice?: string;
}
