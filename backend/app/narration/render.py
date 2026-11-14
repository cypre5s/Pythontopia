"""Render one PDF page to a bounded JPEG so the model can read what the text layer lost.

Measured on a real lecture deck: 85% of slides carry fewer than 200 characters of text,
and 51% effectively none. The image is therefore the primary input for `presentation`
material; the text layer only supplies anchors.

Poppler builds differ (the Windows 25.02 build has no `-jpeg`), so pages are rendered to
PNG and encoded here — uniform across platforms and keeps the quality knob in one place.
"""

import io
import shutil
import subprocess
import tempfile
from pathlib import Path

from ..models import Material
from ..storage import get_storage, storage_root

TARGET_WIDTH = 1600  # 16:9 -> 900 high, ~1.44M px, still inside the provider's per-image token cap
JPEG_QUALITY = 82
MAX_BYTES = 3 * 1024 * 1024
TIMEOUT_SECONDS = 60


def source_key(material: Material) -> str | None:
    """The converted preview is what students read, so it is what the teacher narrates."""
    content = material.content or {}
    preview = content.get("preview_key")
    if isinstance(preview, str) and preview.strip():
        return preview
    return material.storage_key


def render_page(material: Material, page_no: int) -> bytes | None:
    """Return JPEG bytes for one page, or None when the material has no readable source."""
    key = source_key(material)
    if not key or not key.lower().endswith(".pdf"):
        return None
    binary = shutil.which("pdftoppm")
    if binary is None:
        raise ValueError("缺少 PDF 渲染工具（poppler-utils），无法生成页图")
    storage = get_storage()
    with tempfile.TemporaryDirectory(prefix="narr-", dir=storage_root()) as tmp_name:
        tmp = Path(tmp_name)
        source = tmp / "source.pdf"
        storage.copy_to(key, source)
        prefix = tmp / f"page-{page_no}"
        command = [binary, "-png", "-f", str(page_no), "-l", str(page_no),
                   "-scale-to-x", str(TARGET_WIDTH), "-scale-to-y", "-1", str(source), str(prefix)]
        try:
            subprocess.run(command, check=True, timeout=TIMEOUT_SECONDS, capture_output=True)
        except subprocess.TimeoutExpired as exc:
            raise ValueError("页图渲染超时") from exc
        except subprocess.CalledProcessError as exc:
            raise ValueError("页图渲染失败，可能页码超出范围") from exc
        produced = sorted(tmp.glob("page-*.png"))
        if not produced:
            return None
        return _encode(produced[0])


def _encode(png: Path) -> bytes:
    """JPEG keeps a 1600x900 slide around 200KB, well under the request budget."""
    try:
        from PIL import Image
    except ImportError as exc:  # pragma: no cover - Pillow is a locked dependency
        raise ValueError("缺少图片编码依赖，无法生成页图") from exc
    buffer = io.BytesIO()
    with Image.open(png) as image:
        image.convert("RGB").save(buffer, format="JPEG", quality=JPEG_QUALITY, optimize=True)
    data = buffer.getvalue()
    if len(data) > MAX_BYTES:
        raise ValueError("页图过大，无法作为讲解输入")
    return data
