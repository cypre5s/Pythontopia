#!/usr/bin/env python3
"""Crawl Chaoxing (超星/学习通) task-point videos into ``video_manifest.json``.

Pipeline: chapter knowledge nodes -> ``knowledge/cards`` (parse the embedded ``mArg``
JSON for video ``objectid``s) -> ``ananas/status/{objectid}`` (resolve mp4 URL + duration)
-> download into ``data/course-pack-43/videos/`` -> write ``data/course-pack-43/video_manifest.json``.
The manifest is what the backend ``CourseVideoController`` serves; the mp4 bytes stay local
(``videos/`` is gitignored).

The cookie is read from ``secrets/chaoxing.cookie`` (gitignored). It is NEVER printed or
hardcoded.

IMPORTANT CAVEATS
-----------------
(a) ``document.cookie`` lacks HttpOnly cookies so a copy-pasted browser cookie may be
    insufficient for mooc1 server auth — export a COMPLETE cookie (incl. HttpOnly) from
    DevTools (Application > Cookies, or the Network tab request headers).
(b) the new "v2" course portal (mooc2-ans) loads the chapter tree via JS-injected ajax
    (``mooc2-ans/mycourse/studentcourse?...&stuenc=...``), so for v2 courses the node
    enumeration may need the browser session (this script's classic-portal scrape will
    come up empty and you must feed it a session that resolves the v2 tree).
(c) the bundled default course (Python语言程序设计及AI应用) has NO task-point videos
    (章节 empty, 资料 empty) — verified — so this script yields an empty manifest for it
    and is intended for courses that DO have task-point videos.

The script never crashes on empty results: it always writes a valid JSON array (``[]``).

Usage
-----
    python3 crawl_chaoxing_videos.py                 # full crawl + download (defaults)
    python3 crawl_chaoxing_videos.py --probe         # dry run, write nothing
    python3 crawl_chaoxing_videos.py --manifest-only # resolve + record, skip downloads
    python3 crawl_chaoxing_videos.py --limit 3       # cap number of videos
    python3 crawl_chaoxing_videos.py --course-id 123 --clazz-id 456 --cpi 789
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import time
from pathlib import Path
from typing import Any

import httpx

COURSE_DIR = Path(__file__).resolve().parent
REPO_ROOT = COURSE_DIR.parents[1]
VIDEOS_DIR = COURSE_DIR / "videos"
MANIFEST_PATH = COURSE_DIR / "video_manifest.json"
COOKIE_PATH = REPO_ROOT / "secrets" / "chaoxing.cookie"

DEFAULT_COURSE_ID = "256810003"
DEFAULT_CLAZZ_ID = "131320251"
DEFAULT_CPI = "337240930"

BASE = "https://mooc1.chaoxing.com"
USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
)


def log(message: str) -> None:
    """Progress goes to stderr so stdout stays clean for piping."""
    print(message, file=sys.stderr)


def load_cookie() -> str:
    """Read the Chaoxing cookie from secrets/. Returns "" if absent; never logs its value."""
    if not COOKIE_PATH.is_file():
        log(f"[warn] cookie file not found at {COOKIE_PATH} (continuing anonymously)")
        return ""
    cookie = COOKIE_PATH.read_text(encoding="utf-8").strip()
    if not cookie:
        log("[warn] cookie file is empty (continuing anonymously)")
    return cookie


def make_client(cookie: str) -> httpx.Client:
    headers = {"User-Agent": USER_AGENT, "Referer": f"{BASE}/"}
    if cookie:
        headers["Cookie"] = cookie
    return httpx.Client(headers=headers, timeout=30.0, follow_redirects=True)


def _balanced_json_objects(html: str, marker: str) -> list[str]:
    """Yield the brace-balanced JSON object literal following each ``marker`` occurrence.

    A naive regex cannot capture nested braces, so we scan with a small brace counter that
    is aware of quoted strings / escapes.
    """
    out: list[str] = []
    cursor = 0
    while True:
        hit = html.find(marker, cursor)
        if hit < 0:
            break
        start = html.find("{", hit)
        if start < 0:
            break
        depth = 0
        in_str = False
        escaped = False
        i = start
        end = -1
        while i < len(html):
            ch = html[i]
            if in_str:
                if escaped:
                    escaped = False
                elif ch == "\\":
                    escaped = True
                elif ch == '"':
                    in_str = False
            elif ch == '"':
                in_str = True
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    end = i
                    break
            i += 1
        if end < 0:
            break
        out.append(html[start : end + 1])
        cursor = end + 1
    return out


def fetch_nodes(client: httpx.Client, course_id: str, clazz_id: str, cpi: str) -> list[str]:
    """Best-effort: scrape distinct knowledge node ids from the classic study page.

    Returns [] for v2 (mooc2-ans) courses whose tree is ajax-injected (see caveat b) and for
    courses with no chapters (see caveat c).
    """
    try:
        resp = client.get(
            f"{BASE}/mycourse/studentcourse",
            params={"courseId": course_id, "clazzid": clazz_id, "cpi": cpi, "ut": "s"},
        )
        resp.raise_for_status()
    except httpx.HTTPError as exc:
        log(f"[warn] knowledge node list fetch failed: {exc}")
        return []
    seen: set[str] = set()
    nodes: list[str] = []
    for match in re.finditer(r"knowledgeId=(\d+)", resp.text):
        node_id = match.group(1)
        if node_id not in seen:
            seen.add(node_id)
            nodes.append(node_id)
    return nodes


def fetch_card_videos(
    client: httpx.Client, course_id: str, clazz_id: str, cpi: str, knowledge_id: str
) -> list[dict[str, str]]:
    """Parse a node's cards page ``mArg`` JSON for insert-video attachments."""
    try:
        resp = client.get(
            f"{BASE}/knowledge/cards",
            params={
                "clazzid": clazz_id,
                "courseid": course_id,
                "knowledgeid": knowledge_id,
                "num": 0,
                "ut": "s",
                "cpi": cpi,
                "v": 20160407,
            },
        )
        resp.raise_for_status()
    except httpx.HTTPError as exc:
        log(f"[warn] cards fetch failed for node {knowledge_id}: {exc}")
        return []

    videos: list[dict[str, str]] = []
    for blob in _balanced_json_objects(resp.text, "mArg"):
        try:
            data = json.loads(blob)
        except json.JSONDecodeError:
            continue
        for attachment in data.get("attachments") or []:
            prop = attachment.get("property") or {}
            module = str(prop.get("module") or attachment.get("type") or "").lower()
            objectid = (
                prop.get("objectid")
                or attachment.get("objectId")
                or attachment.get("objectid")
            )
            if not objectid:
                continue
            if module and "video" not in module:
                continue
            title = prop.get("name") or prop.get("title") or data.get("name") or ""
            videos.append({"objectid": str(objectid), "title": str(title)})
    return videos


