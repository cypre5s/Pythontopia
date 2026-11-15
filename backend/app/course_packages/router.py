"""Course package HTTP routes and authenticated dependencies."""

from typing import Annotated

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.auth import require_teacher
from app.course_packages.contracts import ApplyInput, AvailablePackageInput
from app.course_packages.service import apply_course as _apply_course
from app.course_packages.service import available_packages as _available_packages
from app.course_packages.service import export_course as _export_course
from app.course_packages.service import preview_available_course as _preview_available_course
from app.db import get_db
from app.models import User

router = APIRouter(prefix="/teacher/classrooms/{classroom_id}/course", tags=["course-packages"])


@router.get("/export")
def export_course(classroom_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    return _export_course(classroom_id=classroom_id, db=db, user=user)


@router.get("/packages")
def available_packages(classroom_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    return _available_packages(classroom_id=classroom_id, db=db, user=user)


@router.post("/imports/available/preview")
def preview_available_course(
    classroom_id: int, body: AvailablePackageInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]
):
    return _preview_available_course(classroom_id=classroom_id, body=body, db=db, user=user)


@router.post("/imports/{preview_id}/apply")
def apply_course(
    classroom_id: int, preview_id: str, body: ApplyInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]
):
    return _apply_course(classroom_id=classroom_id, preview_id=preview_id, body=body, db=db, user=user)
