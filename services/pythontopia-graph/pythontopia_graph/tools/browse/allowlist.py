"""BROWSE 安全墙域白名单（§3.4，复用既有 security-wall 思路）。

设计要点：
- 冻结初始白名单（演示安全）：``docs.python.org`` / ``peps.python.org`` / ``pypi.org``（只读
  官方站）。``docs.python.org/zh-cn`` 等中文路径同属 host ``docs.python.org``，已被覆盖，
  故白名单按**纯 host** 维护，不放路径。
- 项目自有判题站 host 由 env ``BROWSE_ALLOWED_DOMAINS``（逗号分隔）注入。
- 判定按 host 归一化 + 精确域/子域匹配；只放行 http(s)，其余 scheme（file/javascript/
  data/ftp…）一律拒绝（防本地文件读取 / 伪协议注入）。
- ``enforce_allowlist`` 做**交集裁剪**（防御纵深）：即便上游误注入更宽的域，executor 也
  绝不超出安全墙；交集为空 = 拒绝整次浏览（executor 据此 fail-fast，不发任何请求）。

纯函数 + 无 I/O：可被 executor / 后端注入层 / 测试零联网复用。
"""

from __future__ import annotations

import os
from collections.abc import Iterable, Mapping
from urllib.parse import urlsplit

# 环境变量名：逗号分隔的额外白名单 host（项目自有判题站等）。
BROWSE_ALLOWED_DOMAINS_ENV = "BROWSE_ALLOWED_DOMAINS"

# 冻结初始白名单（归一化纯 host；只读官方文档/索引站）。
DEFAULT_ALLOWED_DOMAINS: tuple[str, ...] = (
    "docs.python.org",
    "peps.python.org",
    "pypi.org",
)

# 仅放行的 URL scheme（只读导航；其余一律拒绝）。
_ALLOWED_SCHEMES = ("http", "https")


class DomainNotAllowedError(ValueError):
    """目标 URL 的 host 不在白名单 → fail-fast，executor 绝不发起请求。

    继承 ``ValueError``，命中 harness 永久错误语义（不重试、不污染熔断）。
    """


def normalize_domain(entry: str) -> str:
    """把白名单条目 / 域名归一化为纯小写 host。

    去掉 scheme、路径/查询/锚点、用户名密码、端口与前导通配 ``*.`` / ``.``；
    无法解析（空）时返回空串。
    """
    value = (entry or "").strip().lower()
    if not value:
        return ""
    if "://" in value:
        value = value.split("://", 1)[1]
    # 去路径 / 查询 / 锚点（取首段）。
    for sep in ("/", "?", "#"):
        if sep in value:
            value = value.split(sep, 1)[0]
    # 去用户名密码（取 @ 后真实 host）。
    if "@" in value:
        value = value.rsplit("@", 1)[1]
    # 去端口。
    if ":" in value:
        value = value.split(":", 1)[0]
    # 去前导通配/点（"*.example.com" / ".example.com" → "example.com"）。
    value = value.lstrip("*.")
    return value


def load_allowed_domains(env: Mapping[str, str] | None = None) -> list[str]:
    """安全墙完整白名单 = 冻结默认集 + env 注入（归一化、去重、保序）。

    ``env=None`` 时读 ``os.environ``；显式传 ``{}`` 可在测试中隔离宿主机变量。
    """
    environ = os.environ if env is None else env
    domains: list[str] = list(DEFAULT_ALLOWED_DOMAINS)
    seen = set(domains)
    raw = environ.get(BROWSE_ALLOWED_DOMAINS_ENV, "")
    for piece in raw.split(","):
        host = normalize_domain(piece)
        if host and host not in seen:
            domains.append(host)
            seen.add(host)
    return domains


def _host_if_http(url: str) -> str:
    """抽取 http(s) URL 的小写 host；非 http(s) scheme 或无 host 时返回空串。"""
    parsed = urlsplit((url or "").strip())
    if parsed.scheme.lower() not in _ALLOWED_SCHEMES:
        return ""
    return (parsed.hostname or "").lower()


def _host_matches(host: str, domain: str) -> bool:
    """host 命中域：精确相等或为其子域（以 ``.domain`` 结尾，防 lookalike 后缀）。"""
    if not host or not domain:
        return False
    return host == domain or host.endswith("." + domain)


def is_domain_allowed(url: str, allowed_domains: Iterable[str]) -> bool:
    """URL 是否在白名单内（只读 http(s) + 精确域/子域）。空白名单 = 全部拒绝。"""
    normalized = [d for d in (normalize_domain(x) for x in allowed_domains) if d]
    if not normalized:
        return False
    host = _host_if_http(url)
    if not host:
        return False
    return any(_host_matches(host, domain) for domain in normalized)


def ensure_url_allowed(url: str, allowed_domains: Iterable[str]) -> None:
    """守卫：URL 不在白名单立刻抛 :class:`DomainNotAllowedError`（fail-fast，不发请求）。"""
    if not is_domain_allowed(url, allowed_domains):
        raise DomainNotAllowedError(f"domain not in allowlist: {url!r}")


def enforce_allowlist(
    requested: Iterable[str], env: Mapping[str, str] | None = None
) -> list[str]:
    """把请求声明的域与安全墙白名单求**交集**（归一化、去重、保序）。

    交集语义：请求域等于墙内域，或为墙内域的子域，才保留（防御纵深——上游误注入
    更宽的域不会被放行）。结果为空表示拒绝整次浏览。
    """
    wall = load_allowed_domains(env)
    effective: list[str] = []
    seen: set[str] = set()
    for item in requested:
        host = normalize_domain(item)
        if not host or host in seen:
            continue
        if any(_host_matches(host, domain) for domain in wall):
            effective.append(host)
            seen.add(host)
    return effective
