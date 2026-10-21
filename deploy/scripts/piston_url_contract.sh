#!/usr/bin/env bash

is_private_piston_http_url() {
  python3 - "$1" <<'PY'
import ipaddress
import sys
from urllib.parse import urlsplit

try:
    url = sys.argv[1]
    if any(ord(char) <= 32 or ord(char) == 127 for char in url):
        raise ValueError
    parsed = urlsplit(url)
    if parsed.scheme != "http" or not parsed.netloc:
        raise ValueError
    if parsed.username is not None or parsed.password is not None:
        raise ValueError
    _ = parsed.port
    host = parsed.hostname
    if host == "piston":
        sys.exit(0)
    if not host or "%" in host:
        raise ValueError
    address = ipaddress.ip_address(host)
    allowed = (
        "127.0.0.0/8",
        "10.0.0.0/8",
        "172.16.0.0/12",
        "192.168.0.0/16",
        "::1/128",
        "fc00::/7",
    )
    if any(address.version == network.version and address in network
           for network in map(ipaddress.ip_network, allowed)):
        sys.exit(0)
except ValueError:
    pass
sys.exit(1)
PY
}
