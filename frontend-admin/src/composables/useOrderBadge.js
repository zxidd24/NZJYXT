import { ref } from "vue";
import request from "../api/request";

// 模块级共享状态，跨组件实例共享（Layout 与 Orders 共用同一份计数）
const pendingOrderCount = ref(0);
let timer = null;

// 需要管理员处理的订单状态：1=待审核、2=待发货、3=待收货
const HANDLED_STATUS = [1, 2, 3];

/** 从后端拉取订单列表，统计待处理订单数量 */
async function fetchPendingOrderCount() {
  try {
    const data = await request.get("/api/admin/order/page");
    const list = Array.isArray(data?.list) ? data.list : [];
    pendingOrderCount.value = list.filter((o) =>
      HANDLED_STATUS.includes(o.orderStatus),
    ).length;
  } catch (_) {
    // 静默失败，避免影响页面正常交互
  }
}

/** 启动定时轮询（默认 30 秒），首次立即拉取一次 */
function startPolling(interval = 30000) {
  stopPolling();
  fetchPendingOrderCount();
  timer = setInterval(fetchPendingOrderCount, interval);
}

/** 停止定时轮询 */
function stopPolling() {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
}

export function useOrderBadge() {
  return {
    pendingOrderCount,
    fetchPendingOrderCount,
    startPolling,
    stopPolling,
  };
}
