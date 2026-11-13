"""Programming question authoring fields."""
from alembic import op
import sqlalchemy as sa

revision = "c421b8e091aa"
down_revision = "f318a72c90de"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column("questions", sa.Column("hints", sa.JSON(), nullable=False, server_default="[]"))
    op.add_column("questions", sa.Column("starter_code", sa.Text(), nullable=False, server_default=""))


def downgrade():
    op.drop_column("questions", "starter_code")
    op.drop_column("questions", "hints")
