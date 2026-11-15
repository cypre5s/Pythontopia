"""Private local and object-storage adapters with bounded key access."""

from __future__ import annotations

import os
import re
import shutil
from pathlib import Path
from typing import Protocol


def storage_root() -> Path:
    path = Path(os.getenv("REBORN_STORAGE_ROOT", "./reborn-storage")).resolve()
    path.mkdir(parents=True, exist_ok=True)
    return path


def safe_key(key: str) -> str:
    if not re.fullmatch(r"[a-zA-Z0-9][a-zA-Z0-9/_.-]{0,250}", key) or any(x in {"", ".", ".."} for x in key.split("/")):
        raise ValueError("非法存储键")
    return key


class StorageAdapter(Protocol):
    def delete(self, key: str) -> None: ...
    def put(self, key: str, source: Path) -> None: ...
    def copy_to(self, key: str, destination: Path) -> None: ...
    def size(self, key: str) -> int: ...
    def chunks(self, key: str, start: int, end: int): ...


class LocalStorage:
    def delete(self, key: str):
        self.path(key).unlink(missing_ok=True)

    def path(self, key: str) -> Path:
        root = storage_root()
        target = (root / safe_key(key)).resolve()
        if not target.is_relative_to(root):
            raise ValueError("存储路径越界")
        return target

    def put(self, key: str, source: Path):
        target = self.path(key)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)

    def copy_to(self, key: str, destination: Path):
        shutil.copyfile(self.path(key), destination)

    def size(self, key: str):
        return self.path(key).stat().st_size

    def chunks(self, key: str, start: int, end: int):
        with self.path(key).open("rb") as stream:
            stream.seek(start)
            remaining = end - start + 1
            while remaining > 0:
                chunk = stream.read(min(65536, remaining))
                if not chunk:
                    break
                remaining -= len(chunk)
                yield chunk


class S3Storage:
    def delete(self, key):
        self.client.delete_object(Bucket=self.bucket, Key=self.key(key))

    def __init__(self):
        import boto3

        self.client = boto3.client("s3", endpoint_url=os.getenv("REBORN_S3_ENDPOINT") or None)
        self.bucket = os.environ["REBORN_S3_BUCKET"]
        self.prefix = os.getenv("REBORN_S3_PREFIX", "reborn/").strip("/") + "/"
        if not self.prefix.startswith("reborn/"):
            raise RuntimeError("S3 prefix must remain inside reborn/")

    def key(self, key):
        return self.prefix + safe_key(key)

    def put(self, key, source):
        self.client.upload_file(str(source), self.bucket, self.key(key))

    def copy_to(self, key, destination):
        self.client.download_file(self.bucket, self.key(key), str(destination))

    def size(self, key):
        return self.client.head_object(Bucket=self.bucket, Key=self.key(key))["ContentLength"]

    def chunks(self, key, start, end):
        stream = self.client.get_object(Bucket=self.bucket, Key=self.key(key), Range=f"bytes={start}-{end}")["Body"]
        try:
            yield from stream.iter_chunks(chunk_size=65536)
        finally:
            stream.close()


def get_storage() -> StorageAdapter:
    mode = os.getenv("REBORN_STORAGE", "local")
    if mode == "s3":
        return S3Storage()
    if mode != "local":
        raise RuntimeError("REBORN_STORAGE must be local or s3")
    return LocalStorage()
