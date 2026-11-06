<script setup>
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import axios from "axios";
import request from "../api/request";
import ListPagination from "../components/ListPagination.vue";
import { usePagination } from "../composables/usePagination";
const { pagination, fetchPage } = usePagination();

const tab = ref("refund");
const loading = ref(false);
const rows = ref([]);
// 收支账单 / 对账凭据的查询条件
const period = ref("day");
const statementToken = ref(0);
const incomeRows = ref([]);
const statusNames = ["待审核", "待打款", "驳回", "已退款"];
const loanStatusNames = ["申请中", "已放款", "已还款", "驳回"];
const invoiceStatusNames = ["待开票", "已开票", "驳回"];
// 流水类型：1-入金 2-出金 3-支付 4-退款 5-贷款放款 6-贷款还款 7-转账收款
const transTypeNames = { 1: "入金", 2: "出金", 3: "支付", 4: "退款", 5: "贷款放款", 6: "贷款还款", 7: "转账收款" };

async function load() {
  loading.value = true;
  try {
    // 收支账单是汇总数据（非分页），单独请求；对账凭据与账户明细共用分页流水接口。
    if (tab.value === "incomeExpense") {
      incomeRows.value = await request.get("/api/admin/finance/income-expense", { params: { period: period.value } });
      rows.value = [];
      return;
    }
    const endpoints = {
      refund: "/api/admin/refund/page",
      comment: "/api/admin/comment/page",
      invoice: "/api/admin/invoice/page",
      loan: "/api/admin/loan/page",
      account: "/api/admin/finance/account-detail",
      statement: "/api/admin/finance/statement",
    };
    const data = await fetchPage((params) => request.get(endpoints[tab.value], { params }));
    rows.value = data.list;
  } catch (error) { ElMessage.error(error.message || "加载失败"); }
  finally { loading.value = false; }
}
async function auditRefund(row, approved) { const remark = approved ? "" : window.prompt("驳回原因", "资料不完整"); if (!approved && !remark) return; try { await request.post("/api/admin/refund/audit", { id: row.id, approved, remark }); ElMessage.success(approved ? "已同意，请线下打款后登记" : "已驳回"); await load(); } catch (error) { ElMessage.error(error.message || "处理失败"); } }
// 线下退款登记：财务在银行完成打款后回填，此时退款单与订单才置为已退款。
async function registerOfflineRefund(row) {
  const remark = window.prompt("线下退款备注（如流水号）", "线下退款已登记");
  if (remark === null) return;
  try { await request.post(`/api/admin/refund/${row.id}/offline-register`, { remark }); ElMessage.success("已登记为已退款"); await load(); }
  catch (error) { ElMessage.error(error.message || "登记失败"); }
}
async function issue(row) { const invoiceNo = window.prompt("发票号码"); if (!invoiceNo) return; try { await request.post(`/api/admin/invoice/${row.id}/issue`, { invoiceNo }); ElMessage.success("已开票"); await load(); } catch (error) { ElMessage.error(error.message || "开票失败"); } }
async function rejectInvoice(row) { try { await request.post(`/api/admin/invoice/${row.id}/reject`, { remark: window.prompt("驳回原因", "信息有误") || "" }); ElMessage.success("已驳回"); await load(); } catch (error) { ElMessage.error(error.message || "操作失败"); } }
async function auditLoan(row, approved) { const remark = approved ? "" : window.prompt("驳回原因", "授信资料不符合要求"); if (!approved && !remark) return; try { await request.post(`/api/admin/loan/${row.id}/audit`, { approved, remark }); ElMessage.success("处理成功"); await load(); } catch (error) { ElMessage.error(error.message || "处理失败"); } }
async function loanAction(row, action) { try { await request.post(`/api/admin/loan/${row.id}/${action}`); ElMessage.success("操作成功"); await load(); } catch (error) { ElMessage.error(error.message || "操作失败"); } }
// 财务凭证：给某条流水挂上凭证影像链接（后端已有 attachVoucher 接口）。
async function uploadVoucher(row) {
  const url = window.prompt("请输入财务凭证图片地址（如 /uploads/xxx.jpg）", row.voucherUrl || "");
  if (url === null) return;
  try {
    await request.post(`/api/admin/finance/account-detail/${row.id}/voucher`, { voucherUrl: url });
    ElMessage.success("凭证已保存");
    await load();
  } catch (error) { ElMessage.error(error.message || "保存失败"); }
}
// 对账凭据导出：接口返回 Excel 二进制流。
// 不能用封装好的 request（它的响应拦截器会读 result.code，二进制没有该字段会被判为失败），
// 这里直接用原生 axios 取 blob。
async function exportStatement() {
  try {
    const response = await axios.get(
      `${import.meta.env.VITE_API_BASE_URL || "http://localhost:8080"}/api/admin/finance/statement/export`,
      { responseType: "blob", headers: { Authorization: `Bearer ${localStorage.getItem("admin_token") || ""}` } },
    );
    const link = document.createElement("a");
    link.href = URL.createObjectURL(new Blob([response.data]));
    link.download = `finance-statement-${Date.now()}.xlsx`;
    link.click();
    URL.revokeObjectURL(link.href);
    ElMessage.success("已导出");
  } catch (error) { ElMessage.error(error.message || "导出失败"); }
}
function search() {
  pagination.pageNum = 1;
  return load();
}
onMounted(load);
</script>
<template>
  <section>
    <div class="page-heading"><h2>财务管理</h2><div><el-button v-if="tab === 'statement'" @click="exportStatement">导出对账凭据</el-button><el-button :loading="loading" @click="load">刷新</el-button></div></div>
    <el-tabs v-model="tab" @tab-change="search">
      <el-tab-pane label="退款管理" name="refund" /><el-tab-pane label="评价管理" name="comment" /><el-tab-pane label="账户明细" name="account" /><el-tab-pane label="收支账单" name="incomeExpense" /><el-tab-pane label="对账凭据" name="statement" /><el-tab-pane label="发票管理" name="invoice" /><el-tab-pane label="贷款服务" name="loan" />
    </el-tabs>
    <div v-if="tab === 'incomeExpense'" v-loading="loading">
      <div class="filter-bar"><el-radio-group v-model="period" size="small" @change="search">
        <el-radio-button label="day">按日</el-radio-button>
        <el-radio-button label="month">按月</el-radio-button>
      </el-radio-group></div>
      <el-table :data="incomeRows" stripe>
        <el-table-column prop="period" label="统计周期" min-width="140" />
        <el-table-column label="收入" width="150"><template #default="s"><span class="income">+{{ s.row.income }}</span></template>
