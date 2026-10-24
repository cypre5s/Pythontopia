# 生产部署运维备忘（共享服务器 · compose-only）

> 记录在共享服务器 `master`（101.34.249.229，SSH 8122，用户 `jialanhu`）上以
> **compose-only + 主机 Nginx** 方式部署 Pythontopia 的真实踩坑与操作结论。
> 最后更新：2026-10-24。与 `dev-startup.md`（本地开发启动）互补。

## 0. 为什么不用 `install_release.sh`

`deploy/scripts/install_release.sh` 面向**独占机**：会 `apt install nginx`、写
`sites-enabled/`、删 `default`、并把 `https://<本机IP>/healthz` 的证书校验写死。
`master` 是多人共享实验机（同一份手写 `nginx.conf` 还服务 aidhu.net / view / ml /
api / harbor / milvus 等他人站点），因此只跑 Compose，把流量接到
`127.0.0.1:18088`，其余全部手工。

## 1. 首次启动顺序（全新数据库必读）

全新库直接 `docker compose up -d` 会死锁：

```
rag 启动 → 读课程页 → 表不存在（Flyway 未跑）→ 索引为空 → /readyz 503
   ↑                                                       ↓
backend 起不来 ← graph 依赖 rag healthy ←──────────────────┘
   ↑
Flyway 只在 backend 启动时执行
```

正确顺序：

```bash
sudo pytc up -d postgres redis piston
sudo pytc up -d --no-deps backend     # 只起 backend 跑 Flyway（V001→V23x）
sudo pytc restart pythontopia-rag     # rag 此时能读到课程页并自愈建索引
sudo pytc up -d
```

## 2. Piston 判题运行时

- piston 默认首次启动联网从 GitHub Releases 拉运行时；国内机器拉不动，
  `piston-runtime-init` 永远失败、`/readyz` 恒为 `judge: ... not installed`。
- 解决：构建期把 `python-3.11.0.pkg.tar.gz` 解压进镜像 `/piston/packages/python/3.11.0/`，
  并且**必须 `touch .ppman-installed`** —— piston 的 `src/index.js` 用这个标记文件过滤
  已安装版本，缺了它 `/api/v2/runtimes` 返回 `[]`（最隐蔽的一个坑）。
- 换镜像后**必须删命名卷**再起容器，否则旧卷会遮蔽镜像内容：
  ```bash
  sudo pytc stop piston piston-runtime-init
  sudo pytc rm -f piston piston-runtime-init
  docker volume rm pythontopia_piston_packages
  sudo pytc up -d
  ```

## 3. 账号体系：注册默认关闭 + 自举管理员

- 自助注册由 `PYT_AUTH_REGISTRATION_ENABLED`（默认 `false`）控制，关闭时
  `SecurityConfig` 对 `POST /api/auth/register` 用 `denyAll`。
  **匿名请求得到的响应码是 401 而不是 403**（Spring 把匿名 + AccessDenied 交给
  未认证入口），旧版镜像对空 body 会返回 `400 VALIDATION_ERROR` —— 用这个差异可以
  快速判断线上跑的是新镜像还是旧镜像。
- 全新库没有任何账号，由 `BootstrapAdminInitializer` 在 `ApplicationReadyEvent`
  （Flyway 之后）按 `.env` 自举：
  ```bash
  PYT_BOOTSTRAP_ADMIN_USERNAME=admin
  PYT_BOOTSTRAP_ADMIN_PASSWORD=<≥8 位>
  PYT_BOOTSTRAP_ADMIN_DISPLAY_NAME=管理员
  ```
  规则：库里已有任意 ADMIN → 整体跳过（永不重置口令）；用户名已被非管理员占用 →
  提升为 ADMIN 并 `token_version + 1`（旧 JWT 立即失效）；两项留空 → 关闭自举。
- 学生账号由管理员在 `/admin/users` 上传 `.xlsx` 名单导入：表头需含
  「学号/学生学号/student id」与「姓名/学生姓名/name」，最多 1000 行，
  **初始密码 = 学号字符串反转**，明文只在校验响应里返回一次。
