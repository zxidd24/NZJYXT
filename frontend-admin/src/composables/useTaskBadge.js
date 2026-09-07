import { ref } from "vue";
import request from "../api/request";

// 模块级共享状态，跨组件实例共享（Layout 与 Tasks 共用同一份计数）
const pendingCount = ref(0);
let timer = null;

/** 从后端拉取当前登录管理员的待办任务数量 */
async function fetchPendingCount() {
  try {
    const data = await request.get("/api/admin/task/pending");
    pendingCount.value = Array.isArray(data?.list) ? data.list.length : 0;
  } catch (_) {
    // 静默失败，避免影响页面正常交互
  }
}

/** 启动定时轮询（默认 30 秒），首次立即拉取一次 */
function startPolling(interval = 30000) {
  stopPolling();
  fetchPendingCount();
  timer = setInterval(fetchPendingCount, interval);
}

/** 停止定时轮询 */
function stopPolling() {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
}

export function useTaskBadge() {
  return {
    pendingCount,
    fetchPendingCount,
    startPolling,
    stopPolling,
  };
}
