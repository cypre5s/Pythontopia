"""Compute replacement impact without changing course content or learner history."""

import hashlib

from sqlalchemy import func, select

from app.course_content.snapshot import snapshot_content, stable_id
from app.course_instances import CONTENT_MODELS, MODEL_NAMES, content_mapping
from app.course_packages.contracts import CONFIG_FILES
from app.integration_models import AssistantSession
from app.materials.storage import get_storage
from app.models import ActivityProgress, AssessmentAttempt, LearningRecord, Material, PlayerQuest, StorySession, Submission


def has_learning_history(db, classroom_id):
    from app.models import BuildingUnlock, PlayerPosition, WorldFlag

    return any(
        db.scalar(select(model).where(model.classroom_id == classroom_id).limit(1)) is not None
        for model in (
            PlayerQuest,
            StorySession,
            ActivityProgress,
            LearningRecord,
            Submission,
            AssessmentAttempt,
            AssistantSession,
            WorldFlag,
            PlayerPosition,
            BuildingUnlock,
        )
    )


def preview_impact(db, instance, bundle):
    snapshot = snapshot_content(db, instance) if instance else None
    previous = snapshot[0] if snapshot else {kind: [] for kind in MODEL_NAMES.values()}
    diff = {}

    def compare(kind, old, new):
        diff[kind] = {
            "added": sorted(new.keys() - old.keys()),
            "removed": sorted(old.keys() - new.keys()),
            "updated": sorted(key for key in old.keys() & new.keys() if old[key] != new[key]),
        }

    for kind, incoming in bundle["data"].items():
        compare(kind, {row["id"]: row for row in previous[kind]}, {row["id"]: row for row in incoming})
    compare("answers", {row["id"]: row for row in snapshot[1]} if snapshot else {}, {row["id"]: row for row in bundle["secrets"]})
    old_config = snapshot[3] if snapshot else {}
    compare("configuration", {key: old_config.get(key, {}) for key in CONFIG_FILES}, bundle["configuration"])
    compare(
        "releases",
        {row["chapter_id"]: row for row in snapshot[2]} if snapshot else {},
        {row["chapter_id"]: row for row in bundle["course"]["releases"]},
    )
    old_assets = {row["id"]: row["sha256"] for row in old_config.get("_assets", [])}
    if snapshot:
        material_assets = {row["id"]: row["asset_id"] for row in previous["material"]}
        adapter = get_storage()
        for material in snapshot[4][Material]:
            if material.storage_key:
                try:
                    digest = hashlib.sha256()
                    for chunk in adapter.chunks(material.storage_key, 0, adapter.size(material.storage_key) - 1):
                        digest.update(chunk)
                    old_assets[material_assets[stable_id(material)]] = digest.hexdigest()
                except Exception:
                    old_assets[material_assets[stable_id(material)]] = None
    compare("assets", old_assets, {row["id"]: row["sha256"] for row in bundle["assets"]})
    compare(
        "manifest",
        {"package_id": instance.package_id, "name": instance.name, "package_version": instance.package_version} if instance else {},
        {key: bundle["manifest"][key] for key in ("package_id", "name", "package_version")},
    )
    blockers, learning_impact = [], []
    if instance:
        from app.db import RetirableContent

        for model in CONTENT_MODELS:
            if not issubclass(model, RetirableContent):
                continue
            incoming_ids = {row["id"] for row in bundle["data"][MODEL_NAMES[model]]}
            retired = db.scalars(
                select(model.stable_id)
                .where(model.course_instance_id == instance.id, model.deleted_at.is_not(None), model.stable_id.in_(incoming_ids))
                .execution_options(include_deleted=True)
            ).all()
            if retired:
                blockers.append(f"{MODEL_NAMES[model]} 包含已删除且保留历史的ID，请为新内容使用新ID：" + "、".join(retired[:10]))
    if instance and db.scalar(
        select(Material.id).where(Material.course_instance_id == instance.id, Material.status.in_(["queued", "processing"])).limit(1)
    ):
        blockers.append("班级资料仍在转换，请完成后重新预览")
    if instance and has_learning_history(db, instance.classroom_id):
        if instance.package_id != bundle["manifest"]["package_id"]:
            blockers.append("班级已有学习记录，不能替换为另一课程；请创建新班级")
        elif any(diff[kind]["removed"] for kind in MODEL_NAMES.values()):
            blockers.append("班级已有学习记录，本次更新包含删除定义；请保留原ID并停用内容后重新预览")
    if instance:
        from app.characters import character_issues

        blockers.extend(issue.diagnostic()["message"] for issue in character_issues(db, instance, bundle["data"]))
        incoming = {kind: {row["id"]: row for row in values} for kind, values in bundle["data"].items()}
        current = {kind: {row["id"]: row for row in values} for kind, values in previous.items()}
        maps = content_mapping(db, instance)
        # Retired definitions still own historical foreign keys, even when no
        # learner has used them. Package replacement must not hard-delete their
        # current parents or fail later as an unexplained integrity error.
        from sqlalchemy import or_

        table_kinds = {model.__tablename__: MODEL_NAMES[model] for model in CONTENT_MODELS}
        for model in CONTENT_MODELS:
            if not issubclass(model, RetirableContent):
                continue
            references = []
            for foreign in model.__table__.foreign_keys:
                kind = table_kinds.get(foreign.column.table.name)
                if kind:
                    removed_keys = [maps[kind][key] for key in diff[kind]["removed"]]
                    if removed_keys:
                        references.append(foreign.parent.in_(removed_keys))
            if references and db.scalar(
                select(model)
                .where(model.course_instance_id == instance.id, model.deleted_at.is_not(None), or_(*references))
                .execution_options(include_deleted=True)
                .limit(1)
            ):
                blockers.append("待移除定义仍被历史保留内容引用，请先使用级联删除预览处理，再重新导入")
        attempts = list(db.scalars(select(AssessmentAttempt).where(AssessmentAttempt.classroom_id == instance.classroom_id)))
        submissions = list(db.scalars(select(Submission).where(Submission.classroom_id == instance.classroom_id)))
        active_stories = list(
            db.scalars(select(StorySession).where(StorySession.classroom_id == instance.classroom_id, StorySession.status.in_(["active", "waiting"])))
        )

        def protect(kind, stable_id, count, reason):
            if count:
                learning_impact.append({"kind": kind, "stable_id": stable_id, "affected_records": count, "reason": reason})
                blockers.append(reason)

        from app.authoring_protection import changed_quest_structure

        for stable in diff["quest"]["updated"]:
            old, new = current["quest"][stable], incoming["quest"][stable]
            if old["chapter_id"] != new["chapter_id"] or changed_quest_structure(old["definition"], new["definition"]):
                progress_count = db.scalar(
                    select(func.count())
                    .select_from(PlayerQuest)
                    .where(PlayerQuest.classroom_id == instance.classroom_id, PlayerQuest.quest_id == maps["quest"][stable])
                )
                protect("quest", stable, progress_count, f"任务「{old['title']}」已有班级进度，请在教学流程编辑器中确认重置后再调整任务结构")
        for stable in diff["question"]["updated"]:
            key = maps["question"][stable]
            if current["question"][stable]["kind"] != incoming["question"][stable]["kind"]:
                count = sum(row.question_id == key for row in submissions) + sum(key in row.question_ids for row in attempts)
                protect("question", stable, count, "已有正式题目证据，改变题型需要新的题目ID")
        for stable in diff["activity"]["updated"]:
            old, new = current["activity"][stable], incoming["activity"][stable]
            key = maps["activity"][stable]
            if old["kind"] != new["kind"] or (old["kind"] == "assessment" and old["question_ids"] != new["question_ids"]):
                count = sum(row.activity_id == key for row in attempts) + sum(row.activity_id == key for row in submissions)
                count += len(
                    list(
                        db.scalars(
                            select(ActivityProgress).where(
                                ActivityProgress.classroom_id == instance.classroom_id, ActivityProgress.activity_id == key
                            )
                        )
                    )
                )
                count += len(
                    list(
                        db.scalars(
                            select(LearningRecord).where(LearningRecord.classroom_id == instance.classroom_id, LearningRecord.activity_id == key)
                        )
                    )
                )
                protect("activity", stable, count, "活动已有学习或测验证据，改变活动类型或测验题集需要新的活动ID")
        changed_activities = {maps["activity"][stable] for stable in diff["activity"]["updated"]}
        changed_assets = set(diff["assets"]["updated"] + diff["assets"]["removed"])
        changed_materials = set(diff["material"]["updated"]) | {
            row["id"] for row in previous["material"] if row["asset_id"] in changed_assets or row["preview_asset_id"] in changed_assets
        }
        changed_questions = set(diff["question"]["updated"] + diff["answers"]["updated"])
        changed_activities.update(
            maps["activity"][stable]
            for stable, row in current["activity"].items()
            if row["material_id"] in changed_materials or changed_questions.intersection(row["question_ids"])
        )
        for stable in maps["story"]:
            affected = [
                session
                for session in active_stories
                if session.story_id == maps["story"][stable]
                and (stable in diff["story"]["updated"] or stable in diff["story"]["removed"] or session.pending_activity_id in changed_activities)
            ]
            protect("story", stable, len(affected), "流程仍有进行中或等待活动的会话，请先完成或关闭后重新预览更新")
    return {
        "diff": diff,
        "blockers": list(dict.fromkeys(blockers)),
        "learning_impact": learning_impact,
        "warnings": ["同ID内容更新保留历史证据与成绩，不重新评分"],
    }
