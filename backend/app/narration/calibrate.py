"""Whole-deck calibration: a rolling summary walks the whole material, then flags pages.

A 560-page course cannot be reviewed in one prompt (hundreds of thousands of tokens), so
the pass is a sliding window that carries a rolling summary and term table forward. The
output is a report, not silent rewrites: flagged pages are re-generated only when a
teacher asks for it, and approved pages are never edited behind their back.
"""

import json
import base64
from typing import Any, Callable, cast

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..models import Material, now
from . import client as model_client
from . import config, repository, render
from .contracts import AppliedCalibration, CalibrationIssue, CalibrationReport
from .models import NarrationCalibration, PageNarration

WINDOW = 4
SUMMARY_LIMIT = 1800
TERM_LIMIT = 40
HEAD_CHARS, TAIL_CHARS = 80, 40

SYSTEM = (
    "你在校对一份课程讲解稿的整体质量。只依据给出的原文、页面图像与讲解判断，不要虚构页面内容。"
    "只指出确实存在的问题；没问题就不要凑数。"
)

TEMPLATE = """检查整份讲解的三件事：
1. 前后是否连贯（同一概念是否前后说法冲突、是否有明显跳跃或重复）。
2. 与幻灯片原文是否一致（讲解是否讲了页面上没有的内容）。
3. 术语是否统一（同一个东西是否被叫成不同名字）。

你会收到：之前窗口的滚动摘要、已用术语表、本窗口每页讲解及原文，必要时附页图。
原文标记截断时只判断已给内容；没有原文或页图时不得判断 mismatch，不把补充说明自动当成错误。
只输出 JSON：{"summary": "更新后的滚动摘要，不超过400字", "terms": ["术语"], "issues": [{"page_no": 3, "kind": "flow|mismatch|term", "detail": "一句话说明"}]}
issues 最多 8 条，没有就给空数组。kind：flow=连贯性，mismatch=与页面不符，term=术语不统一。
"""


def page_payload(item: PageNarration, material: Material) -> dict[str, Any]:
    text = item.text.strip()
    source = repository.page_text(material, item.page_no) or ""
    return {"page_no": item.page_no, "status": item.status,
            "narration": text[:4000], "source_text": source[:2000], "source_truncated": len(source) > 2000}


def windows(items: list[PageNarration]) -> list[list[PageNarration]]:
    ordered = sorted(items, key=lambda row: row.page_no)
    return [ordered[start:start + WINDOW] for start in range(0, len(ordered), WINDOW)]


def latest(db: Session, material_id: int) -> NarrationCalibration | None:
    return db.scalar(select(NarrationCalibration).where(NarrationCalibration.material_id == material_id)
                     .order_by(NarrationCalibration.id.desc()))


