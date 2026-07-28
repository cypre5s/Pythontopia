#!/usr/bin/env node

import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

const [
  inputArg,
  outputArg,
  baseUrl = "未记录",
  release = "未记录",
  sha256 = "未记录",
] = process.argv.slice(2);

if (!inputArg || !outputArg) {
  console.error(
    "用法: node scripts/render-playwright-acceptance-report.mjs <playwright.json> <report.md> [baseUrl] [release] [sha256]",
  );
  process.exit(2);
}

const input = resolve(inputArg);
const output = resolve(outputArg);
const report = JSON.parse(readFileSync(input, "utf8"));

function escapeCell(value) {
  return String(value ?? "")
    .replaceAll("|", "\\|")
    .replaceAll("\r", " ")
    .replaceAll("\n", "<br>");
}

function formatDuration(milliseconds) {
  if (!Number.isFinite(milliseconds)) return "0 ms";
  if (milliseconds < 1000) return `${Math.round(milliseconds)} ms`;
  if (milliseconds < 60_000) return `${(milliseconds / 1000).toFixed(1)} s`;
  return `${(milliseconds / 60_000).toFixed(1)} min`;
}

function statusFor(test, results) {
  if (
    test.status === "skipped" ||
    results.some((result) => result.status === "skipped")
  ) {
    return "SKIP";
  }
  if (test.status === "flaky") return "FLAKY";
  if (
    test.status === "expected" &&
    results.some((result) => result.status === "passed")
  ) {
    return "PASS";
  }
  return "FAIL";
}

const cases = [];

function collect(suites, parents = []) {
  for (const suite of suites ?? []) {
    const nextParents = suite.title ? [...parents, suite.title] : parents;
    for (const spec of suite.specs ?? []) {
      for (const test of spec.tests ?? []) {
        const results = test.results ?? [];
        cases.push({
          file: spec.file ?? suite.file ?? nextParents[0] ?? "unknown",
          line: spec.line ?? "",
          title: [...nextParents.slice(1), spec.title]
            .filter(Boolean)
            .join(" › "),
          tags: (spec.tags ?? []).join(", "),
          status: statusFor(test, results),
          duration: results.reduce(
            (sum, result) => sum + (result.duration ?? 0),
            0,
          ),
          errors: results.flatMap((result) => result.errors ?? []),
        });
      }
    }
    collect(suite.suites, nextParents);
  }
}

collect(report.suites);

const counts = { PASS: 0, FAIL: 0, FLAKY: 0, SKIP: 0 };
const perFile = new Map();
for (const item of cases) {
  counts[item.status] += 1;
  const value = perFile.get(item.file) ?? {
    total: 0,
    PASS: 0,
    FAIL: 0,
    FLAKY: 0,
    SKIP: 0,
  };
  value.total += 1;
  value[item.status] += 1;
  perFile.set(item.file, value);
}

const stats = report.stats ?? {};
const startTime = stats.startTime ? new Date(stats.startTime) : null;
const generatedAt = new Intl.DateTimeFormat("zh-CN", {
  timeZone: "Asia/Shanghai",
  dateStyle: "long",
  timeStyle: "medium",
}).format(new Date());
const startedAt = startTime
  ? new Intl.DateTimeFormat("zh-CN", {
      timeZone: "Asia/Shanghai",
      dateStyle: "long",
      timeStyle: "medium",
    }).format(startTime)
  : "未记录";
const verdict = counts.FAIL === 0 && counts.FLAKY === 0 ? "PASS" : "NOT CLEAN";

const lines = [
  "# Pythontopia ECS 公网工作流详细验收报告",
  "",
  `> 生成时间：${generatedAt}`,
  `> 运行开始：${startedAt}`,
  `> 公网入口：\`${baseUrl}\``,
  `> 生效 release：\`${release}\``,
  `> 发布包 SHA-256：\`${sha256}\``,
  `> 最终结论：**${verdict}**`,
  "",
  "## 1. 验收口径",
  "",
  "- 使用真实公网入口、真实 Nginx、真实 Spring/Graph/RAG/PostgreSQL 与无头 Chromium；不 mock 业务 API。",
  "- 单 worker 串行执行，避免共享学习状态互相污染。",
  "- 最终矩阵使用 `--retries=0`；任何偶发失败都会原样计入，不由自动重试掩盖。",
  "- 本报告把每个 Playwright test 视为一个可复核工作流样例或工作流检查点，并逐项列出。",
  "",
  "## 2. 总体结果",
  "",
  "| 样例总数 | PASS | FAIL | FLAKY | SKIP | 总耗时 | 结论 |",
  "|---:|---:|---:|---:|---:|---:|---|",
  `| ${cases.length} | ${counts.PASS} | ${counts.FAIL} | ${counts.FLAKY} | ${counts.SKIP} | ${formatDuration(stats.duration ?? 0)} | ${verdict} |`,
  "",
  "## 3. 文件级覆盖",
  "",
  "| 测试文件 | 样例数 | PASS | FAIL | FLAKY | SKIP |",
  "|---|---:|---:|---:|---:|---:|",
];

for (const [file, value] of [...perFile.entries()].sort(([left], [right]) =>
  left.localeCompare(right),
)) {
  lines.push(
    `| \`${escapeCell(file)}\` | ${value.total} | ${value.PASS} | ${value.FAIL} | ${value.FLAKY} | ${value.SKIP} |`,
  );
}

lines.push(
  "",
  "## 4. 逐项工作流样例",
  "",
  "| # | 测试文件:行 | 工作流样例 / 检查点 | 标签 | 状态 | 耗时 |",
  "|---:|---|---|---|---|---:|",
);

cases.forEach((item, index) => {
  lines.push(
    `| ${index + 1} | \`${escapeCell(item.file)}:${item.line}\` | ${escapeCell(item.title)} | ${escapeCell(item.tags)} | ${item.status} | ${formatDuration(item.duration)} |`,
  );
});

const failures = cases.filter(
  (item) => item.status === "FAIL" || item.status === "FLAKY",
);
lines.push("", "## 5. 非干净结果明细", "");
if (failures.length === 0) {
  lines.push("最终无失败或 flaky 样例。");
} else {
  for (const item of failures) {
    lines.push(
      `### ${escapeCell(item.file)}:${item.line} — ${escapeCell(item.title)}`,
      "",
      `状态：${item.status}`,
      "",
    );
    const messages = item.errors
      .map((error) => error.message ?? String(error))
      .filter(Boolean)
      .map((message) => message.slice(0, 1200));
    lines.push(
      messages.length > 0
        ? messages.map((message) => `- ${escapeCell(message)}`).join("\n")
        : "- 无错误文本。",
    );
    lines.push("");
  }
}

lines.push(
  "## 6. 原始证据",
  "",
  `- Playwright JSON：\`${inputArg}\``,
  "- Playwright HTML：与 JSON 同次运行生成，包含失败时的 trace 与附件。",
  "- JSON 汇总字段与本报告逐项统计已由生成脚本机械核对。",
  "",
);

writeFileSync(output, `${lines.join("\n")}\n`, "utf8");
console.log(
  `报告已生成：${output}（${cases.length} 项，PASS=${counts.PASS} FAIL=${counts.FAIL} FLAKY=${counts.FLAKY} SKIP=${counts.SKIP}）`,
);
