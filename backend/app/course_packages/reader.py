"""Load a staged package and resolve its validated content and media."""

import json

import yaml
from yaml.tokens import AliasToken, AnchorToken

from app.course_instances import MODEL_NAMES
from app.course_packages.contracts import CONFIG_FILES, JSONL_FILES, LIMITS
from app.course_packages.manifest import ManifestLoader, PackageError, unique_json_pairs, validate_package
from app.course_packages.validation import validate_bundle
from app.models import Question


def _load(path, *, json_lines=False):
    if not path.is_file() or path.stat().st_size > LIMITS.index_bytes:
        raise PackageError("课程配置缺失或超过16MiB")
    try:
        raw = path.read_text(encoding="utf-8-sig")
        if json_lines:
            values = [
                json.loads(line, object_pairs_hook=unique_json_pairs, parse_constant=lambda _: (_ for _ in ()).throw(ValueError()))
                for line in raw.splitlines()
                if line.strip()
            ]
            if len(values) > 10000:
                raise ValueError()
            return values
        for index, token in enumerate(yaml.scan(raw)):
            if index > 200000 or isinstance(token, (AliasToken, AnchorToken)):
                raise ValueError()
        return yaml.load(raw, Loader=ManifestLoader)
    except (UnicodeError, ValueError, yaml.YAMLError, RecursionError) as exc:
        raise PackageError("课程配置格式无效、含重复键或超限") from exc


def load_bundle(root):
    validate_package(root)
    accepted = {
        "manifest.yaml",
        "curriculum/course.yaml",
        "world/npcs.yaml",
        "assets/index.jsonl",
        "content/secrets/answers.jsonl",
        *JSONL_FILES.values(),
        *CONFIG_FILES.values(),
    }
    patterns = (
        "curriculum/chapters/*/chapter.yaml",
        "curriculum/chapters/*/quests/*.yaml",
        "curriculum/chapters/*/flows/*.yaml",
        "world/flows/*.yaml",
        "content/activities/*.yaml",
        "content/questions/*.jsonl",
        "narrations/*/pages.jsonl",
    )
    for pattern in patterns:
        accepted.update(path.relative_to(root).as_posix() for path in root.glob(pattern) if path.is_file())
    asset_rows = _load(root / "assets/index.jsonl", json_lines=True)
    accepted.update(row["path"] for row in asset_rows)
    if any(path.relative_to(root).as_posix() not in accepted for path in root.rglob("*") if path.is_file()):
        raise PackageError("课程包含未识别配置文件，不能静默丢弃")
    data = {kind: [] for kind in MODEL_NAMES.values()}
    data["chapter"] = [_load(path) for path in sorted(root.glob("curriculum/chapters/*/chapter.yaml"))]
    data["quest"] = [_load(path) for path in sorted(root.glob("curriculum/chapters/*/quests/*.yaml"))]
    data["story"] = [_load(path) for path in sorted(root.glob("curriculum/chapters/*/flows/*.yaml"))] + [
        _load(path) for path in sorted(root.glob("world/flows/*.yaml"))
    ]
    data["activity"] = [_load(path) for path in sorted(root.glob("content/activities/*.yaml"))]
    data["npc"] = _load(root / "world/npcs.yaml")
    for model, path in JSONL_FILES.items():
        if model is Question:
            data["question"] = [row for file in sorted((root / "content/questions").glob("*.jsonl")) for row in _load(file, json_lines=True)]
        else:
            data[MODEL_NAMES[model]] = _load(root / path, json_lines=True)
    secrets = _load(root / "content/secrets/answers.jsonl", json_lines=True)
    assets = asset_rows
    config = {key: _load(root / path) for key, path in CONFIG_FILES.items()}
    course = _load(root / "curriculum/course.yaml")
    manifest = _load(root / "manifest.yaml")
    bundle = {"data": data, "secrets": secrets, "assets": assets, "configuration": config, "course": course, "manifest": manifest}
    from app.course_packages.narration_contract import directory

    bundle["narrations"] = []
    for path in sorted(root.glob("narrations/*/pages.jsonl")):
        pages = _load(path, json_lines=True)
        for row in pages:
            if not isinstance(row, dict) or not isinstance(row.get("material_id"), str) or path.parent != root / directory(row["material_id"]):
                raise PackageError("讲稿目录与资料稳定ID不匹配")
        bundle["narrations"].extend(pages)
    if len(bundle["narrations"]) > 10000:
        raise PackageError("课程讲稿页数超限")
    validate_bundle(bundle, root)
    return bundle
