#!/usr/bin/env bash
set -eo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
run_dir="$project_root/.local/run"
stopped_pids=" "

stop_pid() {
  local pid="$1"
  local name="$2"
  [[ "$pid" =~ ^[0-9]+$ ]] || return
  case "$stopped_pids" in
    *" $pid "*) return ;;
  esac
  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid"
    printf '已停止 %s（PID %s）\n' "$name" "$pid"
    local attempts=0
    while kill -0 "$pid" 2>/dev/null && [[ "$attempts" -lt 20 ]]; do
      sleep 0.1
      attempts=$((attempts + 1))
    done
    if kill -0 "$pid" 2>/dev/null; then
      kill -9 "$pid" 2>/dev/null || true
      printf '已强制结束 %s（PID %s）\n' "$name" "$pid"
    fi
  fi
  stopped_pids="$stopped_pids$pid "
}

for pidfile in "$run_dir"/*.pid; do
  [[ -e "$pidfile" ]] || continue
  pid="$(<"$pidfile")"
  name="${pidfile##*/}"
  name="${name%.pid}"
  [[ -n "$pid" ]] || { rm -f "$pidfile"; continue; }
  stop_pid "$pid" "$name"
  rm -f "$pidfile"
done

# PID files can be lost when a terminal or shell is forcibly closed. Clean up
# remaining project listeners so stop-local always releases the configured ports.
stop_listener() {
  local port="$1"
  local name="$2"
  local pid command
  command -v lsof >/dev/null 2>&1 || return
  for pid in $(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true); do
    command="$(ps -p "$pid" -o command= 2>/dev/null || true)"
    case "$command" in
      *agri-admin-api-*.jar*|*agri-portal-api-*.jar*|*node_modules/.bin/vite*)
        stop_pid "$pid" "$name"
        ;;
    esac
  done
}

stop_listener "${ADMIN_WEB_PORT:-3000}" admin-web
stop_listener "${PORTAL_WEB_PORT:-3001}" portal-web
stop_listener 8080 admin-api
stop_listener 8081 portal-api
