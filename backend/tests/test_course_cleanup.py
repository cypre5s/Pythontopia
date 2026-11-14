from contextlib import nullcontext
from datetime import timedelta
import os
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
from uuid import uuid4

import pytest

from app.course_cleanup import cleanup_exchange, exchange_guard, remove_scratch
from app.course_exchange import exchange_root
from app.course_exchange_models import CourseImportPreview
from app.models import now
from test_course_exchange import exchange as exchange, export, preview, apply


def test_cleanup_expired_applied_and_orphans_preserves_live_data(client, db, teacher, exchange):
    source, target = exchange
    package = export(client, teacher, source)
    expired = preview(client, teacher, target.id, package)
    active = preview(client, teacher, target.id, package)
    completed = preview(client, teacher, target.id, package)
    request_id = str(uuid4())
    response = apply(client, teacher, target.id, completed, request_id)
    assert response.status_code == 200
    root = exchange_root()
    expired_row = db.get(CourseImportPreview, expired["id"])
    expired_row.expires_at = now() - timedelta(seconds=1)
    db.commit()
    expired_path = root / expired_row.storage_key
    completed_path = root / db.get(CourseImportPreview, completed["id"]).storage_key
    active_path = root / db.get(CourseImportPreview, active["id"]).storage_key
    orphan = root / "imports" / (uuid4().hex + ".zip")
    orphan.write_bytes(b"abandoned")
    recent = root / "imports" / uuid4().hex
    recent.mkdir()
    scratch = root / "course-catalog-abcdefgh"
    scratch.mkdir()
    (scratch / "partial").write_bytes(b"partial")
    for path in (orphan, scratch):
        os.utime(path, (1, 1))
    exports = list(root.glob("export-*"))
    assert cleanup_exchange(root, lambda: nullcontext(db)) == 4
    assert not expired_path.exists() and not completed_path.exists()
    assert not orphan.exists() and not scratch.exists()
    assert active_path.exists() and recent.exists()
    assert all(path.exists() for path in exports)
    retry = apply(client, teacher, target.id, completed, request_id)
    assert retry.status_code == 200 and retry.json()["duplicate"]
    assert apply(client, teacher, target.id, expired).status_code == 409
    assert cleanup_exchange(root, lambda: nullcontext(db)) == 0


def test_cleanup_skips_active_exchange_and_releases_lock_after_failure(tmp_path):
    with exchange_guard(tmp_path):
        with ThreadPoolExecutor() as pool:
            assert pool.submit(cleanup_exchange, tmp_path, lambda: pytest.fail("locked cleanup opened DB")).result(timeout=3) == 0
    with pytest.raises(RuntimeError):
        with exchange_guard(tmp_path):
            raise RuntimeError("request failed")
    with exchange_guard(tmp_path, blocking=False) as acquired:
        assert acquired


def test_exchange_lock_excludes_another_process(tmp_path):
    import subprocess
    import sys

    probe = (
        "from pathlib import Path\n"
        "import sys\n"
        "from app.course_cleanup import exchange_guard\n"
        "with exchange_guard(Path(sys.argv[1]), blocking=False) as acquired:\n"
        "    print(int(acquired))\n"
    )
    # CI 从仓库根运行 pytest，子进程的 cwd 不是 backend/，默认找不到 app；显式给 cwd 才与解释器一致。
    backend = Path(__file__).resolve().parents[1]

    def run():
        return subprocess.check_output(
            [sys.executable, "-c", probe, str(tmp_path)], cwd=backend, timeout=20,
        ).strip()
    with exchange_guard(tmp_path):
        assert run() == b"0"
    assert run() == b"1"


def test_cleanup_retries_failed_removal(client, db, teacher, exchange, monkeypatch):
    import app.course_cleanup as cleanup
    source, target = exchange
    item = preview(client, teacher, target.id, export(client, teacher, source))
    row = db.get(CourseImportPreview, item["id"])
    row.expires_at = now() - timedelta(seconds=1)
    db.commit()
    key = row.storage_key
    real_remove = cleanup.remove_scratch
    monkeypatch.setattr(cleanup, "remove_scratch", lambda *args: (_ for _ in ()).throw(OSError("busy")))
    assert cleanup_exchange(exchange_root(), lambda: nullcontext(db)) == 0
    assert row.storage_key == key
    monkeypatch.setattr(cleanup, "remove_scratch", real_remove)
    assert cleanup_exchange(exchange_root(), lambda: nullcontext(db)) == 1


def test_cleanup_rejects_outside_paths_and_links(tmp_path):
    root = tmp_path / "exchange"
    root.mkdir()
    outside = tmp_path / "keep"
    outside.mkdir()
    (outside / "file").write_bytes(b"keep")
    with pytest.raises(OSError):
        remove_scratch(root, outside)
    link = root / "linked"
    try:
        link.symlink_to(outside, target_is_directory=True)
    except OSError:
        pytest.skip("symlink creation unavailable")
    with pytest.raises(OSError):
        remove_scratch(root, link)
    assert (outside / "file").read_bytes() == b"keep"
