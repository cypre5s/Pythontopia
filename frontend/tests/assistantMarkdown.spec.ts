import { expect, it } from "vitest";
import { renderAssistantMarkdown } from "../src/shared/assistantMarkdown";

it("renders Markdown structure but never raw HTML, executable links or image requests", () => {
  const rendered = renderAssistantMarkdown('**提示**\n\n1. 读取输入\n2. 打印结果\n\n```python\nprint("你好")\n```\n\n<script>alert(1)</script>\n\n[危险](javascript:alert(1))\n![远程](https://example.com/track.png)\n\n[文档](https://docs.python.org)');
  expect(rendered).toContain('<strong>提示</strong>');
  expect(rendered).toContain('<ol>');
  expect(rendered).toContain('class="language-python"');
  expect(rendered).not.toMatch(/<script|<img|href="javascript:/);
  expect(rendered).toContain('&lt;script&gt;');
  expect(rendered).toContain('href="https://docs.python.org"');
});
