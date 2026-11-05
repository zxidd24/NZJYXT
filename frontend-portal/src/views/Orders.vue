<script setup>
import { onMounted, ref } from "vue";
import { showToast, showConfirmDialog } from "vant";
import request from "../api/request";
const rows = ref([]); const activeTab = ref("all"); const showReceiptUpload = ref(false); const receiptOrder = ref(null); const receiptFiles = ref([]); const receiptUploading = ref(false); const statusNames = ["待付款", "待审核", "待发货", "待收货", "已完成", "已取消", "退款中", "已退款"]; const tabs = [{ label: "待付款", status: 0 }, { label: "待审核", status: 1 }, { label: "待发货", status: 2 }, { label: "待收货", status: 3 }, { label: "已完成", status: 4 }, { label: "退款", status: 6 }];

// 线下转账：订单待付款时展示平台收款账户，买家汇款后上传凭证，由后台核对到账。
const showVoucher = ref(false); const voucherOrder = ref(null); const voucherFiles = ref([]);
const voucherUploading = ref(false); const settlement = ref(null);

async function load(status = null) { try { const data = await request.get("/api/portal/order/page", { params: status == null ? {} : { status } }); rows.value = data.list; } catch (e) { showToast(e.message || "订单加载失败"); } }
function onTabChange(name) { load(name === "all" ? null : name); }
function openReceiptUpload(row) { receiptOrder.value = row; receiptFiles.value = []; showReceiptUpload.value = true; }
async function uploadReceipt(item) {
  if (!receiptOrder.value || receiptUploading.value) return;
  receiptUploading.value = true; item.status = "uploading"; item.message = "上传中";
  const body = new FormData(); body.append("file", item.file);
  try {
    const data = await request.post("/api/portal/upload", body);
    await request.post(`/api/portal/order/${receiptOrder.value.id}/receipt`, { fileUrl: data.url, fileName: item.file?.name || data.filename });
    item.status = "done"; showToast("盖章回传单已上传"); showReceiptUpload.value = false; await load();
  } catch (e) { item.status = "failed"; item.message = "上传失败"; showToast(e.message || "回传单上传失败"); }
  finally { receiptUploading.value = false; }
}
// 打开上传凭证弹窗：同时拉取平台收款账户，方便买家比对后再上传。
async function openVoucher(row) {
  voucherOrder.value = row; voucherFiles.value = []; settlement.value = null;
  showVoucher.value = true;
  try {
    settlement.value = await request.get("/api/portal/order/settlement-account");
  } catch (e) { showToast(e.message || "收款账户加载失败"); }
}
// 上传转账凭证：后端据此把订单转待审核并创建审核任务，钱到账由后台审核确认。
async function uploadVoucher(item) {
  if (!voucherOrder.value || voucherUploading.value) return;
  voucherUploading.value = true; item.status = "uploading"; item.message = "上传中";
  const body = new FormData(); body.append("file", item.file);
  try {
    const data = await request.post("/api/portal/upload", body);
    await request.post(`/api/portal/order/${voucherOrder.value.id}/transfer-voucher`, {
      fileUrl: data.url, fileName: item.file?.name || data.filename,
    });
    item.status = "done";
    showToast("凭证已提交，等待平台核对到账");
    showVoucher.value = false;
    await load();
  } catch (e) { item.status = "failed"; item.message = "上传失败"; showToast(e.message || "凭证上传失败"); }
  finally { voucherUploading.value = false; }
}
async function cancel(row) {
  try {
    await showConfirmDialog({ title: "取消订单", message: `确认取消订单 ${row.orderNo}？` });
  } catch (_) { return; }
  try { await request.put("/api/portal/order/cancel", { orderNo: row.orderNo }); showToast("订单已取消"); await load(); }
  catch (e) { showToast(e.message || "取消失败"); }
}
async function rebuy(row) { try { await request.post(`/api/portal/order/${row.id}/rebuy`); showToast("已加入购物车"); } catch (e) { showToast(e.message || "再次下单失败"); } }
// 一期只支持整单退款：金额固定为订单实付金额，用户不能自行填写。
async function refund(row) {
  try {
    await showConfirmDialog({ title: "申请退款", message: `将按订单实付金额 ¥${row.payAmount} 整单退款，确认提交申请？` });
  } catch (_) { return; }
  try {
    await request.post("/api/portal/refund/apply", { orderId: row.id, amount: row.payAmount, reason: "用户申请退款" });
    showToast("退款申请已提交");
    await load();
  } catch (e) { showToast(e.message || "退款申请失败"); }
}
async function comment(row) { const product = row.details?.[0]; if (!product) return; const score = window.prompt("评分（1-5）", "5"); if (!score) return; try { await request.post("/api/portal/comment/add", { orderId: row.id, productId: product.productId, score: Number(score), content: "交易完成，满意" }); showToast("评价已提交"); } catch (e) { showToast(e.message || "评价失败"); } }
onMounted(() => load());
</script>
<template>
  <section><van-nav-bar title="我的订单" left-arrow @click-left="$router.back()" /><van-tabs v-model:active="activeTab" @change="onTabChange" ><van-tab title="全部" name="all"/><van-tab v-for="tab in tabs" :key="tab.status" :title="tab.label" :name="tab.status" /></van-tabs><van-empty v-if="!rows.length" description="暂无订单" /><van-cell-group v-else inset><van-cell v-for="row in rows" :key="row.id" :title="row.orderNo" :label="row.details.map(d => `${d.productName} × ${d.quantity}`).join('、')"><template #value><span class="product-price">¥{{ row.payAmount }}</span><br />{{ statusNames[row.orderStatus] }}</template><template #extra><van-button v-if="row.orderStatus === 0" size="small" type="primary" @click="openVoucher(row)">上传转账凭证</van-button><van-button v-if="row.orderStatus === 0" size="small" plain @click="cancel(row)">取消订单</van-button><van-button v-if="row.orderStatus !== 0" size="small" @click="rebuy(row)">再次下单</van-button><van-button v-if="row.orderStatus === 3" size="small" type="primary" plain @click="openReceiptUpload(row)">上传盖章回传单</van-button><van-button v-if="[1,2,3].includes(row.orderStatus)" size="small" type="danger" plain @click="refund(row)">退款</van-button><van-button v-if="row.orderStatus === 4" size="small" type="primary" plain @click="comment(row)">评价</van-button></template></van-cell></van-cell-group><van-popup v-model:show="showVoucher" position="bottom" round closeable class="pay-popup"><div class="pay-panel"><h3>上传转账凭证</h3><p class="pay-tip">订单号：{{ voucherOrder?.orderNo }}</p><p class="pay-tip">应付金额：<span class="product-price">¥{{ voucherOrder?.payAmount }}</span></p><van-cell-group v-if="settlement" inset class="settlement"><van-cell title="收款人" :value="settlement.payeeName || '-'" /><van-cell title="开户行" :value="settlement.bankName || '-'" /><van-cell title="账号" :value="settlement.accountNo || '-'" /><van-cell title="转账附言" :value="voucherOrder?.orderNo || '-'" /></van-cell-group><p class="pay-tip">请先通过银行转账把货款汇到上述账户（附言填订单号），再上传转账回单或截图。</p><span class="receipt-upload-tip">支持 JPG、PNG、WEBP，大小不超过 10MB</span><van-uploader v-model="voucherFiles" :max-count="1" accept="image/jpeg,image/png,image/webp" :disabled="voucherUploading" :after-read="uploadVoucher" /><van-loading v-if="voucherUploading" size="24px">上传中</van-loading></div></van-popup><van-popup v-model:show="showReceiptUpload" position="bottom" round closeable class="receipt-upload-popup"><div class="receipt-upload"><h3>上传盖章回传单</h3><p>订单：{{ receiptOrder?.orderNo }}</p><span class="receipt-upload-tip">请上传盖章后的回传单图片，支持 JPG、PNG、WEBP，大小不超过 10MB</span><van-uploader v-model="receiptFiles" :max-count="1" accept="image/jpeg,image/png,image/webp" :disabled="receiptUploading" :after-read="uploadReceipt" /><van-loading v-if="receiptUploading" size="24px">上传中</van-loading></div></van-popup></section>
</template>
<style scoped>
.receipt-upload { padding: 20px 16px 28px; }
.receipt-upload h3 { margin: 0 0 10px; font-size: 18px; }
.receipt-upload p { margin: 0 0 8px; color: #646566; }
.receipt-upload-tip { display: block; margin-bottom: 16px; color: #969799; font-size: 13px; line-height: 20px; }
.receipt-upload .van-loading { margin-top: 12px; }
.pay-panel { padding: 20px 16px 28px; }
.pay-panel h3 { margin: 0 0 10px; font-size: 18px; }
.pay-tip { margin: 0 0 8px; color: #646566; font-size: 13px; }
.settlement { margin-top: 12px; }
.pay-button { margin-top: 20px; }
.pay-panel .receipt-upload-tip { display: block; margin: 12px 0 8px; color: #969799; font-size: 13px; }
</style>
