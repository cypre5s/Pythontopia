"""Read teacher chapter and knowledge-point catalogues."""

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.content_relations import activity_questions
from app.models import Activity, Chapter, KnowledgePoint, Npc, User
from app.teacher_scope import teacher_instance_id


def teacher_courses(db: Session, user: User, classroom_id: int | None = None):
    instance_id = teacher_instance_id(db, user, classroom_id)
    return {
        "chapters": [
            {"id": c.id, "title": c.title, "order_index": c.order_index, "owner_id": c.owner_id}
            for c in db.scalars(
                select(Chapter)
                .where(Chapter.course_instance_id == instance_id)
                .where(True if user.role == "admin" else (Chapter.owner_id.is_(None)) | (Chapter.owner_id == user.id))
                .order_by(Chapter.order_index)
            )
        ],
        "knowledge_points": [
            {"id": k.id, "chapter_id": k.chapter_id, "title": k.title, "content": k.content, "owner_id": k.owner_id}
            for k in db.scalars(
                select(KnowledgePoint)
                .where(KnowledgePoint.course_instance_id == instance_id)
                .join(Chapter, Chapter.id == KnowledgePoint.chapter_id)
                .where(
                    True
                    if user.role == "admin"
                    else ((KnowledgePoint.owner_id.is_(None)) | (KnowledgePoint.owner_id == user.id))
                    & ((Chapter.owner_id.is_(None)) | (Chapter.owner_id == user.id))
                )
                .order_by(KnowledgePoint.id)
            )
        ],
        "activities": [
            {
                "id": a.id,
                "chapter_id": a.chapter_id,
                "title": a.title,
                "kind": a.kind,
                "npc_key": a.npc_key,
                "assistant_allowed": a.assistant_allowed,
                "learning": a.content.get("learning", {}),
                "material_id": a.material_id,
                "owner_id": a.owner_id,
                "knowledge_point_ids": a.content.get("knowledge_point_ids", []),
                "question_ids": [q.id for q in activity_questions(db, a)],
            }
            for a in db.scalars(
                select(Activity)
                .where(Activity.course_instance_id == instance_id)
                .where(True if user.role == "admin" else (Activity.owner_id.is_(None)) | (Activity.owner_id == user.id))
            )
        ],
        "npcs": [
            {"key": n.key, "name": n.name, "map_key": n.map_key, "tile_x": n.tile_x, "tile_y": n.tile_y}
            for n in db.scalars(select(Npc).where(Npc.course_instance_id == instance_id))
        ],
    }
