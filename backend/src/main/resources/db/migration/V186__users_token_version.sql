-- 会话吊销能力（安全加固）：在此之前系统没有任何作废已签发 JWT 的手段——
-- users.status 全仓无人读取，token 也没有代次概念，720 分钟 TTL 内可用 /api/auth/refresh 无限续期。
--
-- token_version 与 JWT payload 的 tv 声明配对：签发时写入当前代次，登录 / 刷新 / 每次请求鉴权
-- 都会比对，代次不一致即视为已吊销（401）。吊销单个用户的全部会话只需：
--   UPDATE users SET token_version = token_version + 1 WHERE id = <userId>;
-- （应用内等价入口：UserRepository#bumpTokenVersion）
--
-- Tables affected: users（新增列，默认 0，向后兼容）。
-- Irreversible risk: none；仅新增列。

ALTER TABLE users ADD COLUMN IF NOT EXISTS token_version INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN users.token_version IS
  '会话代次：自增一次即吊销该用户全部已签发 JWT（与 token payload 的 tv 比对）';
