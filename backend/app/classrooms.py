import secrets
import json
from datetime import datetime, timezone
from typing import Annotated, Literal
from fastapi import APIRouter, Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials
from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator
from sqlalchemy import select
from sqlalchemy.orm import Session
from sqlalchemy.exc import IntegrityError
from .auth import as_utc, bearer, current_user, hash_password, issue, preview_classroom, require_teacher, token_hash
from .db import get_db
from .teacher_scope import teacher_instance_id, require_scope, scoped_key, commit_content
from .content_relations import (
    activity_questions, material_in_chapter, question_chapter_ids, question_knowledge_point_ids,
    set_activity_questions, set_question_chapters, set_question_knowledge_points,
)
from .models import (
    Activity,
    ActivityQuestion,
    AssessmentAttempt,
    Chapter,
    ChapterRelease,
    Classroom,
    CourseInstance,
    LearningEvent,
    LearningRecord,
    KnowledgePoint,
    Membership,
    Npc,
    PlayerQuest,
    Question,
    QuestionSecret,
    Submission,
    TownPreviewStudent,
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
    preview = db.get(TownPreviewStudent, user.id)
    if preview:
        classroom = preview_classroom(db, preview)
        return [classroom_payload(classroom, user, db.get(User, classroom.teacher_id).name)]
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


@router.post("/teacher/classrooms/{classroom_id}/town-session")
def open_town_session(
    classroom_id: int,
    db: Annotated[Session, Depends(get_db)],
    user: Annotated[User, Depends(require_teacher)],
    credentials: Annotated[HTTPAuthorizationCredentials, Depends(bearer)],
):
    # Serialize initial creation against another launch of this classroom.
    teacher_instance_id(db, user, classroom_id, exclusive=True)
    preview = db.scalar(select(TownPreviewStudent).where(
        TownPreviewStudent.teacher_id == user.id, TownPreviewStudent.classroom_id == classroom_id,
    ))
    if preview:
        student = db.get(User, preview.student_user_id)
        if not student or student.role != "student" or student.must_change_password:
            raise HTTPException(409, "本班的体验学生账号不可用")
    else:
        student = User(student_id=f"{secrets.randbelow(10**32):032d}", name="教师体验学生", role="student",
                       password_hash=hash_password(secrets.token_urlsafe(32)))
        db.add(student)
        db.flush()
        db.add(TownPreviewStudent(student_user_id=student.id, teacher_id=user.id, classroom_id=classroom_id))
    return issue(db, student, preview_teacher_token_hash=token_hash(credentials.credentials))


class JoinInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    join_code: str = Field(min_length=4, max_length=24)


@router.post("/classrooms/join")
def join_classroom(body: JoinInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    if db.get(TownPreviewStudent, user.id):
        raise HTTPException(403, "体验学生只能进入教师选择的班级")
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
    from .course_scope import lock_course
    lock_course(db, classroom_id, exclusive=True)
    classroom = own_classroom(db, user, classroom_id)
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)
    chapter = db.get(Chapter, chapter_id)
    require_scope(chapter, instance_id)
    if not chapter:
        raise HTTPException(404, "章节不存在")
    if chapter.owner_id is not None and chapter.owner_id != classroom.teacher_id:
        raise HTTPException(403, "不能向本班发布其他教师的私有章节")
    if body.prerequisite_chapter_id == chapter_id or (body.prerequisite_chapter_id and not db.get(Chapter, body.prerequisite_chapter_id)):
        raise HTTPException(422, "前置章节无效")
    if body.prerequisite_chapter_id:
        prerequisite = require_scope(db.get(Chapter, body.prerequisite_chapter_id), instance_id)
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
    commit_content(db, instance_id)
    return {
        "classroom_id": classroom_id,
        "chapter_id": chapter_id,
        **body.model_dump(),
        "opens_at": as_utc(release.opens_at).isoformat() if release.opens_at is not None else None,
        "pause_policy": "deny_all_new_requests_preserve_history",
    }


@router.get("/teacher/courses")
def teacher_courses(db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)], classroom_id: int | None = None):
    instance_id = teacher_instance_id(db, user, classroom_id)
    return {
        "chapters": [
            {"id": c.id, "title": c.title, "order_index": c.order_index, "owner_id": c.owner_id}
            for c in db.scalars(
                select(Chapter).where(Chapter.course_instance_id == instance_id)
                .where(True if user.role == "admin" else (Chapter.owner_id.is_(None)) | (Chapter.owner_id == user.id))
                .order_by(Chapter.order_index)
            )
        ],
        "knowledge_points": [
            {"id": k.id, "chapter_id": k.chapter_id, "title": k.title, "content": k.content, "owner_id": k.owner_id}
            for k in db.scalars(
                select(KnowledgePoint).where(KnowledgePoint.course_instance_id == instance_id)
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
                select(Activity).where(Activity.course_instance_id == instance_id).where(
                    True if user.role == "admin" else (Activity.owner_id.is_(None)) | (Activity.owner_id == user.id))
            )
        ],
        "npcs": [{"key": n.key, "name": n.name, "map_key": n.map_key, "tile_x": n.tile_x, "tile_y": n.tile_y} for n in db.scalars(select(Npc).where(Npc.course_instance_id == instance_id))],
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
    preview_ids = select(TownPreviewStudent.student_user_id).where(TownPreviewStudent.classroom_id == classroom_id)
    events = db.scalars(select(LearningEvent).where(LearningEvent.classroom_id == classroom_id, LearningEvent.user_id.not_in(preview_ids))).all()
    submissions = db.scalars(select(Submission).where(Submission.classroom_id == classroom_id, Submission.mode == "submit",
                                                      Submission.user_id.not_in(preview_ids))).all()
    records = db.scalars(select(LearningRecord).where(LearningRecord.classroom_id == classroom_id, LearningRecord.user_id.not_in(preview_ids))).all()
    distribution = {}
    for item in submissions:
        distribution[item.status] = distribution.get(item.status, 0) + 1
    return {
        "members": [
            {
                "id": m.id,
                "name": m.name,
                "student_id": m.student_id,
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
    npc_key: str | None = None
    assistant_allowed: bool = True
    knowledge_point_ids: list[int] = Field(default_factory=list, max_length=65)
    learning: dict | None = None

    @field_validator("knowledge_point_ids", mode="before")
    @classmethod
    def valid_points(cls, value):
        from .curriculum import validate_id_list

        return validate_id_list(value)


@router.post("/teacher/activities", status_code=201)
def create_activity(body: ActivityInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)], classroom_id: int | None = None):
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)
    from .models import Material

    runtime_id = scoped_key(instance_id, "activity", body.id)
    if db.get(Activity, runtime_id, execution_options={"include_deleted": True}):
        raise HTTPException(409, "活动ID已存在")
    from .curriculum import validate_activity_points

    validate_activity_points(db, user, body.chapter_id, body.knowledge_point_ids, instance_id)
    if body.npc_key is not None and (not db.get(Npc, body.npc_key) or db.get(Npc, body.npc_key).course_instance_id != instance_id):
        raise HTTPException(422, "NPC不存在")
    material = db.scalar(select(Material).where(Material.id == body.material_id).with_for_update()) if body.material_id else None
    if material:
        require_scope(material, instance_id)
    if material and (not material_in_chapter(db, material.id, body.chapter_id) or material.owner_id not in {None, user.id}):
        raise HTTPException(403, "资料不属于当前教师或章节")
    if body.material_id and not material:
        raise HTTPException(404, "资料不存在")
    from .learning_rules import validate_learning

    content = {"published": True, "knowledge_point_ids": body.knowledge_point_ids}
    if body.learning is not None:
        content["learning"] = body.learning
    try:
        validate_learning(content, body.kind, material, resolve=True)
    except ValueError as exc:
        raise HTTPException(422, str(exc)) from exc
    item = Activity(
        **body.model_dump(exclude={"id", "knowledge_point_ids", "learning"}),
        id=runtime_id, stable_id=body.id, course_instance_id=instance_id,
        owner_id=user.id,
        content=content,
    )
    db.add(item)
    db.flush()
    commit_content(db, instance_id)
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
def update_activity(activity_id: str, body: ActivityUpdate, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)], classroom_id: int | None = None):
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)
    from .models import Material

    item = db.scalar(select(Activity).where(Activity.id == activity_id).with_for_update())
    if not item:
        raise HTTPException(404, "活动不存在")
    require_scope(item, instance_id)
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

        validate_activity_points(db, user, item.chapter_id, new_points, instance_id)
    if any(key in changes and changes[key] is None for key in ("title", "assistant_allowed")):
        raise HTTPException(422, "活动必填字段不能设为空")
    if "npc_key" in changes and changes["npc_key"] is not None and (not db.get(Npc, changes["npc_key"]) or db.get(Npc, changes["npc_key"]).course_instance_id != instance_id):
        raise HTTPException(422, "NPC不存在")
    if changes.get("material_id"):
        material = db.scalar(select(Material).where(Material.id == changes["material_id"]).with_for_update())
        if not material or not material_in_chapter(db, material.id, item.chapter_id) or material.owner_id not in {None, item.owner_id}:
            raise HTTPException(403, "资料不属于当前教师或章节")
    from .learning_rules import validate_learning
    from .models import Material

    material_id = changes.get("material_id", item.material_id)
    material = db.get(Material, material_id) if material_id else None
    if material:
        require_scope(material, instance_id)
    try:
        validate_learning(content, item.kind, material, resolve=True)
    except ValueError as exc:
        raise HTTPException(422, str(exc)) from exc
    for key, value in changes.items():
        setattr(item, key, value)
    if new_points is not None:
        content["knowledge_point_ids"] = new_points
    item.content = content
    commit_content(db, instance_id)
    return {"id": item.id}


