"""BROWSE 安全墙 allowlist 单测（§3.4）：域白名单交集 + 非白名单 fail-fast，绝不发请求。

零真实联网：纯函数测试，只校验白名单判定逻辑（host 归一化、子域匹配、scheme 拦截、
env 扩展、交集裁剪、lookalike 防伪）。
"""

from __future__ import annotations

import pytest

from pythontopia_graph.tools.browse.allowlist import (
    BROWSE_ALLOWED_DOMAINS_ENV,
    DEFAULT_ALLOWED_DOMAINS,
    DomainNotAllowedError,
    ensure_url_allowed,
    enforce_allowlist,
    is_domain_allowed,
    load_allowed_domains,
)

DOCS = "docs.python.org"
PEPS = "peps.python.org"
PYPI = "pypi.org"


# ── 默认白名单（冻结演示安全集）──


def test_default_allowlist_is_frozen_safe_set() -> None:
    """冻结初始白名单含 docs.python.org / peps.python.org / pypi.org（只读官方站）。"""
    assert DOCS in DEFAULT_ALLOWED_DOMAINS
    assert PEPS in DEFAULT_ALLOWED_DOMAINS
    assert PYPI in DEFAULT_ALLOWED_DOMAINS
    # 全部为归一化后的纯 host（不含 scheme / 路径 / 大写）。
    for domain in DEFAULT_ALLOWED_DOMAINS:
        assert domain == domain.lower()
        assert "://" not in domain and "/" not in domain


def test_load_allowed_domains_default_without_env() -> None:
    """显式空 env：只返回默认集，不泄漏宿主机 BROWSE_ALLOWED_DOMAINS。"""
    assert load_allowed_domains({}) == list(DEFAULT_ALLOWED_DOMAINS)


def test_load_allowed_domains_merges_env_judge_host() -> None:
    """env 提供的判题站 host（逗号分隔）并入白名单，去重保序。"""
    env = {BROWSE_ALLOWED_DOMAINS_ENV: "judge.pythontopia.cn, oj.pythontopia.cn"}
    domains = load_allowed_domains(env)
    assert "judge.pythontopia.cn" in domains
    assert "oj.pythontopia.cn" in domains
    # 默认集仍在；无重复。
    assert DOCS in domains
    assert len(domains) == len(set(domains))


def test_load_allowed_domains_normalizes_env_entries() -> None:
    """env 条目带 scheme/路径/端口/大写时归一化为纯 host。"""
    env = {BROWSE_ALLOWED_DOMAINS_ENV: "HTTPS://Judge.Example.COM:443/problems?x=1"}
    domains = load_allowed_domains(env)
    assert "judge.example.com" in domains
    assert "https://judge.example.com:443/problems?x=1" not in domains


def test_load_allowed_domains_skips_blank_entries() -> None:
    """空白 / 纯逗号条目被忽略，不产生空 host。"""
    env = {BROWSE_ALLOWED_DOMAINS_ENV: " , ,, "}
    assert load_allowed_domains(env) == list(DEFAULT_ALLOWED_DOMAINS)


def test_load_allowed_domains_reads_process_env(monkeypatch: pytest.MonkeyPatch) -> None:
    """env=None 时回落 os.environ；缺变量时仅默认集。"""
    monkeypatch.delenv(BROWSE_ALLOWED_DOMAINS_ENV, raising=False)
    assert load_allowed_domains() == list(DEFAULT_ALLOWED_DOMAINS)
    monkeypatch.setenv(BROWSE_ALLOWED_DOMAINS_ENV, "judge.pythontopia.cn")
    assert "judge.pythontopia.cn" in load_allowed_domains()


# ── 单 URL 判定 ──


def test_is_domain_allowed_exact_host() -> None:
    assert is_domain_allowed("https://docs.python.org/3/library/functions.html", [DOCS])
    assert is_domain_allowed("http://pypi.org/project/pytest/", [PYPI])


