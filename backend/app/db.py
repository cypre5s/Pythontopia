from datetime import datetime
from sqlalchemy import DateTime, create_engine, event
from sqlalchemy.orm import DeclarativeBase, Mapped, Session, mapped_column, sessionmaker, with_loader_criteria
from .config import DATABASE_URL


class Base(DeclarativeBase):
    pass


class RetirableContent:
    """Removed from current teaching; retained for immutable learner evidence."""
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)


@event.listens_for(Session, "do_orm_execute")
def current_content_only(state):
    if state.is_select and not state.is_column_load and not state.execution_options.get("include_deleted", False):
        state.statement = state.statement.options(with_loader_criteria(
            RetirableContent, lambda model: model.deleted_at.is_(None), include_aliases=True,
        ))


engine = create_engine(
    DATABASE_URL,
    pool_pre_ping=True,
    hide_parameters=True,
    pool_timeout=10,
    connect_args={"check_same_thread": False} if DATABASE_URL.startswith("sqlite") else {"connect_timeout": 5},
)
if DATABASE_URL.startswith("sqlite"):

    @event.listens_for(engine, "connect")
    def sqlite_foreign_keys(dbapi_connection, _):
        dbapi_connection.execute("PRAGMA foreign_keys=ON")


SessionLocal = sessionmaker(engine, expire_on_commit=False)


def get_db():
    with SessionLocal() as session:
        yield session
