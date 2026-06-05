"""答疑「答后审查」节点：把资源流水线里的三道审查（引用 / 防幻觉 / 安全）

迁移到 TutorAgent 答案文本上，让答疑也真正经过 审查官 / 校对官 / 安全员，
而不再只有 馆员(RAG)+导师(LLM)。三个函数都是确定式规则（零 LLM 成本、不阻塞答疑），
与 ``nodes/{grounding_critic,hallucination_detector,safety_critic}`` 同源思路，
但作用对象是 markdown 答案 + 检索证据（``RagHit`` 列表）而非 ``ResourceDraft``。
"""

from __future__ import annotations

import re

from pythontopia_graph.nodes.evidence_fetcher import RagHit
from pythontopia_graph.schemas.tutor import GroundingResult, SafetyResult

# 答案里的课件引用标记：`[页 12]` / `[页12]`（SYSTEM_PROMPT 要求有证据时必须这样引用）。
_CITATION_PATTERN = re.compile(r"\[页\s*(\d+)\]")
# 统计型「数字幻觉」高发模式：百分比 与 4 位及以上整数（年份 / 大额统计）。
# 故意不校验小整数（如「3 种方法 / 10 分钟」属正常表述），以免误伤。
_STAT_NUMBER_PATTERN = re.compile(r"\d+(?:\.\d+)?%|\d{4,}")

# 泄答 / 歧视 / 危险代码关键词（与 nodes/safety_critic 对齐，作用于答案文本）。
ANSWER_LEAK_KEYWORDS = (
    "answer_key",
    "hidden_test",
    "hidden_test_cases",
    "标准答案",
    "完整答案",
)
DISCRIMINATION_KEYWORDS = ("种族歧视", "暴力分裂", "色情")
_PII_PATTERNS = [
    re.compile(r"\b1[3-9]\d{9}\b"),  # 手机号
    re.compile(r"\b\d{17}[0-9Xx]\b"),  # 身份证
    re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"),  # 邮箱
]
_DANGEROUS_CODE = ("os.system", "rm -rf", "shutil.rmtree")


def grounding_review(answer_md: str, evidence_chunks: list[RagHit]) -> GroundingResult:
    """审查官·引用审查：答案里的 ``[页 X]`` 必须落在检索到的证据页内。

    - 无证据 → 通过并标注 ``NO_EVIDENCE``（通识知识，前端显示「通识知识」）。
    - 有证据：逐条核对答案引用的页码是否真实命中证据页；命中外页 = 编造引用 → 不通过。
      答案没写任何 ``[页 X]`` 不判失败（导师有时给引导式回答），只拦「编造的页码」。
    """
    if not evidence_chunks:
        return GroundingResult(passed=True, reasons=["NO_EVIDENCE"])
    valid_pages = {hit.page_no for hit in evidence_chunks}
    cited = {int(m) for m in _CITATION_PATTERN.findall(answer_md or "")}
    invalid = sorted(page for page in cited if page not in valid_pages)
    if invalid:
        return GroundingResult(
            passed=False,
            reasons=[f"CITED_PAGE_NOT_IN_EVIDENCE:{page}" for page in invalid],
        )
    return GroundingResult(passed=True)


def hallucination_review(answer_md: str, evidence_chunks: list[RagHit]) -> GroundingResult:
    """校对官·防幻觉：答案里的统计型数字（百分比 / 4 位+整数）须出现在证据中。

    仅在有证据时校验（无证据视为通识知识，跳过）；命中疑似编造的统计数字 → 不通过。
    """
    if not evidence_chunks:
        return GroundingResult(passed=True)
    evidence_text = " ".join((hit.text or "") for hit in evidence_chunks)
    suspicious: set[str] = set()
    for token in _STAT_NUMBER_PATTERN.findall(answer_md or ""):
        digits = re.sub(r"\D", "", token)
        if digits and digits not in evidence_text:
            suspicious.add(token)
    if suspicious:
        return GroundingResult(
            passed=False,
            reasons=["UNGROUNDED_NUMBERS:" + ",".join(sorted(suspicious))],
        )
    return GroundingResult(passed=True)


def safety_review(answer_md: str) -> SafetyResult:
    """安全员·安全审查：泄答关键词 / 歧视内容 / PII / 危险代码。"""
    reasons: list[str] = []
    text = answer_md or ""
    lowered = text.lower()
    for keyword in ANSWER_LEAK_KEYWORDS:
        if keyword in lowered:
            reasons.append(f"ANSWER_LEAK:{keyword}")
    for keyword in DISCRIMINATION_KEYWORDS:
        if keyword in text:
            reasons.append(f"DISCRIMINATION:{keyword}")
    for pattern in _PII_PATTERNS:
        if pattern.search(text):
            reasons.append("PII_DETECTED")
            break
    for snippet in _DANGEROUS_CODE:
        if snippet in text:
            reasons.append("DANGEROUS_CODE")
            break
    return SafetyResult(passed=not reasons, reasons=reasons)
