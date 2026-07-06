# Pythontopia Security Audit Harness

The harness generates exactly 1,000 deterministic security cases. Full active testing is restricted to loopback targets. Public mode executes only cases marked `public-safe` and requires an explicitly approved host.

```bash
python3 -m unittest discover -s security/tests -v
python3 security/attack_runner.py \
  --mode local \
  --output .work/security/local-results.jsonl

python3 security/attack_runner.py \
  --mode public \
  --public-base http://47.120.29.62 \
  --delay 0.25 \
  --output .work/security/public-results.jsonl
```

The evidence files redact credentials and cap response bodies. Do not add production tokens, cookies, student data, or destructive payloads to the catalog.
