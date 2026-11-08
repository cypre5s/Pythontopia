import secrets
from datetime import datetime, timezone
from typing import Annotated, Literal
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator
from sqlalchemy import select
from sqlalchemy.orm import Session
from .auth import as_utc, current_user, require_teacher
from .db import get_db
from .world_config import validate_point
from .models import (
    Activity,
    Chapter,
    ChapterRelease,
    Classroom,
    LearningEvent,
    LearningRecord,
    KnowledgePoint,
    Membership,
    Npc,
    PlayerQuest,
    Question,
    QuestionSecret,
    Submission,
    User,
)

router = APIRouter(tags=["classrooms"])


def own_classroom(db, user, classroom_id):
    classroom = db.get(Classroom, classroom_id)
    if not classroom:
        raise HTTPException(404, "班级不存在")
    if user.role != "admin" and classroom.teacher_id != user.id:
        raise HTTPException(403, "只能管理自己的班级")
    return classroom


def classroom_payload(item, user, teacher_name=None):
    payload = {"id": item.id, "name": item.name, "teacher_id": item.teacher_id}
    if teacher_name is not None:
        payload["teacher_name"] = teacher_name
    if item.teacher_id == user.id or user.role == "admin":
        payload["join_code"] = item.join_code
    return payload


@router.get("/classrooms")
def list_classrooms(db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    memberships = select(Membership.classroom_id).where(Membership.user_id == user.id)
    query = select(Classroom, User.name).join(User, User.id == Classroom.teacher_id)
    if user.role != "admin":
        query = query.where((Classroom.teacher_id == user.id) | Classroom.id.in_(memberships))
    return [classroom_payload(item, user, name) for item, name in db.execute(query)]


class ClassroomInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    name: str = Field(min_length=1, max_length=100)


@router.post("/classrooms", status_code=201)
def create_classroom(body: ClassroomInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    item = Classroom(name=body.name.strip(), teacher_id=user.id, join_code=secrets.token_urlsafe(9))
    db.add(item)
    db.commit()
    return classroom_payload(item, user, user.name)


class JoinInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    join_code: str = Field(min_length=4, max_length=24)


@router.post("/classrooms/join")
def join_classroom(body: JoinInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    classroom = db.scalar(select(Classroom).where(Classroom.join_code == body.join_code.strip()))
    if not classroom:
        raise HTTPException(404, "加入码无效")
    if not db.get(Membership, (user.id, classroom.id)):
        db.add(Membership(user_id=user.id, classroom_id=classroom.id))
        db.commit()
    teacher = db.get(User, classroom.teacher_id)
    return classroom_payload(classroom, user, teacher.name if teacher else None)


@router.delete("/teacher/classrooms/{classroom_id}/members/{student_id}")
def remove_member(classroom_id: int, student_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    own_classroom(db, user, classroom_id)
    item = db.get(Membership, (student_id, classroom_id))
    if item:
        db.delete(item)
        db.commit()
    return {"ok": True}


class ReleaseInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    status: Literal["published", "paused"] = "published"
    opens_at: datetime | None = None
    prerequisite_chapter_id: int | None = None

    @field_validator("opens_at")
    @classmethod
    def timezone_required(cls, value):
        if value and value.tzinfo is None:
            raise ValueError("开放时间必须包含时区")
        return value.astimezone(timezone.utc) if value is not None else None


@router.put("/teacher/classrooms/{classroom_id}/releases/{chapter_id}")
def release_chapter(
    classroom_id: int, chapter_id: int, body: ReleaseInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]
):
    classroom = own_classroom(db, user, classroom_id)
    chapter = db.get(Chapter, chapter_id)
    if not chapter:
        raise HTTPException(404, "章节不存在")
    if chapter.owner_id is not None and chapter.owner_id != classroom.teacher_id:
        raise HTTPException(403, "不能向本班发布其他教师的私有章节")
    if body.prerequisite_chapter_id == chapter_id or (body.prerequisite_chapter_id and not db.get(Chapter, body.prerequisite_chapter_id)):
        raise HTTPException(422, "前置章节无效")
    if body.prerequisite_chapter_id:
        prerequisite = db.get(Chapter, body.prerequisite_chapter_id)
        if prerequisite.owner_id is not None and prerequisite.owner_id != classroom.teacher_id:
            raise HTTPException(403, "不能引用其他教师的私有前置章节")
    visited = {chapter_id}
    previous = body.prerequisite_chapter_id
    while previous:
        if previous in visited:
            raise HTTPException(422, "章节前置不能成环")
        visited.add(previous)
        item = db.get(ChapterRelease, (classroom_id, previous))
        previous = item.prerequisite_chapter_id if item else None
    release = db.get(ChapterRelease, (classroom_id, chapter_id))
    if not release:
        release = ChapterRelease(classroom_id=classroom_id, chapter_id=chapter_id)
        db.add(release)
    for key, value in body.model_dump().items():
        setattr(release, key, value)
    db.commit()
    return {
        "classroom_id": classroom_id,
        "chapter_id": chapter_id,
        **body.model_dump(),
        "opens_at": as_utc(release.opens_at).isoformat() if release.opens_at is not None else None,
        "pause_policy": "deny_all_new_requests_preserve_history",
    }


@router.get("/teacher/courses")
def teacher_courses(db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    return {
        "chapters": [
            {"id": c.id, "title": c.title, "order_index": c.order_index, "owner_id": c.owner_id}
            for c in db.scalars(
                select(Chapter)
                .where(True if user.role == "admin" else (Chapter.owner_id.is_(None)) | (Chapter.owner_id == user.id))
                .order_by(Chapter.order_index)
            )
        ],
        "knowledge_points": [
            {"id": k.id, "chapter_id": k.chapter_id, "title": k.title, "content": k.content, "owner_id": k.owner_id}
            for k in db.scalars(
                select(KnowledgePoint)
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
            }
            for a in db.scalars(
                select(Activity) if user.role == "admin" else select(Activity).where((Activity.owner_id.is_(None)) | (Activity.owner_id == user.id))
            )
        ],
        "npcs": [{"key": n.key, "name": n.name, "map_key": n.map_key, "tile_x": n.tile_x, "tile_y": n.tile_y} for n in db.scalars(select(Npc))],
    }


@router.get("/teacher/classrooms/{classroom_id}/progress")
def progress(classroom_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    own_classroom(db, user, classroom_id)
    from .courses import chapter_completion_requirements

    requirements = chapter_completion_requirements(db, classroom_id)
    completed_by_user: dict[int, set[str]] = {}
    for user_id, quest_id in db.execute(
        select(PlayerQuest.user_id, PlayerQuest.quest_id).where(PlayerQuest.classroom_id == classroom_id, PlayerQuest.status == "complete")
    ):
        completed_by_user.setdefault(user_id, set()).add(quest_id)
    members = db.scalars(select(User).join(Membership, Membership.user_id == User.id).where(Membership.classroom_id == classroom_id)).all()
    events = db.scalars(select(LearningEvent).where(LearningEvent.classroom_id == classroom_id)).all()
    submissions = db.scalars(select(Submission).where(Submission.classroom_id == classroom_id, Submission.mode == "submit")).all()
    records = db.scalars(select(LearningRecord).where(LearningRecord.classroom_id == classroom_id)).all()
    distribution = {}
    for item in submissions:
        distribution[item.status] = distribution.get(item.status, 0) + 1
    return {
        "members": [
            {
                "id": m.id,
                "name": m.name,
                "email": m.email,
                "completed_chapters": sum(ids.issubset(completed_by_user.get(m.id, set())) for ids in requirements.values()),
                "completed_nodes": len([e for e in events if e.user_id == m.id]),
                "submissions": len([s for s in submissions if s.user_id == m.id]),
                "records": len([r for r in records if r.user_id == m.id]),
            }
            for m in members
        ],
        "events": [
            {
                "id": e.id,
                "user_id": e.user_id,
                "node_id": e.node_id,
                "created_at": as_utc(e.created_at).isoformat(),
            }
            for e in events
        ],
        "submissions": [
            {"id": s.id, "user_id": s.user_id, "question_id": s.question_id, "status": s.status, "created_at": as_utc(s.created_at).isoformat()}
            for s in submissions
        ],
        "error_distribution": distribution,
    }


class ActivityInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    id: str = Field(min_length=1, max_length=100)
    chapter_id: int
    title: str = Field(min_length=1, max_length=200)
    kind: Literal["lesson", "reading", "practice", "assessment"]
    material_id: int | None = None
    npc_key: str
    assistant_allowed: bool = True
    knowledge_point_ids: list[int] = Field(default_factory=list, max_length=65)
    learning: dict | None = None

    @field_validator("knowledge_point_ids", mode="before")
    @classmethod
    def valid_points(cls, value):
        from .curriculum import validate_id_list

        return validate_id_list(value)


@router.post("/teacher/activities", status_code=201)
def create_activity(body: ActivityInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    from .models import Material

    if db.get(Activity, body.id):
        raise HTTPException(409, "活动ID已存在")
    from .curriculum import validate_activity_points

    validate_activity_points(db, user, body.chapter_id, body.knowledge_point_ids)
    if not db.get(Npc, body.npc_key):
        raise HTTPException(422, "NPC不存在")
    material = db.get(Material, body.material_id) if body.material_id else None
    if material and (material.chapter_id != body.chapter_id or (material.owner_id and material.owner_id != user.id and user.role != "admin")):
        raise HTTPException(403, "资料不属于当前教师或章节")
    if body.material_id and not material:
        raise HTTPException(404, "资料不存在")
    from .learning_rules import validate_learning

    content = {"published": True, "question_ids": [], "knowledge_point_ids": body.knowledge_point_ids}
    if body.learning is not None:
        content["learning"] = body.learning
    try:
        validate_learning(content, body.kind, material, resolve=True)
    except ValueError as exc:
        raise HTTPException(422, str(exc)) from exc
    item = Activity(
        **body.model_dump(exclude={"knowledge_point_ids", "learning"}),
        owner_id=user.id,
        content=content,
    )
    db.add(item)
    db.flush()
    db.commit()
    return {"id": item.id}


class ActivityUpdate(BaseModel):
    model_config = ConfigDict(extra="forbid")
    title: str | None = Field(default=None, min_length=1, max_length=200)
    npc_key: str | None = None
    assistant_allowed: bool | None = None
    material_id: int | None = None
    knowledge_point_ids: list[int] | None = Field(default=None, max_length=65)
    learning: dict | None = None

    @field_validator("knowledge_point_ids", mode="before")
    @classmethod
    def valid_points(cls, value):
        from .curriculum import validate_id_list

        return validate_id_list(value)


@router.put("/teacher/activities/{activity_id}")
def update_activity(activity_id: str, body: ActivityUpdate, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    from .models import Material

    item = db.get(Activity, activity_id)
    if not item:
        raise HTTPException(404, "活动不存在")
    if item.owner_id != user.id and user.role != "admin":
        raise HTTPException(403, "共享种子不可覆盖，请创建教师自己的活动并绑定任务")
    changes = body.model_dump(exclude_unset=True)
    new_learning = changes.pop("learning", None)
    content = dict(item.content)
    if "learning" in body.model_fields_set:
        if new_learning is None:
            content.pop("learning", None)
        else:
            content["learning"] = new_learning
    new_points = changes.pop("knowledge_point_ids", None)
    if new_points is not None:
        from .curriculum import validate_activity_points

        validate_activity_points(db, user, item.chapter_id, new_points)
    if any(key in changes and changes[key] is None for key in ("title", "npc_key", "assistant_allowed")):
        raise HTTPException(422, "活动必填字段不能设为空")
    if changes.get("npc_key") and not db.get(Npc, changes["npc_key"]):
        raise HTTPException(422, "NPC不存在")
    if changes.get("material_id"):
        material = db.get(Material, changes["material_id"])
        if not material or material.chapter_id != item.chapter_id or (material.owner_id not in {None, user.id} and user.role != "admin"):
            raise HTTPException(403, "资料不属于当前教师或章节")
    from .learning_rules import validate_learning
    from .models import Material

    material_id = changes.get("material_id", item.material_id)
    material = db.get(Material, material_id) if material_id else None
    try:
        validate_learning(content, item.kind, material, resolve=True)
    except ValueError as exc:
        raise HTTPException(422, str(exc)) from exc
    for key, value in changes.items():
        setattr(item, key, value)
    if new_points is not None:
        content["knowledge_point_ids"] = new_points
    item.content = content
    db.commit()
    return {"id": item.id}


class QuestionInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    id: str = Field(min_length=1, max_length=100)
    activity_id: str
    title: str = Field(min_length=1, max_length=200)
    kind: Literal["single_choice", "boolean", "fill", "python"]
    prompt: str = Field(min_length=1, max_length=30000)
    options: list = Field(default_factory=list, max_length=20)
    samples: list = Field(default_factory=list, max_length=20)
    answer: dict | list | str | bool | None = None
    hidden_cases: list = Field(default_factory=list, max_length=20)
    source: dict = Field(default_factory=dict)

    @field_validator("samples", "hidden_cases")
    @classmethod
    def validate_cases(cls, cases):
        for case in cases:
            if not isinstance(case, dict) or set(case) not in ({"input", "output"}, {"input", "expected_output"}):
                raise ValueError("用例必须包含 input 和 output 或 expected_output，不能附加其他字段")
            if any(not isinstance(value, str) or len(value) > 10000 for value in case.values()):
                raise ValueError("用例输入和输出必须为不超过10000字符的字符串")
        return cases

    @model_validator(mode="after")
    def validate_judge_budget(self):
        if self.kind == "python" and len(self.samples) + len(self.hidden_cases) > 20:
            raise ValueError("编程题公开样例与隐藏用例合计不能超过20个")
        return self


@router.post("/teacher/questions", status_code=201)
def create_question(body: QuestionInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    activity = db.get(Activity, body.activity_id)
    if not activity or (activity.owner_id != user.id and user.role != "admin"):
        raise HTTPException(403, "只能管理自己活动中的题目")
    if db.get(Question, body.id):
        raise HTTPException(409, "题目ID已存在")
    if body.kind == "python" and not body.hidden_cases:
        raise HTTPException(422, "编程题必须设置私有用例")
    if body.kind == "boolean" and not isinstance(body.answer, bool):
        raise HTTPException(422, "判断题答案必须是布尔值")
    if body.kind == "single_choice" and body.answer not in body.options:
        raise HTTPException(422, "单选答案必须属于选项")
    question = Question(**body.model_dump(exclude={"answer", "hidden_cases"}), chapter_id=activity.chapter_id)
    db.add(question)
    db.flush()
    db.add(QuestionSecret(question_id=question.id, answer=body.answer, hidden_cases=body.hidden_cases))
    activity.content = {**activity.content, "question_ids": [*activity.content.get("question_ids", []), question.id]}
    db.commit()
    return {"id": question.id}


@router.get("/teacher/questions")
def list_questions(db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    from .courses import public_question

    result = []
    for item in db.scalars(
        select(Question).join(Activity, Activity.id == Question.activity_id).where((Activity.owner_id.is_(None)) | (Activity.owner_id == user.id))
    ):
        secret = db.get(QuestionSecret, item.id)
        result.append(
            {
                **public_question(item),
                "activity_id": item.activity_id,
                "chapter_id": item.chapter_id,
                "answer": secret.answer if secret else None,
                "hidden_cases": secret.hidden_cases if secret else [],
            }
        )
    return result


class NpcInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    key: str = Field(min_length=1, max_length=80)
    name: str = Field(min_length=1, max_length=80)
    map_key: str = Field(min_length=1, max_length=40)
    tile_x: int = Field(ge=0)
    tile_y: int = Field(ge=0)
    portrait: str = ""
    dialogue: list = Field(default_factory=list, max_length=100)

    @model_validator(mode="after")
    def validate_map_position(self):
        validate_point(self.map_key, self.tile_x, self.tile_y)
        return self


@router.post("/teacher/npcs", status_code=201)
def create_npc(body: NpcInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    if user.role != "admin":
        raise HTTPException(403, "全局世界角色仅管理员可新增，教师可绑定现有角色")
    from .dialogues import validate_dialogue

    try:
        validate_dialogue(body.dialogue)
    except ValueError as exc:
        raise HTTPException(422, str(exc))
    if db.get(Npc, body.key):
        raise HTTPException(409, "NPC已存在")
    if body.portrait and not body.portrait.startswith("/assets/pixel/"):
        raise HTTPException(422, "头像须引用受审计素材")
    item = Npc(**body.model_dump())
    db.add(item)
    db.commit()
    return {"key": item.key}


@router.get("/teacher/classrooms/{classroom_id}/releases")
def list_releases(classroom_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    own_classroom(db, user, classroom_id)
    return [
        {
            "classroom_id": r.classroom_id,
            "chapter_id": r.chapter_id,
            "status": r.status,
            "opens_at": as_utc(r.opens_at).isoformat() if r.opens_at is not None else None,
            "prerequisite_chapter_id": r.prerequisite_chapter_id,
            "pause_policy": "deny_all_new_requests_preserve_history",
        }
        for r in db.scalars(select(ChapterRelease).where(ChapterRelease.classroom_id == classroom_id))
    ]


@router.get("/teacher/classrooms/{classroom_id}/students/{student_id}")
def student_record(classroom_id: int, student_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    own_classroom(db, user, classroom_id)
    if not db.get(Membership, (student_id, classroom_id)):
        raise HTTPException(404, "学生不在当前班级")
    student = db.get(User, student_id)
    return {
        "student": {"id": student.id, "name": student.name, "email": student.email},
        "events": [
            {
                "id": e.id,
                "node_id": e.node_id,
                "player_quest_id": e.player_quest_id,
                "evidence": e.evidence,
                "created_at": as_utc(e.created_at).isoformat(),
            }
            for e in db.scalars(
                select(LearningEvent)
                .where(LearningEvent.classroom_id == classroom_id, LearningEvent.user_id == student_id)
                .order_by(LearningEvent.created_at)
            )
        ],
        "participation": [
            {
                "id": r.id,
                "activity_id": r.activity_id,
                "kind": r.kind,
                "data": r.data,
                "created_at": as_utc(r.created_at).isoformat(),
            }
            for r in db.scalars(select(LearningRecord).where(LearningRecord.classroom_id == classroom_id, LearningRecord.user_id == student_id))
        ],
        "submissions": [
            {
                "id": s.id,
                "question_id": s.question_id,
                "mode": s.mode,
                "status": s.status,
                "answer": s.answer,
                "code": s.code,
                "feedback": s.feedback,
                "created_at": as_utc(s.created_at).isoformat(),
            }
            for s in db.scalars(
                select(Submission).where(Submission.classroom_id == classroom_id, Submission.user_id == student_id).order_by(Submission.created_at)
            )
        ],
    }
