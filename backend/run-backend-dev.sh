#!/usr/bin/env bash
# 从 backend/env.clearml.local 加载 ClearML 变量后启动 Spring Boot（端口见 application.yml）。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
set -a
if [[ -f "$ROOT/env.clearml.local" ]]; then
  # shellcheck source=/dev/null
  source "$ROOT/env.clearml.local"
fi
set +a
exec mvn -f "$ROOT/pom.xml" spring-boot:run "$@"
