"""Fail readiness if required isolation controls are missing."""
import json
import urllib.request

with urllib.request.urlopen("http://127.0.0.1:5050/config", timeout=2) as response:
    config = json.load(response)["runnerConfig"]
assert config["cgroupType"] in (1, 2)
assert {"cpu", "memory", "pids"}.issubset(config["cgroupControllers"])
assert config["seccomp"] is True
assert config["uid"] != 0