def resolve_video(client: httpx.Client, objectid: str) -> dict[str, Any] | None:
    """Resolve mp4 URL + duration via the ananas status endpoint."""
    try:
        resp = client.get(
            f"{BASE}/ananas/status/{objectid}",
            params={"k": "", "flag": "normal", "_dc": int(time.time() * 1000)},
        )
        resp.raise_for_status()
        data = resp.json()
    except (httpx.HTTPError, json.JSONDecodeError, ValueError) as exc:
        log(f"[warn] status resolve failed for {objectid}: {exc}")
        return None
    url = data.get("download") or data.get("http")
    duration = data.get("duration")
    try:
        duration_sec = int(duration) if duration not in (None, "") else None
    except (TypeError, ValueError):
        duration_sec = None
    return {"url": url, "durationSec": duration_sec}


def download(client: httpx.Client, url: str, dest: Path) -> bool:
    try:
        dest.parent.mkdir(parents=True, exist_ok=True)
        with client.stream("GET", url) as resp:
            resp.raise_for_status()
            with dest.open("wb") as handle:
                for chunk in resp.iter_bytes(65536):
                    handle.write(chunk)
        return True
    except httpx.HTTPError as exc:
        log(f"[warn] download failed for {dest.name}: {exc}")
        dest.unlink(missing_ok=True)
        return False


def write_manifest(entries: list[dict[str, Any]]) -> None:
    MANIFEST_PATH.write_text(
        json.dumps(entries, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )


def crawl(client: httpx.Client, args: argparse.Namespace) -> list[dict[str, Any]]:
    nodes = fetch_nodes(client, args.course_id, args.clazz_id, args.cpi)
    log(f"[info] discovered {len(nodes)} knowledge node(s)")

    entries: list[dict[str, Any]] = []
    next_id = 1
    for chapter_idx, node_id in enumerate(nodes, start=1):
        for card in fetch_card_videos(client, args.course_id, args.clazz_id, args.cpi, node_id):
            if args.limit and len(entries) >= args.limit:
                return entries
            resolved = resolve_video(client, card["objectid"])
            if not resolved or not resolved["url"]:
                continue
            filename = f"ch{chapter_idx}_{next_id:03d}.mp4"
            if args.probe:
                log(
                    f"[probe] node={node_id} chapter={chapter_idx} objectid={card['objectid']} "
                    f"durationSec={resolved['durationSec']} -> {filename}"
                )
            elif not args.manifest_only:
                ok = download(client, resolved["url"], VIDEOS_DIR / filename)
                log(f"[info] {'downloaded' if ok else 'FAILED  '} {filename}")
            entries.append(
                {
                    "id": next_id,
                    "chapter": chapter_idx,
                    "title": card["title"] or f"第{chapter_idx}章 视频 {next_id}",
                    "objectid": card["objectid"],
                    "file": filename,
                    "durationSec": resolved["durationSec"],
                    "kcCodes": [],
                }
            )
            next_id += 1
    return entries


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Crawl Chaoxing task-point videos into video_manifest.json"
    )
    parser.add_argument("--course-id", default=DEFAULT_COURSE_ID)
    parser.add_argument("--clazz-id", default=DEFAULT_CLAZZ_ID)
    parser.add_argument("--cpi", default=DEFAULT_CPI)
    parser.add_argument(
        "--manifest-only",
        action="store_true",
        help="resolve + record manifest entries but do not download mp4 bytes",
    )
    parser.add_argument(
        "--probe",
        action="store_true",
        help="dry run: print what would be fetched, change no files",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=0,
        help="cap number of videos processed (0 = no cap)",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    cookie = load_cookie()
    try:
        with make_client(cookie) as client:
            entries = crawl(client, args)
    except httpx.HTTPError as exc:
        log(f"[error] crawl aborted: {exc}")
        entries = []

    if args.probe:
        log(f"[probe] {len(entries)} video(s) would be recorded; no files written")
        return 0

    write_manifest(entries)
    log(f"[info] wrote {len(entries)} entry(ies) to {MANIFEST_PATH}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
