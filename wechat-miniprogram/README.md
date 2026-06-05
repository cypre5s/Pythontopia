# Pythontopia 学习伙伴 · 原生微信小程序

面向高校 Python 初学者的「学习伙伴」端，使用 **原生微信小程序框架**（TypeScript + WXML + WXSS）开发，
**不是** uni-app / Taro / mpvue 等跨端框架。它复用 Pythontopia 现有后端（Spring Boot），覆盖
「看 → 学 → 练 → 测」学习闭环：今日任务、课程知识地图、服务端判题训练场、八维画像、班级。

> 像素小镇（Phaser 网页端 / LibGDX Android 端的星露谷式地图）是**独立的小游戏**，刻意**不**包含在本小程序里。
> 本小程序聚焦学习伙伴本身（公告板 / 课程 / 练习 / 画像 / 班级），保持轻量、可读、可上架。

## 技术栈

- 原生小程序框架（`App` / `Page` / WXML / WXSS）
- TypeScript（strict）+ 官方 `useCompilerPlugins: ["typescript"]` 编译模型（开发者工具直接编译 `.ts`）
- 类型校验：`tsc --noEmit` + `miniprogram-api-typings`
- 无第三方运行时依赖、无 Pyodide：所有判题走**服务端**。

## 目录结构

```text
wechat-miniprogram/
├── project.config.json          # 项目配置：miniprogramRoot=miniprogram/、appid、TS 编译插件
├── project.private.config.json  # 个人本地配置（关闭域名校验，便于访问 localhost）
├── tsconfig.json                # 仅做类型校验（noEmit）
├── package.json                 # devDeps: typescript, miniprogram-api-typings
├── typings/index.d.ts           # IAppOption + miniprogram-api-typings 引用
└── miniprogram/                 # 小程序根目录
    ├── app.ts / app.json / app.wxss / sitemap.json
    ├── config.ts                # API_BASE / API_PREFIX / 存储 key / 路由常量
    ├── utils/
    │   ├── request.ts           # wx.request Promise 封装：注入 JWT、解析错误信封、401 重登
    │   ├── markdown.ts          # 极简 Markdown → 可读文本块
    │   └── format.ts            # 0..1 得分 → 百分比 / 进度条宽度
    ├── api/
    │   ├── dto/                 # 显式接口（strict，无 any）
    │   │   ├── auth.ts learner.ts quests.ts course.ts submissions.ts classroom.ts
    │   ├── auth.ts learner.ts quests.ts course.ts submissions.ts classroom.ts
    │   └── ...
    ├── store/session.ts         # token + user 内存态 + 本地存储；login/register/logout/hydrate
    └── pages/
        ├── login/               # 登录 + 注册切换（入口页，成功后进入学习 tab）
        ├── index/      [tab 学习] # 今日任务 + 班级任务 + 掌握度概览
        ├── course/     [tab 课程] # 课程包统计 + 章节 → 知识点 → 课件页图片 / 去练习
        ├── practice/list         # 按 kcCode 列题
        ├── practice/detail       # 题面 + 代码 textarea + 提交 → 轮询服务端判题结果
        ├── profile/    [tab 画像] # 八维画像得分条 + 可自评维度 PUT + 退出登录
        └── classroom/  [tab 班级] # 我的班级 + 邀请码加入 + 班级任务
```

## 后端契约

- 基址在 `miniprogram/config.ts`：`API_BASE`，默认 `http://localhost:18080`，统一前缀 `/api`。
- 鉴权：登录后保存 JWT（`wx.setStorageSync('pyt_token', ...)`，后端 TTL 12h）；
  每次请求注入 `Authorization: Bearer <token>`；收到 `401` 时清除 token 并 `wx.reLaunch` 回登录页。
- 统一错误信封：`{ code, message, details[], traceId, timestamp }`，由 `utils/request.ts` 解析成 `ApiError`。

已对接的接口：

| 模块 | 接口 |
| --- | --- |
| 认证 | `POST /auth/login`、`POST /auth/register` |
| 画像 | `GET /learner/profile`(204)、`GET /learner/profile/dimensions`、`PUT /learner/profile/dimensions/{key}` |
| 掌握度 | `GET /learner/mastery` |
| 任务 | `GET /quests/today`、`GET /quests`、`POST /quests/{id}/start｜complete｜skip` |
| 课程 | `GET /course/pack｜/chapters｜/kcs｜/pages｜/problems｜/videos`、页图片 `/course/pages/{id}/image`、视频 `/course/videos/{id}` |
| 题目 | `GET /problems/{id}/public` |
| 提交 | `POST /submissions`（`source: "TRAINING"`）、轮询 `GET /submissions/{id}` 直至 `PASSED/FAILED/ERROR` |
| 班级 | `GET /classrooms/mine`、`POST /classrooms/join`、`GET /classroom/quests/mine` |

## 本地开发：在微信开发者工具中打开

1. 安装依赖并做一次类型校验：

```bash
cd wechat-miniprogram
npm install
npx tsc --noEmit   # 应当无报错
```

2. 启动后端（默认 `http://localhost:18080`）：见仓库根目录 `./dev.sh` 或 `backend` 的 `mvn spring-boot:run`。

3. 打开「微信开发者工具」→ 导入项目，目录选择本 `wechat-miniprogram/`。
   - 工具会按 `project.config.json` 识别 `miniprogramRoot: miniprogram/` 与 TypeScript 编译插件。
   - 如提示需要构建 npm：菜单「工具 → 构建 npm」。本项目运行时无第三方依赖，通常无需此步。

4. **关闭域名校验以访问 `http://localhost:18080`（开发必做）**：
   开发者工具右上角「详情 → 本地设置」，勾选
   **「不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书」**。
   否则 `wx.request` 会因 http / 非白名单域名被拦截。

5. 在模拟器里：注册（默认角色 STUDENT）或登录 → 进入「学习」tab。

> 真机预览：本项目 `appid` 为 `touristappid`（游客号），**仅**支持开发者工具模拟器；
> 真机/体验版需替换为你自己的 AppID（见下）。

## 上线前必做

1. **替换 AppID**：把 `project.config.json` 里的 `"appid": "touristappid"` 改为你在
   [微信公众平台](https://mp.weixin.qq.com) 申请的真实小程序 AppID。

2. **配置后端 HTTPS 域名白名单**：把 `miniprogram/config.ts` 的 `API_BASE` 改为你的后端 **HTTPS** 域名
   （如 `https://api.example.com`），并在
   「微信公众平台 → 开发管理 → 开发设置 → 服务器域名 → **request 合法域名**」加入该域名。
   生产环境**必须**走 HTTPS，且建议把 `project.config.json` 的 `setting.urlCheck` 改回 `true`。
   - 课件页图片 / 视频流为公开端点（`<image>` / `<video>` 的 `src` 不带鉴权头），其域名同样需在
     **downloadFile / 业务域名**（视频）与 request 白名单中按需配置。

3. 关闭开发者工具的「不校验合法域名」选项，以贴近线上行为做回归。

## 设计说明

- 视觉为星露谷式像素小镇暖木色调（粗描边、直角、偏移阴影、等宽字体），保持课程内容可读、可复制。
- 严格 TypeScript：业务层不使用 `any`；wx 事件 / dataset 在读取处做窄化类型断言。
- 仅真实接口，无前端假数据；判题只认服务端结果（无 Pyodide）。
```
