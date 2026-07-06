# 演示数据

阶段 14 用于演示与 Beta 验收。**不要写进种子 SQL**，避免污染单元测试。

| 文件 | 说明 |
| --- | --- |
| `demo_users.json` | 1 教师 + 3 学生，账号统一前缀 `demo-`，便于一键清理 |
| `demo_classroom.json` | 演示班级名 + 课程包 ID（固定 43） |
| `demo_quests.json` | 1 个班级任务样例（题目 ID 来自课程包） |
| `seed.py` | 真实调 backend REST API 注册账号 + 创建班级 + 加入 + 发布任务，幂等 |

## 使用

```bash
cd /home/cypress/Pythontopia/deploy
docker compose up -d
bash scripts/init_rag_index.sh
bash scripts/seed_demo_classroom.sh
bash scripts/demo_smoke.sh
```

`seed.py` 会随机生成一次性密码并打印到 stdout，请手动复制（不会落盘）。
