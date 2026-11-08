"""Replace email login with assigned student IDs without deleting learning data.

Revision ID: a91c7d20e648
Revises: f4b92c71a806
"""
from alembic import op
import sqlalchemy as sa

revision = "a91c7d20e648"
down_revision = "f4b92c71a806"
branch_labels = None
depends_on = None


def upgrade():
    # Do not guess real student IDs from email addresses. Unassigned legacy users
    # cannot authenticate until an operator assigns their verified number.
    op.add_column("users", sa.Column("student_id", sa.String(32), nullable=True))
    op.create_index("ix_users_student_id", "users", ["student_id"], unique=True)
    op.execute(sa.text("DELETE FROM auth_sessions"))
    op.drop_index("ix_users_email", table_name="users")
    # Native DROP COLUMN (SQLite >=3.35 / PostgreSQL) preserves referencing FKs.
    op.drop_column("users", "email")


def downgrade():
    raise RuntimeError("Email identities cannot be reconstructed; restore the pre-upgrade backup instead.")
