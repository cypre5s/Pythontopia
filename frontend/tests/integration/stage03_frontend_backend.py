from __future__ import annotations

import time
from urllib.parse import urljoin

from PIL import Image
from playwright.sync_api import Page, expect, sync_playwright


BASE_URL = "http://127.0.0.1:5173"


def canvas_point(page: Page, world_x: float, world_y: float) -> tuple[float, float]:
    box = page.locator(".game-root canvas").bounding_box()
    assert box is not None
    return box["x"] + (world_x / 960) * box["width"], box["y"] + (world_y / 640) * box["height"]


def click_world(page: Page, world_x: float, world_y: float) -> None:
    x, y = canvas_point(page, world_x, world_y)
    page.mouse.click(x, y)


def assert_canvas_nonblank(page: Page) -> None:
    path = "/tmp/pythontopia_stage03_canvas.png"
    page.locator(".game-root canvas").screenshot(path=path)
    image = Image.open(path).convert("RGBA")
    sample_count = 0
    for x in range(0, image.width, 8):
        for y in range(0, image.height, 8):
            r, g, b, a = image.getpixel((x, y))
            if a > 0 and (r, g, b) != (0, 0, 0):
                sample_count += 1
    assert sample_count > 500


def backend_world_session(page: Page) -> dict:
    return page.evaluate(
        """async () => {
          const token = localStorage.getItem('pythontopia.token');
          const response = await fetch('/api/world/session', {
            headers: { Authorization: `Bearer ${token}` }
          });
          return response.json();
        }"""
    )


def run() -> None:
    username = f"stage03_{int(time.time())}"
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True)
        page = browser.new_page(viewport={"width": 1280, "height": 820})
        page.goto(urljoin(BASE_URL, "/register"))
        page.get_by_label("用户名").fill(username)
        page.get_by_label("显示名称").fill("阶段三学生")
        page.get_by_label("密码").fill("P@ssw0rd1")
        page.get_by_role("button", name="创建账号").click()
        page.wait_for_url("**/town", timeout=15_000)
        page.wait_for_selector(".game-root canvas", timeout=15_000)
        page.wait_for_load_state("networkidle")
        assert_canvas_nonblank(page)

        session = backend_world_session(page)
        assert session["dayIndex"] == 1
        assert session["weather"] == "SUNNY"

        click_world(page, 14 * 32 + 16, 9 * 32 + 16)
        expect(page.locator(".dialog-panel")).to_contain_text("[占位]")
        page.get_by_role("button", name="知道了").click()

        building_routes = [
            (160, 200, "/profile-room", "画像屋"),
            (464, 168, "/library", "图书馆"),
            (784, 232, "/board", "公告板"),
            (240, 520, "/training", "训练场"),
            (720, 520, "/teacher", "教师屋"),
        ]
        for x, y, route, heading in building_routes:
            page.goto(urljoin(BASE_URL, "/town"))
            page.wait_for_selector(".game-root canvas", timeout=15_000)
            click_world(page, x, y)
            page.wait_for_url(f"**{route}", timeout=10_000)
            expect(page.get_by_role("heading", name=heading)).to_be_visible()

        page.goto(urljoin(BASE_URL, "/town"))
        page.wait_for_selector(".game-root canvas", timeout=15_000)
        click_world(page, 480, 352)
        page.keyboard.press("ArrowRight")
        page.wait_for_timeout(250)
        page.keyboard.press("ArrowDown")
        page.wait_for_timeout(800)
        moved = backend_world_session(page)
        player = moved["player"]
        assert (player["tileX"], player["tileY"]) != (15, 11)

        page.reload()
        page.wait_for_selector(".game-root canvas", timeout=15_000)
        restored = backend_world_session(page)
        assert restored["player"]["tileX"] == player["tileX"]
        assert restored["player"]["tileY"] == player["tileY"]

        page.get_by_role("button", name="退出").click()
        page.wait_for_url("**/login", timeout=10_000)
        assert page.evaluate("localStorage.getItem('pythontopia.token')") is None
        browser.close()


if __name__ == "__main__":
    run()
