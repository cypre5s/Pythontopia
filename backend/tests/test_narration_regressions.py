"""Regressions reproduced during PR review; assert protected production behavior."""
import pytest
from app.narration import repository
from app.narration.models import NarrationCalibration
from test_narration import material_fixture, url
from test_narration_generation import doc_material

@pytest.fixture(autouse=True)
def model_stub(monkeypatch):
    monkeypatch.setenv('REBORN_AUTHORING_AI_API_KEY', 'review-fixture')
    monkeypatch.setenv('REBORN_AI_API_KEY', 'review-fixture')
    calls=[]
    def chat(*args, **kwargs):
        calls.append(True)
        return {'raw':{'answer':'fixture answer','cites':[1]},'text':'replacement generated wording',
                'bridge_in':'','anchors':[],'usage':{},'model':'fixture'}
    monkeypatch.setattr('app.narration.client.chat',chat)
    return calls

def test_image_only_replacement_invalidates_review(client,db,classroom,teacher,student):
    material=material_fixture(db,classroom,[''])
    material.storage_key='old.pdf'
    db.commit()
    assert client.put(url(classroom,material,'/1'),json={'text':'old approved explanation'},headers=teacher['headers']).status_code==200
    assert client.post(url(classroom,material,'/1/accept'),headers=teacher['headers']).status_code==200
    material.storage_key='new.pdf'
    material.content={**material.content,'preview_key':'new-preview.pdf'}
    db.commit()
    response=client.get(f'/api/materials/{material.id}/narrations?classroom_id={classroom["id"]}',headers=student['headers'])
    assert response.json()['pages'] == []

def test_old_report_preserves_approved_teacher_edit(client,db,classroom,teacher,model_stub):
    material=doc_material(db,classroom,'page text')
    db.commit()
    client.put(url(classroom,material,'/1'),json={'text':'initial draft'},headers=teacher['headers'])
    db.add(NarrationCalibration(material_id=material.id,report={'issues':[{'page_no':1,'status':'draft'}]}))
    db.commit()
    client.put(url(classroom,material,'/1'),json={'text':'teacher corrected and approved'},headers=teacher['headers'])
    client.post(url(classroom,material,'/1/accept'),headers=teacher['headers'])
    response=client.post(url(classroom,material,'/calibrate/apply'),headers=teacher['headers'])
    assert response.status_code==200,response.text
    item=repository.row(db,material.id,1)
    assert item.text=='teacher corrected and approved' and item.status=='accepted'
    assert model_stub == []
    assert response.json()['awaiting_teacher'] == [1]

@pytest.mark.parametrize("suffix,body", [("/1/generate", None), ("/generate", {"first_page": 1, "last_page": 1})])
def test_unconfigured_generation_releases_lock(client,db,classroom,teacher,monkeypatch,suffix,body):
    material=doc_material(db,classroom,'page text')
    db.commit()
    monkeypatch.delenv('REBORN_AUTHORING_AI_API_KEY')
    response=client.post(url(classroom,material,suffix),json=body,headers=teacher['headers'])
    assert response.status_code==409
    assert not repository.job_running(repository.job_of(db,material.id))


def test_generation_cannot_overwrite_edit_made_during_model_call(client, db, classroom, teacher, monkeypatch):
    from sqlalchemy.orm import Session
    from app.models import Material
    material = doc_material(db, classroom, "原文")
    repository.upsert(db, material, 1, text="原始草稿")
    db.commit()
    def model(*args, **kwargs):
        with Session(db.bind) as writer:
            repository.upsert(writer, writer.get(Material, material.id), 1, text="老师刚刚修订")
            writer.commit()
        return {"text": "迟到的模型稿", "bridge_in": "", "anchors": [], "usage": {}, "model": "test"}
    monkeypatch.setattr("app.narration.client.chat", model)
    result = client.post(url(classroom, material, "/1/generate"), headers=teacher["headers"])
    assert result.status_code == 422, result.text
    db.expire_all()
    assert repository.row(db, material.id, 1).text == "老师刚刚修订"
    assert not repository.job_running(repository.job_of(db, material.id))


def test_report_uses_source_and_preserves_a_later_draft_edit(client, db, classroom, teacher, monkeypatch):
    import json
    material = doc_material(db, classroom, "原文中的独特证据")
    repository.upsert(db, material, 1, text="待检查稿")
    db.commit()
    calls = []
    def model(messages, **kwargs):
        payload = json.loads(messages[1]["content"].rsplit("\n\n", 1)[1])
        assert "原文中的独特证据" in payload["pages"][0]["source_text"]
        calls.append(payload)
        return {"raw": {"issues": [{"page_no": 1, "kind": "mismatch", "detail": "与原文不符"}]}}
    monkeypatch.setattr("app.narration.client.chat", model)
    assert client.post(url(classroom, material, "/calibrate"), headers=teacher["headers"]).status_code == 200
    repository.upsert(db, material, 1, text="老师已经修好了，尚未接受")
    db.commit()
    result = client.post(url(classroom, material, "/calibrate/apply"), headers=teacher["headers"])
    assert result.status_code == 200, result.text
    assert result.json()["regenerated"] == [] and result.json()["awaiting_teacher"] == [1]
    assert len(calls) == 1


def test_old_worker_cannot_release_replacement_lease(db, classroom):
    from datetime import timedelta
    from sqlalchemy.orm import Session
    from app.models import Material, now
    material = doc_material(db, classroom, "测试")
    db.commit()
    job = repository.acquire(db, material, "generate")
    # A second worker takes over only after the old heartbeat expires.
    job.heartbeat_at = now() - timedelta(seconds=400)
    db.commit()
    with Session(db.bind) as worker:
        repository.acquire(worker, worker.get(Material, material.id), "audio")
        repository.release(db, material.id, status="failed")
        worker.expire_all()
        assert repository.job_of(worker, material.id).kind == "audio"
        assert repository.job_running(repository.job_of(worker, material.id))
        repository.release(worker, material.id)