- TEACHER 是 ADMIN 的子集（所有教师端点都是 `hasAnyRole('TEACHER','ADMIN')`，
  `ClassroomService.canManageClassrooms` 也包含 ADMIN），因此单管理员即可完成
  建班、发任务、看板，无需单独发教师号。

## 4. 坑：单独重建 backend 后全站 502

`frontend/nginx.conf` 使用静态上游 `proxy_pass http://backend:18080;`，nginx 只在
**启动时解析一次 IP**。`docker compose up -d --force-recreate backend` 之后 backend
换了容器 IP，前端仍连旧地址 → 所有 `/api` 与 `/healthz` 全部 502。

```bash
sudo pytc restart frontend     # 必然的收尾步骤
```

根治需要把上游改成 `resolver 127.0.0.11 valid=10s` + 变量式 `proxy_pass`，但会牵连
SSE 缓冲相关配置，现阶段不做，只作为操作纪律。

## 5. 坑：`docker load` 会留下 dangling 镜像

同名 tag 覆盖时 Docker 把旧镜像改名成 `<none>`（日志里的
`renaming the old one ... to empty string`），继续占盘。共享机**禁止**
`docker image prune -a`，只按日志给出的旧 ID 精确删除：

```bash
docker rmi <旧镜像ID>            # 例如 load 日志里 1b1f91341651 / 5700ae21bc12
```

根分区常年 88%，发版前后都要 `df -h /`。

## 6. 增量发版（只重建变化的镜像）

只有 `backend` 与 `frontend` 变更时，不必重打 1.5GB 全量包：

```bash
# WSL 内：源码拷到内部盘，避开 /mnt/b 的 9p 慢 I/O
mkdir -p ~/pyt-build/src && rsync -a --exclude .git --exclude node_modules \
  --exclude target --exclude dist <repo>/backend <repo>/frontend ~/pyt-build/src/
export DOCKER_BUILDKIT=1
docker build --network host --build-arg HTTP_PROXY=... --build-arg HTTPS_PROXY=... \
  -t pythontopia/backend:release -f ~/pyt-build/src/backend/Dockerfile ~/pyt-build/src/backend
docker save pythontopia/backend:release | zstd -19 -T0 -o backend.tar.zst
```

实测：backend+frontend 两个镜像压缩后 **351MB + 77MB**（全量包 1.5GB），构建约
12 分钟。服务器侧 `docker load < x.tar.zst` 支持 zstd 自动解压。

> WSL 构建前置：`rsync`、`docker-buildx` 两个包必须装（docker 29 默认 BuildKit，
> 缺 buildx 会报 `BuildKit is enabled but the buildx component is missing`）。

## 7. 代理与网络

- 构建机默认探测 `PYT_BUILD_PROXY=http://127.0.0.1:7892`；7892 不可用时可用
  `PYT_BUILD_PROXY=http://127.0.0.1:7897`（`docker pull` 走 daemon 的
  `daemon.json` 代理配置，BuildKit 的元数据解析则不继承该代理，所以基础镜像
  最好先 `docker pull` 好，构建时直接用本地层）。
- 服务器直连 GitHub 被墙；WSL 侧可用 `https://ghfast.top/<原始URL>` 镜像下载。
- 课程媒体（7 PDF + 34 MP4）不在仓库里，WSL 构建时用
  `PYT_BUILD_SKIP_COURSE_RUNTIME_DATA_VALIDATION=true` 跳过校验；安装器与预检
  仍会在目标机做完整校验。

## 8. 共享机纪律（会伤到别人）

- 禁止 `docker image prune -a` / `docker system prune -a`。
- 只删自己的镜像与容器；不碰他人端口、证书、nginx 站点。
- 改 `/etc/nginx/nginx.conf` 前 `cp -a` 备份，`nginx -t` 通过才能 reload。
- 项目 Nginx 配置放在 `/etc/nginx/conf.d/pythontopia.conf` **不生效** —— 该机
  `nginx.conf` 是单文件手写，没有 `include conf.d/*.conf`。