def analyse(db: Session, material: Material, *, instance_id: int) -> NarrationCalibration:
    """One call per window; approved pages are reported but never rewritten here."""
    reason = config.unavailable_reason()
    if reason:
        raise RuntimeError(reason)
    items = [row for row in repository.rows(db, material.id) if row.text.strip() and row.status in {"draft", "accepted"}]
    if not items:
        raise LookupError("没有可校准的讲解，请先生成")
    settings = config.settings()
    summary: str = ""
    terms: list[str] = []
    issues: list[CalibrationIssue] = []
    groups = windows(items)
    revisions = {str(item.page_no): repository.revision(item) for item in items}
    for index, group in enumerate(groups):
        repository.ensure_lease(db, material.id)
        repository.beat(db, material.id, done=index, total=len(groups))
        db.commit()
        payload: dict[str, Any] = {"summary": summary, "terms": terms, "pages": [page_payload(item, material) for item in group]}
        images: list[dict[str, Any]] = []
        evidence = {p["page_no"] for p in payload["pages"] if p["source_text"].strip()}
        for page in payload["pages"]:
            if not page["source_text"].strip():
                image = render.render_page(material, page["page_no"])
                if image:
                    images.extend([{"type": "text", "text": f"第 {page['page_no']} 页原图"},
                                   {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64," + base64.b64encode(image).decode("ascii")}}])
                    evidence.add(page["page_no"])
                repository.beat(db, material.id, done=index, total=len(groups))
                db.commit()
        content = TEMPLATE + "\n\n" + json.dumps(payload, ensure_ascii=False)
        messages: list[dict[str, Any]] = [{"role": "system", "content": SYSTEM},
                    {"role": "user", "content": [{"type": "text", "text": content}, *images] if images else content}]
        # 校准回复的是 {summary, terms, issues}，没有 text 字段；默认的“必须有 text”校验会把它误判成空讲解。
        result = model_client.chat(messages, settings=settings, model=settings["vision_model"] if images else settings["model"], effort="low",
                                   # 一个 40 页窗口的回复比单页长得多，默认 8000 会被截断。
                                   max_tokens=2048, require_text=False)
        repository.ensure_lease(db, material.id)
        repository.beat(db, material.id, done=index + 1, total=len(groups))
        db.commit()
        reply = result.get("raw") or {}
        summary = str(reply.get("summary") or summary)[:SUMMARY_LIMIT]
        terms = [str(term)[:40] for term in (reply.get("terms") or terms or [])][:TERM_LIMIT]
        for issue in (reply.get("issues") or [])[:8]:
            if not isinstance(issue, dict):
                continue
            page_no = issue.get("page_no")
            kind = issue.get("kind")
            if page_no not in {item.page_no for item in group} or kind not in {"flow", "mismatch", "term"}:
                continue
            if kind == "mismatch" and page_no not in evidence:
                continue
            issues.append(CalibrationIssue(
                page_no=page_no,
                kind=str(issue.get("kind") or "flow")[:16],
                detail=str(issue.get("detail") or "")[:200],
                status="accepted" if any(item.page_no == page_no and item.status == "accepted"
                                         for item in items) else "draft",
            ))
    # The same page can be flagged by two windows; keep one entry per page and kind.
    unique: list[CalibrationIssue] = []
    keys: set[tuple[int, str]] = set()
    for issue in issues:
        key = (issue["page_no"], issue["kind"])
        if key in keys:
            continue
        keys.add(key)
        unique.append(issue)
    issues = unique
    report = CalibrationReport(windows=len(groups), pages=len(items), summary=summary, terms=terms,
                               issues=issues, revisions=revisions, generated_at=now().isoformat())
    entry = latest(db, material.id) or NarrationCalibration(material_id=material.id)
    if entry.id is None:
        db.add(entry)
    entry.report = cast(Any, report)
    entry.windows = len(groups)
    entry.created_at = now()
    entry.applied_at = None
    db.flush()
    return entry


def apply_report(db: Session, material: Material, entry: NarrationCalibration, *,
                 regenerate: Callable[[int], object]) -> AppliedCalibration:
    """Re-generate only flagged draft pages; approved pages stay untouched and listed."""
    report: dict[str, Any] = entry.report or {}
    issues: list[CalibrationIssue] = report.get("issues") or []
    approved = [issue["page_no"] for issue in issues if issue["status"] == "accepted"]
    targets = sorted({issue["page_no"] for issue in issues if issue["status"] == "draft"})
    done: list[int] = []
    failed: list[dict[str, Any]] = []
    for page_no in targets:
        item = repository.row(db, material.id, page_no)
        if item is not None:
            db.refresh(item)
        if (item is None or item.status != "draft" or
                repository.revision(item) != (report.get("revisions") or {}).get(str(page_no)) or
                item.page_hash != repository.source_hash(material, page_no)):
            approved.append(page_no)
            continue
        try:
            db.info["narration_expected_revision"] = repository.revision(item)
            regenerate(page_no)
            done.append(page_no)
        except Exception as exc:  # noqa: BLE001 -- one page must not abort the fix pass
            db.rollback()
            failed.append({"page_no": page_no, "error": str(exc)[:160]})
        finally:
            db.info.pop("narration_expected_revision", None)
        repository.ensure_lease(db, material.id)
        repository.beat(db, material.id, done=len(done) + len(failed), total=len(targets))
        db.commit()
    entry.applied_at = now()
    entry.applied_pages = {"regenerated": done, "failed": failed, "awaiting_teacher": approved}
    db.flush()
    return cast(AppliedCalibration, entry.applied_pages)
