"""One source for current content associations; callers own authorization and commits."""

from sqlalchemy import delete, select

from .models import (
    Activity,
    ActivityQuestion,
    Chapter,
    KnowledgePoint,
    Material,
    MaterialChapter,
    Question,
    QuestionChapter,
    QuestionKnowledgePoint,
)


def material_chapter_ids(db, material_id):
    return list(db.scalars(select(MaterialChapter.chapter_id).where(
        MaterialChapter.material_id == material_id).order_by(MaterialChapter.chapter_id)))


def material_in_chapter(db, material_id, chapter_id):
    return db.get(MaterialChapter, (material_id, chapter_id)) is not None


def question_chapter_ids(db, question_id):
    return list(db.scalars(select(QuestionChapter.chapter_id).where(
        QuestionChapter.question_id == question_id).order_by(QuestionChapter.chapter_id)))


def question_knowledge_point_ids(db, question_id):
    return list(db.scalars(select(QuestionKnowledgePoint.knowledge_point_id).where(
        QuestionKnowledgePoint.question_id == question_id).order_by(QuestionKnowledgePoint.knowledge_point_id)))


def activity_questions(db, activity):
    activity_id = activity.id if isinstance(activity, Activity) else activity
    return list(db.scalars(select(Question).join(ActivityQuestion, ActivityQuestion.question_id == Question.id).where(
        ActivityQuestion.activity_id == activity_id).order_by(ActivityQuestion.order_index, Question.id)))


def _validate_ids(db, parent, parent_id, model, ids):
    if not isinstance(ids, (list, tuple)) or len(ids) > 5000:
        raise ValueError("关联必须为最多5000项的标识列表")
    if any(type(key) is not (str if model is Question else int) for key in ids):
        raise ValueError("关联标识类型无效")
    if len(set(ids)) != len(ids):
        raise ValueError("关联标识不能重复")
    db.flush()
    parent_item = db.get(parent, parent_id)
    if parent_item is None:
        raise ValueError("关联对象不存在")
    found = set(db.scalars(select(model.id).where(
        model.id.in_(ids), model.course_instance_id == parent_item.course_instance_id))) if ids else set()
    if found != set(ids):
        raise ValueError("引用的内容不存在或不属于同一班级课程")


def set_material_chapters(db, material_id, ids):
    _validate_ids(db, Material, material_id, Chapter, ids)
    db.execute(delete(MaterialChapter).where(MaterialChapter.material_id == material_id))
    db.add_all(MaterialChapter(material_id=material_id, chapter_id=key) for key in ids)
    db.flush()


def set_question_chapters(db, question_id, ids):
    _validate_ids(db, Question, question_id, Chapter, ids)
    db.execute(delete(QuestionChapter).where(QuestionChapter.question_id == question_id))
    db.add_all(QuestionChapter(question_id=question_id, chapter_id=key) for key in ids)
    db.flush()


def set_question_knowledge_points(db, question_id, ids):
    _validate_ids(db, Question, question_id, KnowledgePoint, ids)
    db.execute(delete(QuestionKnowledgePoint).where(QuestionKnowledgePoint.question_id == question_id))
    db.add_all(QuestionKnowledgePoint(question_id=question_id, knowledge_point_id=key) for key in ids)
    db.flush()


def set_activity_questions(db, activity, ids):
    activity_id = activity.id if isinstance(activity, Activity) else activity
    _validate_ids(db, Activity, activity_id, Question, ids)
    db.execute(delete(ActivityQuestion).where(ActivityQuestion.activity_id == activity_id))
    db.add_all(ActivityQuestion(activity_id=activity_id, question_id=key, order_index=index) for index, key in enumerate(ids))
    db.flush()
