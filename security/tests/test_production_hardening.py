from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class ProductionHardeningTest(unittest.TestCase):
    def test_production_disables_public_api_docs(self) -> None:
        compose = (ROOT / "deploy/docker-compose.production.yml").read_text(encoding="utf-8")
        self.assertIn('PYT_EXPOSE_API_DOCS: "false"', compose)

    def test_host_nginx_hides_version_and_rate_limits_auth(self) -> None:
        nginx = (ROOT / "deploy/nginx/pythontopia.conf").read_text(encoding="utf-8")
        self.assertIn("server_tokens off;", nginx)
        self.assertIn("limit_req_zone", nginx)
        self.assertIn("limit_req zone=pythontopia_auth", nginx)
        self.assertIn("limit_req_status 429;", nginx)

    def test_host_nginx_blocks_public_api_docs(self) -> None:
        nginx = (ROOT / "deploy/nginx/pythontopia.conf").read_text(encoding="utf-8")
        self.assertIn("location = /v3/api-docs", nginx)
        self.assertIn("location ^~ /swagger-ui/", nginx)

    def test_host_nginx_keeps_http_only_surface_hardened(self) -> None:
        nginx = (ROOT / "deploy/nginx/pythontopia.conf").read_text(encoding="utf-8")
        self.assertIn("listen 80 default_server;", nginx)
        self.assertIn('add_header X-Content-Type-Options "nosniff" always;', nginx)
        self.assertIn('add_header X-Frame-Options "SAMEORIGIN" always;', nginx)
        self.assertIn('add_header Content-Security-Policy "frame-ancestors', nginx)
        self.assertIn("location /downloads/", nginx)


if __name__ == "__main__":
    unittest.main()
