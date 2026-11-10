from datetime import datetime, timezone
from uuid import uuid4
from sqlalchemy import Boolean, CheckConstraint, DateTime, ForeignKey, Index, Integer, JSON, String, Text, UniqueConstraint, text
from sqlalchemy.orm import Mapped, declared_attr, mapped_column
from .db import Base


def now():
    return datetime.now(timezone.utc)


def uid():
    return str(uuid4())


class User(Base):
    __tablename__ = "users"
    id: Mapped[int] = mapped_column(primary_key=True)
    # Legacy accounts remain unassigned until an operator supplies a real number.
    student_id: Mapped[str | None] = mapped_column(String(32), unique=True, index=True)
    name: Mapped[str] = mapped_column(String(80))
    role: Mapped[str] = mapped_column(String(16), default="student")
    password_hash: Mapped[str] = mapped_column(Text)
    must_change_password: Mapped[bool] = mapped_column(Boolean, default=False, server_default=text("false"))


class AuthSession(Base):
    __tablename__ = "auth_sessions"
    token_hash: Mapped[str] = mapped_column(String(64), primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    # A deleted teacher session revokes the associated preview credentials.
    preview_teacher_token_hash: Mapped[str | None] = mapped_column(String(64))


class Classroom(Base):
    __tablename__ = "classrooms"
    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(100))
    teacher_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    join_code: Mapped[str] = mapped_column(String(24), unique=True)


class TownPreviewStudent(Base):
    __tablename__ = "town_preview_students"
    __table_args__ = (UniqueConstraint("teacher_id", "classroom_id", name="uq_town_preview_teacher_classroom"),)
    student_user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    teacher_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    classroom_id: Mapped[int] = mapped_column(ForeignKey("classrooms.id"), index=True)


class CourseInstance(Base):
    __tablename__ = "course_instances"
    id: Mapped[int] = mapped_column(primary_key=True)
    classroom_id: Mapped[int] = mapped_column(ForeignKey("classrooms.id"), unique=True)
    package_id: Mapped[str] = mapped_column(String(100))
    name: Mapped[str] = mapped_column(String(200))
    package_version: Mapped[str] = mapped_column(String(100))
    revision: Mapped[int] = mapped_column(Integer, default=1, server_default="1")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)


class CourseContent:
    """NULL instance rows are retained import sources, never a class-owned definition."""
    course_instance_id: Mapped[int | None] = mapped_column(ForeignKey("course_instances.id"), nullable=True, index=True)
    stable_id: Mapped[str | None] = mapped_column(String(100), nullable=True)

    @declared_attr.directive
    def __table_args__(cls):
        return (UniqueConstraint("course_instance_id", "stable_id", name=f"uq_{cls.__tablename__}_instance_stable"),)


class Membership(Base):
    __tablename__ = "memberships"
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    classroom_id: Mapped[int] = mapped_column(ForeignKey("classrooms.id", ondelete="CASCADE"), primary_key=True)


class Chapter(CourseContent, Base):
    __tablename__ = "chapters"
    __table_args__ = (
        UniqueConstraint("course_instance_id", "stable_id", name="uq_chapters_instance_stable"),
        UniqueConstraint("course_instance_id", "order_index", name="uq_chapters_instance_order"),
        Index("uq_chapters_template_order", "order_index", unique=True,
              sqlite_where=text("course_instance_id IS NULL"), postgresql_where=text("course_instance_id IS NULL")),
    )
    id: Mapped[int] = mapped_column(primary_key=True)
    title: Mapped[str] = mapped_column(String(200))
    order_index: Mapped[int] = mapped_column(Integer)
    course_id: Mapped[int] = mapped_column(Integer, default=43)
    owner_id: Mapped[int | None] = mapped_column(ForeignKey("users.id"), nullable=True, index=True)


class KnowledgePoint(CourseContent, Base):
    __tablename__ = "knowledge_points"
    id: Mapped[int] = mapped_column(primary_key=True)
    owner_id: Mapped[int | None] = mapped_column(ForeignKey("users.id"), nullable=True, index=True)
    chapter_id: Mapped[int] = mapped_column(ForeignKey("chapters.id"), index=True)
    title: Mapped[str] = mapped_column(String(200))
    content: Mapped[dict] = mapped_column(JSON, default=dict)


