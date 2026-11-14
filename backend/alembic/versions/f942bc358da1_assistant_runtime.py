"""Durable assistant conversation and encrypted model settings."""
from alembic import op
import sqlalchemy as sa

revision = "f942bc358da1"
down_revision = "e831ab247c90"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column("assistant_sessions", sa.Column("revision", sa.Integer(), nullable=False, server_default="0"))
    op.add_column("assistant_sessions", sa.Column("runtime", sa.JSON(), nullable=False, server_default="{}"))
    for column in (
        sa.Column("client_request_id", sa.String(100), nullable=True),
        sa.Column("request_hash", sa.String(64), nullable=True),
        sa.Column("reserved_tokens", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("trace", sa.JSON(), nullable=False, server_default="{}"),
    ):
        op.add_column("assistant_calls", column)
    with op.batch_alter_table("assistant_calls") as batch:
        batch.create_unique_constraint("uq_assistant_request", ["session_id", "client_request_id"])
    op.create_table("assistant_model_settings",
                    sa.Column("id", sa.Integer(), primary_key=True),
                    sa.Column("revision", sa.Integer(), nullable=False),
                    sa.Column("configuration", sa.JSON(), nullable=False),
                    sa.Column("encrypted_key", sa.Text(), nullable=False))


def downgrade():
    op.drop_table("assistant_model_settings")
    with op.batch_alter_table("assistant_calls") as batch:
        batch.drop_constraint("uq_assistant_request", type_="unique")
        for name in ("client_request_id", "request_hash", "reserved_tokens", "trace"):
            batch.drop_column(name)
    op.drop_column("assistant_sessions", "runtime")
    op.drop_column("assistant_sessions", "revision")
