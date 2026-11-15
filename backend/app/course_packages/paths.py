"""Bounded package paths rooted in the configured exchange directory."""

import os
from pathlib import Path

from app.config import ROOT
from app.course_packages.manifest import PackageError, relative_package_path


def exchange_root():
    root = Path(os.getenv("REBORN_COURSE_EXCHANGE_ROOT", str(ROOT / "data/course"))).resolve()
    root.mkdir(parents=True, exist_ok=True)
    return root


def exchange_path(key):
    root = exchange_root()
    path = (root / relative_package_path(key)).resolve()
    if not path.is_relative_to(root):
        raise PackageError("课程交换路径越界")
    path.parent.mkdir(parents=True, exist_ok=True)
    return path