@router.delete("/teacher/activities/{activity_id}")
def delete_activity(activity_id: str, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)], classroom_id: int | None = None):
    from .curriculum import editable
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)
    editable(db.scalar(select(Activity).where(Activity.id == activity_id).with_for_update()), user, instance_id)
    raise HTTPException(409, "请先预览删除影响并完成二次确认")


class ActivityQuestionsInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    question_ids: list[str] = Field(max_length=5000)

    @field_validator("question_ids")
    @classmethod
    def unique_questions(cls, value):
        if any(not key or len(key) > 100 for key in value) or len(set(value)) != len(value):
            raise ValueError("题目ID必须有效且不能重复")
        return value


@router.put("/teacher/activities/{activity_id}/questions")
def update_activity_questions(activity_id: str, body: ActivityQuestionsInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)], classroom_id: int | None = None):
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)
    from .curriculum import editable

    activity = editable(db.scalar(select(Activity).where(Activity.id == activity_id).with_for_update()), user, instance_id)
    if activity.kind not in {"practice", "assessment"}:
        raise HTTPException(422, "只有练习与测验活动可以选题")
    if activity.kind == "assessment" and len(body.question_ids) > 100:
        raise HTTPException(422, "章节测验最多选择100道题")
    if (activity.kind == "assessment" and body.question_ids != [q.id for q in activity_questions(db, activity)]
            and db.scalar(select(AssessmentAttempt.id).where(AssessmentAttempt.activity_id == activity.id).limit(1)) is not None):
        raise HTTPException(409, "测验已有试卷记录，不能更换题集或顺序；请新建测验活动")
    for question_id in sorted(body.question_ids):
        question = db.scalar(select(Question).where(Question.id == question_id).with_for_update())
        if not question:
            raise HTTPException(422, "选中的题目不存在")
        require_scope(question, instance_id)
        if question.owner_id not in {None, activity.owner_id}:
            raise HTTPException(403, "题目必须属于活动教师或共享题库")
        if question.status != "ready":
            raise HTTPException(422, "只有已就绪题目可以用于活动")
        if activity.chapter_id not in question_chapter_ids(db, question.id):
            raise HTTPException(422, "题目必须关联活动所在章节")
    try:
        set_activity_questions(db, activity, body.question_ids)
        commit_content(db, instance_id)
    except IntegrityError as exc:
        db.rollback()
        raise HTTPException(409, "内容同时更新，请刷新后重试") from exc
    return {"id": activity.id, "question_ids": [q.id for q in activity_questions(db, activity)]}


class QuestionInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    id: str = Field(min_length=1, max_length=100)
    title: str = Field(min_length=1, max_length=200)
    kind: Literal["single_choice", "boolean", "fill", "python"]
    prompt: str = Field(min_length=1, max_length=30000)
    chapter_ids: list[int] = Field(default_factory=list, max_length=65)
    knowledge_point_ids: list[int] = Field(default_factory=list, max_length=65)
    source_type: Literal["ai_generated", "imported"] = "imported"
    status: Literal["draft", "ready", "disabled"] = "draft"
    options: list = Field(default_factory=list, max_length=20)
    samples: list = Field(default_factory=list, max_length=20)
    hints: list[str] = Field(default_factory=list, max_length=10)
    starter_code: str = Field(default="", max_length=20000)
    answer: dict | list | str | bool | None = None
    hidden_cases: list = Field(default_factory=list, max_length=20)
    source: dict = Field(default_factory=dict)

    @field_validator("chapter_ids", "knowledge_point_ids", mode="before")
    @classmethod
    def valid_ids(cls, value):
        from .curriculum import validate_id_list
        return validate_id_list(value)

    @field_validator("id", "title", "prompt")
    @classmethod
    def nonblank(cls, value):
        if not value.strip():
            raise ValueError("题目标识、标题与题干不能为空白")
        return value.strip()

    @field_validator("source", "options")
    @classmethod
    def bounded_json(cls, value):
        if len(json.dumps(value, ensure_ascii=False)) > 30000:
            raise ValueError("题目选项或来源超过30000字符")
        return value

    @field_validator("hints")
    @classmethod
    def validate_hints(cls, hints):
        if any(not hint.strip() or len(hint) > 2000 for hint in hints):
            raise ValueError("每条提示须为1至2000字符")
        return hints

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
        if self.status == "ready":
            if not self.chapter_ids:
                raise ValueError("就绪题目必须关联章节")
            if self.kind == "python" and not self.hidden_cases:
                raise ValueError("就绪编程题必须设置私有用例")
            expected = self.answer.get("value") if isinstance(self.answer, dict) and "value" in self.answer else self.answer
            if self.kind == "boolean" and not isinstance(expected, bool):
                raise ValueError("判断题答案必须是布尔值")
            if self.kind == "single_choice" and (not self.options or expected not in self.options):
                raise ValueError("单选答案必须属于选项")
            if self.kind == "fill" and (not isinstance(expected, (str, list)) or not expected):
                raise ValueError("填空题必须设置答案")
        return self


