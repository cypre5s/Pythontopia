"""Validate and manage reusable questions while preserving private answer data."""

from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.content_relations import question_chapter_ids, question_knowledge_point_ids, set_question_chapters, set_question_knowledge_points
from app.course_content.question_contracts import QuestionInput, QuestionUpdate
from app.models import (
    Activity,
    ActivityQuestion,
    AssessmentAttempt,
    Chapter,
    CourseInstance,
    KnowledgePoint,
    Question,
    QuestionSecret,
    Submission,
    User,
)
from app.teacher_scope import commit_content, require_scope, scoped_key, teacher_instance_id


def validate_question_links(db, user, body, instance_id=None):
    from app.curriculum import authoring_chapter
    from app.models import Material

    if "material_id" in body.source:
        material_id = body.source["material_id"]
        material = (
            db.scalar(select(Material).where(Material.id == material_id).with_for_update()) if type(material_id) is int and material_id > 0 else None
        )
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
    from app.course_content.presentation import public_question

    secret = db.get(QuestionSecret, question.id)
    activities_query = (
        select(Activity).join(ActivityQuestion, ActivityQuestion.activity_id == Activity.id).where(ActivityQuestion.question_id == question.id)
    )
    chapter_ids = question_chapter_ids(db, question.id)
    point_ids = question_knowledge_point_ids(db, question.id)
    if user.role != "admin":
        activities_query = activities_query.where(Activity.owner_id.is_(None) | (Activity.owner_id == user.id))
        chapter_ids = list(
            db.scalars(select(Chapter.id).where(Chapter.id.in_(chapter_ids), Chapter.owner_id.is_(None) | (Chapter.owner_id == user.id)))
        )
        point_ids = list(
            db.scalars(
                select(KnowledgePoint.id).where(
                    KnowledgePoint.id.in_(point_ids),
                    KnowledgePoint.chapter_id.in_(chapter_ids),
                    KnowledgePoint.owner_id.is_(None) | (KnowledgePoint.owner_id == user.id),
                )
            )
        )
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
    if (
        question.kind is not None
        and question.kind != body.kind
        and (
            db.scalar(select(Submission.id).where(Submission.question_id == question.id).limit(1)) is not None
            or any(
                question.id in attempt.question_ids
                for attempt in db.scalars(
                    select(AssessmentAttempt).where(
                        AssessmentAttempt.classroom_id.in_(
                            select(CourseInstance.classroom_id).where(CourseInstance.id == question.course_instance_id)
                        )
                    )
                )
            )
        )
    ):
        raise HTTPException(409, "题目已有作答或试卷记录，不能改变题型；请新建题目")
    bound = db.scalars(
        select(Activity).join(ActivityQuestion, ActivityQuestion.activity_id == Activity.id).where(ActivityQuestion.question_id == question.id)
    ).all()
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


def create_question(body: QuestionInput, db: Session, user: User, classroom_id: int | None = None):
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)
    runtime_id = scoped_key(instance_id, "question", body.id)
    if db.get(Question, runtime_id, execution_options={"include_deleted": True}):
        raise HTTPException(409, "题目ID已存在")
    return save_question(db, user, Question(id=runtime_id, stable_id=body.id, course_instance_id=instance_id, owner_id=user.id), body)


def list_questions(db: Session, user: User, classroom_id: int | None = None):
    instance_id = teacher_instance_id(db, user, classroom_id)
    query = select(Question).where(Question.course_instance_id == instance_id)
    if user.role != "admin":
        query = query.where(Question.owner_id.is_(None) | (Question.owner_id == user.id))
    return [teacher_question_payload(db, item, user) for item in db.scalars(query.order_by(Question.id))]


def update_question(question_id: str, body: QuestionUpdate, db: Session, user: User, classroom_id: int | None = None):
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)
    from pydantic import ValidationError

    from app.curriculum import editable

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


def delete_question(question_id: str, db: Session, user: User, classroom_id: int | None = None):
    from app.curriculum import editable

    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)
    editable(db.scalar(select(Question).where(Question.id == question_id).with_for_update()), user, instance_id)
    raise HTTPException(409, "请先预览删除影响并完成二次确认")