def test_is_domain_allowed_subdomain_match() -> None:
    """白名单条目作为父域时，子域命中（host 以 '.domain' 结尾）。"""
    assert is_domain_allowed("https://docs.python.org/zh-cn/3/", ["python.org"])
    assert is_domain_allowed("https://x.docs.python.org/a", [DOCS])


def test_is_domain_allowed_rejects_other_allowed_peer() -> None:
    """白名单只给 docs 时，peps/pypi 不被放行（精确域隔离）。"""
    assert not is_domain_allowed("https://peps.python.org/pep-0008/", [DOCS])


def test_is_domain_allowed_rejects_non_allowed_domain() -> None:
    assert not is_domain_allowed("https://evil.example.com/x", [DOCS, PEPS, PYPI])


def test_is_domain_allowed_rejects_lookalike_suffix() -> None:
    """防伪：notpython.org / docs.python.org.evil.com / python.org.evil.com 全部拒绝。"""
    assert not is_domain_allowed("https://notpython.org/x", ["python.org"])
    assert not is_domain_allowed("https://evilpython.org/x", ["python.org"])
    assert not is_domain_allowed("https://docs.python.org.evil.com/x", [DOCS])
    assert not is_domain_allowed("https://python.org.evil.com/x", ["python.org"])


def test_is_domain_allowed_rejects_non_http_scheme() -> None:
    """只读 http(s)：file/javascript/data/ftp 等 scheme 一律拒绝（防本地文件/注入）。"""
    assert not is_domain_allowed("file:///etc/passwd", [DOCS])
    assert not is_domain_allowed("javascript:alert(1)", [DOCS])
    assert not is_domain_allowed("data:text/html,<script>1</script>", [DOCS])
    assert not is_domain_allowed("ftp://docs.python.org/x", [DOCS])


def test_is_domain_allowed_empty_allowlist_rejects_all() -> None:
    """空白名单 = 拒绝发起（§3.4：空 = 拒绝）。"""
    assert not is_domain_allowed("https://docs.python.org/3/", [])


def test_is_domain_allowed_credential_host_uses_real_host() -> None:
    """带用户名的 URL 以 @ 后真实 host 判定，不被前缀伪装绕过。"""
    assert not is_domain_allowed("https://docs.python.org@evil.com/x", [DOCS])


# ── 交集裁剪（防御纵深：即便后端误注入也不超墙）──


def test_enforce_allowlist_intersection_drops_outsiders() -> None:
    effective = enforce_allowlist([DOCS, "evil.com"], env={})
    assert effective == [DOCS]


def test_enforce_allowlist_keeps_subdomain_of_wall() -> None:
    effective = enforce_allowlist(["x.docs.python.org"], env={})
    assert effective == ["x.docs.python.org"]


def test_enforce_allowlist_empty_when_no_overlap() -> None:
    """无交集 → 空（executor 据此拒绝整次浏览）。"""
    assert enforce_allowlist(["evil.com", "phish.net"], env={}) == []


def test_enforce_allowlist_includes_env_judge_domain() -> None:
    env = {BROWSE_ALLOWED_DOMAINS_ENV: "judge.pythontopia.cn"}
    assert enforce_allowlist(["judge.pythontopia.cn"], env=env) == ["judge.pythontopia.cn"]


def test_enforce_allowlist_dedups_and_normalizes() -> None:
    effective = enforce_allowlist(
        ["https://docs.python.org/3", "DOCS.PYTHON.ORG", "docs.python.org"], env={}
    )
    assert effective == [DOCS]


# ── fail-fast 守卫 ──


def test_ensure_url_allowed_raises_for_disallowed() -> None:
    with pytest.raises(DomainNotAllowedError):
        ensure_url_allowed("https://evil.example.com/x", [DOCS])


def test_ensure_url_allowed_passes_for_allowed() -> None:
    ensure_url_allowed("https://docs.python.org/3/", [DOCS])  # 不抛即通过
