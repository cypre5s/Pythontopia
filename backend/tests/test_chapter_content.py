from copy import deepcopy

import pytest

from app.chapter_content import load_content, load_yaml, validate_sources


@pytest.mark.parametrize("text", [
    "schema_version: 1\nschema_version: 1\n",
    "schema_version: 1\nvalue: !!python/object/apply:os.system ['echo bad']\n",
    "schema_version: 1\nvalue: &shared [one]\nother: *shared\n",
    "schema_version: 1\nvalue: 2026-11-05\n",
    "schema_version: 1\nvalue: " + "[" * 35 + "1" + "]" * 35 + "\n",
])
def test_chapter_text_rejects_ambiguous_or_executable_yaml(tmp_path, text):
    source = tmp_path / "chapter.yaml"
    source.write_text(text, encoding="utf-8")
    with pytest.raises(ValueError, match="chapter.yaml"):
        load_yaml(source)


def test_published_sources_validate_without_database():
    counts = validate_sources()
    assert counts == {"npcs": 4, "activities": 8, "quests": 3, "stories": 1, "questions": 9}
    content = load_content()
    story = content["stories"][0]
    assert story["quest"]["version"] == 2
    assert story["story"]["bindings"]["lesson"]["version"] == 2
    assert len([q for q in content["questions"] if q["activity_id"] == "ch1-assessment"]) == 3


def test_repeated_seed_refuses_same_version_story_changes(db, monkeypatch):
    from app import seed as seed_module

    changed = deepcopy(load_content())
    changed["stories"][0]["story"]["nodes"][0]["text"] += "修改已发布稿件"
    monkeypatch.setattr(seed_module, "load_content", lambda: changed)
    with pytest.raises(ValueError, match="修改内容必须增加版本"):
        seed_module.seed(db)
    db.rollback()


def test_repeated_seed_refuses_same_version_quest_changes(db, monkeypatch):
    from app import seed as seed_module

    changed = deepcopy(load_content())
    changed["quests"][0]["title"] += "修改"
    monkeypatch.setattr(seed_module, "load_content", lambda: changed)
    with pytest.raises(ValueError, match="修改内容必须增加版本"):
        seed_module.seed(db)
    db.rollback()


def test_new_question_and_activity_version_preserve_old_question_set(db, monkeypatch):
    from app import seed as seed_module
    from app.models import ActivityVersion

    changed = deepcopy(load_content())
    question = deepcopy(next(q for q in changed["questions"] if q["activity_id"] == "ch1-practice"))
    question["id"] = "new-edition-question"
    question["title"] += "修订题"
    changed["questions"].append(question)
    activity = deepcopy(next(a for a in changed["activities"] if a["id"] == "ch1-practice"))
    original_ids = list(activity["content"]["question_ids"])
    activity["version"] = 2
    activity["content"]["question_ids"] = [*original_ids, question["id"]]
    changed["activities"].append(activity)
    monkeypatch.setattr(seed_module, "load_content", lambda: changed)
    seed_module.seed(db)
    seed_module.seed(db)
    assert db.get(ActivityVersion, ("ch1-practice", 1)).snapshot["content"]["question_ids"] == original_ids
    assert db.get(ActivityVersion, ("ch1-practice", 2)).snapshot["content"]["question_ids"] == [*original_ids, question["id"]]


def test_same_question_id_cannot_silently_ignore_revised_text(db, monkeypatch):
    from app import seed as seed_module

    changed = deepcopy(load_content())
    changed["questions"][0]["prompt"] += "修订"
    monkeypatch.setattr(seed_module, "load_content", lambda: changed)
    with pytest.raises(ValueError, match="修订必须使用新题目ID"):
        seed_module.seed(db)
    db.rollback()
