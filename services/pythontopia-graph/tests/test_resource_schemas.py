from __future__ import annotations

import pytest

from pythontopia_graph.schemas.resource import (
    HandoutContent,
    HandoutSection,
    PracticeContent,
    ProblemSpec,
    ResourceDraft,
    SampleIo,
)


def test_resource_draft_requires_exactly_one_body() -> None:
    handout = HandoutContent(
        sections=[HandoutSection(heading="A", paragraphs=["text"])]
    )
    with pytest.raises(ValueError):
        ResourceDraft(title="t", content_format="MARKDOWN")
    with pytest.raises(ValueError):
        ResourceDraft(
            title="t",
            content_format="MARKDOWN",
            handout=handout,
            practice=PracticeContent(
                problems=[
                    ProblemSpec(
                        title="p",
                        statement_md="long enough statement",
                        sample_io=[SampleIo(input="a", output="b")],
                    )
                ]
            ),
        )


def test_practice_content_rejects_hidden_keys() -> None:
    with pytest.raises(ValueError):
        PracticeContent(
            problems=[
                ProblemSpec(
                    title="p",
                    statement_md="this problem references hidden_test_cases internally",
                    sample_io=[SampleIo(input="a", output="b")],
                )
            ]
        )
