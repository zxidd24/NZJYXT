<script setup>
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import request from "../api/request";
const rows = ref([]); const loading = ref(false); const statusNames = ["待付款", "待审核", "待发货", "待收货", "已完成", "已取消", "退款中", "已退款"];
async function load() { loading.value = true; try { const data = await request.get("/api/admin/order/page"); rows.value = data.list; } catch (e) { ElMessage.error(e.message || "订单加载失败"); } finally { loading.value = false; } }
async function confirm(row) { try { await request.put("/api/admin/order/confirm", { orderId: row.id }); ElMessage.success("订单已完成"); await load(); } catch (e) { ElMessage.error(e.message || "确认失败"); } }
async function ship(row) { const company = window.prompt("物流公司", "中通"); const trackingNo = window.prompt("物流单号"); if (!company || !trackingNo) return; try { await request.put("/api/admin/order/delivery", { orderId: row.id, company, trackingNo }); ElMessage.success("已登记发货"); await load(); } catch (e) { ElMessage.error(e.message || "发货失败"); } }
onMounted(load);
</script>
<template><section><div class="page-heading"><h2>订单管理</h2><el-button :loading="loading" @click="load">刷新</el-button></div><el-table :data="rows" v-loading="loading" stripe><el-table-column prop="orderNo" label="订单号" min-width="180" /><el-table-column label="商品" min-width="220"><template #default="s">{{ s.row.details.map(d => `${d.productName} × ${d.quantity}`).join("、") }}</template></el-table-column><el-table-column prop="payAmount" label="金额" width="110" /><el-table-column label="状态" width="100"><template #default="s">{{ statusNames[s.row.orderStatus] }}</template></el-table-column><el-table-column label="收货人" width="120" prop="receiverName" /><el-table-column label="操作" width="180"><template #default="s"><el-button v-if="s.row.orderStatus === 2" link type="primary" @click="ship(s.row)">登记发货</el-button><el-button v-if="s.row.orderStatus === 3" link type="success" @click="confirm(s.row)">确认完成</el-button></template></el-table-column></el-table></section></template>
