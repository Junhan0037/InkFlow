#!/usr/bin/env bash
set -euo pipefail

# 로컬 개발용 샘플 데이터를 PostgreSQL에 시딩한다.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SEED_SQL="${ROOT_DIR}/infra/seed/seed_local.sql"

# .env가 있으면 로컬 DB 설정을 재사용한다.
if [[ -f "${ROOT_DIR}/.env" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${ROOT_DIR}/.env"
  set +a
fi

JDBC_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://localhost:5432/inkflow}"
DB_USER="${SPRING_DATASOURCE_USERNAME:-inkflow}"
DB_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-inkflow_pw}"

# JDBC URL을 psql용 host/port/database로 파싱한다.
DB_HOST="$(echo "${JDBC_URL}" | sed -E 's|jdbc:postgresql://([^:/]+).*|\\1|')"
DB_PORT="$(echo "${JDBC_URL}" | sed -E 's|jdbc:postgresql://[^:/]+:([0-9]+).*|\\1|')"
DB_NAME="$(echo "${JDBC_URL}" | sed -E 's|jdbc:postgresql://[^/]+/([^?]+).*|\\1|')"

if [[ -z "${DB_HOST}" || -z "${DB_PORT}" || -z "${DB_NAME}" ]]; then
  echo "Invalid JDBC URL: ${JDBC_URL}" >&2
  exit 1
fi

export PGPASSWORD="${DB_PASSWORD}"

psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" \
  -v ON_ERROR_STOP=1 \
  -f "${SEED_SQL}"

echo "Seed data applied from ${SEED_SQL}"
