-- R-12 引入 ADMIN 角色（教师 + 学生 + 后台管理员），扩展 users.role CHECK。
-- Stage02 集成测试与 W3-CTRL admin-only 端点（库存 grant、shop 后台管理等）依赖此扩展。

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('STUDENT', 'TEACHER', 'ADMIN'));

COMMENT ON COLUMN users.role IS 'STUDENT / TEACHER / ADMIN：分别对应学习者、班级教师、后台管理员';