class Material(CourseContent, Base):
    __tablename__ = "materials"
    id: Mapped[int] = mapped_column(primary_key=True)
    title: Mapped[str] = mapped_column(String(200))
    kind: Mapped[str] = mapped_column(String(24), default="text")
    owner_id: Mapped[int | None] = mapped_column(ForeignKey("users.id"), nullable=True)

    content: Mapped[dict] = mapped_column(JSON, default=dict)
    storage_key: Mapped[str | None] = mapped_column(Text, nullable=True)
    status: Mapped[str] = mapped_column(String(24), default="ready")
    error: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)


class MaterialChapter(Base):
    __tablename__ = "material_chapters"
    material_id: Mapped[int] = mapped_column(ForeignKey("materials.id", ondelete="CASCADE"), primary_key=True)
    chapter_id: Mapped[int] = mapped_column(ForeignKey("chapters.id", ondelete="CASCADE"), primary_key=True, index=True)


class Activity(CourseContent, Base):
    __tablename__ = "activities"
    id: Mapped[str] = mapped_column(String(100), primary_key=True)
    chapter_id: Mapped[int] = mapped_column(ForeignKey("chapters.id"), index=True)
    title: Mapped[str] = mapped_column(String(200))
    kind: Mapped[str] = mapped_column(String(24))
    material_id: Mapped[int | None] = mapped_column(ForeignKey("materials.id"), nullable=True)
    owner_id: Mapped[int | None] = mapped_column(ForeignKey("users.id"), nullable=True)
    assistant_allowed: Mapped[bool] = mapped_column(Boolean, default=True)
    npc_key: Mapped[str] = mapped_column(String(80), default="professor")
    content: Mapped[dict] = mapped_column(JSON, default=dict)


class ChapterRelease(Base):
    __tablename__ = "chapter_releases"
    classroom_id: Mapped[int] = mapped_column(ForeignKey("classrooms.id"), primary_key=True)
    chapter_id: Mapped[int] = mapped_column(ForeignKey("chapters.id"), primary_key=True)
    status: Mapped[str] = mapped_column(String(24), default="published")
    opens_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    prerequisite_chapter_id: Mapped[int | None] = mapped_column(ForeignKey("chapters.id"), nullable=True)


class Npc(CourseContent, Base):
    __tablename__ = "npcs"
    key: Mapped[str] = mapped_column(String(80), primary_key=True)
    name: Mapped[str] = mapped_column(String(80))
    map_key: Mapped[str] = mapped_column(String(40), default="town_hub")
    tile_x: Mapped[int] = mapped_column(Integer)
    tile_y: Mapped[int] = mapped_column(Integer)
    portrait: Mapped[str] = mapped_column(Text, default="")
    dialogue: Mapped[list] = mapped_column(JSON, default=list)


class QuestDefinition(CourseContent, Base):
    __tablename__ = "quest_definitions"
    id: Mapped[str] = mapped_column(String(100), primary_key=True)
    chapter_id: Mapped[int] = mapped_column(ForeignKey("chapters.id"), index=True)
    title: Mapped[str] = mapped_column(String(200))
    owner_id: Mapped[int | None] = mapped_column(ForeignKey("users.id"), nullable=True)
    definition: Mapped[dict] = mapped_column(JSON)


class PlayerQuest(Base):
    __tablename__ = "player_quests"
    __table_args__ = (UniqueConstraint("user_id", "classroom_id", "quest_id"),)
    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    classroom_id: Mapped[int] = mapped_column(ForeignKey("classrooms.id"), index=True)
    quest_id: Mapped[str] = mapped_column(String(100))
    status: Mapped[str] = mapped_column(String(24), default="active")
    completed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)


class NodeState(Base):
    __tablename__ = "node_states"
    player_quest_id: Mapped[int] = mapped_column(ForeignKey("player_quests.id", ondelete="CASCADE"), primary_key=True)
    node_id: Mapped[str] = mapped_column(String(100), primary_key=True)
    status: Mapped[str] = mapped_column(String(24), default="complete")
    choice: Mapped[str | None] = mapped_column(String(80), nullable=True)
    evidence: Mapped[dict] = mapped_column(JSON, default=dict)


WORLD_FLAG_KEY_MAX_LENGTH = 320


class WorldFlag(Base):
    __tablename__ = "world_flags"
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), primary_key=True)
    classroom_id: Mapped[int] = mapped_column(ForeignKey("classrooms.id"), primary_key=True)
    key: Mapped[str] = mapped_column(String(WORLD_FLAG_KEY_MAX_LENGTH), primary_key=True)


