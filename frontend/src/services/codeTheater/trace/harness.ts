/**
 * Python 插桩 harness（代码剧场基座）。
 *
 * harness 是一段 **Python 源码模板**，由 Pyodide 适配层执行（见 `runTrace.ts`）。它用
 * `ast` 预扫描标注 for/while/if 位置 + `sys.settrace` 逐行追踪，捕获：行号、变量
 * 赋值/重绑定、for/while 每轮迭代（含循环变量值）、if/elif/else 条件求值与选路、
 * print 输出、函数调用/返回、运行时异常。不支持的语义优雅降级为 `line` 事件。
 *
 * 关键安全机制：
 * - **步数预算**：记录数达到 `MAX_TRACE_STEPS` 时，line 追踪点 `sys.settrace(None)` 并
 *   raise 一个内部异常，**真正中断**死循环（已在 CPython 验证 raise 会停住循环），
 *   而非仅停止记录——否则 Pyodide 主线程会被无限循环卡死。
 * - **stdout 隔离**：学生程序的 print 被重定向进 payload.stdout；harness 只把
 *   sentinel 包裹的 trace JSON 写到真实 stdout，供 `runTrace` 解析，互不污染。
 *
 * 本模块产出的是「原始记录」（见 `rawTypes.ts`），由纯函数 `buildTrace` 再整形为
 * 契约 `Trace`。harness 在 Pyodide 内运行，难以单测；其逻辑已用真实 CPython 的
 * settrace 语义逐例验证（顺序/assign/for+range/分支/输出/调用/异常/死循环截断）。
 *
 * 红线：Pyodide 预览 ≠ 判题。本 trace 仅用于「看执行」动画，绝不作为掌握度判分依据。
 */
import { MAX_TRACE_STEPS } from '../types';

/** 真实 stdout 中标记 trace JSON 起始的哨兵（学生输出已隔离，不会包含它）。 */
export const TRACE_SENTINEL_BEGIN = '<<<PYTHONTOPIA_TRACE_V1_BEGIN>>>';
/** 标记 trace JSON 结束的哨兵。 */
export const TRACE_SENTINEL_END = '<<<PYTHONTOPIA_TRACE_V1_END>>>';

/**
 * Python harness 模板。占位符在 `buildHarnessProgram` 注入：
 * `__PYT_MAX_STEPS__` / `__PYT_SRC_B64__` / `__PYT_BEGIN__` / `__PYT_END__`。
 *
 * 用 `String.raw` 保留 Python 中的反斜杠转义（如 "\n"）。学生代码以 base64 注入，
 * 避免把多行源码当字符串字面量嵌入（适配层会对整段做统一缩进，base64 不受影响）。
 */
