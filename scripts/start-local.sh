#!/usr/bin/env bash
set -eo pipefail

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

run_dir="$project_root/.local/run"
log_dir="$project_root/.local/logs"
mkdir -p "$run_dir" "$log_dir" "$project_root/uploads"

start_process() {
  local name="${1:-}"
  if [[ -z "$name" ]]; then
    echo "启动失败：服务名称不能为空" >&2
    return 1
  fi
  shift
  local logfile="$log_dir/$name.log"
  local pidfile="$run_dir/$name.pid"
  if [[ -f "$pidfile" ]] && kill -0 "$(<"$pidfile")" 2>/dev/null; then
    printf '%s 已运行（PID %s）\n' "$name" "$(<"$pidfile")"
    return
  fi
  rm -f "$pidfile"
  nohup "$@" >"$logfile" 2>&1 &
  local process_pid=$!
  printf '%s\n' "$process_pid" >"$pidfile"
  printf '已启动 %s（PID %s），日志：%s\n' "$name" "$process_pid" "$logfile"
}

common_env=(DB_HOST="${DB_HOST:-localhost}" DB_PORT="${DB_PORT:-3306}" DB_NAME="${DB_NAME:-agri_trading}" DB_USER="${DB_USER:-root}" DB_PASSWORD="$DB_PASSWORD" REDIS_HOST="${REDIS_HOST:-localhost}" REDIS_PORT="${REDIS_PORT:-6379}" REDIS_PASSWORD="${REDIS_PASSWORD:-}" JWT_SECRET="$JWT_SECRET" AGRI_AES_KEY="$AGRI_AES_KEY" UPLOAD_PATH="${UPLOAD_PATH:-$project_root/uploads}" ORDER_TIMEOUT_ENABLED="${ORDER_TIMEOUT_ENABLED:-false}")
start_process admin-api env "${common_env[@]}" java -jar "$project_root/backend/agri-admin-api/target/agri-admin-api-0.1.0-SNAPSHOT.jar"
start_process portal-api env "${common_env[@]}" java -jar "$project_root/backend/agri-portal-api/target/agri-portal-api-0.1.0-SNAPSHOT.jar"
start_process admin-web bash -c "cd '$project_root/frontend-admin' && exec '$project_root/frontend-admin/node_modules/.bin/vite' preview --host 0.0.0.0 --port '${ADMIN_WEB_PORT:-3000}' --strictPort"
start_process portal-web bash -c "cd '$project_root/frontend-portal' && exec '$project_root/frontend-portal/node_modules/.bin/vite' preview --host 0.0.0.0 --port '${PORTAL_WEB_PORT:-3001}' --strictPort"

wait_for_url() {
  local url="$1"
  local attempts=0
  while [[ "$attempts" -lt 60 ]]; do
    if curl -fsS -o /dev/null "$url" 2>/dev/null; then
      return 0
    fi
    attempts=$((attempts + 1))
    sleep 1
  done
  echo "服务未就绪：$url" >&2
  return 1
}

if command -v curl >/dev/null 2>&1; then
  echo "正在等待本地服务就绪..."
  wait_for_url "http://localhost:8080/doc.html"
  wait_for_url "http://localhost:8081/doc.html"
  wait_for_url "http://localhost:${ADMIN_WEB_PORT:-3000}/"
  wait_for_url "http://localhost:${PORTAL_WEB_PORT:-3001}/"
fi

echo
echo "=== 农资现货交易系统已启动 ==="
printf '后台管理：     http://localhost:%s\n' "${ADMIN_WEB_PORT:-3000}"
printf '门户网站：     http://localhost:%s\n' "${PORTAL_WEB_PORT:-3001}"
printf '后台 API 文档： http://localhost:8080/doc.html\n'
printf '门户 API 文档： http://localhost:8081/doc.html\n'
echo "=============================="
