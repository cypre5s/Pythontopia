-- Purpose: Agentic Loop 记忆层 provider 路由审计 —— 把外部记忆服务（Mem0 / Zep）remember 后返回的
--          记忆 id 映射到本地 user，使闭环可审计「这条记忆来自哪个 provider / 外部 id / 何时写入」，
--          并支持 MEMORY_PROVIDER 切换后的对账与回退。local provider 仍直接复用既有 memory_embedding /
--          learning_event_log 召回，本迁移不重建任何既有记忆/画像表，仅新增映射表。
-- Tables affected: agent_memory_ref（新建，无前缀，沿用项目 RPG 存档表命名约定）。
-- Irreversible risk: 删表仅丢失「外部记忆 id 映射 / remember 审计」；本地记忆（learning_event_log /
--                    memory_embedding）与画像（learner_profile）不受影响，可由外部 provider 重新
--                    remember 重建映射，无业务数据损失。

CREATE TABLE IF NOT EXISTS agent_memory_ref (
  id          BIGSERIAL PRIMARY KEY,
  user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  -- 写入该条记忆的 provider；与 MEMORY_PROVIDER 取值域一致。
  provider    VARCHAR(16) NOT NULL,
  -- provider 侧记忆主键（Mem0 memory id / Zep edge|node uuid / local 生成的 UUID）。
  external_id VARCHAR(128) NOT NULL,
  -- 记忆类别（如 fact / preference / weak_kc）；供 brief 聚合与审计分类。
  kind        VARCHAR(64) NOT NULL DEFAULT 'fact',
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT agent_memory_ref_provider_check CHECK (provider IN ('local', 'mem0', 'zep'))
);

CREATE INDEX IF NOT EXISTS idx_agent_memory_ref_user ON agent_memory_ref(user_id);

-- provider 侧 id 在同一 provider 内唯一；据此对 remember 做幂等 upsert（ON CONFLICT DO NOTHING）。
CREATE UNIQUE INDEX IF NOT EXISTS ux_agent_memory_ref_provider_external
  ON agent_memory_ref(provider, external_id);
