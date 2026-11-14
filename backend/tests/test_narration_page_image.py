"""页图预览接口：审校时左边看页面、右边听讲义。"""
import pytest

from test_narration import url
from test_narration_generation import doc_material


@pytest.fixture(autouse=True)
def authoring_key(monkeypatch):
    monkeypatch.setenv("REBORN_AUTHORING_AI_API_KEY", "test-key")


def test_page_image_returns_the_rendered_jpeg(client, db, classroom, teacher, monkeypatch):
    material = doc_material(db, classroom, "第一页正文")
    db.commit()
    monkeypatch.setattr("app.narration.render.render_page", lambda material, page_no: b"\xff\xd8\xff" + b"\x00" * 600)

    response = client.get(url(classroom, material, "/1/page-image"), headers=teacher["headers"])
    assert response.status_code == 200
    assert response.headers["content-type"] == "image/jpeg"
    assert response.content[:3] == b"\xff\xd8\xff"


def test_page_image_is_404_when_the_page_has_no_source(client, db, classroom, teacher, monkeypatch):
    material = doc_material(db, classroom, "第一页正文")
    db.commit()
    monkeypatch.setattr("app.narration.render.render_page", lambda material, page_no: None)

    response = client.get(url(classroom, material, "/1/page-image"), headers=teacher["headers"])
    assert response.status_code == 404
    assert "没有可预览" in response.json()["detail"]


def test_page_image_requires_classroom_scope(client, db, classroom, teacher, student, monkeypatch):
    material = doc_material(db, classroom, "第一页正文")
    db.commit()
    monkeypatch.setattr("app.narration.render.render_page", lambda material, page_no: b"\xff\xd8\xff" + b"\x00" * 600)

    assert client.get(url(classroom, material, "/1/page-image"), headers=student["headers"]).status_code == 403


def test_student_page_image_is_scoped_to_the_classroom(client, db, classroom, student, monkeypatch):
    """学生页图走与讲解列表一致的授权；换一个班就必须被拒。"""
    material = doc_material(db, classroom, "第一页正文")
    db.commit()
    monkeypatch.setattr("app.narration.render.render_page", lambda material, page_no: b"\xff\xd8\xff" + b"\x00" * 600)

    response = client.get(f"/api/materials/{material.id}/narrations/1/page-image?classroom_id={classroom['id'] + 99}",
                          headers=student["headers"])
    assert response.status_code in (403, 404), response.text


def test_page_image_requires_authentication(client, db, classroom, monkeypatch):
    material = doc_material(db, classroom, "第一页正文")
    db.commit()
    monkeypatch.setattr("app.narration.render.render_page", lambda material, page_no: b"\xff\xd8\xff" + b"\x00" * 600)
    response = client.get(f"/api/materials/{material.id}/narrations/1/page-image?classroom_id={classroom['id']}")
    assert response.status_code in (401, 403)
