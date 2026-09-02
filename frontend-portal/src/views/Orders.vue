<script setup>
import { onMounted, ref } from "vue";
import { showToast } from "vant";
import request from "../api/request";
const rows = ref([]); const statusNames = ["待付款", "待审核", "待发货", "待收货", "已完成", "已取消", "退款中", "已退款"]; const tabs = [{ label: "待审核", status: 1 }, { label: "待发货", status: 2 }, { label: "待收货", status: 3 }, { label: "已完成", status: 4 }, { label: "退款", status: 6 }];
async function load(status) { try { const data = await request.get("/api/portal/order/page", { params: status == null ? {} : { status } }); rows.value = data.list; } catch (e) { showToast(e.message || "订单加载失败"); } }
async function rebuy(row) { try { await request.post(`/api/portal/order/${row.id}/rebuy`); showToast("已加入购物车"); } catch (e) { showToast(e.message || "再次下单失败"); } }
async function refund(row) { const amount = window.prompt("退款金额", row.payAmount); if (!amount) return; try { await request.post("/api/portal/refund/apply", { orderId: row.id, amount, reason: "用户申请退款" }); showToast("退款申请已提交"); await load(); } catch (e) { showToast(e.message || "退款申请失败"); } }
async function comment(row) { const product = row.details?.[0]; if (!product) return; const score = window.prompt("评分（1-5）", "5"); if (!score) return; try { await request.post("/api/portal/comment/add", { orderId: row.id, productId: product.productId, score: Number(score), content: "交易完成，满意" }); showToast("评价已提交"); } catch (e) { showToast(e.message || "评价失败"); } }
onMounted(() => load());
</script>
<template>
  <section><van-nav-bar title="我的订单" left-arrow @click-left="$router.back()" /><van-tabs @change="(i) => load(i === 0 ? null : tabs[i - 1].status)"><van-tab title="全部" /><van-tab v-for="tab in tabs" :key="tab.status" :title="tab.label" /></van-tabs><van-empty v-if="!rows.length" description="暂无订单" /><van-cell-group v-else inset><van-cell v-for="row in rows" :key="row.id" :title="row.orderNo" :label="row.details.map(d => `${d.productName} × ${d.quantity}`).join('、')"><template #value><span class="product-price">¥{{ row.payAmount }}</span><br />{{ statusNames[row.orderStatus] }}</template><template #extra><van-button size="small" @click="rebuy(row)">再次下单</van-button><van-button v-if="[1,2,3].includes(row.orderStatus)" size="small" type="danger" plain @click="refund(row)">退款</van-button><van-button v-if="row.orderStatus === 4" size="small" type="primary" plain @click="comment(row)">评价</van-button></template></van-cell></van-cell-group></section>
</template>
