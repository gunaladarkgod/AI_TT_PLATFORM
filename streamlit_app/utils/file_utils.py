"""JSON 读写工具"""
from __future__ import annotations

import json
import os
from typing import Any


def load_json(path: str, default: Any | None = None) -> Any:
    if not os.path.isfile(path):
        return default if default is not None else {}
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def save_json(data: Any, path: str) -> None:
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
