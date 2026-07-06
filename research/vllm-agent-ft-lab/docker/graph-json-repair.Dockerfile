FROM pythontopia-graph:dev

RUN HTTP_PROXY="" HTTPS_PROXY="" http_proxy="" https_proxy="" ALL_PROXY="" all_proxy="" \
    pip install --no-cache-dir \
      --index-url https://mirrors.aliyun.com/pypi/simple/ \
      --trusted-host mirrors.aliyun.com \
      "json-repair>=0.59,<0.60"
