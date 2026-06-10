// 后端答疑图解（kind=svg）的前端消毒：DOMPurify svg profile（纵深防御第二道）。
// 服务端（tutor_agent._sanitize_svg）已黑名单整体拒绝过一次；这里再白名单清洗，
// 双保险确保 v-html 渲染的 SVG 无脚本 / 无事件属性 / 无外链 / 无嵌入内容。

import DOMPurify from 'dompurify';

export function sanitizeSvgMarkup(source: string): string {
  return DOMPurify.sanitize(source, {
    USE_PROFILES: { svg: true, svgFilters: true },
    FORBID_TAGS: ['script', 'foreignObject', 'style', 'image', 'iframe', 'embed', 'link', 'a'],
    FORBID_ATTR: ['href', 'xlink:href'],
  });
}