const HARNESS_TEMPLATE = String.raw`import ast
import base64
import io
import json
import re
import sys
from contextlib import redirect_stdout

_PYT_MAX_STEPS = __PYT_MAX_STEPS__
_PYT_FILENAME = "<student>"
_PYT_SRC_B64 = "__PYT_SRC_B64__"
_PYT_BEGIN = "__PYT_BEGIN__"
_PYT_END = "__PYT_END__"

_PYT_ADDR_RE = re.compile(r" at 0x[0-9a-fA-F]+>")


class _PytMissing:
    pass


def _pyt_safe_repr(value):
    try:
        text = repr(value)
    except Exception:
        text = "<unrepr>"
    text = _PYT_ADDR_RE.sub(">", text)
    if len(text) > 200:
        text = text[:197] + "..."
    return text


def _pyt_segment(src, node):
    try:
        seg = ast.get_source_segment(src, node)
        if seg:
            return seg.strip()
    except Exception:
        pass
    try:
        return ast.unparse(node)
    except Exception:
        return "<expr>"


def _pyt_name_of(target):
    if isinstance(target, ast.Name):
        return target.id
    if isinstance(target, (ast.Tuple, ast.List)):
        return ", ".join(_pyt_name_of(e) for e in target.elts)
    return "_"


def _pyt_body_lines(stmts):
    lines = set()
    for stmt in stmts:
        for node in ast.walk(stmt):
            ln = getattr(node, "lineno", None)
            if isinstance(ln, int):
                lines.add(ln)
    return lines


def _pyt_run(source):
    records = []
    state = {"truncated": False}
    cur_line = [1]
    depth = [0]
    frame_state = {}
    loop_count = {}

    try:
        tree = ast.parse(source)
        code_obj = compile(tree, _PYT_FILENAME, "exec")
    except SyntaxError as exc:
        records.append({
            "kind": "exception",
            "line": int(getattr(exc, "lineno", 1) or 1),
            "excType": "SyntaxError",
            "message": str(exc.msg),
        })
        return {"records": records, "stdout": "", "truncated": False}

    loop_headers = {}
    loop_body = {}
    if_table = {}
    for node in ast.walk(tree):
        if isinstance(node, ast.For):
            lid = "for@%d" % node.lineno
            loop_headers[node.lineno] = {"loopId": lid, "loopType": "for", "var": _pyt_name_of(node.target)}
            loop_body[lid] = _pyt_body_lines(node.body)
        elif isinstance(node, ast.While):
            lid = "while@%d" % node.lineno
            loop_headers[node.lineno] = {"loopId": lid, "loopType": "while", "var": None}
            loop_body[lid] = _pyt_body_lines(node.body)
        elif isinstance(node, ast.If):
            if_table[node.lineno] = {"cond": _pyt_segment(source, node.test), "then": _pyt_body_lines(node.body)}

    class _PytBudgetStop(Exception):
        pass

    def _add(rec):
        if len(records) >= _PYT_MAX_STEPS:
            state["truncated"] = True
            return False
        records.append(rec)
        return True

    def _add_exc(line, exc_type, message):
        if records:
            last = records[-1]
            if last.get("kind") == "exception" and last.get("excType") == exc_type and last.get("message") == message:
                return
        _add({"kind": "exception", "line": line, "excType": exc_type, "message": message})

    class _PytOut(io.TextIOBase):
        def __init__(self):
            self.parts = []
            self._pending = ""

        def writable(self):
            return True

        def write(self, s):
            s = str(s)
            self.parts.append(s)
            self._pending += s
            while "\n" in self._pending:
                idx = self._pending.index("\n")
                text = self._pending[:idx]
                self._pending = self._pending[idx + 1:]
                _add({"kind": "output", "line": cur_line[0], "text": text})
            return len(s)

        def flush(self):
            pass

        def getvalue(self):
            return "".join(self.parts)

    def _local(frame, event, arg):
        if frame.f_code.co_filename != _PYT_FILENAME:
            return _local
        if event == "line":
            cur = frame.f_lineno
            cur_line[0] = cur
            fid = id(frame)
            st = frame_state.get(fid)
            if st is None:
                st = {"prev": None, "snap": dict(frame.f_locals), "branch": None}
                frame_state[fid] = st
            prev = st["prev"]
            new_snap = dict(frame.f_locals)
            scope = "global" if frame.f_code.co_name == "<module>" else "local"

            loop_var_skip = None
            loop_hit = None
            if prev is not None and prev in loop_headers:
                meta = loop_headers[prev]
                if cur in loop_body.get(meta["loopId"], set()) and cur != prev:
                    loop_hit = meta
                    loop_var_skip = meta["var"]

            if prev is not None:
                for name, value in new_snap.items():
                    if name == loop_var_skip:
                        continue
                    old = st["snap"].get(name, _PytMissing)
                    if old is _PytMissing or _pyt_safe_repr(old) != _pyt_safe_repr(value):
                        _add({
                            "kind": "assign",
                            "line": prev,
                            "varName": name,
                            "value": _pyt_safe_repr(value),
                            "scope": scope,
                        })

            pending = st["branch"]
            if pending is not None:
                taken_true = cur in pending["then"]
                _add({
                    "kind": "branch",
                    "line": pending["header"],
                    "cond": pending["cond"],
                    "result": bool(taken_true),
                    "lineTaken": cur,
                })
                st["branch"] = None

            if loop_hit is not None:
                lid = loop_hit["loopId"]
                it = loop_count.get(lid, 0)
                rec = {
                    "kind": "loop_iter",
                    "line": prev,
                    "loopId": lid,
                    "loopType": loop_hit["loopType"],
                    "iter": it,
                }
                var = loop_hit["var"]
                if var:
                    rec["varName"] = var
                    rec["value"] = _pyt_safe_repr(new_snap.get(var))
                _add(rec)
                loop_count[lid] = it + 1

            if not _add({"kind": "line", "line": cur}):
                sys.settrace(None)
                raise _PytBudgetStop()

            if cur in if_table:
                st["branch"] = {"header": cur, "cond": if_table[cur]["cond"], "then": if_table[cur]["then"]}

            st["prev"] = cur
            st["snap"] = new_snap
        elif event == "return":
            if frame.f_code.co_name != "<module>":
                _add({
                    "kind": "return",
                    "line": frame.f_lineno,
                    "funcName": frame.f_code.co_name,
                    "value": _pyt_safe_repr(arg),
                    "depth": max(depth[0] - 1, 0),
                })
                depth[0] = max(depth[0] - 1, 0)
            frame_state.pop(id(frame), None)
        elif event == "exception":
            exc_type, exc_val, _tb = arg
            _add_exc(frame.f_lineno, getattr(exc_type, "__name__", "Exception"), str(exc_val))
        return _local

    def _global(frame, event, arg):
        if frame.f_code.co_filename != _PYT_FILENAME:
            return None
        if event == "call":
            code = frame.f_code
            if code.co_name == "<module>":
                return _local
            depth[0] += 1
            argcount = code.co_argcount
            names = code.co_varnames[:argcount]
            args = [_pyt_safe_repr(frame.f_locals.get(n)) for n in names]
            _add({
                "kind": "call",
                "line": frame.f_lineno,
                "funcName": code.co_name,
                "args": args,
                "depth": depth[0],
            })
            return _local
        return _local

    capture = _PytOut()
    sys.settrace(_global)
    try:
        with redirect_stdout(capture):
            exec(code_obj, {"__name__": "__main__"})
    except _PytBudgetStop:
        state["truncated"] = True
    except SystemExit:
        pass
    except Exception as exc:
        _add_exc(cur_line[0], type(exc).__name__, str(exc))
    finally:
        sys.settrace(None)

    return {"records": records, "stdout": capture.getvalue(), "truncated": state["truncated"]}


def _pyt_main():
    try:
        source = base64.b64decode(_PYT_SRC_B64).decode("utf-8")
    except Exception as exc:
        payload = {
            "records": [{"kind": "exception", "line": 1, "excType": "HarnessDecodeError", "message": str(exc)}],
            "stdout": "",
            "truncated": False,
        }
    else:
        payload = _pyt_run(source)
    sys.stdout.write(_PYT_BEGIN)
    sys.stdout.write("\n")
    sys.stdout.write(json.dumps(payload))
    sys.stdout.write("\n")
    sys.stdout.write(_PYT_END)
    sys.stdout.write("\n")
    sys.stdout.flush()


_pyt_main()
`;

/**
 * 把任意 UTF-8 文本编码为 base64（浏览器 / jsdom / Node 通用，避免 `btoa` 的 Latin-1 限制）。
 * 学生代码量小，分块拼接足够。
 */
export function toBase64Utf8(text: string): string {
  const bytes = new TextEncoder().encode(text);
  let binary = '';
  const chunkSize = 0x8000;
  for (let i = 0; i < bytes.length; i += chunkSize) {
    const chunk = bytes.subarray(i, i + chunkSize);
    binary += String.fromCharCode(...chunk);
  }
  return btoa(binary);
}

/**
 * 组装可直接交给 Pyodide 执行的完整 harness 程序：注入步数预算、学生代码（base64）与哨兵。
 * 纯字符串拼装，无副作用，可单测。
 */
export function buildHarnessProgram(userCode: string): string {
  const b64 = toBase64Utf8(userCode);
  return HARNESS_TEMPLATE.replace('__PYT_MAX_STEPS__', () => String(MAX_TRACE_STEPS))
    .replace('__PYT_SRC_B64__', () => b64)
    .replace('__PYT_BEGIN__', () => TRACE_SENTINEL_BEGIN)
    .replace('__PYT_END__', () => TRACE_SENTINEL_END);
}
