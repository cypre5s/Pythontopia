// 把 Python 运行报错（Pyodide / 判题 stderr）「翻译」成初学者友好的中文提示 + 修复建议。
// 纯函数、无副作用，便于单测；匹配不到时返回 null（UI 保留原始 traceback，不臆造）。

export interface PyErrorHint {
  /** 错误类别（中文 + 英文原名），如「名称未定义（NameError）」。 */
  title: string;
  /** 面向初学者的解释 + 排查/修复建议。 */
  hint: string;
}

interface Rule {
  test: RegExp;
  build: (match: RegExpExecArray) => PyErrorHint;
}

const RULES: readonly Rule[] = [
  {
    test: /TabError|IndentationError|unexpected indent|expected an indented block/,
    build: () => ({
      title: '缩进错误（IndentationError）',
      hint: 'Python 用缩进表示代码块。请检查：① 同一块内每行缩进一致（统一 4 个空格，别混用 Tab 与空格）；② if/for/while/def/else 等行末冒号后的下一行要缩进。',
    }),
  },
  {
    test: /NameError: name '([^']+)' is not defined/,
    build: (m) => ({
      title: `名称未定义（NameError：${m[1] ?? ''}）`,
      hint: `变量或函数「${m[1] ?? ''}」还没定义就被使用。请检查：① 是否拼写错误或大小写不一致；② 是否在使用前先赋值；③ 是否在可访问的作用域内。`,
    }),
  },
  {
    test: /ModuleNotFoundError: No module named '([^']+)'|ImportError/,
    build: (m) => ({
      title: '模块导入失败（ModuleNotFoundError）',
      hint: m[1]
        ? `找不到模块「${m[1]}」。浏览器内 Pyodide 仅内置部分库；请确认拼写，或该库在此环境不可用（正式判题以服务端为准）。`
        : '导入失败。请检查模块名拼写，或该库在浏览器 Pyodide 环境不可用。',
    }),
  },
  {
    test: /IndexError/,
    build: () => ({
      title: '下标越界（IndexError）',
      hint: '访问了不存在的列表/序列位置。合法下标是 0 到 len(x)-1；请检查循环边界是否把 < 写成了 <=，或下标计算是否超界。',
    }),
  },
  {
    test: /KeyError: '?([^'\n]+)'?/,
    build: (m) => ({
      title: `字典缺少键（KeyError：${(m[1] ?? '').trim()}）`,
      hint: `字典里没有键「${(m[1] ?? '').trim()}」。请确认键名拼写；或先用 in 判断键是否存在，再用 dict.get(key, 默认值) 安全取值。`,
    }),
  },
  {
    test: /ZeroDivisionError/,
    build: () => ({
      title: '除以零（ZeroDivisionError）',
      hint: '出现了「除以 0」或「对 0 取模」。请在做除法前判断除数是否为 0。',
    }),
  },
  {
    test: /ValueError: (.*)$/,
    build: (m) => ({
      title: '取值/转换错误（ValueError）',
      hint: `值不合法：${(m[1] ?? '').trim()}。常见于 int("abc") 这类无法转换的情况，请检查输入内容与转换类型。`,
    }),
  },
  {
    test: /TypeError: (.*)$/,
    build: (m) => ({
      title: '类型错误（TypeError）',
      hint: `类型不匹配：${(m[1] ?? '').trim()}。请检查是否把字符串与数字直接相加、或给函数传了错误数量/类型的参数。`,
    }),
  },
  {
    test: /AttributeError: (.*)$/,
    build: (m) => ({
      title: '属性错误（AttributeError）',
      hint: `对象没有该属性或方法：${(m[1] ?? '').trim()}。请检查方法名拼写，以及该对象类型是否符合预期。`,
    }),
  },
  {
    test: /EOFError|EOF when reading a line/,
    build: () => ({
      title: '输入读取失败（EOFError）',
      hint: '代码调用了 input() 但没有可读输入。请在左侧「Input」框按行填入输入后再运行。',
    }),
  },
  {
    test: /RecursionError|maximum recursion depth/,
    build: () => ({
      title: '递归过深（RecursionError）',
      hint: '递归没有正确的终止条件或层数过深。请检查递归的「基线条件（base case）」是否一定会被触发。',
    }),
  },
  {
    test: /SyntaxError: (.*)$|invalid syntax|EOL while scanning|unexpected EOF/,
    build: (m) => ({
      title: '语法错误（SyntaxError）',
      hint: `代码语法有误${m[1] ? `：${m[1].trim()}` : ''}。常见原因：漏写冒号 :、括号/引号不配对、误用中文标点（，：）、把赋值 = 写成了比较 ==。`,
    }),
  },
];

/** 翻译报错；匹配不到返回 null（调用方继续展示原始报错，绝不臆造）。 */
export function translatePyError(stderr: string | null | undefined): PyErrorHint | null {
  if (!stderr || !stderr.trim()) {
    return null;
  }
  const lines = stderr.trimEnd().split(/\r?\n/);
  const lastLine = [...lines].reverse().find((line) => line.trim().length > 0) ?? '';
  for (const rule of RULES) {
    const match = rule.test.exec(lastLine) ?? rule.test.exec(stderr);
    if (match) {
      return rule.build(match);
    }
  }
  return null;
}
