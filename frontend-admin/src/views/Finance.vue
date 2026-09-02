<script setup>
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import request from "../api/request";

const tab = ref("refund");
const loading = ref(false);
const rows = ref([]);
const statusNames = ["待审核", "同意", "驳回", "已退款"];
const loanStatusNames = ["申请中", "已放款", "已还款", "驳回"];
const invoiceStatusNames = ["待开票", "已开票", "驳回"];

async function load() {
  loading.value = true;
  try {
    if (tab.value === "refund") rows.value = (await request.get("/api/admin/refund/page")).list;
    if (tab.value === "comment") rows.value = (await request.get("/api/admin/comment/page")).list;
    if (tab.value === "invoice") rows.value = (await request.get("/api/admin/invoice/page")).list;
    if (tab.value === "loan") rows.value = (await request.get("/api/admin/loan/page")).list;
    if (tab.value === "account") rows.value = (await request.get("/api/admin/finance/account-detail")).list;
  } catch (error) { ElMessage.error(error.message || "加载失败"); }
  finally { loading.value = false; }
}
async function auditRefund(row, approved) { const remark = approved ? "" : window.prompt("驳回原因", "资料不完整"); if (!approved && !remark) return; try { await request.post("/api/admin/refund/audit", { id: row.id, approved, remark }); ElMessage.success("处理成功"); await load(); } catch (error) { ElMessage.error(error.message || "处理失败"); } }
async function issue(row) { const invoiceNo = window.prompt("发票号码"); if (!invoiceNo) return; try { await request.post(`/api/admin/invoice/${row.id}/issue`, { invoiceNo }); ElMessage.success("已开票"); await load(); } catch (error) { ElMessage.error(error.message || "开票失败"); } }
async function rejectInvoice(row) { try { await request.post(`/api/admin/invoice/${row.id}/reject`, { remark: window.prompt("驳回原因", "信息有误") || "" }); ElMessage.success("已驳回"); await load(); } catch (error) { ElMessage.error(error.message || "操作失败"); } }
async function auditLoan(row, approved) { const remark = approved ? "" : window.prompt("驳回原因", "授信资料不符合要求"); if (!approved && !remark) return; try { await request.post(`/api/admin/loan/${row.id}/audit`, { approved, remark }); ElMessage.success("处理成功"); await load(); } catch (error) { ElMessage.error(error.message || "处理失败"); } }
async function loanAction(row, action) { try { await request.post(`/api/admin/loan/${row.id}/${action}`); ElMessage.success("操作成功"); await load(); } catch (error) { ElMessage.error(error.message || "操作失败"); } }
onMounted(load);
</script>
<template>
  <section>
    <div class="page-heading"><h2>财务管理</h2><el-button :loading="loading" @click="load">刷新</el-button></div>
    <el-tabs v-model="tab" @tab-change="load">
      <el-tab-pane label="退款管理" name="refund" /><el-tab-pane label="评价管理" name="comment" /><el-tab-pane label="账户明细" name="account" /><el-tab-pane label="发票管理" name="invoice" /><el-tab-pane label="贷款服务" name="loan" />
    </el-tabs>
    <el-table :data="rows" v-loading="loading" stripe>
      <template v-if="tab === 'refund'"><el-table-column prop="refundNo" label="退款单号" min-width="170" /><el-table-column prop="productName" label="商品" /><el-table-column prop="refundAmount" label="退款金额" /><el-table-column prop="reason" label="原因" /><el-table-column label="状态"><template #default="s">{{ statusNames[s.row.status] }}</template></el-table-column><el-table-column label="操作"><template #default="s"><el-button v-if="s.row.status === 0" link type="success" @click="auditRefund(s.row, true)">通过</el-button><el-button v-if="s.row.status === 0" link type="danger" @click="auditRefund(s.row, false)">驳回</el-button></template></el-table-column></template>
      <template v-else-if="tab === 'comment'"><el-table-column prop="productName" label="商品" /><el-table-column prop="buyerName" label="买家" /><el-table-column prop="score" label="评分" /><el-table-column prop="content" label="评价内容" min-width="280" /></template>
      <template v-else-if="tab === 'account'"><el-table-column prop="transNo" label="流水号" /><el-table-column prop="orderId" label="订单" /><el-table-column prop="amount" label="金额" /><el-table-column prop="direction" label="方向" /><el-table-column prop="transType" label="类型" /><el-table-column prop="createdAt" label="时间" /></template>
      <template v-else-if="tab === 'invoice'"><el-table-column prop="applyNo" label="申请单号" /><el-table-column prop="orderId" label="订单" /><el-table-column prop="amount" label="金额" /><el-table-column label="状态"><template #default="s">{{ invoiceStatusNames[s.row.status] }}</template></el-table-column><el-table-column label="操作"><template #default="s"><el-button v-if="s.row.status === 0" link type="success" @click="issue(s.row)">开票</el-button><el-button v-if="s.row.status === 0" link type="danger" @click="rejectInvoice(s.row)">驳回</el-button></template></el-table-column></template>
      <template v-else><el-table-column prop="loanNo" label="贷款编号" /><el-table-column prop="amount" label="金额" /><el-table-column label="状态"><template #default="s">{{ loanStatusNames[s.row.status] }}</template></el-table-column><el-table-column label="操作"><template #default="s"><el-button v-if="s.row.status === 0" link type="success" @click="auditLoan(s.row, true)">审核通过</el-button><el-button v-if="s.row.status === 0" link type="danger" @click="auditLoan(s.row, false)">驳回</el-button><el-button v-if="s.row.status === 0" link @click="loanAction(s.row, 'release')">确认放款</el-button><el-button v-if="s.row.status === 1" link @click="loanAction(s.row, 'repay')">确认还款</el-button></template></el-table-column></template>
    </el-table>
  </section>
</template>
