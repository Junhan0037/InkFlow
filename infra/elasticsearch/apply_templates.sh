#!/usr/bin/env bash
set -euo pipefail

# 로컬 Elasticsearch에 인덱스 템플릿을 적용한다.
ES_URL="${ES_URL:-http://localhost:9200}"

# 스크립트 위치를 기준으로 템플릿 디렉터리를 계산한다.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATE_DIR="${SCRIPT_DIR}/templates"

# 템플릿 등록 로직을 공통 함수로 분리해 관리 포인트를 줄인다.
apply_template() {
  local name="$1"
  local file="$2"

  # 템플릿 파일을 Elasticsearch에 등록한다.
  curl -sS -X PUT "${ES_URL}/_index_template/${name}" \
    -H "Content-Type: application/json" \
    --data-binary "@${file}" > /dev/null
}

apply_template "works_template" "${TEMPLATE_DIR}/index-template-works.json"
apply_template "episodes_template" "${TEMPLATE_DIR}/index-template-episodes.json"
apply_template "assets_template" "${TEMPLATE_DIR}/index-template-assets.json"

echo "Elasticsearch 인덱스 템플릿 적용 완료"
