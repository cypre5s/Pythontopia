import logging

from pythontopia_graph import main  # noqa: F401


def test_autogen_message_loggers_do_not_emit_info_content() -> None:
    assert logging.getLogger("autogen_core").level >= logging.WARNING
    assert logging.getLogger("autogen_core.events").level >= logging.WARNING
