import { describe, expect, it } from 'vitest';
import { MAX_TRACE_STEPS } from '../types';
import {
  buildHarnessProgram,
  TRACE_SENTINEL_BEGIN,
  TRACE_SENTINEL_END,
  toBase64Utf8,
} from './harness';

function fromBase64Utf8(b64: string): string {
  const binary = atob(b64);
  const bytes = Uint8Array.from(binary, (ch) => ch.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

describe('toBase64Utf8', () => {
  it('ASCII 往返', () => {
    const text = 'x = 1\nprint(x)\n';
    expect(fromBase64Utf8(toBase64Utf8(text))).toBe(text);
  });

  it('UTF-8（中文注释/字符串）往返不丢字符', () => {
    const text = '名字 = "世界"  # 你好\nprint(名字)\n';
    expect(fromBase64Utf8(toBase64Utf8(text))).toBe(text);
  });
});

describe('buildHarnessProgram', () => {
  it('注入步数预算 / 哨兵 / settrace，且无残留占位符', () => {
    const program = buildHarnessProgram('print(1)');

    expect(program).toContain(`_PYT_MAX_STEPS = ${MAX_TRACE_STEPS}`);
    expect(program).toContain(TRACE_SENTINEL_BEGIN);
    expect(program).toContain(TRACE_SENTINEL_END);
    expect(program).toContain('sys.settrace');
    expect(program).toContain('ast.parse');
    expect(program).not.toContain('__PYT_MAX_STEPS__');
    expect(program).not.toContain('__PYT_SRC_B64__');
    expect(program).not.toContain('__PYT_BEGIN__');
    expect(program).not.toContain('__PYT_END__');
  });

  it('内嵌的 base64 可解码回原始学生代码', () => {
    const code = 'for i in range(3):\n    print(i)\n';
    const program = buildHarnessProgram(code);
    const expectedB64 = toBase64Utf8(code);

    expect(program).toContain(`_PYT_SRC_B64 = "${expectedB64}"`);
    expect(fromBase64Utf8(expectedB64)).toBe(code);
  });

  it('多行 + Python 转义在模板中保留为字面 \\n（未被 JS 模板字符串吃掉）', () => {
    const program = buildHarnessProgram('pass');
    // 模板里应保留 Python 源中的反斜杠转义，而非真实换行字符。
    expect(program).toContain('"\\n"');
    expect(program.includes('redirect_stdout')).toBe(true);
  });
});
