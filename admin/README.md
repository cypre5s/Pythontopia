# React 教学工作台

独立的 React + TypeScript + shadcn/ui 应用，入口 `/teacher/`；学生 Vue/Phaser 应用继续使用 `/world`。部署入口与迁移状态以项目 `docs/` 为准。

```powershell
npm ci
npm run dev
npm run typecheck
npm run lint
npm test
npm run build
```

开发服务默认 `127.0.0.1:5179`，API 代理通过 `API_PROXY` 指定（默认 `http://127.0.0.1:18081`）。生产静态文件在 `dist/`；反向代理应将 `/teacher/*` 的深链回退到本应用 `index.html`，`/api/*` 交给同源 FastAPI。

内容请求显式包含当前 `classroom_id`。与学生前端共享同源的 `sessionStorage` 登录令牌，切班和导航会检查未保存修改；跨应用导航是完整页面跳转，后台 CSS 不会注入学生应用。官方 shadcn 源码直接保存在 `src/components/ui/`，来源和许可见 `NOTICE.md` 与 `legal/`。

流程编辑器使用现有脚本节点、条件树和任务 DAG，不执行配置代码。布局单独保存到 `configuration.editor`；保存先调用后端验证，并携带课程修订号。只读预览选用本班学生的实际记录，不写进度，也不调用模型。

浏览器回归必须指向新建的隔离测试环境：

```powershell
$env:ADMIN_E2E_URL = 'http://127.0.0.1:15234'
$env:ADMIN_E2E_CREDENTIALS = '绝对路径/私有测试账号.json'
$env:PLAYWRIGHT_CHANNEL = 'chrome'
npm run test:e2e
```

私有账号文件只需包含 `student_id` 和 `password`，不能提交到仓库。测试会新建班级、学生和教学内容，使用实际 API、资料转换和浏览器，不模拟成功响应。截图与下载结果保存在忽略的 `test-results/`。设置 `ADMIN_E2E_PPTX` 为自制演示文稿路径，并配置真实转换器，才能执行 PPTX 转换场景；默认明确跳过该外部依赖场景。AI 和 Judge0 的在线验证由项目验收流程单独执行。

CI 可直接提供 `E2E_TEACHER_STUDENT_ID`、`E2E_TEACHER_PASSWORD`，无需写入凭据文件。默认使用完整 Chromium 的新 headless 模式（先执行 `npx playwright install chromium`），以覆盖真实 PDF 阅读器；本机也可显式使用已安装的 Chrome。排课时区在 `admin/.env.local` 设置 `VITE_TIME_ZONE`，默认 `Asia/Shanghai`。