class PlayerPosition(Base):
    __tablename__ = "player_positions"
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), primary_key=True)
    classroom_id: Mapped[int] = mapped_column(ForeignKey("classrooms.id"), primary_key=True)
    map_key: Mapped[str] = mapped_column(String(40), default="town_hub")
    tile_x: Mapped[int] = mapped_column(Integer, default=22)
    tile_y: Mapped[int] = mapped_column(Integer, default=36)
    direction: Mapped[str] = mapped_column(String(8), default="down")


class BuildingUnlock(Base):
    __tablename__ = "building_unlocks"
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), primary_key=True)
    classroom_id: Mapped[int] = mapped_column(ForeignKey("classrooms.id"), primary_key=True)
    course_instance_id: Mapped[int] = mapped_column(ForeignKey("course_instances.id"), primary_key=True)
    building_id: Mapped[str] = mapped_column(String(100), primary_key=True)


class LearningEvent(Base):
    __tablename__ = "learning_events"
    __table_args__ = (UniqueConstraint("user_id", "classroom_id", "event_id"),)
    id: Mapped[int] = mapped_column(primary_key=True)
    event_id: Mapped[str] = mapped_column(String(100))
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    classroom_id: Mapped[int] = mapped_column(ForeignKey("classrooms.id"), index=True)
    player_quest_id: Mapped[int] = mapped_column(ForeignKey("player_quests.id"))
    node_id: Mapped[str] = mapped_column(String(100))
    evidence: Mapped[dict] = mapped_column(JSON)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)


class LearningRecord(Base):
    __tablename__ = "learning_records"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=uid)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    classroom_id: Mapped[int] = mapped_column(ForeignKey("classrooms.id"), index=True)
    activity_id: Mapped[str] = mapped_column(ForeignKey("activities.id"), index=True)
    kind: Mapped[str] = mapped_column(String(24), default="participation")
    data: Mapped[dict] = mapped_column(JSON, default=dict)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)


class StoryDefinition(CourseContent, Base):
    __tablename__ = "story_definitions"
    id: Mapped[str] = mapped_column(String(100), primary_key=True)
    chapter_id: Mapped[int | None] = mapped_column(ForeignKey("chapters.id"), nullable=True)
    quest_id: Mapped[str | None] = mapped_column(String(100), nullable=True)
    content: Mapped[dict] = mapped_column(JSON)


class StorySession(Base):
    __tablename__ = "story_sessions"
    __table_args__ = (
        Index("uq_story_session_open", "user_id", "classroom_id", unique=True,
              postgresql_where=text("status IN ('active', 'waiting')"), sqlite_where=text("status IN ('active', 'waiting')")),
    )
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=uid)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    classroom_id: Mapped[int] = mapped_column(ForeignKey("classrooms.id"), index=True)
    story_id: Mapped[str] = mapped_column(String(100))
    npc_key: Mapped[str | None] = mapped_column(ForeignKey("npcs.key"), nullable=True)
    interaction_id: Mapped[str | None] = mapped_column(String(100), nullable=True)
    anchor: Mapped[dict | None] = mapped_column(JSON, nullable=True)
    node_id: Mapped[str] = mapped_column(String(100))
    status: Mapped[str] = mapped_column(String(16), default="active")
    revision: Mapped[int] = mapped_column(Integer, default=0)
    pending_activity_id: Mapped[str | None] = mapped_column(String(100), nullable=True)
    return_mapping: Mapped[dict] = mapped_column(JSON, default=dict)
    receipts: Mapped[list] = mapped_column(JSON, default=list)


class ActivityProgress(Base):
    __tablename__ = "activity_progress"
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), primary_key=True)
    classroom_id: Mapped[int] = mapped_column(ForeignKey("classrooms.id"), primary_key=True)
    activity_id: Mapped[str] = mapped_column(ForeignKey("activities.id"), primary_key=True)
    page_no: Mapped[int | None] = mapped_column(Integer, nullable=True)
    visited_pages: Mapped[list] = mapped_column(JSON, default=list)
    completed: Mapped[bool] = mapped_column(Boolean, default=False)
    media_progress: Mapped[dict] = mapped_column(JSON, default=dict, server_default=text("'{}'"))


