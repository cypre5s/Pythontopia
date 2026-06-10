-- Purpose: R-15 主线核心域——小章节(stage)静态定义 + 学生三件套进度 + 大章节星级权威 + 星级审计事件。
--   后端权威重构旧前端 mainQuest 存档（前端 mainQuests.ts 退役由 Wave 2 B2 负责）。
-- Tables affected (新建):
--   * mainline_stage          —— 24 个 stage 静态定义（seed），全局共享，覆盖 packId=43 全部 65 KC。
--   * mainline_stage_progress —— 每学生×stage 的「看(视频秒)/学(课件页)/练(例题)」三件套打点。
--   * chapter_star            —— 每学生×章 0..3★ 唯一权威（公式见 r15 计划 §5），breakdown 记录构成。
--   * star_award_event        —— 星级变更审计 + 教师端聚合数据源。
--   依赖外键：users(id)（与项目既有迁移一致，RPG/主线表无前缀沿用约定 V100-）。
-- Irreversible risk:
--   * drop 这些表会清空学生主线进度与星级（旧 mainQuest 存档已作废重开，无法回灌）；不影响课程种子/判题表。
--   * mainline_stage 为 seed 静态表，重灌需重新执行本迁移 seed 段。

CREATE TABLE mainline_stage (            -- 静态定义（seed），全局共享
  id BIGSERIAL PRIMARY KEY,
  chapter INT NOT NULL CHECK (chapter BETWEEN 1 AND 7),
  stage_index INT NOT NULL,              -- 章内序号 1..5
  title VARCHAR(128) NOT NULL,
  kc_codes TEXT[] NOT NULL,
  video_keyword VARCHAR(120) NOT NULL,   -- B站检索词（Python 前缀）
  courseware_page_ids BIGINT[] NOT NULL DEFAULT '{}',
  example_problem_ids BIGINT[] NOT NULL DEFAULT '{}',
  required_video_sec INT NOT NULL DEFAULT 240,
  required_pages INT NOT NULL DEFAULT 0,        -- 0=按 courseware_page_ids 全读
  required_example_count INT NOT NULL DEFAULT 2,
  UNIQUE (chapter, stage_index)
);

CREATE TABLE mainline_stage_progress (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  stage_id BIGINT NOT NULL REFERENCES mainline_stage(id),
  video_watched_sec INT NOT NULL DEFAULT 0,
  pages_read BIGINT[] NOT NULL DEFAULT '{}',
  examples_done BIGINT[] NOT NULL DEFAULT '{}',
  completed_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, stage_id)
);

CREATE TABLE chapter_star (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  chapter INT NOT NULL CHECK (chapter BETWEEN 1 AND 7),
  stars SMALLINT NOT NULL DEFAULT 0 CHECK (stars BETWEEN 0 AND 3),
  best_test_score INT,
  breakdown JSONB NOT NULL DEFAULT '{}',  -- {test:1,stage:1,mastery:0,recovered:["ENDLESS"]}
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, chapter)
);

