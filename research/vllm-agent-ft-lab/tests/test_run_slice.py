from pyt_agent_lab.run_slice import filter_rows_by_case_ids


def test_filter_rows_by_case_ids_keeps_only_requested_rows() -> None:
    rows = [
        {"case_id": "a", "score": "0.1"},
        {"case_id": "b", "score": "0.2"},
        {"case_id": "c", "score": "0.3"},
    ]

    assert filter_rows_by_case_ids(rows, ["c", "a"]) == [
        {"case_id": "a", "score": "0.1"},
        {"case_id": "c", "score": "0.3"},
    ]
