# ECS Dual HTTP and HTTPS Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Keep `http://47.120.29.62` and `https://47.120.29.62` simultaneously available with a trusted, automatically renewed IP certificate.

**Architecture:** One host Nginx `server` block listens on both 80 and 443, so both protocols share the existing proxy, security, SSE, download, and ACME routes. The repository template remains authoritative for future installs and rollbacks; the live ECS configuration is installed only after static checks and `nginx -t`, with a timestamped rollback copy.

**Tech Stack:** Nginx, Let’s Encrypt IP certificates, Certbot 5.6, systemd timers, Bash, curl, OpenSSL, Playwright

---

### Task 1: Add a dual-protocol configuration verifier

**Files:**
- Create: `deploy/scripts/verify_nginx_dual_protocol.sh`
- Test: `deploy/nginx/pythontopia.conf`

**Step 1: Write the failing verifier**

Create a strict Bash verifier that accepts an optional Nginx config path and asserts:

- IPv4 and IPv6 listeners exist for 80 and `443 ssl`;
- the certificate and private-key paths point to
  `/etc/letsencrypt/live/47.120.29.62/`;
- TLS 1.2 and 1.3 are enabled;
- the ACME webroot route remains present;
- no HTTP-to-HTTPS redirect or HSTS header is present.

**Step 2: Run it to verify RED**

Run:

```bash
bash deploy/scripts/verify_nginx_dual_protocol.sh
```

Expected: non-zero exit with a missing `listen 443 ssl` assertion.

### Task 2: Make the repository Nginx template dual-protocol

**Files:**
- Modify: `deploy/nginx/pythontopia.conf`
- Test: `deploy/scripts/verify_nginx_dual_protocol.sh`

**Step 1: Add the minimal TLS directives**

In the existing `server` block add:

```nginx
listen 443 ssl default_server;
listen [::]:443 ssl default_server;

ssl_certificate     /etc/letsencrypt/live/47.120.29.62/fullchain.pem;
ssl_certificate_key /etc/letsencrypt/live/47.120.29.62/privkey.pem;
ssl_protocols TLSv1.2 TLSv1.3;
ssl_session_cache shared:PYTHONTOPIA_TLS:10m;
ssl_session_timeout 1d;
ssl_session_tickets off;
```

Update comments to describe public ports 80 and 443. Do not add redirects or HSTS.

**Step 2: Run the verifier to verify GREEN**

Run:

```bash
bash deploy/scripts/verify_nginx_dual_protocol.sh
```

Expected: all assertions pass.

**Step 3: Validate with the real Nginx parser**

Copy the candidate to a temporary ECS path and run:

```bash
nginx -t -c /etc/nginx/nginx.conf
```

The live file is not replaced until the candidate contents and certificate paths are verified.

### Task 3: Document the production HTTPS contract

**Files:**
- Modify: `deploy/README.md`
- Test: `deploy/scripts/verify_nginx_dual_protocol.sh`

**Step 1: Update production topology and operations**

Document:

- public ports 80 and 443;
- both public health commands;
- trusted Let’s Encrypt IP certificate paths;
- 160-hour certificate lifetime and twice-daily timer;
- required Aliyun security-group rules for TCP 80 and TCP 443;
- no redirect and no HSTS.

**Step 2: Check documentation drift**

Run:

```bash
rg -n "仅 80|只有.*80|必须仅 80|SECURITY_GROUP_PORT_80" deploy/README.md deploy/nginx/pythontopia.conf
```

Expected: no stale claim that production exposes only port 80.

**Step 3: Commit repository changes narrowly**

Run:

```bash
git add deploy/nginx/pythontopia.conf \
  deploy/scripts/verify_nginx_dual_protocol.sh \
  deploy/README.md
git diff --cached --name-only
git commit -m "feat(deploy): keep HTTP and HTTPS public entrypoints"
```

Expected: only those three implementation files are committed.

### Task 4: Install the live ECS configuration safely

**Files:**
- Modify on ECS: `/etc/nginx/sites-available/pythontopia.conf`
- Create on ECS: `/opt/pythontopia/backups/<timestamp>/nginx-pythontopia.conf.before-dual-tls`

**Step 1: Verify certificate and renewal prerequisites**

Run on ECS:

```bash
openssl x509 \
  -in /etc/letsencrypt/live/47.120.29.62/fullchain.pem \
  -noout -checkend 86400 -dates -ext subjectAltName
systemctl is-enabled pythontopia-certbot-renew.timer
systemctl is-active pythontopia-certbot-renew.timer
```

Expected: certificate remains valid for at least 24 hours, SAN contains the IP,
and timer is enabled and active.

**Step 2: Save a timestamped rollback copy**

Create a mode-700 backup directory and copy the current site configuration into
it before replacement.

**Step 3: Install, parse, and reload without downtime**

Install the repository template with mode 644, run `nginx -t`, and only then run:

```bash
systemctl reload nginx
```

If parsing or local HTTPS verification fails, restore the backup, run
`nginx -t`, and reload.

**Step 4: Verify local dual listeners**

Run:

```bash
ss -ltnp | grep -E ':(80|443)\b'
curl -fsS http://127.0.0.1/healthz
curl --resolve 47.120.29.62:443:127.0.0.1 \
  -fsS https://47.120.29.62/healthz
```

Expected: both listeners exist and both health checks return `ok`.

### Task 5: Prove renewal and public/browser acceptance

**Files:**
- Verify on ECS: `/etc/letsencrypt/renewal/47.120.29.62.conf`

**Step 1: Verify renewal configuration and service**

Confirm `preferred_profile = shortlived`, `authenticator = webroot`, the timer
next-run time, and a successful renewal-service execution.

**Step 2: Verify public HTTP and HTTPS without bypasses**

Run from the workstation with proxy bypass:

```bash
curl --noproxy '*' -fsS http://47.120.29.62/healthz
curl --noproxy '*' -fsS https://47.120.29.62/healthz
openssl s_client -connect 47.120.29.62:443 \
  -verify_return_error -verify_ip 47.120.29.62
```

Expected: both health checks return `ok` and OpenSSL reports verification code 0.

**Step 3: Run real Chromium checks**

Open both schemes, wait for the rendered login page, capture screenshots, and
assert no console errors, failed requests, or responses at status 400+.

**Step 4: Check application health after reload**

Run:

```bash
docker ps --format '{{.Names}}|{{.Status}}'
```

Expected: all six containers remain healthy.

### Task 6: Resolve the Aliyun security-group boundary

**Files:**
- No repository changes

**Step 1: Compare local and public TCP 443**

If local HTTPS succeeds but public HTTPS times out, record that the remaining
boundary is the Aliyun security group.

**Step 2: Apply the only required cloud rule when credentials are available**

Add inbound TCP 443 from `0.0.0.0/0` to the ECS security group. Do not expose
8080, 18080, 18091, 18092, PostgreSQL, or Redis.

**Step 3: Repeat Task 5**

The work is complete only when both public schemes pass without `-k`.

