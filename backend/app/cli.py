import argparse
import os
from .auth import hash_password
from .db import SessionLocal
from .models import User
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from .account_ids import normalize_student_id


def main():
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    create = sub.add_parser("provision-user")
    create.add_argument("--student-id", required=True)
    create.add_argument("--name", default="教师")
    create.add_argument("--role", choices=["teacher", "admin", "student"], default="teacher")
    create.add_argument("--password-env", required=True)
    assign = sub.add_parser("assign-student-id")
    assign.add_argument("--user-id", type=int, required=True)
    assign.add_argument("--student-id", required=True)
    args = parser.parse_args()
    try:
        student_id = normalize_student_id(args.student_id)
    except ValueError as exc:
        parser.error(str(exc))
    if args.command == "assign-student-id":
        with SessionLocal() as db:
            user = db.get(User, args.user_id)
            if not user:
                parser.error("Account not found")
            if user.student_id is not None:
                parser.error("Account already has a student ID")
            user.student_id = student_id
            try:
                db.commit()
            except IntegrityError:
                db.rollback()
                parser.error("Student ID already exists")
            print(f"Assigned student ID to user #{user.id}")
        return
    password = os.environ.get(args.password_env, "")
    if len(password) < 12:
        parser.error("Password environment variable must contain at least 12 characters")
    with SessionLocal() as db:
        if db.scalar(select(User).where(User.student_id == student_id)):
            parser.error("Account already exists")
        user = User(student_id=student_id, name=args.name, role=args.role, password_hash=hash_password(password))
        db.add(user)
        try:
            db.commit()
        except IntegrityError:
            db.rollback()
            parser.error("Student ID already exists")
        print(f"Created {user.role} #{user.id}")


if __name__ == "__main__":
    main()
