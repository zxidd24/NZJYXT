<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import request from "../api/request";
import ListPagination from "../components/ListPagination.vue";
import { usePagination } from "../composables/usePagination";
import { useOrderBadge } from "../composables/useOrderBadge";

const { pagination, fetchPage } = usePagination();
const { fetchPendingOrderCount } = useOrderBadge();
const rows = ref([]); const loading = ref(false); const attachmentType = ref(1); const attachments = ref([]); const attachmentDialog = ref(false); const attachmentLoading = ref(false); const attachmentOrder = ref(null); const shipDialog = ref(false); const shipForm = ref({ orderId: null, orderNo: "", receiverName: "", receiverPhone: "", receiverAddress: "", company: "", trackingNo: "", fileUrl: "", fileName: "" }); const shipUploading = ref(false); const statusNames = ["待付款", "待审核", "待发货", "待收货", "已完成", "已取消", "退款中", "已退款"];
const payMethodNames = { WALLET: "钱包", BANK_TRANSFER: "银行转账", CREDIT: "授信" };
const portalApiBase = import.meta.env.VITE_PORTAL_API_BASE_URL || "http://localhost:8081";
// 查询条件：订单号、状态（后端 /api/admin/order/page 已支持 orderNo / status）
const filters = reactive({ orderNo: "", status: undefined });
function buildParams() { const params = {}; if (filters.orderNo && filters.orderNo.trim()) params.orderNo = filters.orderNo.trim(); if (filters.status !== undefined && filters.status !== null && filters.status !== "") params.status = filters.status; return params; }
function search() { pagination.pageNum = 1; load(); }
function resetFilters() { filters.orderNo = ""; filters.status = undefined; search(); }
async function load() { loading.value = true; try { const data = await fetchPage((params) => request.get("/api/admin/order/page", { params: { ...buildParams(), ...params } })); rows.value = data.list; fetchPendingOrderCount();} catch (e) { ElMessage.error(e.message || "订单加载失败"); } finally { loading.value = false; } }
async function confirm(row) { try { await request.put("/api/admin/order/confirm", { orderId: row.id }); ElMessage.success("订单已完成"); await load();fetchPendingOrderCount(); } catch (e) { ElMessage.error(e.message || "确认失败"); } }
// 一期银行转账由财务人工核对流水后确认到账，二期由银行回调替代。
async function confirmPayment(row) {
  try { await request.put("/api/admin/order/confirm-payment", { orderId: row.id }); ElMessage.success("到账已确认，订单进入审核"); await load(); fetchPendingOrderCount(); }
  catch (e) { ElMessage.error(e.message || "确认到账失败"); }
}
// 待确认到账：待付款且已登记银行转账支付方式。
function awaitsPayment(row) { return row.orderStatus === 0 && row.payStatus === 0 && row.payMethod === "BANK_TRANSFER"; }
function openShip(row) { shipForm.value = { orderId: row.id, orderNo: row.orderNo, receiverName: row.receiverName, receiverPhone: row.receiverPhone, receiverAddress: row.receiverAddress, company: "中通", trackingNo: "", fileUrl: "", fileName: "" }; shipDialog.value = true; }
// 签章发货单：先上传拿到 URL，随发货一起提交（delivery 接口本身支持 fileUrl）。
async function uploadShipFile(file) {
  if (!file) return;
  shipUploading.value = true;
  const body = new FormData(); body.append("file", file);
  try {
    const data = await request.post("/api/admin/order-upload", body);
    shipForm.value.fileUrl = data.url;
    shipForm.value.fileName = file.name || data.filename;
    ElMessage.success("签章已上传");
  } catch (e) { ElMessage.error(e.message || "签章上传失败"); }
  finally { shipUploading.value = false; }
}
async function submitShip() { const { orderId, company, trackingNo, fileUrl, fileName } = shipForm.value; if (!company || !trackingNo) return ElMessage.warning("请填写物流公司和物流单号"); try { await request.put("/api/admin/order/delivery", { orderId, company, trackingNo, fileUrl, fileName }); ElMessage.success("已登记发货"); shipDialog.value = false; await load(); fetchPendingOrderCount(); } catch (e) { ElMessage.error(e.message || "发货失败"); } }
async function viewAttachments(row, type = 1) { attachmentOrder.value = row; attachmentType.value = type; attachmentDialog.value = true; attachmentLoading.value = true; try { const data = await request.get(`/api/admin/order/${row.id}/attachments`); attachments.value = (data || []).filter((item) => item.type === type); } catch (e) { attachments.value = []; ElMessage.error(e.message || "附件加载失败"); } finally { attachmentLoading.value = false; } }
// 转账凭证（附件类型 3）：核对到账的依据。
function viewVoucher(row) { viewAttachments(row, 3); }
// 客户盖章回传单（附件类型 1）：后端「确认完成」要求必须已上传，否则必然报错。
// 列表接口已随订单返回 attachments，这里直接判断，避免用户点了才知道不行。
function hasReceipt(row) {
  return (row.attachments || []).some((item) => item.type === 1);
}
function attachmentTitle() { return attachmentType.value === 3 ? "转账凭证" : "盖章回传单"; }
function attachmentUrl(url) { return url && /^https?:\/\//.test(url) ? url : `${portalApiBase}${url || ""}`; }
onMounted(load);
</script>
<template><section><div class="page-heading"><h2>订单管理</h2><el-button :loading="loading" @click="load">刷新</el-button></div><el-form inline class="filter-bar"><el-form-item label="订单号"><el-input v-model="filters.orderNo" placeholder="请输入订单号" clearable style="width: 200px" @keyup.enter="search" /></el-form-item><el-form-item label="状态"><el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 130px"><el-option v-for="(label, value) in statusNames" :key="value" :label="label" :value="value" /></el-select></el-form-item><el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="resetFilters">重置</el-button></el-form-item></el-form><el-table :data="rows" v-loading="loading" stripe><el-table-column prop="orderNo" label="订单号" min-width="180" /><el-table-column label="商品" min-width="220"><template #default="s">{{ s.row.details.map(d => `${d.productName} × ${d.quantity}`).join("、") }}</template>
<style scoped>
.ship-upload { display: flex; align-items: center; gap: 10px; }
.ship-file { color: #67c23a; font-size: 13px; }
</style></el-table-column><el-table-column prop="payAmount" label="金额" width="110" /><el-table-column label="状态" width="100"><template #default="s">{{ statusNames[s.row.orderStatus] }}</template></el-table-column><el-table-column label="收货人" width="120" prop="receiverName" /><el-table-column prop="receiverPhone" label="收货电话" min-width="130" /><el-table-column prop="receiverAddress" label="收货地址" min-width="240" show-overflow-tooltip /><el-table-column label="支付方式" width="110"><template #default="s">{{ payMethodNames[s.row.payMethod] || '-' }}</template></el-table-column><el-table-column label="操作" width="270"><template #default="s"><el-button v-if="s.row.payMethod === 'BANK_TRANSFER'" link type="warning" @click="viewVoucher(s.row)">查看凭证</el-button><el-button v-if="awaitsPayment(s.row)" link type="warning" @click="confirmPayment(s.row)">确认到账</el-button><el-button v-if="[3, 4].includes(s.row.orderStatus)" link type="primary" @click="viewAttachments(s.row)">查看回传单</el-button><el-button v-if="s.row.orderStatus === 2" link type="primary" @click="openShip(s.row)">登记发货</el-button><el-tooltip :disabled="hasReceipt(s.row)" content="等待客户上传盖章回传单" placement="top"><span><el-button v-if="s.row.orderStatus === 3" link type="success" :disabled="!hasReceipt(s.row)" @click="confirm(s.row)">确认完成</el-button></span></el-tooltip></template></el-table-column></el-table>
    <ListPagination :pagination="pagination" :disabled="loading" @change="load" />
    <el-dialog v-model="attachmentDialog" :title="`订单 ${attachmentOrder?.orderNo || ''} 的${attachmentTitle()}`" width="560px"><el-skeleton v-if="attachmentLoading" :rows="3" animated /><el-empty v-else-if="!attachments.length" :description="`暂无${attachmentTitle()}`" /><el-table v-else :data="attachments" size="small"><el-table-column prop="fileName" label="文件名" min-width="260" /><el-table-column prop="createdAt" label="上传时间" width="170" /><el-table-column label="查看" width="90"><template #default="s"><el-link :href="attachmentUrl(s.row.fileUrl)" target="_blank" type="primary">打开</el-link></template></el-table-column></el-table></el-dialog><el-dialog v-model="shipDialog" title="登记发货" width="520px"><el-descriptions :column="1" border size="small"><el-descriptions-item label="订单号">{{ shipForm.orderNo }}</el-descriptions-item><el-descriptions-item label="收货人">{{ shipForm.receiverName }}</el-descriptions-item><el-descriptions-item label="收货电话">{{ shipForm.receiverPhone }}</el-descriptions-item><el-descriptions-item label="收货地址">{{ shipForm.receiverAddress }}</el-descriptions-item></el-descriptions><el-form label-width="80px" style="margin-top:16px"><el-form-item label="物流公司" required><el-input v-model="shipForm.company" placeholder="请输入物流公司" /></el-form-item><el-form-item label="物流单号" required><el-input v-model="shipForm.trackingNo" placeholder="请输入物流单号" /></el-form-item><el-form-item label="签章发货单"><div class="ship-upload"><el-upload :show-file-list="false" accept="image/jpeg,image/png,image/webp" :before-upload="(f) => { uploadShipFile(f); return false; }"><el-button :loading="shipUploading">上传签章</el-button></el-upload><span v-if="shipForm.fileUrl" class="ship-file">{{ shipForm.fileName || '已上传' }}</span></div></el-form-item></el-form><template #footer><el-button @click="shipDialog = false">取消</el-button><el-button type="primary" @click="submitShip">确认发货</el-button></template></el-dialog></section></template>
