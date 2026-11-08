"""Track temporary student passwords without changing existing accounts."""
from alembic import op
import sqlalchemy as sa

revision = "c3187f5db204"
down_revision = "b2076e4ca193"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column("users", sa.Column("must_change_password", sa.Boolean(), nullable=False, server_default=sa.false()))


def downgrade():
    op.drop_column("users", "must_change_password")
