#!/usr/bin/env python3
"""Browser smoke test for the Pythontopia workshop page."""

from __future__ import annotations

import argparse
import json
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

from playwright.sync_api import sync_playwright


def _json_request(method: str, url: str, payload: dict[str, Any]) -> dict[str, Any]:
    body = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=body,
        method=method,
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=20) as response:
        return json.loads(response.read().decode("utf-8"))


def _create_session(api_base_url: str) -> dict[str, Any]:
    suffix = int(time.time() * 1000)
    username = f"browser_smoke_{suffix}"
    password = "Pythontopia123"
    register_payload = {
        "username": username,
        "password": password,
        "displayName": "Browser Smoke",
        "role": "STUDENT",
        "email": f"{username}@example.test",
        "teacherInviteCode": None,
    }
    try:
        _json_request("POST", f"{api_base_url}/api/auth/register", register_payload)
    except urllib.error.HTTPError as exc:
        if exc.code != 409:
            raise
    return _json_request(
        "POST",
        f"{api_base_url}/api/auth/login",
        {"username": username, "password": password},
    )


def run(base_url: str, api_base_url: str, output_dir: Path) -> dict[str, Any]:
    output_dir.mkdir(parents=True, exist_ok=True)
    session = _create_session(api_base_url)
    screenshot_path = output_dir / "workshop_page.png"
    result_path = output_dir / "workshop_browser_smoke.json"
    console_errors: list[str] = []
    page_errors: list[str] = []

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page(viewport={"width": 1366, "height": 860})
        page.on(
            "console",
            lambda msg: console_errors.append(msg.text) if msg.type == "error" else None,
        )
        page.on("pageerror", lambda exc: page_errors.append(str(exc)))

        page.goto(base_url, wait_until="domcontentloaded")
        page.evaluate(
            """(session) => {
              localStorage.setItem('pythontopia.token', session.token);
              localStorage.setItem('pythontopia.expiresAt', session.expiresAt);
              localStorage.setItem('pythontopia.user', JSON.stringify(session.user));
            }""",
            session,
        )

        page.goto(f"{base_url}/workshop", wait_until="networkidle")
        page.get_by_test_id("workshop-page").wait_for(timeout=15_000)
        page.get_by_test_id("workshop-chat-pane").wait_for(timeout=15_000)

        page.get_by_test_id("workshop-open-study").click()
        page.get_by_test_id("workshop-drawer-study").wait_for(timeout=10_000)
        page.get_by_test_id("workshop-drawer-close").click()
        page.get_by_test_id("workshop-open-calls").click()
        page.get_by_test_id("workshop-drawer-calls").wait_for(timeout=10_000)

        title = page.locator('[data-testid="workshop-page"]').inner_text(timeout=10_000)
        page.screenshot(path=str(screenshot_path), full_page=True)
        browser.close()

    result = {
        "base_url": base_url,
        "user_id": session.get("user", {}).get("id"),
        "checks": {
            "workshop_page_visible": True,
            "chat_pane_visible": True,
            "study_drawer_visible": True,
            "calls_drawer_visible": True,
            "no_page_errors": len(page_errors) == 0,
        },
        "console_error_count": len(console_errors),
        "page_errors": page_errors,
        "title_text_sample": title[:240],
        "screenshot": str(screenshot_path),
    }
    result_path.write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return result


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:5173")
    parser.add_argument("--api-base-url", default="http://127.0.0.1:18080")
    parser.add_argument("--output-dir", required=True)
    args = parser.parse_args()
    result = run(args.base_url.rstrip("/"), args.api_base_url.rstrip("/"), Path(args.output_dir))
    print(json.dumps(result["checks"], ensure_ascii=False, sort_keys=True))
    if not all(result["checks"].values()):
        raise SystemExit(2)


if __name__ == "__main__":
    main()
