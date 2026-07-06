# Pythontopia 明早演示快速启动指南（R-12 / W4 收尾版）

适用场景：明早 9 点演示前，从零把后端 + 前端拉起来，让评委可登录并看到星露谷小镇首屏。

## 1. 启动步骤（建议顺序）

```bash
cd /home/cypress/Pythontopia/deploy
docker compose up -d postgres redis

cd /home/cypress/Pythontopia/backend
mvn spring-boot:run

cd /home/cypress/Pythontopia/frontend
npm run dev
```

后端默认监听 `:18080`，前端 vite dev server 默认 `:5173`，已配置 `/api` 与 `/healthz` 反向代理到 18080。

后端首次 `mvn spring-boot:run` 会触发 Flyway 迁移（V100~V113），可能要 20-40 秒，期间 `/healthz` 还未 ready，登录页面会显示"服务器还没就绪"红色提示。

## 2. 演示账号

| 用户名 | 密码 | 角色 |
| --- | --- | --- |
| `demo` | `P@ssw0rd1` | STUDENT |

登录页右下方有"快速体验：演示账号 demo / P@ssw0rd1"按钮，一键填入并提交。点击按钮的副作用：解锁 Audio Context，让 `/world` 的 BGM 不被浏览器 autoplay 策略静音。

## 3. 预期首屏与可见组件

登录成功后跳转 `/world`，应依次看到：

1. WorldCanvas 像素加载条（黄色填充 + 棕色边框 + "Pythontopia 加载中…"），从 0 → 100% 后 600ms 淡出。
2. Phaser WorldScene 显示 `town_hub` 中央地图，玩家精灵在中心。
3. `FirstTimeHelpModal`（首次登录时弹）讲解方向键 + E 交互 + Tab 打开公告板。
4. `ChapterNavPanel` 右侧侧栏列出 7 章 65 KC。
5. 屏幕底部的操作提示条："方向键移动 · E 与 NPC 对话 · Tab 公告板 · Esc 关闭"。
6. 镇长 NPC 头顶感叹号气泡，2 秒后自动弹欢迎对白（不需要玩家走过去）。

NPC 触发的对话框使用 `DialogTree`，按 E 推进、Esc 关闭。

## 4. 已知遗留 P1 项（明早玩家可能看见）

- **小地图未实现**：右上角不会出现 minimap，只能凭中心精灵位置判断方向。
- **移动端 / 平板未适配**：固定 960×640 画布，手机会左右滚动。演示请用 1080p+ 笔记本。
- **跨地图过渡 fade 在某些低端 GPU 上会闪一帧**：走过 town_hub 边缘切到 farm_north 时偶发。
- **R-12 仅 4 个 NPC 写满了完整对话树**：其余 NPC 走默认 "Hello." 兜底分支。
- **Pyodide 首次预览延迟 4-6 秒**：浏览器要拉 11MB wasm，期间训练面板按钮置灰。属于已知一次性开销。
- **离线情况下不会弹全屏覆盖层**：仅靠 toast 提示"无法连接服务器，请检查网络或刷新页面"，5 秒冷却防刷屏。如评委网络真断了请直接刷新。

## 5. 排错指引

按这个顺序快速验证：

```bash
curl -s http://localhost:18080/healthz
# 期望：{"status":"ok"}

curl -s -X POST http://localhost:18080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"P@ssw0rd1"}'
# 期望：返回 token + user 信息，HTTP 200

# 通过前端代理也再验一次
curl -s http://localhost:5173/healthz
# 期望：{"status":"ok"}

# 登录后用 token 拉游戏引导数据
TOKEN="<上一步拿到的 token>"
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:18080/api/world/bootstrap | head -c 200
# 期望：JSON 开头是 {"world":{...},"player":{...},...
```

### 常见症状对照

| 症状 | 第一手排查 |
| --- | --- |
| 登录页一直显示"服务器还没就绪" | 看后端日志，是否 Flyway 还在跑 V100-V113 |
| 登录返回 401 / "用户名或密码错误" | 确认 `demo` 用户是否已注册：`docker compose exec postgres psql -U pythontopia -c "select username,role from users where username='demo';"` |
| 登录后 `/world` 黑屏不动 | 看浏览器 DevTools Network：bootstrap 是否 200；Console 是否有 Phaser load failed |
| BGM 没声音 | 必须从登录按钮进入，跳过登录直接打 `/world` 的 URL 时 audio 未解锁 |
| 公告板 Tab 打不开 | 确认 backend `/api/quests` 是否 200，jwt 未过期 |

## 6. 演示话术建议（30 秒版）

> "Pythontopia 是面向 Python 初学者的网页像素学习小镇。玩家用方向键探索小镇，跟 NPC 对话领任务，在训练场写代码、矿洞修 Bug、公告板看班级任务。学生的 8 维画像 + KC 掌握度都实时入库，老师能看到全班的 KC 热力图。今天我们演示完整的：登录 → 镇长接任务 → 训练场写代码 → 提交判题 → 矿洞 Bug。"

完整版话术见 `docs/presentations/demo_script.md`。
