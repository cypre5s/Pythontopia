"""Keep a teacher-facing location with semantic validation errors."""

from contextlib import contextmanager


INPUT_ERRORS = (ValueError, TypeError, KeyError, AttributeError, RecursionError)


class AuthoringIssue(ValueError):
    def __init__(self, message, *, path=(), target=None, code="invalid_content", suggestion=None, label=None):
        super().__init__(message)
        self.path = tuple(path)
        self.target = target or {}
        self.code = code
        self.suggestion = suggestion
        self.label = label

    def diagnostic(self):
        pointer = "/".join(str(part).replace("~", "~0").replace("/", "~1") for part in self.path)
        result = {"message": f"{self.label}：{self}" if self.label else str(self),
                  "path": f"/{pointer}" if pointer else "", "code": self.code}
        if self.target:
            result["target"] = self.target
        if self.suggestion:
            result["suggestion"] = self.suggestion
        return result


@contextmanager
def located(*path, target=None, label=None, suggestion=None):
    try:
        yield
    except INPUT_ERRORS as exc:
        issue = exc if isinstance(exc, AuthoringIssue) else AuthoringIssue(
            str(exc) if isinstance(exc, ValueError) else "字段格式不正确或缺少必填项")
        labels = " · ".join(item for item in (label, issue.label) if item)
        raise AuthoringIssue(str(issue), path=(*path, *issue.path), target={**(target or {}), **issue.target},
                             code=issue.code, suggestion=issue.suggestion or suggestion, label=labels) from exc


def row_target(kind, row, **fields):
    """Malformed request rows can still report an index, even without a usable ID."""
    key = row.get("id") if isinstance(row, dict) else None
    return {"kind": kind, **({"id": key} if isinstance(key, str) and key else {}), **fields}


def node_label(node, index):
    label = node.get("text") or node.get("title")
    if isinstance(label, str) and label.strip():
        short = " ".join(label.split())
        return f"节点 {index + 1}「{short[:44]}{'…' if len(short) > 44 else ''}」"
    names = {"action": "界面操作", "activity": "学习活动", "branch": "条件判断", "dialogue": "对白",
             "end": "结束", "random": "随机分支", "unlock_building": "解锁建筑"}
    kind = node.get("type")
    name = names.get(kind) if isinstance(kind, str) else None
    return f"节点 {index + 1}（{name}）" if name else f"节点 {index + 1}"