<style scoped>
.income { color: #67c23a; }
.expense { color: #f56c6c; }
</style></el-table-column>
        <el-table-column label="支出" width="150"><template #default="s"><span class="expense">-{{ s.row.expense }}</span></template></el-table-column>
        <el-table-column label="净额" width="150"><template #default="s">{{ s.row.net }}</template></el-table-column>
      </el-table>
      <el-empty v-if="!incomeRows.length" description="暂无收支数据" />
    </div>
    <el-table v-else :data="rows" v-loading="loading" stripe>
      <template v-if="tab === 'refund'"><el-table-column prop="refundNo" label="退款单号" min-width="170" /><el-table-column prop="productName" label="商品" /><el-table-column prop="orderAmount" label="交易金额" width="110" /><el-table-column prop="refundAmount" label="退款金额" width="110" /><el-table-column prop="reason" label="原因" /><el-table-column label="状态" width="100"><template #default="s">{{ statusNames[s.row.status] }}</template></el-table-column><el-table-column label="操作" width="190"><template #default="s"><el-button v-if="s.row.status === 0" link type="success" @click="auditRefund(s.row, true)">通过</el-button><el-button v-if="s.row.status === 0" link type="danger" @click="auditRefund(s.row, false)">驳回</el-button><el-button v-if="s.row.status === 1" link type="warning" @click="registerOfflineRefund(s.row)">已打款登记</el-button></template></el-table-column></template>
      <template v-else-if="tab === 'comment'"><el-table-column prop="productName" label="商品" /><el-table-column prop="buyerName" label="买家" /><el-table-column prop="score" label="评分" /><el-table-column prop="content" label="评价内容" min-width="280" /></template>
      <template v-else-if="tab === 'account'"><el-table-column prop="transNo" label="流水号" min-width="170" /><el-table-column label="订单号" min-width="190"><template #default="s">{{ s.row.orderNo || '-' }}</template></el-table-column><el-table-column prop="amount" label="金额" width="110" /><el-table-column label="方向" width="80"><template #default="s">{{ s.row.direction === 1 ? '收入' : '支出' }}</template></el-table-column><el-table-column label="类型" width="110"><template #default="s">{{ transTypeNames[s.row.transType] || s.row.transType }}</template></el-table-column><el-table-column label="财务凭证" min-width="150"><template #default="s"><el-link v-if="s.row.voucherUrl" :href="s.row.voucherUrl" target="_blank" type="primary">查看凭证</el-link><el-button v-else link type="primary" @click="uploadVoucher(s.row)">上传凭证</el-button></template></el-table-column><el-table-column prop="createdAt" label="时间" width="170" /></template>
      <template v-else-if="tab === 'statement'"><el-table-column prop="transNo" label="流水号" min-width="170" /><el-table-column label="订单号" min-width="190"><template #default="s">{{ s.row.orderNo || '-' }}</template></el-table-column><el-table-column prop="amount" label="金额" width="110" /><el-table-column label="方向" width="80"><template #default="s">{{ s.row.direction === 1 ? '收入' : '支出' }}</template></el-table-column><el-table-column label="类型" width="110"><template #default="s">{{ transTypeNames[s.row.transType] || s.row.transType }}</template></el-table-column><el-table-column prop="balanceAfter" label="交易后余额" width="130" /><el-table-column prop="createdAt" label="时间" width="170" /></template>
      <template v-else-if="tab === 'invoice'"><el-table-column prop="applyNo" label="申请单号" /><el-table-column label="订单ID" width="90"><template #default="s">{{ s.row.orderId }}</template></el-table-column><el-table-column prop="amount" label="金额" /><el-table-column label="状态"><template #default="s">{{ invoiceStatusNames[s.row.status] }}</template></el-table-column><el-table-column label="操作"><template #default="s"><el-button v-if="s.row.status === 0" link type="success" @click="issue(s.row)">开票</el-button><el-button v-if="s.row.status === 0" link type="danger" @click="rejectInvoice(s.row)">驳回</el-button></template></el-table-column></template>
      <template v-else><el-table-column prop="loanNo" label="贷款编号" /><el-table-column prop="amount" label="金额" /><el-table-column label="状态"><template #default="s">{{ loanStatusNames[s.row.status] }}</template></el-table-column><el-table-column label="操作"><template #default="s"><el-button v-if="s.row.status === 0" link type="success" @click="auditLoan(s.row, true)">审核通过</el-button><el-button v-if="s.row.status === 0" link type="danger" @click="auditLoan(s.row, false)">驳回</el-button><el-button v-if="s.row.status === 0" link @click="loanAction(s.row, 'release')">确认放款</el-button><el-button v-if="s.row.status === 1" link @click="loanAction(s.row, 'repay')">确认还款</el-button></template></el-table-column></template>
    </el-table>
    <ListPagination :pagination="pagination" :disabled="loading" @change="load" />

  </section>
</template>