CREATE TABLE star_award_event (          -- 审计 + 教师端聚合
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  chapter INT NOT NULL,
  delta SMALLINT NOT NULL,
  source VARCHAR(24) NOT NULL CHECK (source IN
    ('TEST','STAGE_COMPLETE','MASTERY','ERRORBOOK_RECOVERY','ENDLESS_RECOVERY','ADMIN')),
  reason VARCHAR(256),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 教师端按 (班级成员)×章×时间 聚合星级变更（§9 mainline-stars 看板）。
CREATE INDEX idx_star_award_event_user_ch ON star_award_event(user_id, chapter, created_at DESC);

-- ---------------------------------------------------------------------------
-- Stage seed：24 个 stage 覆盖 packId=43 全部 65 KC（7 章，每章 3–5 个，按 KC 主题分组）。
-- KC 编号与 data/course-pack-43/json/course_kc.json 校对一致；章名取 course_chapter.json 短意。
-- 评审约定：分组写进 seed，人工核对（r15 计划 §3.1）。
--
-- 注意（防幻觉，AGENTS.md「不伪造课件引用」）：courseware_page_ids / example_problem_ids 暂留空 '{}'
--   （走列默认值，本 seed 不显式赋值）。原因：
--     1) 本后端无 courseware_page 表可校验「课件页 id」空间（course_kc.page_refs 来自 Alethicode 页号体系，
--        尚未在本仓库建立阅读打点表/映射），不可臆造为本表 id。
--     2) example_problem_ids 应引用 course_problem.id，但「哪些题归哪个 stage」需真实课程策划，
--        course_problem 仅 39 题且非每 KC 都有题，不可凭空指派。
--   TODO(integration/B-wave 或课程策划)：用真实 courseware_page id + 精选 example/course_problem id 回填这两列，
--   并据可用例题数调整 required_pages / required_example_count；当前 required_pages 默认 0（=读空集即过线）、
--   required_example_count 默认 2（任意 2 道通过的本人例题即可）。
-- ---------------------------------------------------------------------------
INSERT INTO mainline_stage (chapter, stage_index, title, kc_codes, video_keyword) VALUES
  -- 第 1 章：计算工具与计算思维（KC001–007）
  (1, 1, '计算思维的概念与方法',   ARRAY['PY43-KC-004','PY43-KC-003'],                                  'Python 计算思维 入门'),
  (1, 2, '计算与计算科学',         ARRAY['PY43-KC-006','PY43-KC-007'],                                  'Python 计算科学 概述'),
  (1, 3, '计算工具与计算机发展',   ARRAY['PY43-KC-001','PY43-KC-002','PY43-KC-005'],                    'Python 计算机发展史 科普'),
  -- 第 2 章：Python 语言基础（KC008–016）
  (2, 1, 'Python 与程序语言入门',  ARRAY['PY43-KC-009','PY43-KC-015','PY43-KC-008','PY43-KC-014'],      'Python 环境搭建 第一个程序'),
  (2, 2, '变量、类型与运算',       ARRAY['PY43-KC-010','PY43-KC-011'],                                  'Python 变量 数据类型 运算符'),
  (2, 3, '输入输出与运行原理',     ARRAY['PY43-KC-012','PY43-KC-013','PY43-KC-016'],                    'Python input print 输入输出'),
  -- 第 3 章：信息表示及计算（KC017–030）
  (3, 1, '进制与二进制运算',       ARRAY['PY43-KC-017','PY43-KC-029','PY43-KC-030','PY43-KC-021'],      'Python 二进制 进制转换'),
  (3, 2, '信息编码',               ARRAY['PY43-KC-026','PY43-KC-027','PY43-KC-020'],                    'Python 字符编码 Unicode 汉字'),
  (3, 3, '数值类型与运算',         ARRAY['PY43-KC-019','PY43-KC-025','PY43-KC-028'],                    'Python 数值类型 运算 random'),
  (3, 4, '字符串处理与格式化',     ARRAY['PY43-KC-024','PY43-KC-022','PY43-KC-023','PY43-KC-018'],      'Python 字符串 切片 format'),
  -- 第 4 章：流程自动化（KC031–038）
  (4, 1, '顺序与分支结构',         ARRAY['PY43-KC-038','PY43-KC-033','PY43-KC-035'],                    'Python if 分支 顺序结构'),
  (4, 2, '循环结构',               ARRAY['PY43-KC-031','PY43-KC-032','PY43-KC-037'],                    'Python for while 循环'),
  (4, 3, '嵌套结构',               ARRAY['PY43-KC-034','PY43-KC-036'],                                  'Python 循环嵌套 分支嵌套'),
  -- 第 5 章：数据结构（KC039–047）
  (5, 1, '序列与列表',             ARRAY['PY43-KC-045','PY43-KC-044','PY43-KC-040','PY43-KC-039'],      'Python 列表 序列 操作'),
  (5, 2, '字典',                   ARRAY['PY43-KC-043','PY43-KC-042','PY43-KC-041'],                    'Python 字典 dict 基础'),
  (5, 3, '集合',                   ARRAY['PY43-KC-047','PY43-KC-046'],                                  'Python 集合 set 操作'),
  -- 第 6 章：数据存取与分析（KC048–053）
  (6, 1, '文件系统与文件操作',     ARRAY['PY43-KC-051','PY43-KC-048'],                                  'Python 文件操作 open'),
  (6, 2, '文件读写',               ARRAY['PY43-KC-052','PY43-KC-050'],                                  'Python 文件 读写 read write'),
  (6, 3, '文件数据分析',           ARRAY['PY43-KC-049','PY43-KC-053'],                                  'Python 文件 数据分析 词频统计'),
  -- 第 7 章：归纳与抽象（KC054–065）
  (7, 1, '函数进阶',               ARRAY['PY43-KC-059','PY43-KC-060','PY43-KC-054'],                    'Python 函数 参数 作用域 lambda'),
  (7, 2, '递归',                   ARRAY['PY43-KC-061'],                                                'Python 递归 函数'),
  (7, 3, '模块与包',               ARRAY['PY43-KC-062','PY43-KC-063','PY43-KC-056'],                    'Python 模块 包 import'),
  (7, 4, '库与打包',               ARRAY['PY43-KC-057','PY43-KC-058','PY43-KC-055'],                    'Python pip 库 pyinstaller 打包'),
  (7, 5, '面向对象',               ARRAY['PY43-KC-064','PY43-KC-065'],                                  'Python 面向对象 类 对象');
