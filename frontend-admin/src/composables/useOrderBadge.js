import { ref } from "vue";
import request from "../api/request";

// 模块级共享状态，跨组件实例共享（Layout 与 Orders 共用同一份计数）
const pendingOrderCount = ref(0);
let timer = null;

// 需要管理员处理的订单状态：1=待审核、2=待发货、3=待收货
const HANDLED_STATUS = [1, 2, 3];

/** 按待处理状态汇总分页总数，避免只统计第一页订单 */
async function fetchPendingOrderCount() {
  try {
    const pages = await Promise.all(
      HANDLED_STATUS.map((status) =>
        request.get("/api/admin/order/page", {
          params: { pageNum: 1, pageSize: 1, status },
        }),
      ),
    );
    pendingOrderCount.value = pages.reduce(
      (total, page) => total + Number(page?.total ?? 0),
      0,
    );
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
