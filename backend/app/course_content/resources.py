"""Ordered activity resources, their independent requirements and earned progress."""

from fastapi import HTTPException

from app.content_relations import material_in_chapter
from app.learning_rules import required_pages, validate_learning, video_watched, video_state_for_material
from app.models import ActivityMaterial, Material


def resource_payload(resource):
    return {"material_id": resource.material_id, "required": resource.required, "learning": resource.learning}


def validate_resources(rows, kind, resolve):
    if not isinstance(rows, list) or len(rows) > 64:
        raise ValueError("每个活动最多配置64份资料")
    ids = set()
    for row in rows:
        if not isinstance(row, dict) or set(row) != {"material_id", "required", "learning"}:
            raise ValueError("活动资料需要资料标识、必读开关和学习要求")
        if type(row["material_id"]) not in {str, int} or not row["material_id"] or row["material_id"] in ids or type(row["required"]) is not bool:
            raise ValueError("活动资料不能重复，必读开关必须为布尔值")
        ids.add(row["material_id"])
        material = resolve(row["material_id"])
        if material is None:
            raise ValueError("活动资料不存在")
        validate_learning({"learning": row["learning"]}, kind, material, resolve=True)
    if rows and kind in {"lesson", "reading"} and not any(row["required"] for row in rows):
        raise ValueError("阅读活动至少需要一份必读资料")


def set_resources(db, activity, rows):
    def resolve(key):
        if type(key) is not int or key <= 0:
            raise ValueError("活动资料标识必须为正整数")
        material = db.get(Material, key)
        if material and (material.course_instance_id != activity.course_instance_id
                         or not material_in_chapter(db, key, activity.chapter_id)
                         or material.owner_id not in {None, activity.owner_id}):
            raise HTTPException(403, "活动资料必须属于本班课程、教师及活动章节")
        return material
    validate_resources(rows, activity.kind, resolve)
    existing = {row.material_id: row for row in activity.resources}
    resources = []
    for index, values in enumerate(rows):
        row = existing.get(values["material_id"]) or ActivityMaterial(material_id=values["material_id"])
        row.required, row.learning, row.order_index = values["required"], values["learning"], index
        resources.append(row)
    activity.resources = resources


def learning_materials(db, activity):
    result = {}
    for row in activity.resources:
        material = db.get(Material, row.material_id)
        if not material or material.course_instance_id != activity.course_instance_id or not material_in_chapter(db, material.id, activity.chapter_id):
            raise HTTPException(409, "学习资料必须属于活动章节")
        try:
            validate_learning({"learning": row.learning}, activity.kind, material, resolve=True)
        except ValueError as exc:
            raise HTTPException(409, str(exc)) from exc
        result[material.id] = material
    return result


def resource_progress(resource, material, progress):
    required = required_pages(resource, material)
    media = (progress.media_progress or {}) if progress else {}
    visited = set(media.get("reading_pages", {}).get(str(material.id), []))
    state = video_state_for_material(media, material.id)
    watched = video_watched(media, material.id)
    video = resource.learning.get("video", {})
    segments = video.get("segments", [])
    covered = state.get("covered", [])
    passed = [index for index, segment in enumerate(segments) if any(
        start <= segment["start_seconds"] + .25 and end >= segment["end_seconds"] - .25 for start, end in covered)]
    seconds = video.get("minimum_seconds", 0)
    if material.kind == "video":
        done = watched >= seconds and len(passed) == len(segments) and bool(video or watched)
        portions = ([min(1, watched / seconds)] if seconds else []) + ([len(passed) / len(segments)] if segments else [])
    else:
        done = bool(required) and set(required).issubset(visited)
        portions = [len(set(required) & visited) / len(required)] if required else []
    position = media.get("reading_positions", {}).get(str(material.id))
    return {"material_id": material.id, "completed": done and material.status == "ready",
            "page_no": position, "required_page_ids": required, "visited_pages": sorted(visited & {p["id"] for p in material.content.get("pages", [])}),
            "read_count": len(set(required) & visited), "read_total": len(required),
            "watched_seconds": watched, "required_seconds": seconds, "completed_segments": passed,
            "fraction": sum(portions) / len(portions) if portions else float(done)}


def reading_progress(activity, progress, materials):
    rows = [resource_progress(row, materials[row.material_id], progress) for row in activity.resources]
    required = [state for row, state in zip(activity.resources, rows, strict=True) if row.required]
    return {"resources": rows, "completed": bool(required) and all(row["completed"] for row in required),
            "required_count": len(required), "completed_count": sum(row["completed"] for row in required),
            "fraction": sum(row["fraction"] for row in required) / len(required) if required else 0,
            "material_id": (progress.media_progress or {}).get("selected_material_id") if progress else None,
            "page_no": progress.page_no if progress else None, "visited_pages": progress.visited_pages if progress else []}
