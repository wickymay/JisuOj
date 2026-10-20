#!/usr/bin/env bash
set -euo pipefail
project="$(cd "$(dirname "$0")/.." && pwd)"
python="${RAG_PYTHON:-$(readlink -f "$project/.venv-rag")/bin/python}"
if [[ ! -x "$python" ]]; then
  echo "RAG Python environment missing. See $project/docs/RAG.md" >&2
  exit 1
fi
export HF_HUB_OFFLINE=1 TRANSFORMERS_OFFLINE=1 TOKENIZERS_PARALLELISM=false
export RAG_SERVICE_TOKEN="${RAG_SERVICE_TOKEN:-${AI_RAG_TOKEN:-}}"
exec "$python" "$project/rag/service.py"