class QuestionUpdate(BaseModel):
    model_config = ConfigDict(extra="forbid")
    title: str | None = None
    kind: Literal["single_choice", "boolean", "fill", "python"] | None = None
    prompt: str | None = None
    chapter_ids: list[int] | None = None
    knowledge_point_ids: list[int] | None = None
    source_type: Literal["ai_generated", "imported"] | None = None
    status: Literal["draft", "ready", "disabled"] | None = None
    options: list | None = None
    samples: list | None = None
    hints: list[str] | None = None
    starter_code: str | None = None
    answer: dict | list | str | bool | None = None
    hidden_cases: list | None = None
    source: dict | None = None


def validate_question_links(db, user, body, instance_id=None):
    from .curriculum import authoring_chapter
    from .models import Material

    if "material_id" in body.source:
        material_id = body.source["material_id"]
        material = db.scalar(select(Material).where(Material.id == material_id).with_for_update()) if type(material_id) is int and material_id > 0 else None
        if not material:
            raise HTTPException(422, "题目来源资料不存在")
        require_scope(material, instance_id)
        if user.role != "admin" and material.owner_id not in {None, user.id}:
            raise HTTPException(403, "无权引用其他教师的私有资料")
        if "page_no" in body.source:
            page_no = body.source["page_no"]
            if type(page_no) is not int or not any(page.get("page_no") == page_no for page in material.content.get("pages", [])):
                raise HTTPException(422, "题目来源页必须属于所选资料")
    for chapter_id in body.chapter_ids:
        authoring_chapter(db, user, chapter_id, instance_id)
    for point_id in body.knowledge_point_ids:
        point = db.get(KnowledgePoint, point_id)
        if not point or point.course_instance_id != instance_id or point.chapter_id not in body.chapter_ids:
            raise HTTPException(422, "知识点必须存在且属于所选章节")
        if user.role != "admin" and point.owner_id not in {None, user.id}:
            raise HTTPException(403, "无权使用其他教师的私有知识点")


def teacher_question_payload(db, question, user):
    from .courses import public_question

    secret = db.get(QuestionSecret, question.id)
    activities_query = select(Activity).join(ActivityQuestion, ActivityQuestion.activity_id == Activity.id).where(ActivityQuestion.question_id == question.id)
    chapter_ids = question_chapter_ids(db, question.id)
    point_ids = question_knowledge_point_ids(db, question.id)
    if user.role != "admin":
        activities_query = activities_query.where(Activity.owner_id.is_(None) | (Activity.owner_id == user.id))
        chapter_ids = list(db.scalars(select(Chapter.id).where(Chapter.id.in_(chapter_ids), Chapter.owner_id.is_(None) | (Chapter.owner_id == user.id))))
        point_ids = list(db.scalars(select(KnowledgePoint.id).where(KnowledgePoint.id.in_(point_ids), KnowledgePoint.chapter_id.in_(chapter_ids),
            KnowledgePoint.owner_id.is_(None) | (KnowledgePoint.owner_id == user.id))))
    activities = db.scalars(activities_query).all()
    return {
        **public_question(question),
        "chapter_ids": chapter_ids,
        "knowledge_point_ids": point_ids,
        "source_type": question.source_type,
        "status": question.status,
        "source": question.source,
        "owner_id": question.owner_id,
        "activities": [{"id": a.id, "title": a.title, "chapter_id": a.chapter_id} for a in activities],
        "answer": secret.answer if secret else None,
        "hidden_cases": secret.hidden_cases if secret else [],
    }


