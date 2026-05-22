#!/usr/bin/env node
/**
 * sync-course43.mjs — Pythontopia 课程包 43 静态 fixture 同步脚本
 *
 * @file 把仓库根 `data/course-pack-43/json/` 下的 7 章 / 65 KC / 561 页 /
 *        46 示例 / 39 题原始 JSON 转换为前端可直接 fetch 的精简 fixture，输出到
 *        `frontend/public/data/course43/`。
 *
 *        生成文件清单：
 *          - course43/manifest.json              元数据摘要（counts）
 *          - course43/documents.json             7 份文档元数据
 *          - course43/chapters.json              7 章
 *          - course43/kcs.json                   65 KC（含 page_refs）
 *          - course43/problems.json              39 题摘要
 *          - course43/examples.json              46 示例摘要
 *          - course43/pages-{docId}.json         按文档拆分的 561 页（每份 1 文件）
 *
 *        FIXME(W3-CTRL)：后端补齐 listProblems/listExamples/getPage 端点后，
 *        前端可逐步把 fetch fixture 改为调真实 API；本脚本仍可作为开发/演示降级。
 *
 * @example
 *   node scripts/sync-course43.mjs            # 全量覆盖
 *   node scripts/sync-course43.mjs --check    # 仅检查差异
 *   node scripts/sync-course43.mjs --quiet    # 不输出日志
 *
 * @author Pythontopia 子代理 W3-EDU-LIB
 * @license MIT
 * @runtime Node 20+，零 npm 依赖
 */

import { promises as fs } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const FRONTEND_ROOT = path.resolve(__dirname, '..');
const REPO_ROOT = path.resolve(FRONTEND_ROOT, '..');
const SRC_ROOT = path.join(REPO_ROOT, 'data', 'course-pack-43', 'json');
const DEST_ROOT = path.join(FRONTEND_ROOT, 'public', 'data', 'course43');

function parseArgs() {
  const args = process.argv.slice(2);
  return {
    check: args.includes('--check'),
    quiet: args.includes('--quiet'),
  };
}

async function statSafe(p) {
  try {
    return await fs.stat(p);
  } catch (err) {
    if (err && err.code === 'ENOENT') return null;
    throw err;
  }
}

async function readJson(name) {
  const buf = await fs.readFile(path.join(SRC_ROOT, name), 'utf8');
  return JSON.parse(buf);
}

async function writeJson(name, value) {
  const dest = path.join(DEST_ROOT, name);
  await fs.mkdir(path.dirname(dest), { recursive: true });
  await fs.writeFile(dest, JSON.stringify(value, null, 0) + '\n', 'utf8');
  return dest;
}

function safeJsonParse(raw, fallback) {
  if (raw == null) return fallback;
  if (typeof raw !== 'string') return raw;
  try {
    return JSON.parse(raw);
  } catch {
    return fallback;
  }
}

function projectDocuments(rows) {
  return rows
    .slice()
    .sort((a, b) => (a.sort_order ?? 0) - (b.sort_order ?? 0))
    .map((row) => ({
      documentId: row.id,
      packId: row.pack_id,
      sortOrder: row.sort_order ?? 0,
      filename: row.original_filename ?? '',
      title: deriveDocumentTitle(row),
      pageCount: row.page_count ?? 0,
    }));
}

function deriveDocumentTitle(row) {
  const filename = String(row.original_filename ?? '');
  const stripped = filename.replace(/\.pptx?$/i, '').replace(/\(\d+\)$/, '').trim();
  return stripped || `文档 ${row.id}`;
}

function projectChapters(rows) {
  return rows
    .slice()
    .sort((a, b) => (a.order_index ?? 0) - (b.order_index ?? 0))
    .map((row) => ({
      chapterId: row.id,
      packId: row.pack_id,
      orderIndex: row.order_index ?? 0,
      title: row.title ?? '',
      summary: row.summary ?? '',
    }));
}

function projectKcs(rows) {
  return rows
    .slice()
    .sort((a, b) => {
      const co = (a.chapter_order ?? 0) - (b.chapter_order ?? 0);
      if (co !== 0) return co;
      return String(a.kc_code ?? '').localeCompare(String(b.kc_code ?? ''));
    })
    .map((row) => ({
      kcId: row.id,
      packId: row.pack_id,
      kcCode: row.kc_code,
      kcName: row.kc_name,
      chapterId: row.chapter_id,
      chapterOrder: row.chapter_order ?? 0,
      difficulty: row.difficulty ?? null,
      summary: row.summary_markdown ?? '',
      pageRefs: Array.isArray(row.page_refs) ? row.page_refs : [],
    }));
}

function projectProblems(rows) {
  return rows
    .slice()
    .sort((a, b) => (a.id ?? 0) - (b.id ?? 0))
    .map((row) => ({
      problemId: row.id,
      packId: row.pack_id,
      kcCode: row.kc_code,
      title: row.title ?? '',
      difficulty: row.difficulty ?? 'Low',
      statementMarkdown: row.statement_markdown ?? '',
      inputDescription: row.input_description ?? '',
      outputDescription: row.output_description ?? '',
      sourceLabel: row.source_label ?? '',
      sourcePages: safeJsonParse(row.source_pages_json, []),
      teachingExplanation: row.teaching_explanation ?? '',
      commonMistakes: safeJsonParse(row.common_mistakes_json, []),
      samples: Array.isArray(row.samples) ? row.samples : [],
    }));
}

