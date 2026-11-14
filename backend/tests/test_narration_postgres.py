"""First-job creation is serialized even before a narration_jobs row exists."""
import os
from concurrent.futures import ThreadPoolExecutor
from threading import Barrier
from uuid import uuid4

import pytest
from sqlalchemy import create_engine, select, text
from sqlalchemy.orm import Session

from app.db import Base
from app.models import Material
from app.narration import repository
from fixture_seed import seed


@pytest.mark.skipif(not os.getenv("REBORN_TEST_DATABASE_URL"), reason="Requires isolated PostgreSQL")
def test_first_acquisition_has_one_owner():
    url = os.environ["REBORN_TEST_DATABASE_URL"]
    admin = create_engine(url)
    schema = "narration_lock_" + uuid4().hex
    with admin.begin() as connection:
        connection.execute(text(f'CREATE SCHEMA "{schema}"'))
    engine = create_engine(url, connect_args={"options": f"-csearch_path={schema}"})
    try:
        Base.metadata.create_all(engine)
        with Session(engine) as db:
            seed(db)
            mid = db.scalar(select(Material.id).limit(1))
        barrier = Barrier(2)
        def acquire():
            with Session(engine) as db:
                material = db.get(Material, mid)
                barrier.wait(timeout=10)
                try:
                    repository.acquire(db, material, "generate")
                    return "owned"
                except repository.JobBusy:
                    return "busy"
        with ThreadPoolExecutor(max_workers=2) as pool:
            results = list(pool.map(lambda _: acquire(), range(2)))
        assert sorted(results) == ["busy", "owned"]
    finally:
        engine.dispose()
        with admin.begin() as connection:
            connection.execute(text(f'DROP SCHEMA "{schema}" CASCADE'))
        admin.dispose()
