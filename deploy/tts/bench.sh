#!/usr/bin/env bash
# B0 基准：在 master 上量 ZONOS3-Mini CPU 的 峰值内存 / RTF / 首字延迟 / 长文本上限。
# 不写数据库、不碰课程内容，只产生几个 wav 和一行行数字。
#
# 前置：
#   docker build -f deploy/tts/Dockerfile -t pythontopia-reborn-tts:local --build-arg JOBS=8 deploy/tts
#   WITH_OPTIONAL=1 DEST=/srv/pythontopia-tts/models bash deploy/tts/fetch-models.sh
#
# 运行：
#   REBORN_TTS_TOKEN=$(openssl rand -hex 24) bash deploy/tts/bench.sh
#
# 该脚本只把端口发布到 127.0.0.1:19191（本机调试用），测完自动删容器。
set -euo pipefail

IMAGE="${IMAGE:-pythontopia-reborn-tts:local}"
MODELS="${MODELS:-/srv/pythontopia-tts/models}"
TOKEN="${REBORN_TTS_TOKEN:?请设置 REBORN_TTS_TOKEN（>=24 字符）}"
NAME="pythontopia-tts-bench"
PORT="${PORT:-19191}"
VOICE="${VOICE:-default_eaee7325255d4783}"   # Meilin（中文女声）
OUT="${OUT:-.tmp/tts-bench}"

mkdir -p "$OUT"
docker rm -f "$NAME" >/dev/null 2>&1 || true

echo "== 启动容器（限 4G / 8 核，端口仅本机）"
docker run -d --name "$NAME" \
  --memory 4g --cpus 8 \
  --tmpfs /tmp:rw,nosuid,nodev,size=256m \
  -e ZONOS3_ACCESS_TOKEN="$TOKEN" \
  -v "$MODELS:/models:ro" \
  -p "127.0.0.1:${PORT}:1919" \
  "$IMAGE" >/dev/null

cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT

echo "== 等待健康（最多 120s；首次加载模型会慢）"
for i in $(seq 1 60); do
  if curl -fsS -H "Authorization: Bearer $TOKEN" "http://127.0.0.1:${PORT}/v2/capabilities" >/dev/null 2>&1; then
    echo "   ready after ${i}x2s"; break
  fi
  sleep 2
  [[ $i == 60 ]] && { echo "启动超时，查看日志：docker logs $NAME"; docker logs --tail 50 "$NAME"; exit 1; }
done

echo "== 能力探测"
curl -fsS -H "Authorization: Bearer $TOKEN" "http://127.0.0.1:${PORT}/v2/capabilities" > "$OUT/capabilities.json"
python3 - "$OUT/capabilities.json" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
print("  decoders:", d.get("decoders"))
print("  plugins:", d.get("plugins"))
print("  max rows:", (d.get("limits") or {}).get("max_tokens"))
PY

echo "== 音色列表（确认中文音色 id 可用）"
curl -fsS -H "Authorization: Bearer $TOKEN" "http://127.0.0.1:${PORT}/v2/speakers" > "$OUT/speakers.json"
python3 - "$OUT/speakers.json" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
sp = d.get("speakers", [])
print(f"  {len(sp)} voices")
for v in sp[:3]:
    print("   sample:", v.get("id"), v.get("name", ""), v.get("language", ""))
PY

# 三段中文，长度覆盖"一页讲解"到"长页"；后半段故意超长，用来看 HTTP 状态
read -r -d '' T1 <<'EOF' || true
这一页讲的是变量：变量就是给数据起一个名字，之后就能用名字取到它。注意名字要能看懂，不要用单个字母。
EOF
read -r -d '' T2 <<'EOF' || true
这一页把变量和类型放在一起看。整数用来计数，小数用来表示测量值，字符串用来存文字。类型决定了能对数据做什么操作，比如字符串可以拼接，整数可以相加。初学时不必背所有规则，先记住：看到引号就是字符串，看到小数点就是浮点数。真正写代码时，类型错误最常见，所以报错信息要学会读。
EOF

bench() { # bench <label> <text>
  local label="$1" text="$2" file="$OUT/$1.wav"
  local start end http
  start=$(date +%s.%N)
  http=$(curl -sS -o "$file" -w '%{http_code}' \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    --data-binary "$(python3 -c 'import json,sys;print(json.dumps({"model":"zonos3","input":sys.argv[1],"voice":sys.argv[2],"response_format":"wav"},ensure_ascii=False))' "$text" "$VOICE")" \
    "http://127.0.0.1:${PORT}/v1/audio/speech")
  end=$(date +%s.%N)
  python3 - "$label" "$file" "$http" "$start" "$end" <<'PY'
import sys, wave, os
label, path, http, start, end = sys.argv[1], sys.argv[2], sys.argv[3], float(sys.argv[4]), float(sys.argv[5])
wall = end - start
if http != "200" or not os.path.exists(path) or os.path.getsize(path) < 1000:
    print(f"  {label:10s} http={http} wall={wall:6.2f}s  失败（看 {path}）")
    sys.exit(0)
try:
    with wave.open(path) as w:
        dur = w.getnframes() / w.getframerate()
        rate, ch = w.getframerate(), w.getnchannels()
except Exception as exc:
    print(f"  {label:10s} http={http} wall={wall:6.2f}s  无法解析 wav: {exc}")
    sys.exit(0)
size = os.path.getsize(path) / 1e6
print(f"  {label:10s} http={http} wall={wall:6.2f}s audio={dur:6.2f}s RTF={wall/dur:5.2f} {rate}Hz x{ch} {size:.2f}MB")
PY
}

echo "== 合成（RTF = 墙钟时间 / 音频时长，越小越好；>=1 表示比实时慢）"
bench "page_short" "$T1"
bench "page_long"  "$T2"

echo "== 内存（采样 10 次，取峰值；用 docker stats 的 MiB/GiB 数字）"
peak=$(python3 - "$NAME" <<'PY'
import re, subprocess, sys, time
name = sys.argv[1]
best = 0.0
for _ in range(10):
    out = subprocess.run(["docker", "stats", "--no-stream", "--format", "{{.MemUsage}}", name],
                         capture_output=True, text=True).stdout.strip()
    m = re.search(r"([\d.]+)\s*([KMGT]i?B)", out)
    if m:
        value, unit = float(m.group(1)), m.group(2)
        mib = value * {"KiB": 1 / 1024, "MiB": 1, "GiB": 1024, "TiB": 1024 * 1024,
                       "kB": 0.0009766, "KB": 0.0009766, "MB": 0.9537, "GB": 953.7}.get(unit, 1)
        best = max(best, mib)
    time.sleep(1)
print(int(best))
PY
)
echo "  peak RSS ≈ ${peak} MiB"

echo
echo "== 产物：$OUT（3 段 wav 可以下载来听一下中文自然度）"
ls -lh "$OUT" | tail -n +2
echo
echo "== 判读标准（写进验收）："
echo "  1) peak RSS ≤ 3072 MiB —— 容器 4G 上限留出加载峰值余量"
echo "  2) page_short RTF ≤ 1.0  —— 一页 6-8 秒的讲解，1 分钟内能生成完"
echo "  3) page_long http=200    —— 若 400/截断，说明单页讲解必须按句分段"
echo "  4) 中文听感可接受（人名、术语、标点停顿时长）"
