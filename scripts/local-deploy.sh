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

# 数据库连接信息齐全时才具备建库条件，避免缺配置时误执行。
has_db_config() {
  [[ -n "${DB_HOST:-}" && -n "${DB_PORT:-}" && -n "${DB_NAME:-}" && -n "${DB_USER:-}" && -n "${DB_PASSWORD:-}" ]]
}

# 执行 database/init.sql。脚本内为 CREATE ... IF NOT EXISTS 与补列判断，可重复执行且不清空数据。
init_database() {
  local sql_file="$project_root/database/init.sql"
  if [[ ! -f "$sql_file" ]]; then
    echo "未找到 $sql_file，已跳过数据库初始化" >&2
    return 0
  fi
  if ! command -v mysql >/dev/null 2>&1; then
    echo "未检测到 mysql 客户端，已跳过数据库初始化（请手动执行 database/init.sql）" >&2
    return 0
  fi
  # init.sql 内固定使用库名 agri_trading，与配置不一致时会建到另一个库，先提醒。
  if [[ "$DB_NAME" != "agri_trading" ]]; then
    echo "提示：database/init.sql 内固定使用库名 agri_trading，当前 DB_NAME=$DB_NAME 与之一致性需自行确认。" >&2
  fi
  echo "正在初始化数据库 $DB_NAME ..."
  if ! MYSQL_PWD="$DB_PASSWORD" mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" <"$sql_file"; then
    echo "数据库初始化失败：请检查连接配置后重试" >&2
    return 1
  fi
  echo "数据库初始化完成"
}

# 检测到完整数据库配置时询问是否建库；未检测到则直接进入构建流程。
# 用带超时的 read 读取输入：终端下正常等待回答；CI 等 stdin 为空时立即跳过；
# stdin 已打开但无数据时最多等待 INIT_DB_PROMPT_TIMEOUT 秒，不会挂住构建。
if has_db_config; then
  pending="检测到数据库配置（${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}），是否执行 database/init.sql 建库/补表？[y/N] "
  if read -r -t "${INIT_DB_PROMPT_TIMEOUT:-30}" -p "$pending" init_db_answer; then
    case "$init_db_answer" in
      [yY] | [yY][eE][sS]) init_database ;;
      *) echo "已跳过数据库初始化" ;;
    esac
  else
    echo "未获得输入，已跳过数据库初始化（如需建库请手动执行 database/init.sql）"
  fi
fi

cd "$project_root/backend"
mvn clean package -DskipTests

cd "$project_root/frontend-admin"
npm run build

cd "$project_root/frontend-portal"
npm run build

echo "构建完成：backend/*/target/*.jar、frontend-admin/dist、frontend-portal/dist"
echo "执行 scripts/start-local.sh 启动本地服务，执行 scripts/smoke-test.sh 验证接口。"
