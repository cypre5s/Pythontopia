from pythontopia_graph.nodes.code_runner import CodeRunResult, run_python


def test_runs_and_captures_stdout() -> None:
    result = run_python("print(1 + 1)")

    assert isinstance(result, CodeRunResult)
    assert result.stdout.strip() == "2"
    assert result.exit_code == 0
    assert result.timed_out is False


def test_captures_stderr_on_error() -> None:
    result = run_python("raise ValueError('boom')")

    assert result.exit_code != 0
    assert "ValueError" in result.stderr


def test_times_out_long_loop() -> None:
    result = run_python("while True: pass", timeout_s=1)

    assert result.timed_out is True


def test_truncates_huge_output() -> None:
    result = run_python("print('x' * 100000)", max_output=2000)

    assert len(result.stdout) <= 2024
    assert result.truncated is True
