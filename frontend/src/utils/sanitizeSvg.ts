// 后端答疑图解（kind=svg）与 mermaid 渲染产物的前端消毒：DOMPurify svg profile（纵深防御第二道）。
// 服务端（tutor_agent._sanitize_svg）已黑名单整体拒绝过一次；这里再白名单清洗，
// 双保险确保 v-html 渲染的 SVG 无脚本 / 无事件属性 / 无外链 / 无嵌入内容。

import DOMPurify from 'dompurify';

// 脚本与 on* 事件属性由 DOMPurify 白名单本身兜住；这里显式封掉的是「不需要脚本也能作恶」的面。
//
// style 必须显式禁：DOMPurify 的 SVG 属性白名单含 style，且 style 在 DEFAULT_URI_SAFE_ATTRIBUTES
// 里（值不做 URI 检查），于是
//   <rect style="position:fixed;inset:0;z-index:99999;background:url(https://attacker/leak)">
// 既能全屏覆盖做点击劫持，又能发出外部请求——而这些 SVG 是 LLM 生成的、消毒结果直接进 v-html。
const STRICT_FORBID_TAGS = [
  'script',
  'foreignObject',
  'style',
  'image',
  'iframe',
  'embed',
  'link',
  'a',
];
const STRICT_FORBID_ATTR = ['href', 'xlink:href', 'style'];

export function sanitizeSvgMarkup(source: string): string {
  return DOMPurify.sanitize(source, {
    USE_PROFILES: { svg: true, svgFilters: true },
    FORBID_TAGS: STRICT_FORBID_TAGS,
    FORBID_ATTR: STRICT_FORBID_ATTR,
  });
}

/**
 * mermaid 渲染产物专用档。
 *
 * mermaid 把 100% 的主题着色与尺寸放在它自己生成的 <style> 块与 style 属性里（生成的 CSS 规则
 * 全部以 `#<diagramId>` 前缀作用域化），用上面的严格档清洗会得到一张无填充无尺寸的骨架图，
 * 因此这一档保留 style，但同样封掉脚本 / foreignObject / 外链 / 外部资源引用。
 *
 * 配套要求：调用方必须以 `htmlLabels: false` 渲染。mermaid 默认把节点标签塞进 foreignObject，
 * 而 foreignObject 在 DOMPurify 的默认 FORBID_CONTENTS 里（连同内容一起丢弃），
 * 否则整图文字会全部消失。
 */
export function sanitizeMermaidSvgMarkup(source: string): string {
  return DOMPurify.sanitize(source, {
    USE_PROFILES: { svg: true, svgFilters: true },
    FORBID_TAGS: ['script', 'foreignObject', 'image', 'iframe', 'embed', 'link', 'a'],
    FORBID_ATTR: ['href', 'xlink:href'],
  });
}
