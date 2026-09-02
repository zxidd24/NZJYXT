#!/usr/bin/env bash
set -euo pipefail

admin_api="${ADMIN_API_URL:-http://localhost:8080}"
portal_api="${PORTAL_API_URL:-http://localhost:8081}"
curl_bin="$(command -v curl)"

assert_status() {
  local expected="$1"
  shift
  local actual
  actual="$($curl_bin -sS -o /tmp/nzxhjy-smoke-body -w '%{http_code}' "$@")"
  if [[ "$actual" != "$expected" ]]; then
    echo "失败：HTTP $actual（期望 $expected）：$*" >&2
    cat /tmp/nzxhjy-smoke-body >&2
    exit 1
  fi
}

assert_status 401 "$admin_api/api/admin/info"

admin_login="$($curl_bin -sS -X POST "$admin_api/api/admin/login" -H 'Content-Type: application/json' -d '{"username":"admin","password":"123456"}')"
[[ "$admin_login" == *'"code":0'* ]] || { echo "失败：管理员登录" >&2; echo "$admin_login" >&2; exit 1; }
admin_token="$(printf '%s' "$admin_login" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
[[ -n "$admin_token" ]] || { echo "失败：未返回管理员令牌" >&2; exit 1; }

dashboard="$($curl_bin -sS -H "Authorization: Bearer $admin_token" "$admin_api/api/admin/dashboard")"
[[ "$dashboard" == *'"code":0'* && "$dashboard" == *'"overview"'* ]] || { echo "失败：看板接口" >&2; echo "$dashboard" >&2; exit 1; }

suffix="$(date +%s | cut -c 3-10)"
portal_register="$($curl_bin -sS -X POST "$portal_api/api/portal/register" -H 'Content-Type: application/json' -d "{\"phone\":\"138${suffix}\",\"password\":\"123456\",\"confirmPassword\":\"123456\",\"userType\":1}")"
[[ "$portal_register" == *'"code":0'* ]] || { echo "失败：门户注册" >&2; echo "$portal_register" >&2; exit 1; }

echo "接口冒烟通过：401 鉴权、管理员登录、看板查询、门户注册。"
