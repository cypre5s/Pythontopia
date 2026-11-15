"""Validate world interaction evidence and delegate learning completion checks."""

from fastapi import HTTPException

from app.course_content.evidence import validate_learning_evidence
from app.course_scope import require_course_content
from app.models import Npc, WorldFlag
from app.teaching.world.proximity import check_near


def validate_evidence(db, user, classroom_id, node, evidence):
    kind = node["type"]
    target = node.get("target")
    if kind in {"TALK_TO_NPC", "PLAY_DIALOGUE", "REPORT_BACK"}:
        npc = db.get(Npc, target)
        from app.teaching.world.runtime import npc_present

        if npc and not npc_present(db, user, classroom_id, npc):
            raise HTTPException(403, "当前NPC尚未出现")
        require_course_content(db, npc, classroom_id)
        if evidence.get("npc_key") != target:
            raise HTTPException(422, "交谈对象不匹配")
        check_near(db, user, classroom_id, (npc.map_key, npc.tile_x, npc.tile_y))
        if kind == "PLAY_DIALOGUE":
            root = node.get("dialogue_id", target)
            if evidence.get("dialogue_id") != root or not db.get(WorldFlag, (user.id, classroom_id, f"dialogue:completed:{target}:{root}")):
                raise HTTPException(422, "请先完成指定脚本对白")
    elif kind in {"REACH_LOCATION", "INTERACT_OBJECT"}:
        if kind == "INTERACT_OBJECT" and evidence.get("object_id") != target:
            raise HTTPException(422, "互动对象不匹配")
        from app.teaching.world.runtime import course_object_points

        points = course_object_points(db, user, classroom_id, target)
        if not points:
            raise HTTPException(422, "当前交互目标不可用")
        check_near(db, user, classroom_id, points, radius=2 if kind == "INTERACT_OBJECT" else 5)
    elif kind in {"STUDY_LESSON", "COMPLETE_PRACTICE", "PASS_ASSESSMENT"}:
        validate_learning_evidence(db, user, classroom_id, node, evidence)
    choices = node.get("choices", [])
    if choices and evidence.get("choice") not in {c["id"] for c in choices}:
        raise HTTPException(422, "请选择有效的剧情分支")
