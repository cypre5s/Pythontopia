"""Private durable classroom course import previews and authored configuration."""
from alembic import op
import sqlalchemy as sa

revision = "f21b9e7c6a40"
down_revision = "e90a7c4d152b"
branch_labels = None
depends_on = None


def upgrade():
    op.create_table("course_package_state",
        sa.Column("course_instance_id", sa.Integer(), sa.ForeignKey("course_instances.id"), primary_key=True),
        sa.Column("configuration", sa.JSON(), nullable=False))
    op.create_table("course_import_previews",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("teacher_id", sa.Integer(), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("classroom_id", sa.Integer(), sa.ForeignKey("classrooms.id"), nullable=False),
        sa.Column("expected_revision", sa.Integer(), nullable=False),
        sa.Column("archive_sha256", sa.String(64), nullable=False),
        sa.Column("storage_key", sa.Text(), nullable=False),
        sa.Column("summary", sa.JSON(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("applied_request_id", sa.String(36), nullable=True),
        sa.Column("result", sa.JSON(), nullable=True),
        sa.UniqueConstraint("classroom_id", "applied_request_id", name="uq_course_import_request"))
    op.create_index("ix_course_import_previews_teacher_id", "course_import_previews", ["teacher_id"])
    op.create_index("ix_course_import_previews_classroom_id", "course_import_previews", ["classroom_id"])


def downgrade():
    op.drop_table("course_import_previews")
    op.drop_table("course_package_state")