class Question(CourseContent, Base):
    __tablename__ = "questions"
    __table_args__ = (
        UniqueConstraint("course_instance_id", "stable_id", name="uq_questions_instance_stable"),
        CheckConstraint("source_type IN ('ai_generated', 'imported')", name="ck_questions_source_type"),
        CheckConstraint("status IN ('draft', 'ready', 'disabled')", name="ck_questions_status"),
    )
    id: Mapped[str] = mapped_column(String(100), primary_key=True)
    owner_id: Mapped[int | None] = mapped_column(ForeignKey("users.id"), nullable=True, index=True)
    source_type: Mapped[str] = mapped_column(String(24), default="imported", server_default=text("'imported'"))
    status: Mapped[str] = mapped_column(String(16), default="draft", server_default=text("'draft'"))
    title: Mapped[str] = mapped_column(String(200))
    kind: Mapped[str] = mapped_column(String(24))
    prompt: Mapped[str] = mapped_column(Text)
    options: Mapped[list] = mapped_column(JSON, default=list)
    samples: Mapped[list] = mapped_column(JSON, default=list)
    source: Mapped[dict] = mapped_column(JSON, default=dict)


class QuestionChapter(Base):
    __tablename__ = "question_chapters"
    question_id: Mapped[str] = mapped_column(ForeignKey("questions.id", ondelete="CASCADE"), primary_key=True)
    chapter_id: Mapped[int] = mapped_column(ForeignKey("chapters.id", ondelete="CASCADE"), primary_key=True, index=True)


class QuestionKnowledgePoint(Base):
    __tablename__ = "question_knowledge_points"
    question_id: Mapped[str] = mapped_column(ForeignKey("questions.id", ondelete="CASCADE"), primary_key=True)
    knowledge_point_id: Mapped[int] = mapped_column(ForeignKey("knowledge_points.id", ondelete="CASCADE"), primary_key=True, index=True)


class ActivityQuestion(Base):
    __tablename__ = "activity_questions"
    __table_args__ = (CheckConstraint("order_index >= 0", name="ck_activity_questions_order_index"),)
    activity_id: Mapped[str] = mapped_column(ForeignKey("activities.id", ondelete="CASCADE"), primary_key=True)
    question_id: Mapped[str] = mapped_column(ForeignKey("questions.id"), primary_key=True, index=True)
    order_index: Mapped[int] = mapped_column(Integer)


class QuestionSecret(Base):
    __tablename__ = "question_secrets"
    question_id: Mapped[str] = mapped_column(ForeignKey("questions.id"), primary_key=True)
    answer: Mapped[dict | list | str | bool | None] = mapped_column(JSON, nullable=True)
    hidden_cases: Mapped[list] = mapped_column(JSON, default=list)


class Submission(Base):
    __tablename__ = "submissions"
    __table_args__ = (UniqueConstraint("user_id", "classroom_id", "idempotency_key"),)
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=uid)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    classroom_id: Mapped[int] = mapped_column(ForeignKey("classrooms.id"), index=True)
    activity_id: Mapped[str] = mapped_column(ForeignKey("activities.id"), index=True)
    question_id: Mapped[str] = mapped_column(ForeignKey("questions.id"), index=True)
    idempotency_key: Mapped[str] = mapped_column(String(100))
    mode: Mapped[str] = mapped_column(String(16), default="submit")
    status: Mapped[str] = mapped_column(String(24), default="queued")
    answer: Mapped[dict | list | str | bool | None] = mapped_column(JSON, nullable=True)
    code: Mapped[str | None] = mapped_column(Text, nullable=True)
    judge_token: Mapped[str | None] = mapped_column(Text, nullable=True)
    feedback: Mapped[dict] = mapped_column(JSON, default=dict)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now, onupdate=now)


class AssessmentAttempt(Base):
    __tablename__ = "assessment_attempts"
    __table_args__ = (UniqueConstraint("user_id", "classroom_id", "activity_id"),)
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=uid)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    classroom_id: Mapped[int] = mapped_column(ForeignKey("classrooms.id"), index=True)
    activity_id: Mapped[str] = mapped_column(ForeignKey("activities.id"), index=True)
    state: Mapped[str] = mapped_column(String(16), default="draft")
    revision: Mapped[int] = mapped_column(Integer, default=0)
    question_ids: Mapped[list] = mapped_column(JSON, default=list)
    answers: Mapped[dict] = mapped_column(JSON, default=dict)
    submission_ids: Mapped[dict] = mapped_column(JSON, default=dict)
    idempotency_key: Mapped[str | None] = mapped_column(String(100), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now, onupdate=now)
    submitted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)


class CourseExample(CourseContent, Base):
    __tablename__ = "course_examples"
    id: Mapped[int] = mapped_column(primary_key=True)
    content: Mapped[dict] = mapped_column(JSON)
