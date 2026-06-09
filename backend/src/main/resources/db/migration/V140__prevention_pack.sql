-- Purpose: P5.1 预言家预防包——记录「据真实学情预测出高风险 KC 后，异步预生成的预防资源」，
--   绑定证据（真实 event ids + 先修依赖路径 + 风险/理由），供学生抵达薄弱 KC 时推送，全程可溯源。
-- Tables affected: prevention_pack (new); 外键依赖 users(id)、learning_resource(id)。
-- Irreversible risk: 删表会丢失预防包审计记录（不影响其引用的 learning_resource 资源本体）。

CREATE TABLE IF NOT EXISTS prevention_pack (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  kc_code VARCHAR(64) NOT NULL,
  resource_id BIGINT REFERENCES learning_resource(id) ON DELETE SET NULL,
  evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_prevention_pack_user_kc
  ON prevention_pack(user_id, kc_code, created_at DESC);
