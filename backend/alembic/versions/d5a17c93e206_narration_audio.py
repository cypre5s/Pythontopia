"""Narration audio columns: the voice is only valid for the wording it was recorded from."""
from alembic import op
import sqlalchemy as sa

revision = "d5a17c93e206"
down_revision = "c3e9a4f17b28"
branch_labels = None
depends_on = None


def upgrade():
    with op.batch_alter_table("page_narrations") as batch:
        batch.add_column(sa.Column("audio_key", sa.Text(), nullable=True))
        batch.add_column(sa.Column("audio_text_hash", sa.String(64), nullable=True))
        batch.add_column(sa.Column("audio_seconds", sa.Integer(), nullable=True))
        batch.add_column(sa.Column("audio_created_at", sa.DateTime(timezone=True), nullable=True))


def downgrade():
    with op.batch_alter_table("page_narrations") as batch:
        batch.drop_column("audio_created_at")
        batch.drop_column("audio_seconds")
        batch.drop_column("audio_text_hash")
        batch.drop_column("audio_key")