def _save_question(db, user, question, body):
    validate_question_links(db, user, body, question.course_instance_id)
    if question.kind is not None and question.kind != body.kind and (
        db.scalar(select(Submission.id).where(Submission.question_id == question.id).limit(1)) is not None
        or any(question.id in attempt.question_ids for attempt in db.scalars(select(AssessmentAttempt).where(
            AssessmentAttempt.classroom_id.in_(select(CourseInstance.classroom_id).where(CourseInstance.id == question.course_instance_id))
        )))
    ):
        raise HTTPException(409, "题目已有作答或试卷记录，不能改变题型；请新建题目")
    bound = db.scalars(select(Activity).join(ActivityQuestion, ActivityQuestion.activity_id == Activity.id).where(ActivityQuestion.question_id == question.id)).all()
    if bound and body.status != "ready":
        raise HTTPException(409, "题目仍被活动引用，请先从活动移除再改为草稿或停用")
    if any(activity.chapter_id not in body.chapter_ids for activity in bound):
        raise HTTPException(409, "题目仍被活动引用，不能移除所用章节")
    for key, value in body.model_dump(exclude={"id", "answer", "hidden_cases", "chapter_ids", "knowledge_point_ids"}).items():
        setattr(question, key, value)
    db.add(question)
    db.flush()
    secret = db.get(QuestionSecret, question.id)
    if not secret:
        secret = QuestionSecret(question_id=question.id)
        db.add(secret)
    secret.answer, secret.hidden_cases = body.answer, body.hidden_cases
    set_question_chapters(db, question.id, body.chapter_ids)
    set_question_knowledge_points(db, question.id, body.knowledge_point_ids)
    commit_content(db, question.course_instance_id)
    return teacher_question_payload(db, question, user)


def save_question(db, user, question, body):
    try:
        return _save_question(db, user, question, body)
    except IntegrityError as exc:
        db.rollback()
        raise HTTPException(409, "题目标识冲突或内容同时更新，请刷新后重试") from exc


@router.post("/teacher/questions", status_code=201)
def create_question(body: QuestionInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)], classroom_id: int | None = None):
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)
    runtime_id = scoped_key(instance_id, "question", body.id)
    if db.get(Question, runtime_id, execution_options={"include_deleted": True}):
        raise HTTPException(409, "题目ID已存在")
    return save_question(db, user, Question(id=runtime_id, stable_id=body.id, course_instance_id=instance_id, owner_id=user.id), body)


@router.get("/teacher/questions")
def list_questions(db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)], classroom_id: int | None = None):
    instance_id = teacher_instance_id(db, user, classroom_id)
    query = select(Question).where(Question.course_instance_id == instance_id)
    if user.role != "admin":
        query = query.where(Question.owner_id.is_(None) | (Question.owner_id == user.id))
    return [teacher_question_payload(db, item, user) for item in db.scalars(query.order_by(Question.id))]


@router.put("/teacher/questions/{question_id}")
def update_question(question_id: str, body: QuestionUpdate, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)], classroom_id: int | None = None):
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)
    from .curriculum import editable
    from pydantic import ValidationError

    question = editable(db.scalar(select(Question).where(Question.id == question_id).with_for_update()), user, instance_id)
    fields = teacher_question_payload(db, question, user)
    for key in ("activities", "owner_id"):
        fields.pop(key, None)
    fields.update(body.model_dump(exclude_unset=True))
    try:
        merged = QuestionInput.model_validate(fields)
    except ValidationError as exc:
        raise HTTPException(422, "题目配置无效：" + "; ".join(error["msg"] for error in exc.errors())) from exc
    return save_question(db, user, question, merged)


@router.delete("/teacher/questions/{question_id}")
def delete_question(question_id: str, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)], classroom_id: int | None = None):
    from .curriculum import editable
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)
    editable(db.scalar(select(Question).where(Question.id == question_id).with_for_update()), user, instance_id)
    raise HTTPException(409, "请先预览删除影响并完成二次确认")


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
        "student": {"id": student.id, "name": student.name, "student_id": student.student_id},
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
