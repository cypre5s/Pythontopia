"""BROWSE DOM 摘要去敏（§3.1 / §8.6 信息安全）。

把浏览器抓到的原始 DOM/HTML 片段清洗成**可读、去敏**的纯文本摘要，绝不回传：
- 脚本 / 样式 / 模板等可执行或噪声块（``<script>``/``<style>``/``<noscript>``/``<template>``/
  ``<svg>``/``<head>``，含其内容）；
- 隐藏内容（``aria-hidden="true"`` / 布尔 ``hidden`` 属性 / 内联 ``display:none`` 容器，
  以及 ``<input type="hidden">`` 隐藏表单域）；
- PII（邮箱 / 手机号 / 银行卡号 / 长数字身份串）。

纯函数、无 I/O、无外部依赖（stdlib ``re`` + ``html``）。所有正则在限长输入上单趟线性扫描，
无嵌套量词回溯风险；未闭合的危险块按"删到结尾"处理（宁可多删不可泄漏）。
"""

from __future__ import annotations

import html
import re

# 单步摘要默认上限（字符）。executor 再对整体 final_summary 二次截断。
DEFAULT_MAX_SUMMARY_CHARS = 600
# 原始输入硬上限：先截断再清洗，给正则扫描成本封顶（防超大 DOM）。
_RAW_INPUT_CAP = 200_000

# 可执行 / 噪声块（含内容一并删除；未闭合则删到结尾 \Z）。
_BLOCK_RE = re.compile(
    r"<(script|style|noscript|template|svg|head)\b[^>]*>.*?(?:</\1>|\Z)",
    re.IGNORECASE | re.DOTALL,
)

# 隐藏标记：布尔 hidden（独立属性）/ aria-hidden=true / 内联 display:none。
_HIDDEN_MARKER = (
    r"(?:\shidden(?=[\s/>=])"
    r"|aria-hidden\s*=\s*['\"]?true"
    r"|style\s*=\s*['\"][^'\"]*display\s*:\s*none)"
)
# 隐藏容器（含内容删除；未闭合删到结尾）。限定常见块级/行内容器，避免误伤。
_HIDDEN_CONTAINER_RE = re.compile(
    r"<(div|span|section|article|aside|details|p|ul|ol|li|table|tr|td|form|fieldset)\b"
    r"[^>]*" + _HIDDEN_MARKER + r"[^>]*>.*?(?:</\1>|\Z)",
    re.IGNORECASE | re.DOTALL,
)
# 隐藏表单域（void 元素，去掉整个标签即去掉其 value）。
_HIDDEN_INPUT_RE = re.compile(
    r"<input\b[^>]*\btype\s*=\s*['\"]?hidden['\"]?[^>]*>",
    re.IGNORECASE,
)
# 残余标签。
_TAG_RE = re.compile(r"<[^>]+>")
_WS_RE = re.compile(r"\s+")

# ── PII 去敏 ──
_EMAIL_TOKEN = "[已隐去:邮箱]"
_PHONE_TOKEN = "[已隐去:电话]"
_CARD_TOKEN = "[已隐去:卡号]"
_NUMBER_TOKEN = "[已隐去:数字串]"

_EMAIL_RE = re.compile(r"[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}")
# 4-4-4-4 分组银行卡（空格或连字符分隔）。
_CARD_RE = re.compile(r"(?<!\d)(?:\d{4}[ \-]){3}\d{4}(?!\d)")
# 12+ 连续数字（身份证 / 卡号 / 长 id）。
_LONG_DIGITS_RE = re.compile(r"(?<!\d)\d{12,}(?!\d)")
# 中国大陆手机号（11 位，1[3-9] 开头，前后非数字）。
_CN_MOBILE_RE = re.compile(r"(?<!\d)1[3-9]\d{9}(?!\d)")
# 国际电话（'+' 前缀，含分隔符，≥8 位数字总长）。
_INTL_PHONE_RE = re.compile(r"\+\d[\d\s().\-]{6,}\d")


def redact_pii(text: str) -> str:
    """对常见 PII 打码（邮箱 / 卡号 / 长数字 / 手机 / 国际电话）。"""
    text = _EMAIL_RE.sub(_EMAIL_TOKEN, text)
    text = _CARD_RE.sub(_CARD_TOKEN, text)
    text = _LONG_DIGITS_RE.sub(_NUMBER_TOKEN, text)
    text = _CN_MOBILE_RE.sub(_PHONE_TOKEN, text)
    text = _INTL_PHONE_RE.sub(_PHONE_TOKEN, text)
    return text


def strip_html(raw: str) -> str:
    """删除脚本/样式/隐藏块 + 残余标签 → 纯文本（保证无尖括号残留）。"""
    text = _BLOCK_RE.sub(" ", raw)
    text = _HIDDEN_CONTAINER_RE.sub(" ", text)
    text = _HIDDEN_INPUT_RE.sub(" ", text)
    text = _TAG_RE.sub(" ", text)
    # 反转义实体后可能重新出现标签 / 尖括号：再剥一次并清掉残余尖括号（防再注入）。
    text = html.unescape(text)
    text = _TAG_RE.sub(" ", text)
    text = text.replace("<", " ").replace(">", " ")
    return text


def _truncate(text: str, max_chars: int) -> str:
    if max_chars <= 0:
        return ""
    if len(text) <= max_chars:
        return text
    ellipsis = "…"
    if max_chars <= len(ellipsis):
        return text[:max_chars]
    return text[: max_chars - len(ellipsis)].rstrip() + ellipsis


def sanitize_dom_summary(raw: str, max_chars: int = DEFAULT_MAX_SUMMARY_CHARS) -> str:
    """把原始 DOM/HTML 片段清洗为去敏可读摘要（脚本/隐藏域/PII 不回传 + 截断）。"""
    if not raw or not raw.strip():
        return ""
    text = raw[:_RAW_INPUT_CAP]
    text = strip_html(text)
    text = redact_pii(text)
    text = _WS_RE.sub(" ", text).strip()
    return _truncate(text, max_chars)
