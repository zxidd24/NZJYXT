#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Load the ignored local environment file when present; exported variables still work.
if [[ -f "$project_root/.env" ]]; then
  set -a
  source "$project_root/.env"
  set +a
fi
: "${DB_PASSWORD:?请先设置 DB_PASSWORD}"
: "${JWT_SECRET:?请先设置 JWT_SECRET（至少32字节）}"
: "${AGRI_AES_KEY:?请先设置 AGRI_AES_KEY}"

cd "$project_root/backend"
mvn clean package -DskipTests

cd "$project_root/frontend-admin"
npm run build

cd "$project_root/frontend-portal"
npm run build

echo "构建完成：backend/*/target/*.jar、frontend-admin/dist、frontend-portal/dist"
echo "执行 scripts/start-local.sh 启动本地服务，执行 scripts/smoke-test.sh 验证接口。"
