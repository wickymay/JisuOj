#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../backend"
export DB_URL="${DB_URL:-jdbc:mysql://127.0.0.1:3306/jol?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false}"
export DB_USERNAME="${DB_USERNAME:-jisuoj}"
export DB_PASSWORD="${DB_PASSWORD:-jisuoj-dev}"
export JUDGE_NOTIFY_ENABLED="${JUDGE_NOTIFY_ENABLED:-false}"
project="$(cd .. && pwd)"
if [[ "${AI_RAG_ENABLED:-true}" == "true" && "${AI_RAG_BASE_URL:-http://127.0.0.1:8765}" == "http://127.0.0.1:8765" ]]; then
    if ! curl --silent --fail --max-time 2 http://127.0.0.1:8765/health >/dev/null; then
        if [[ -x "$project/.venv-rag/bin/python" ]]; then
            mkdir -p "$project/tmp"
            nohup bash "$project/scripts/dev-rag-wsl.sh" >"$project/tmp/rag-service.log" 2>&1 &
            echo $! >"$project/tmp/rag-service.pid"
            for ((attempt=0; attempt<120; attempt++)); do
                curl --silent --fail --max-time 1 http://127.0.0.1:8765/health >/dev/null && break
                sleep 1
            done
        else
            echo "Local knowledge retrieval is not installed; see $project/docs/RAG.md" >&2
        fi
    fi
fi
exec mvn spring-boot:run
