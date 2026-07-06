from pyt_agent_lab.domain_case_factory import generate_domain_cases


def test_generate_domain_cases_creates_large_stratified_set() -> None:
    cases = generate_domain_cases(target_count=140)

    assert len(cases) == 140
    assert len({case["id"] for case in cases}) == 140
    assert {case["agent"] for case in cases} >= {
        "TutorAgent",
        "PracticeAgent",
        "ResourceDesignerAgent",
        "IntentRouterAgent",
        "GuideCoachAgent",
        "DebugAgent",
        "GroundingCriticAgent",
        "SafetyAgent",
        "PathPlannerAgent",
        "MindMapAgent",
    }
    assert {case["chapter"] for case in cases} >= set(range(1, 8))
    assert all(case["expected_markers"] for case in cases)
    assert any(case["forbidden_answer_risk"] for case in cases)


def test_no_evidence_cases_do_not_request_page_citations() -> None:
    cases = generate_domain_cases(target_count=140)
    no_evidence_cases = [case for case in cases if not case["evidence_ids"]]

    assert no_evidence_cases
    assert all("引用 page-" not in case["user"] for case in no_evidence_cases)


def test_generate_domain_cases_supports_1000_sample_research_set() -> None:
    cases = generate_domain_cases(target_count=1000)
    agent_counts = {agent: sum(1 for case in cases if case["agent"] == agent) for agent in {case["agent"] for case in cases}}
    chapter_counts = {
        chapter: sum(1 for case in cases if case["chapter"] == chapter)
        for chapter in {case["chapter"] for case in cases}
    }

    assert len(cases) == 1000
    assert len({case["id"] for case in cases}) == 1000
    assert set(agent_counts.values()) == {100}
    assert max(chapter_counts.values()) - min(chapter_counts.values()) <= 1
    assert sum(1 for case in cases if not case["evidence_ids"]) == 300
    assert sum(1 for case in cases if case["forbidden_answer_risk"]) >= 60
    assert len({case["user"] for case in cases}) >= 900
    assert all(case.get("learner_profile") for case in cases)
    assert all(case.get("task_focus") for case in cases)
