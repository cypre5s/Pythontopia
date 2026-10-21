// 后端答疑图解（kind=svg）与 mermaid 渲染产物的前端消毒：DOMPurify svg profile（纵深防御第二道）。
// 服务端（tutor_agent._sanitize_svg）已黑名单整体拒绝过一次；这里再白名单清洗，
// 双保险确保 v-html 渲染的 SVG 无脚本 / 无事件属性 / 无外链 / 无嵌入内容。

import DOMPurify from "dompurify";

// 脚本与 on* 事件属性由 DOMPurify 白名单本身兜住；这里显式封掉的是「不需要脚本也能作恶」的面。
//
// style 必须显式禁：DOMPurify 的 SVG 属性白名单含 style，且 style 在 DEFAULT_URI_SAFE_ATTRIBUTES
// 里（值不做 URI 检查），于是
//   <rect style="position:fixed;inset:0;z-index:99999;background:url(https://attacker/leak)">
// 既能全屏覆盖做点击劫持，又能发出外部请求——而这些 SVG 是 LLM 生成的、消毒结果直接进 v-html。
const STRICT_FORBID_TAGS = [
  "script",
  "foreignObject",
  "style",
  "image",
  "iframe",
  "embed",
  "link",
  "a",
];
const STRICT_FORBID_ATTR = ["href", "xlink:href", "style"];
const SVG_URL_REFERENCE_ATTRIBUTES = [
  "clip-path",
  "color-profile",
  "cursor",
  "fill",
  "filter",
  "marker",
  "marker-end",
  "marker-mid",
  "marker-start",
  "mask",
  "stroke",
];
const SVG_URL_REFERENCE = /url\s*\(\s*(?:(["'])(.*?)\1|([^)]*?))\s*\)/gi;
const LOCAL_SVG_FRAGMENT = /^#[A-Za-z_][A-Za-z0-9_.:-]*$/;
const BLOCKED_CSS_PROPERTIES = new Set([
  "position",
  "inset",
  "inset-block",
  "inset-block-end",
  "inset-block-start",
  "inset-inline",
  "inset-inline-end",
  "inset-inline-start",
  "top",
  "right",
  "bottom",
  "left",
  "z-index",
  "transform",
  "transform-origin",
  "overflow",
]);
const BLOCKED_CSS_VALUES =
  /[\\]|url\s*\(|expression\s*\(|javascript\s*:|var\s*\(|-moz-binding|@import/i;

export function sanitizeSvgMarkup(source: string): string {
  const sanitized = DOMPurify.sanitize(source, {
    USE_PROFILES: { svg: true, svgFilters: true },
    FORBID_TAGS: STRICT_FORBID_TAGS,
    FORBID_ATTR: STRICT_FORBID_ATTR,
  });
  const document = new DOMParser().parseFromString(sanitized, "image/svg+xml");
  const svg = document.documentElement;
  if (svg.localName !== "svg") return "";
  removeExternalSvgReferences(svg);
  return svg.outerHTML;
}

/**
 * mermaid 渲染产物专用档。
 *
 * Mermaid 的 classDef 和 init.themeCSS 可以生成任意 CSS；securityLevel='strict' 不会删除它们。
 * 因此保留安全的绘图样式，同时用 CSSOM 丢弃定位覆盖、外部资源和非 SVG 作用域规则。
 *
 * 配套要求：调用方必须以 `htmlLabels: false` 渲染。mermaid 默认把节点标签塞进 foreignObject，
 * 而 foreignObject 在 DOMPurify 的默认 FORBID_CONTENTS 里（连同内容一起丢弃），
 * 否则整图文字会全部消失。
 */
export function sanitizeMermaidSvgMarkup(source: string): string {
  const sanitized = DOMPurify.sanitize(source, {
    USE_PROFILES: { svg: true, svgFilters: true },
    FORBID_TAGS: [
      "script",
      "foreignObject",
      "image",
      "iframe",
      "embed",
      "link",
      "a",
    ],
    FORBID_ATTR: ["href", "xlink:href"],
  });
  const document = new DOMParser().parseFromString(sanitized, "image/svg+xml");
  const svg = document.documentElement;
  if (svg.localName !== "svg") return "";

  const svgId = svg.getAttribute("id") ?? "";
  document.querySelectorAll("style").forEach((style) => {
    const safeCss = sanitizeMermaidStylesheet(style.textContent ?? "", svgId);
    if (safeCss) style.textContent = safeCss;
    else style.remove();
  });
  const inlineStyleParser = document.createElementNS(
    "http://www.w3.org/1999/xhtml",
    "div",
  ) as HTMLDivElement;
  document.querySelectorAll<SVGElement>("[style]").forEach((element) => {
    inlineStyleParser.setAttribute(
      "style",
      element.getAttribute("style") ?? "",
    );
    sanitizeCssDeclarations(inlineStyleParser.style);
    if (inlineStyleParser.style.length) {
      element.setAttribute("style", inlineStyleParser.style.cssText);
    } else element.removeAttribute("style");
  });

  removeExternalSvgReferences(svg);
  return svg.outerHTML;
}

function removeExternalSvgReferences(svg: Element): void {
  const elements = [svg, ...Array.from(svg.querySelectorAll<SVGElement>("*"))];
  for (const element of elements) {
    for (const attribute of SVG_URL_REFERENCE_ATTRIBUTES) {
      const value = element.getAttribute(attribute);
      if (value === null) continue;
      // Backslash escapes can spell `url` without leaving a literal URL token.
      if (value.includes("\\")) {
        element.removeAttribute(attribute);
        continue;
      }
      const references = Array.from(value.matchAll(SVG_URL_REFERENCE));
      if (
        references.some((match) => {
          const target = (match[2] ?? match[3] ?? "").trim();
          return !LOCAL_SVG_FRAGMENT.test(target);
        })
      ) {
        element.removeAttribute(attribute);
      }
    }
  }
}

function sanitizeMermaidStylesheet(source: string, svgId: string): string {
  if (!/^[A-Za-z_][A-Za-z0-9_-]*$/.test(svgId)) return "";
  try {
    const stylesheet = new CSSStyleSheet();
    stylesheet.replaceSync(source);
    return Array.from(stylesheet.cssRules)
      .map((rule) => sanitizeMermaidCssRule(rule, svgId))
      .filter(Boolean)
      .join("");
  } catch {
    return "";
  }
}

function sanitizeMermaidCssRule(
  rule: CSSRule,
  svgId: string,
  inKeyframes = false,
): string {
  if (rule.type === CSSRule.STYLE_RULE) {
    const styleRule = rule as CSSStyleRule;
    if (!inKeyframes && !isSvgScopedSelector(styleRule.selectorText, svgId))
      return "";
    sanitizeCssDeclarations(styleRule.style);
    return styleRule.style.length ? styleRule.cssText : "";
  }
  if (rule.type === CSSRule.KEYFRAMES_RULE) {
    const keyframes = rule as CSSKeyframesRule;
    const frames = Array.from(keyframes.cssRules)
      .map((frame) => sanitizeMermaidCssRule(frame, svgId, true))
      .filter(Boolean)
      .join("");
    return frames ? `@keyframes ${keyframes.name}{${frames}}` : "";
  }
  return "";
}

function isSvgScopedSelector(selectorText: string, svgId: string): boolean {
  const prefix = `#${svgId}`;
  return selectorText.split(",").every((selector) => {
    const trimmed = selector.trim();
    if (!trimmed.startsWith(prefix)) return false;
    const boundary = trimmed[prefix.length];
    return boundary === undefined || " .:#>[+~".includes(boundary);
  });
}

function sanitizeCssDeclarations(style: CSSStyleDeclaration): void {
  for (let index = style.length - 1; index >= 0; index -= 1) {
    const property = style.item(index).toLowerCase();
    const value = style.getPropertyValue(property);
    if (
      BLOCKED_CSS_PROPERTIES.has(property) ||
      BLOCKED_CSS_VALUES.test(value)
    ) {
      style.removeProperty(property);
    }
  }
}
