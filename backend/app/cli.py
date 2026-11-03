import argparse
import os
from .auth import hash_password
from .db import SessionLocal
from .models import User
from sqlalchemy import select


def main():
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    create = sub.add_parser("provision-user")
    create.add_argument("--email", required=True)
    create.add_argument("--name", default="教师")
    create.add_argument("--role", choices=["teacher", "admin", "student"], default="teacher")
    create.add_argument("--password-env", required=True)
    args = parser.parse_args()
    password = os.environ.get(args.password_env, "")
    if len(password) < 12:
        parser.error("Password environment variable must contain at least 12 characters")
    with SessionLocal() as db:
        if db.scalar(select(User).where(User.email == args.email.strip().lower())):
            parser.error("Account already exists")
        user = User(email=args.email.strip().lower(), name=args.name, role=args.role, password_hash=hash_password(password))
        db.add(user)
        db.commit()
        print(f"Created {user.role} #{user.id}")


if __name__ == "__main__":
    main()
