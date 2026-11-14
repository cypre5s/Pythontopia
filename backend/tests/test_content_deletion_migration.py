"""Retirement migration preserves referenced chapter rows and permits live order reuse."""
from sqlalchemy import MetaData, select, text
from sqlalchemy.orm import Session
from app.models import Chapter, KnowledgePoint
from test_curriculum_migration import migrate, sqlite_engine


def test_upgrade_referenced_chapter_retains_rows_and_foreign_keys(tmp_path):
    path = tmp_path / 'retirement.sqlite'
    url = 'sqlite:///' + path.as_posix()
    migrate(url, 'upgrade', 'd729fa031b62')
    engine = sqlite_engine(path)
    meta = MetaData()
    meta.reflect(engine)
    with engine.begin() as conn:
        conn.execute(meta.tables['chapters'].insert().values(id=901, title='保留章节', order_index=901, course_id=43))
        conn.execute(meta.tables['knowledge_points'].insert().values(id=901, chapter_id=901, title='保留知识', content={'text': '原始内容'}))
    engine.dispose()
    migrate(url, 'upgrade', 'head')
    migrate(url, 'upgrade', 'head')
    engine = sqlite_engine(path)
    with Session(engine) as db:
        assert db.scalar(select(Chapter).where(Chapter.id == 901)).title == '保留章节'
        point = db.get(KnowledgePoint, 901)
        assert point.chapter_id == 901 and point.content == {'text': '原始内容'}
        assert db.execute(text('PRAGMA foreign_key_check')).all() == []
        assert db.execute(text('PRAGMA foreign_keys')).scalar() == 1
    engine.dispose()
