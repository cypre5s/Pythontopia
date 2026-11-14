"""Shared remote judging result and failure types."""

from __future__ import annotations

from dataclasses import dataclass


class JudgeUnavailable(Exception):
    pass


class JudgeSubmissionUnknown(Exception):
    """The remote POST may have succeeded. Never resubmit automatically."""


@dataclass(frozen=True)
class JudgeResult:
    status: str
    feedback: dict
