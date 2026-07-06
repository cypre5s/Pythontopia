from pyt_agent_lab.response_audit import audit_response_records, summarize_audits


def test_audit_flags_page_mentions_when_case_has_no_evidence_contract() -> None:
    records = [
        {
            "case": {"id": "c1", "agent": "SafetyAgent", "evidence_ids": []},
            "response": '{"allow": true, "reasons": ["see page-1-006"], "evidence_ids": [], "grounding_note": "common_knowledge"}',
        }
    ]

    audits = audit_response_records(records)

    assert audits[0]["flags"] == ["page_mention_without_evidence_contract"]
    assert summarize_audits(audits)["flag_counts"]["page_mention_without_evidence_contract"] == 1


def test_audit_flags_missing_required_evidence_id() -> None:
    records = [
        {
            "case": {"id": "c2", "agent": "TutorAgent", "evidence_ids": ["page-2-014"]},
            "response": '{"diagnosis": "range", "evidence_ids": []}',
        }
    ]

    audits = audit_response_records(records)

    assert audits[0]["flags"] == ["missing_required_evidence_id"]


def test_audit_accepts_clean_json_response() -> None:
    records = [
        {
            "case": {"id": "c3", "agent": "TutorAgent", "evidence_ids": ["page-3-021"]},
            "response": '{"diagnosis": "if", "evidence_ids": ["page-3-021"]}',
        }
    ]

    audits = audit_response_records(records)

    assert audits[0]["flags"] == []
    assert summarize_audits(audits)["clean_rate"] == 1.0
