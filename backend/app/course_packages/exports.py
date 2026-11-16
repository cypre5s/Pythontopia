"""Delete shared exported packages without touching imported classroom content."""

from typing import TypedDict

from sqlalchemy.orm import Session

from app.course_packages.manifest import PackageError
from app.models import User


class DeleteResult(TypedDict):
    deleted: bool
    directory: str


def delete_export(classroom_id: int, package_key: str, db: Session, user: User) -> DeleteResult:
    from fastapi import HTTPException

    from app.course_packages.catalog import mounted_catalog
    from app.course_packages.cleanup import exchange_guard, remove_scratch
    from app.course_packages.contracts import LIMITS
    from app.course_packages.paths import exchange_root
    from app.course_packages.service import require_teacher

    require_teacher(db, user, classroom_id)
    root = exchange_root()
    with exchange_guard(root):
        try:
            items, _ = mounted_catalog(root, LIMITS)
            item = next((item for item in items if item["key"] == package_key), None)
            if item is None:
                raise HTTPException(404, "课程包已变化或不存在，请刷新列表")
            if not item["can_delete"]:
                raise HTTPException(403, "只能删除导出的课程包，默认课程包不可删除")
            # Reject every link/junction before removal; learner and import staging are separate.
            remove_scratch(root, item["path"])
            return {"deleted": True, "directory": item["directory"]}
        except (OSError, PackageError) as exc:
            raise HTTPException(422, "课程包删除失败，请刷新列表后重试") from exc
