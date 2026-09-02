<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import * as echarts from "echarts";
import { ElMessage } from "element-plus";
import request from "../api/request";

const data = ref(null);
const loading = ref(false);
const annualChart = ref(null);
const categoryChart = ref(null);
const trendChart = ref(null);
const priceChart = ref(null);
let charts = [];
async function load() { loading.value = true; try { data.value = await request.get("/api/admin/dashboard"); await nextTick(); renderCharts(); } catch (error) { ElMessage.error(error.message || "看板加载失败"); } finally { loading.value = false; } }
function renderCharts() {
  charts.forEach((chart) => chart.dispose()); charts = []; if (!data.value) return;
  const annual = echarts.init(annualChart.value); annual.setOption({ tooltip: { trigger: "axis" }, legend: { data: ["交易金额", "订单数"] }, xAxis: { type: "category", data: data.value.annualStats.map((item) => `${item.month}月`) }, yAxis: [{ type: "value", name: "金额" }, { type: "value", name: "订单数" }], series: [{ name: "交易金额", type: "bar", data: data.value.annualStats.map((item) => item.amount) }, { name: "订单数", type: "line", yAxisIndex: 1, data: data.value.annualStats.map((item) => item.orderCount) }] });
  const category = echarts.init(categoryChart.value); category.setOption({ tooltip: { trigger: "item", valueFormatter: (value) => `${value} 元` }, series: [{ type: "pie", radius: ["35%", "70%"], data: data.value.categoryStats.map((item) => ({ name: item.categoryName, value: item.amount })) }] });
  const trend = echarts.init(trendChart.value); trend.setOption({ tooltip: { trigger: "axis" }, legend: { data: ["交易金额", "订单数"] }, xAxis: { type: "category", data: data.value.sevenDayStats.map((item) => item.day) }, yAxis: [{ type: "value" }, { type: "value" }], series: [{ name: "交易金额", type: "line", smooth: true, data: data.value.sevenDayStats.map((item) => item.amount) }, { name: "订单数", type: "bar", yAxisIndex: 1, data: data.value.sevenDayStats.map((item) => item.orderCount) }] });
  const top = data.value.topProducts; const price = echarts.init(priceChart.value); price.setOption({ tooltip: { trigger: "axis" }, legend: { data: top.map((item) => item.productName) }, xAxis: { type: "category", data: [...new Set(data.value.priceTrends.map((item) => item.day))] }, yAxis: { type: "value", name: "成交均价" }, series: top.map((product) => ({ name: product.productName, type: "line", connectNulls: true, data: data.value.priceTrends.filter((item) => item.productId === product.productId).map((item) => [item.day, item.averagePrice]) })) }); charts.push(annual, category, trend, price);
}
function resize() { charts.forEach((chart) => chart.resize()); }
onMounted(() => { load(); window.addEventListener("resize", resize); });
onBeforeUnmount(() => { window.removeEventListener("resize", resize); charts.forEach((chart) => chart.dispose()); });
</script>
<template>
  <section v-loading="loading"><div class="page-heading"><h2>首页看板</h2><el-button @click="load">刷新</el-button></div>
    <el-row v-if="data" :gutter="16" class="dashboard-overview"><el-col v-for="item in [['累计交易额', data.overview.totalAmount + ' 元'], ['累计订单数', data.overview.totalOrderCount], ['注册用户数', data.overview.registeredUserCount], ['今日订单数', data.overview.todayOrderCount], ['今日交易额', data.overview.todayAmount + ' 元'], ['待办数量', data.overview.pendingTodoCount]]" :key="item[0]" :span="4"><el-card shadow="never"><div class="metric-label">{{ item[0] }}</div><strong class="metric-value">{{ item[1] }}</strong></el-card></el-col></el-row>
    <el-row v-if="data" :gutter="16"><el-col :span="14"><el-card shadow="never"><template #header>年度项目交易统计（{{ data.year }}）</template><div ref="annualChart" class="chart" /></el-card></el-col><el-col :span="10"><el-card shadow="never"><template #header>交易商品类型分析</template><div ref="categoryChart" class="chart" /></el-card></el-col><el-col :span="14"><el-card shadow="never"><template #header>近7日交易趋势</template><div ref="trendChart" class="chart" /></el-card></el-col><el-col :span="10"><el-card shadow="never"><template #header>重点商品近30日成交均价</template><div ref="priceChart" class="chart" /></el-card></el-col></el-row><el-empty v-else description="暂无看板数据" />
  </section>
</template>
<style scoped>
.dashboard-overview { margin-bottom: 16px; } .dashboard-overview .el-card { margin-bottom: 16px; } .metric-label { color: #909399; font-size: 13px; } .metric-value { display: block; margin-top: 10px; color: #303133; font-size: 20px; } .chart { height: 300px; }
</style>
