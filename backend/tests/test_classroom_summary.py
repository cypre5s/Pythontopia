"""The member-facing classroom summary exposes its teacher name, not contacts or codes."""


def test_classroom_summary_shows_only_authorized_teacher_name(client, teacher, student, classroom):
    other = client.post("/api/classrooms", json={"name": "未加入的班级"}, headers=teacher["headers"])
    assert other.status_code == 201
    response = client.get("/api/classrooms", headers=student["headers"])
    assert response.status_code == 200
    assert [item["id"] for item in response.json()] == [classroom["id"]]
    assert response.json()[0]["teacher_name"] == "教师"
    assert "join_code" not in response.json()[0]
    assert teacher["email"] not in response.text