function projectExamples(rows) {
  return rows
    .slice()
    .sort((a, b) => (a.id ?? 0) - (b.id ?? 0))
    .map((row) => ({
      exampleId: row.id,
      packId: row.pack_id,
      documentId: row.document_id,
      kcCode: row.kc_code,
      title: row.title ?? '',
      code: row.code ?? '',
      explanationMarkdown: row.explanation_markdown ?? '',
      inputDescription: row.input_description ?? '',
      outputDescription: row.output_description ?? '',
      pageRefs: Array.isArray(row.page_refs) ? row.page_refs : [],
    }));
}

function groupPagesByDocument(rows) {
  const byDoc = new Map();
  for (const row of rows) {
    const docId = row.document_id;
    if (docId == null) continue;
    let bucket = byDoc.get(docId);
    if (!bucket) {
      bucket = [];
      byDoc.set(docId, bucket);
    }
    bucket.push({
      pageId: row.id,
      documentId: docId,
      chapterId: row.chapter_id ?? null,
      pageNo: row.page_no ?? 0,
      chunkIndex: row.chunk_index ?? 0,
      title: row.title ?? '',
      contentMarkdown: row.content_markdown ?? '',
      excerpt: row.excerpt ?? '',
    });
  }
  for (const bucket of byDoc.values()) {
    bucket.sort((a, b) => {
      const pn = (a.pageNo ?? 0) - (b.pageNo ?? 0);
      if (pn !== 0) return pn;
      return (a.chunkIndex ?? 0) - (b.chunkIndex ?? 0);
    });
  }
  return byDoc;
}

async function writeFixture(name, value, opts) {
  const dest = path.join(DEST_ROOT, name);
  const payload = JSON.stringify(value, null, 0) + '\n';
  const prev = await statSafe(dest);
  let prevBuf = null;
  if (prev) {
    prevBuf = await fs.readFile(dest, 'utf8');
  }
  if (prevBuf === payload) {
    return { name, status: 'unchanged' };
  }
  if (opts.check) {
    return { name, status: prev ? 'updated' : 'created' };
  }
  await fs.mkdir(path.dirname(dest), { recursive: true });
  await fs.writeFile(dest, payload, 'utf8');
  return { name, status: prev ? 'updated' : 'created' };
}

async function main() {
  const opts = parseArgs();
  const srcStat = await statSafe(SRC_ROOT);
  if (!srcStat || !srcStat.isDirectory()) {
    if (!opts.quiet) {
      console.warn(`[sync-course43] 跳过：源目录不存在 ${SRC_ROOT}`);
    }
    return;
  }

  const [docs, chapters, kcs, problems, examples, pages] = await Promise.all([
    readJson('course_document.json'),
    readJson('course_chapter.json'),
    readJson('course_kc.json'),
    readJson('course_problem.json'),
    readJson('course_example.json'),
    readJson('course_page.json'),
  ]);

  const documents = projectDocuments(docs);
  const chapterRows = projectChapters(chapters);
  const kcRows = projectKcs(kcs);
  const problemRows = projectProblems(problems);
  const exampleRows = projectExamples(examples);
  const pagesByDoc = groupPagesByDocument(pages);

  const manifest = {
    packId: 43,
    name: 'Python语言基础',
    generatedAt: new Date().toISOString(),
    documentCount: documents.length,
    chapterCount: chapterRows.length,
    kcCount: kcRows.length,
    problemCount: problemRows.length,
    exampleCount: exampleRows.length,
    pageCount: pages.length,
    documents: documents.map((doc) => ({
      documentId: doc.documentId,
      title: doc.title,
      pageCount: doc.pageCount,
    })),
  };

  const reports = [];
  reports.push(await writeFixture('manifest.json', manifest, opts));
  reports.push(await writeFixture('documents.json', documents, opts));
  reports.push(await writeFixture('chapters.json', chapterRows, opts));
  reports.push(await writeFixture('kcs.json', kcRows, opts));
  reports.push(await writeFixture('problems.json', problemRows, opts));
  reports.push(await writeFixture('examples.json', exampleRows, opts));
  for (const [docId, bucket] of pagesByDoc.entries()) {
    reports.push(await writeFixture(`pages-${docId}.json`, bucket, opts));
  }

  if (!opts.quiet) {
    const counts = reports.reduce(
      (acc, r) => {
        acc[r.status] = (acc[r.status] ?? 0) + 1;
        return acc;
      },
      { created: 0, updated: 0, unchanged: 0 },
    );
    console.log(
      `[sync-course43] ${opts.check ? 'check ' : ''}done: +${counts.created}  ~${counts.updated}  =${counts.unchanged}  ` +
        `documents=${documents.length} chapters=${chapterRows.length} kcs=${kcRows.length} ` +
        `problems=${problemRows.length} examples=${exampleRows.length} pages=${pages.length}`,
    );
  }
}

main().catch((err) => {
  console.error('[sync-course43] 运行失败：', err);
  process.exitCode = 1;
});
