# Alethicode Export

一次性导出脚本只用于从 Alethicode 本地只读数据库导出 `packId=43`。CI 不运行该脚本。

```bash
ALETHICODE_DB_URL='postgresql://<user>:<password>@127.0.0.1:5436/alethicode' \
  uv run --with 'psycopg[binary]' python export_from_alethicode.py
```

导出后 JSON 会写入 `../json/`，并立即校验：

- 7 份文档
- 561 页
- 7 章
- 65 个有课件页证据映射的 KC
- 46 个示例
- 39 道可见题目
