"""Private export receipts distinguish generated directories from supplied packages."""

import hashlib
import json
from pathlib import Path
from typing import TypedDict

from sqlalchemy.orm import Session

from app.course_packages.catalog import regular_reader
from app.course_packages.manifest import PackageError
from app.models import User


def receipt_path(root: Path, directory: str) -> Path:
    return root / ".export-receipts" / (hashlib.sha256(directory.encode()).hexdigest() + ".json")


def register_export(root: Path, directory: Path, classroom_id: int) -> None:
    if directory.parent.resolve() != root.resolve() or directory.is_symlink():
        raise PackageError("导出目录越界或使用链接")
    with regular_reader(directory / "manifest.yaml", root) as stream:
        digest = hashlib.sha256(stream.read()).hexdigest()
    receipt = receipt_path(root, directory.name)
    from app.course_packages.cleanup import _plain

    receipt.parent.mkdir(exist_ok=True)
    _plain(receipt.parent)
    if receipt.parent.is_symlink() or receipt.parent.resolve().parent != root.resolve():
        raise PackageError("导出记录目录无效")
    # A receipt is private server state; it is never copied into a portable package.
    with receipt.open("x", encoding="utf-8") as stream:
        json.dump({"directory": directory.name, "classroom_id": classroom_id, "manifest_sha256": digest}, stream)


def is_owned_export(root: Path, directory: Path, classroom_id: int) -> bool:
    try:
        with regular_reader(receipt_path(root, directory.name), root) as stream:
            receipt = json.loads(stream.read(4097))
        with regular_reader(directory / "manifest.yaml", root) as stream:
            digest = hashlib.sha256(stream.read()).hexdigest()
        return receipt == {"directory": directory.name, "classroom_id": classroom_id, "manifest_sha256": digest}
    except (OSError, ValueError, PackageError):
        return False


class DeleteResult(TypedDict):
    deleted: bool
    directory: str


def delete_export(classroom_id: int, package_key: str, db: Session, user: User) -> DeleteResult:
    from fastapi import HTTPException

    from app.course_packages.catalog import mounted_catalog
    from app.course_packages.cleanup import exchange_guard, remove_scratch
    from app.course_packages.contracts import LIMITS
    from app.course_packages.paths import exchange_root
    from app.course_packages.service import _teacher

    _teacher(db, user, classroom_id)
    root = exchange_root()
    with exchange_guard(root):
        try:
            items, _ = mounted_catalog(root, LIMITS)
            item = next((item for item in items if item["key"] == package_key), None)
            if item is None:
                raise HTTPException(404, "课程包已变化或不存在，请刷新列表")
            if not is_owned_export(root, item["path"], classroom_id):
                raise HTTPException(403, "只能删除本班有导出记录的课程包，默认课程包不可删除")
            # Reject every link/junction before removal; learner and import staging are separate.
            remove_scratch(root, item["path"])
            remove_scratch(root, receipt_path(root, item["directory"]))
            return {"deleted": True, "directory": item["directory"]}
        except (OSError, PackageError) as exc:
            raise HTTPException(422, "课程包删除失败，请刷新列表后重试") from exc
