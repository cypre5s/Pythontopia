"""Separate saved action prompts from executable interface actions once."""
from copy import deepcopy
from hashlib import sha256

from alembic import op
import sqlalchemy as sa

revision = "d74e90bc21a6"
down_revision = "c38d01fe72b9"
branch_labels = None
depends_on = None


def _clean_content(content):
    cleaned = deepcopy(content)
    nodes = []
    ids = {node["id"] for node in content.get("nodes", [])}
    for source in cleaned.get("nodes", []):
        if source.get("type") != "action" or "text" not in source:
            nodes.append(source)
            continue
        prompt = source.pop("text")
        # Only these exact redundant labels were shipped in the course pack.
        redundant = source.get("action") == "open_workspace" and (
            (source.get("workspace"), prompt) in {("library", "打开图书馆"), ("journal", "查看学习日志")}
        )
        if prompt and not redundant:
            original_id = source["id"]
            suffix = 0
            while True:
                action_id = "action-" + sha256(f"{original_id}:{suffix}".encode()).hexdigest()[:24]
                if action_id not in ids:
                    break
                suffix += 1
            ids.add(action_id)
            # Keep every incoming edge and saved session at the original prompt.
            nodes.append({"id": original_id, "type": "dialogue", "text": prompt, "next": action_id})
            source["id"] = action_id
        nodes.append(source)
    if len(nodes) > 200:
        raise RuntimeError("旧操作提示拆分后超过200个节点，请先整理流程再升级")
    cleaned["nodes"] = nodes
    return cleaned


def upgrade():
    bind = op.get_bind()
    stories = sa.table("story_definitions", sa.column("id", sa.String),
                       sa.column("course_instance_id", sa.Integer), sa.column("content", sa.JSON))
    instances = sa.table("course_instances", sa.column("id", sa.Integer), sa.column("revision", sa.Integer))
    changes = []
    for story_id, instance_id, content in bind.execute(sa.select(stories)):
        if any(node.get("type") == "action" and "text" in node for node in content.get("nodes", [])):
            changes.append((story_id, instance_id, _clean_content(content)))
    # Preflight every graph before writing, including on SQLite migrations.
    for story_id, _, content in changes:
        bind.execute(stories.update().where(stories.c.id == story_id).values(content=content))
    for instance_id in {instance_id for _, instance_id, _ in changes if instance_id is not None}:
        bind.execute(instances.update().where(instances.c.id == instance_id).values(revision=instances.c.revision + 1))


def downgrade():
    raise RuntimeError("请使用升级前备份恢复；不恢复操作节点的旧提示字段")
