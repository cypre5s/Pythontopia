-- Purpose: 学生在资源工坊上传的个人文件（.py/.txt/.md/.csv/.pdf/.docx）解析后持久化，供 A 通路
--   （前端 GET extracted_text 带标签注入答疑 context）与 B 通路（向量检索，见 V163）共用。上传内容是
--   「用户提供内容」，仅以【你上传的《filename》】标签注入 context，绝不当 packId=43 课件 evidence/引用。
-- Tables affected: workshop_upload (new)。user_id 引用 users(id)，删用户级联清理其全部上传。
-- Irreversible risk: 删表会丢失学生上传文件的解析文本与状态；原始文件不落盘（仅存解析后纯文本），
--   删除后须用户重新上传，无法从本表恢复原件。下游 workshop_upload_chunk(V163) 经 upload_id 级联清理。

CREATE TABLE IF NOT EXISTS workshop_upload (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  -- 原始文件名（含扩展名），仅用于展示与带标签注入，不参与鉴权。
  filename VARCHAR(255) NOT NULL,
  -- 规范化小写扩展名（含点，如 .py / .pdf），白名单之一。
  ext VARCHAR(16) NOT NULL,
  -- 上传字节数（原始大小），用于展示与 ≤5MB 限制核对。
  byte_size BIGINT NOT NULL,
  -- 解析出的纯文本（A 通路注入 context 的来源）；解析失败或无文本时为空串。
  extracted_text TEXT NOT NULL DEFAULT '',
  -- 解析文本的字符数 / 行数（前端 pill 就绪态展示）。
  char_count INTEGER NOT NULL DEFAULT 0,
  line_count INTEGER NOT NULL DEFAULT 0,
  -- 生命周期：PENDING 受理中 → READY 解析成功可用 / FAILED 解析失败。
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING', 'READY', 'FAILED')),
  -- 失败原因（status=FAILED 时填，供前端 pill 显示并支持重试）；成功为空。
  error VARCHAR(512),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 列表查询：取本人上传按时间倒序。
CREATE INDEX IF NOT EXISTS idx_workshop_upload_user_created
  ON workshop_upload(user_id, created_at DESC);
