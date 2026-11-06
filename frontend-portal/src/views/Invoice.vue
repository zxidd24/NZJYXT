<script setup>
import { onMounted, reactive, ref } from "vue";
import { showToast, showConfirmDialog } from "vant";
import request from "../api/request";

const rows = ref([]);
const applyRows = ref([]);
const orders = ref([]);
const loading = ref(false);

// 抬头表单：id 为空表示新增，否则为编辑
const dialog = reactive({ show: false, id: null, title: "", taxNo: "", titleType: 2, isDefault: 0 });
// 开票申请表单
const applyDialog = reactive({ show: false, orderId: null, invoiceInfoId: null, amount: "" });

const statusNames = ["待开票", "已开票", "驳回"];

async function load() {
  loading.value = true;
  try {
    const [infos, applies] = await Promise.all([
      request.get("/api/portal/invoice-info/list"),
      request.get("/api/portal/invoice/list", { params: { pageSize: 50 } }),
    ]);
    rows.value = infos;
    applyRows.value = applies.list;
  } catch (e) { showToast(e.message || "加载失败"); }
  finally { loading.value = false; }
}

function openCreate() {
  Object.assign(dialog, { show: true, id: null, title: "", taxNo: "", titleType: 2, isDefault: 0 });
}
function openEdit(row) {
  Object.assign(dialog, { show: true, id: row.id, title: row.title, taxNo: row.taxNo || "", titleType: row.titleType ?? 2, isDefault: row.isDefault ?? 0 });
}
async function save() {
  if (!dialog.title.trim()) return showToast("请填写发票抬头");
  // isDefault 一并回传，避免编辑时把默认标记清掉。
  const body = { title: dialog.title, taxNo: dialog.taxNo, titleType: dialog.titleType, isDefault: dialog.isDefault };
  try {
    if (dialog.id) await request.put(`/api/portal/invoice-info/${dialog.id}`, body);
    else await request.post("/api/portal/invoice-info", body);
    showToast(dialog.id ? "已修改" : "已添加");
    dialog.show = false;
    await load();
  } catch (e) { showToast(e.message || "保存失败"); }
}
async function remove(row) {
  try { await showConfirmDialog({ title: "删除抬头", message: `确认删除「${row.title}」？` }); }
  catch (_) { return; }
  try { await request.delete(`/api/portal/invoice-info/${row.id}`); showToast("已删除"); await load(); }
  catch (e) { showToast(e.message || "删除失败"); }
}

// 打开开票申请：拉取已支付订单列表供选择
async function openApply() {
  if (!rows.value.length) return showToast("请先添加发票抬头");
  try {
    const data = await request.get("/api/portal/order/page", { params: { pageSize: 100 } });
    // 只有已支付订单可以开票
    orders.value = (data.list || []).filter((o) => o.payStatus === 1);
  } catch (e) { showToast(e.message || "订单加载失败"); return; }
  if (!orders.value.length) return showToast("暂无可开票的已支付订单");
  Object.assign(applyDialog, {
    show: true,
    orderId: orders.value[0].id,
    invoiceInfoId: rows.value[0].id,
    amount: orders.value[0].payAmount,
  });
}
// 切换订单时同步金额（开票金额默认等于订单实付）
function onOrderChange(id) {
  const order = orders.value.find((o) => o.id === id);
  if (order) applyDialog.amount = order.payAmount;
}
async function submitApply() {
  if (!applyDialog.orderId || !applyDialog.invoiceInfoId || !applyDialog.amount) {
    return showToast("请完整填写开票信息");
  }
  try {
    await request.post("/api/portal/invoice/apply", {
      orderId: applyDialog.orderId,
      invoiceInfoId: applyDialog.invoiceInfoId,
      amount: applyDialog.amount,
    });
    showToast("开票申请已提交");
    applyDialog.show = false;
    await load();
  } catch (e) { showToast(e.message || "申请失败"); }
}

onMounted(load);
</script>

<template>
  <section>
    <van-nav-bar title="发票信息" left-arrow @click-left="$router.back()" />
    <van-cell-group inset>
      <van-cell title="我的发票抬头" />
      <van-button block plain type="primary" class="add-btn" @click="openCreate">添加抬头</van-button>
      <van-button block type="primary" class="apply-btn" @click="openApply">申请开票</van-button>
    </van-cell-group>

    <van-cell-group inset>
      <van-cell v-for="row in rows" :key="row.id" :title="row.title" :label="row.taxNo || '未填写税号'">
        <template #value>
          <van-button size="small" plain type="primary" class="row-btn" @click="openEdit(row)">修改</van-button>
          <van-button size="small" plain type="danger" @click="remove(row)">删除</van-button>
        </template>
      </van-cell>
    </van-cell-group>
    <van-empty v-if="!rows.length" description="暂无发票抬头" />

    <van-divider>开票记录</van-divider>
    <van-cell-group inset>
      <van-cell v-for="row in applyRows" :key="row.id" :title="row.applyNo" :label="`金额 ¥${row.amount} · ${row.applyTime || ''}`">
        <template #value>
          <van-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : 'primary'">{{ statusNames[row.status] }}</van-tag>
        </template>
      </van-cell>
    </van-cell-group>
    <van-empty v-if="!applyRows.length" description="暂无开票记录" />

    <van-popup v-model:show="dialog.show" position="bottom" round closeable>
      <div class="panel">
        <h3>{{ dialog.id ? "修改抬头" : "添加抬头" }}</h3>
        <van-field v-model="dialog.title" label="发票抬头" placeholder="请输入抬头" />
        <van-field v-model="dialog.taxNo" label="税号" placeholder="请输入税号（选填）" />
        <van-button block type="primary" class="add-btn" @click="save">保存</van-button>
      </div>
    </van-popup>

    <van-popup v-model:show="applyDialog.show" position="bottom" round closeable>
      <div class="panel">
        <h3>申请开票</h3>
        <van-field label="选择订单">
          <template #input>
            <select v-model="applyDialog.orderId" class="select" @change="onOrderChange(applyDialog.orderId)">
              <option v-for="o in orders" :key="o.id" :value="o.id">{{ o.orderNo }} · ¥{{ o.payAmount }}</option>
            </select>
          </template>
        </van-field>
        <van-field label="发票抬头">
          <template #input>
            <select v-model="applyDialog.invoiceInfoId" class="select">
              <option v-for="i in rows" :key="i.id" :value="i.id">{{ i.title }}</option>
            </select>
          </template>
        </van-field>
        <van-field v-model="applyDialog.amount" type="number" label="开票金额" placeholder="默认订单实付金额" />
        <van-button block type="primary" class="add-btn" @click="submitApply">提交申请</van-button>
      </div>
    </van-popup>
  </section>
</template>

<style scoped>
.panel { padding: 20px 16px 28px; }
.panel h3 { margin: 0 0 16px; font-size: 18px; }
.add-btn { margin-top: 16px; }
.apply-btn { margin-top: 10px; }
.row-btn { margin-right: 8px; }
.select { width: 100%; padding: 6px; border: 1px solid #dcdee0; border-radius: 4px; }
</style>
