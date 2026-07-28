# ECS HTTP + HTTPS 双协议入口设计

## 背景

Pythontopia 公网入口当前为 `47.120.29.62`。主机 Nginx 仅监听
TCP 80，并反向代理到绑定在 `127.0.0.1:8080` 的前端容器。

ECS 已存在由 Let’s Encrypt 为该公网 IPv4 地址签发的可信短期证书：

- 证书路径：`/etc/letsencrypt/live/47.120.29.62/`
- ACME 客户端：Certbot 5.6
- 续期方式：webroot，`/var/www/certbot`
- 证书配置：`shortlived`
- 自动续期：`pythontopia-certbot-renew.timer` 每日检查两次
- 部署钩子：续期成功后 reload Nginx

当前缺口是发布模板和主机 Nginx 配置都没有监听 443，因此已有证书
没有被使用；此外还需确认阿里云安全组是否放行公网 TCP 443。

## 目标

1. `http://47.120.29.62` 与 `https://47.120.29.62` 长期同时可用。
2. HTTPS 使用浏览器可信的 IP 地址证书，不使用自签名证书。
3. HTTP 不跳转到 HTTPS，也不启用 HSTS，保留用户要求的双协议入口。
4. HTTP 与 HTTPS 复用同一套反向代理、安全头、限流和 SSE 配置。
5. 新版本安装与旧版本回滚均不会覆盖掉 HTTPS 能力。
6. 证书自动续期后 Nginx 自动加载新证书。

## 设计

### Nginx

在现有同一个 `server` 块中增加：

- `listen 443 ssl default_server`
- `listen [::]:443 ssl default_server`
- Let’s Encrypt `fullchain.pem` 与 `privkey.pem`
- TLS 1.2/1.3、共享会话缓存和合理的会话超时

80 与 443 共用全部 `location`。现有
`X-Forwarded-Proto $scheme` 会分别向上游传递 `http` 或 `https`。
ACME HTTP-01 challenge 继续通过 80 端口的
`/.well-known/acme-challenge/` 提供。

不配置 80→443 跳转，不配置 HSTS。

### 配置持久化

同时修改仓库中的 `deploy/nginx/pythontopia.conf` 和 ECS 主机上的
`/etc/nginx/sites-available/pythontopia.conf`。发布脚本和回滚脚本本来
就从仓库版本安装该模板，因此仓库模板是长期保持 HTTPS 的权威来源。

### 证书续期

沿用现有 Certbot 5.6、renewal 配置和 systemd timer。验证：

- timer 为 enabled/active；
- renewal service 最近执行成功；
- renewal 配置使用 `shortlived` 和 webroot；
- deploy hook 能 reload Nginx；
- 当前证书 SAN 精确包含 `47.120.29.62`。

### 阿里云安全组

服务器配置完成后从公网验证 TCP 443。如果 Nginx 已监听而公网仍超时，
则故障边界在阿里云安全组，需要添加入方向规则：

- 协议：TCP
- 端口：443
- 来源：`0.0.0.0/0`

主机未配置可调用 ECS API 的 RAM 角色或阿里云 CLI 凭据，因此安全组
变更不能由主机内命令替代。

## 变更与回滚

1. 修改仓库模板并执行静态断言。
2. 在 ECS 保存带时间戳的 Nginx 配置备份。
3. 安装新配置后先运行 `nginx -t`。
4. 仅在校验成功后执行 `systemctl reload nginx`，避免连接中断。
5. 若校验或 HTTPS 健康检查失败，恢复备份、再次 `nginx -t` 并 reload。

## 验收

- Nginx 同时监听 `0.0.0.0:80` 和 `0.0.0.0:443`。
- HTTP `/` 与 `/healthz` 返回 200。
- HTTPS `/` 与 `/healthz` 返回 200，证书校验不使用 `-k`。
- `openssl s_client` 验证链成功，证书 SAN 为目标 IP。
- Chromium 分别打开 HTTP 和 HTTPS 登录页，无控制台错误和失败请求。
- Certbot timer、renewal service 与 deploy hook 状态正常。
- 所有应用容器继续保持 healthy。

