"""Keep Python training downloads on the GitHub proxy used by Git."""

import subprocess
from typing import Dict


_PROXY_KEYS = ("HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "http_proxy", "https_proxy", "all_proxy")


def configured_github_proxy() -> str:
    try:
        result = subprocess.run(
            ["git", "config", "--get-urlmatch", "http.proxy", "https://github.com/"],
            capture_output=True,
            text=True,
            timeout=3,
            check=False,
        )
    except (OSError, subprocess.SubprocessError):
        return ""
    return result.stdout.strip() if result.returncode == 0 else ""


def apply_proxy_environment(env: Dict[str, str]) -> Dict[str, str]:
    """Preserve explicit proxy variables; fill missing ones from Git's GitHub proxy."""
    proxy = configured_github_proxy()
    if proxy:
        for key in _PROXY_KEYS:
            env.setdefault(key, proxy)
    return env
