"""Teacher-owned classroom account provisioning and bounded roster imports."""
import csv
import io
import zipfile
from xml.etree.ElementTree import ParseError
from defusedxml.common import DefusedXmlException
from typing import Annotated, Literal
from fastapi import APIRouter, Depends, HTTPException, UploadFile
from pydantic import BaseModel, ConfigDict, Field, ValidationError, field_validator
from sqlalchemy import delete, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session
from .account_ids import normalize_student_id
from .auth import hash_password, require_teacher
from .classrooms import own_classroom
from .db import get_db
from .models import AuthSession, Membership, User

router = APIRouter(prefix="/teacher/classrooms/{classroom_id}/students", tags=["student accounts"])
MAX_ROWS = 500
MAX_FILE = 2 * 1024 * 1024


class StudentInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    student_id: str = Field(min_length=1, max_length=32)
    name: str = Field(min_length=1, max_length=80)

    @field_validator("student_id")
    @classmethod
    def valid_id(cls, value):
        return normalize_student_id(value)

    @field_validator("name")
    @classmethod
    def valid_name(cls, value):
        if not value.strip():
            raise ValueError("姓名不能为空")
        return value.strip()


class CreateInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    students: list[StudentInput] = Field(min_length=1, max_length=MAX_ROWS)


def inspect_students(db, classroom_id, students):
    seen, errors, result = set(), [], []
    existing = {u.student_id: u for u in db.scalars(select(User).where(User.student_id.in_([s.student_id for s in students])))}
    members = set(db.scalars(select(Membership.user_id).where(Membership.classroom_id == classroom_id)))
    for index, student in enumerate(students, 2):
        old = existing.get(student.student_id)
        if student.student_id in seen:
            errors.append(f"第{index}行：学号重复")
        elif old and (old.role != "student" or old.id not in members):
            errors.append(f"第{index}行：学号已存在且不是本班学生，请由学生使用加入码加入")
        else:
            result.append({**student.model_dump(), "action": "existing" if old else "create"})
        seen.add(student.student_id)
    if errors:
        raise HTTPException(422, "；".join(errors[:20]))
    return result


@router.get("")
def roster(classroom_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    own_classroom(db, user, classroom_id)
    rows = db.scalars(select(User).join(Membership).where(Membership.classroom_id == classroom_id, User.role == "student").order_by(User.student_id))
    return [{"id": u.id, "student_id": u.student_id, "name": u.name, "must_change_password": u.must_change_password} for u in rows]


@router.post("", status_code=201)
def create_students(classroom_id: int, body: CreateInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    own_classroom(db, user, classroom_id)
    rows = inspect_students(db, classroom_id, body.students)
    created = 0
    try:
        for row in rows:
            if row["action"] == "existing":
                continue
            student = User(student_id=row["student_id"], name=row["name"], role="student",
                           password_hash=hash_password(row["student_id"]), must_change_password=True)
            db.add(student)
            db.flush()
            db.add(Membership(user_id=student.id, classroom_id=classroom_id))
            created += 1
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(409, "学号或班级关系已变化，请重新预览；本次未创建任何账号")
    return {"created": created, "existing": len(rows) - created}


def parse_table(filename, data):
    if len(data) > MAX_FILE:
        raise ValueError("文件不能超过2MB")
    if filename.lower().endswith(".csv"):
        rows = list(csv.reader(io.StringIO(data.decode("utf-8-sig")), strict=True))
    elif filename.lower().endswith(".xlsx"):
        from openpyxl import load_workbook
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            if len(archive.infolist()) > 100 or sum(i.file_size for i in archive.infolist()) > 10 * 1024 * 1024:
                raise ValueError("工作簿解压后过大")
        workbook = load_workbook(io.BytesIO(data), read_only=True, data_only=False, keep_links=False)
        try:
            if len(workbook.worksheets) != 1:
                raise ValueError("请仅保留一个工作表")
            sheet = workbook.worksheets[0]
            if (sheet.max_column or 0) > 2 or (sheet.max_row or 0) > MAX_ROWS + 1:
                raise ValueError("表格仅支持学号、姓名两列及最多500名学生")
            sheet.reset_dimensions()
            rows = []
            for cells in sheet.iter_rows(max_row=MAX_ROWS + 2, max_col=3):
                if any(c.data_type == "f" for c in cells):
                    raise ValueError("表格不能包含公式")
                values = [c.value for c in cells]
                if values[2] is not None:
                    raise ValueError("表格只能包含学号、姓名两列")
                rows.append(values[:2])
            if len(rows) > MAX_ROWS + 1:
                raise ValueError("每次导入最多500名学生")
        finally:
            workbook.close()
    else:
        raise ValueError("仅支持UTF-8 CSV或.xlsx文件")
    while rows and not any(v is not None and str(v).strip() for v in rows[-1]):
        rows.pop()
    if not rows or [str(v).strip() for v in rows[0]] != ["学号", "姓名"]:
        raise ValueError("首行必须依次为：学号、姓名")
    if not 1 <= len(rows) - 1 <= MAX_ROWS:
        raise ValueError("每次导入1–500名学生")
    students = []
    for index, row in enumerate(rows[1:], 2):
        if len(row) != 2:
            raise ValueError(f"第{index}行：需要学号和姓名两列")
        # Reject numeric Excel IDs: Excel may already have lost zeros or precision.
        if not isinstance(row[0], str):
            raise ValueError(f"第{index}行：请将学号设为文本格式后重新填写，以保留前导零")
        try:
            students.append(StudentInput(student_id=row[0], name=row[1]))
        except ValidationError:
            raise ValueError(f"第{index}行：学号须为1–32位数字，姓名须为1–80字")
    return students


@router.post("/import-preview")
def preview_import(classroom_id: int, file: UploadFile, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    own_classroom(db, user, classroom_id)
    try:
        students = parse_table(file.filename or "", file.file.read(MAX_FILE + 1))
    except (ValueError, UnicodeError, csv.Error, zipfile.BadZipFile, KeyError, OSError, ParseError, DefusedXmlException) as exc:
        raise HTTPException(422, str(exc) if isinstance(exc, ValueError) else "表格无法读取，请检查文件格式")
    finally:
        file.file.close()
    return {"students": inspect_students(db, classroom_id, students)}


class BatchInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    student_ids: list[str] = Field(min_length=1, max_length=MAX_ROWS)
    action: Literal["reset-password", "remove"]

    @field_validator("student_ids")
    @classmethod
    def valid_ids(cls, values):
        return sorted({normalize_student_id(v) for v in values})


@router.post("/batch")
def batch_students(classroom_id: int, body: BatchInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    own_classroom(db, user, classroom_id)
    rows = list(db.scalars(select(User).where(User.student_id.in_(body.student_ids)).order_by(User.id).with_for_update()))
    if len(rows) != len(body.student_ids) or any(u.role != "student" or not db.get(Membership, (u.id, classroom_id)) for u in rows):
        raise HTTPException(403, "只能操作当前班级的学生；本次未做任何修改")
    for student in rows:
        if body.action == "reset-password":
            student.password_hash = hash_password(student.student_id)
            student.must_change_password = True
            db.execute(delete(AuthSession).where(AuthSession.user_id == student.id))
        else:
            db.delete(db.get(Membership, (student.id, classroom_id)))
    db.commit()
    return {"affected": len(rows)}
