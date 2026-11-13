import MarkdownIt from "markdown-it";

// Model text is untrusted: raw HTML and automatic image requests stay disabled.
const markdown = new MarkdownIt({ html: false, linkify: false, breaks: true });
markdown.disable("image");
markdown.validateLink = url => /^https?:\/\//i.test(url);
export function renderAssistantMarkdown(text: string): string {
  return markdown.render(text);
}
