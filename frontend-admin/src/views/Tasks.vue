<script setup>
import { onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import request from "../api/request";
import ListPagination from "../components/ListPagination.vue";
import { usePagination } from "../composables/usePagination";
import { useTaskBadge } from "../composables/useTaskBadge";
const { pagination: pendingPagination, fetchPage: fetchPendingPage } = usePagination();
const { pagination: donePagination, fetchPage: fetchDonePage } = usePagination();
const { fetchPendingCount } = useTaskBadge();
const active = ref("pending");
const pending = ref([]);
const done = ref([]);
const loading = ref(false);
const detailDialog = ref(null);
const portalBase =
  import.meta.env.VITE_PORTAL_BASE_URL || "http://localhost:8081";

// 各业务类型的展示文案
const orderStatusNames = ["待付款", "待审核", "待发货", "待收货", "已完成", "已取消", "退款中", "已退款"];
const payMethodNames = { WALLET: "钱包", BANK_TRANSFER: "银行转账", CREDIT: "授信" };
const refundStatusNames = ["待审核", "已通过", "已驳回", "已退款"];
const loanStatusNames = ["申请中", "已放款", "已还款", "已驳回"];
const auditStatusNames = ["待处理", "通过", "驳回"];

async function load() {
  loading.value = true;
  try {
    const [pendingData, doneData] = await Promise.all([
      fetchPendingPage((params) => request.get("/api/admin/task/pending", { params })),
      fetchDonePage((params) => request.get("/api/admin/task/done", { params })),
    ]);
    pending.value = pendingData.list;
    done.value = doneData.list;
  } catch (error) {
    ElMessage.error(error.message || "任务加载失败");
  } finally {
    loading.value = false;
  }
}
async function audit(row, approved) {
  const remark = approved
    ? ""
    : await ElMessageBox.prompt("请输入驳回原因", "审核实名认证", {
        inputPattern: /\S+/,
        inputErrorMessage: "驳回原因不能为空",
      })
        .then(({ value }) => value)
        .catch(() => null);
  if (!approved && remark === null) return;
  try {
    const endpoint = row.bizType === 1 ? "/api/admin/task/audit-auth" : row.bizType === 4 ? "/api/admin/task/audit-order" : "/api/admin/task/audit-product";
    await request.post(endpoint, {
      recordId: row.id,
      approved,
      remark,
    });
    ElMessage.success("审核完成");
    await load();
    // 审核完成后立即刷新侧边栏待办红点/数字
    fetchPendingCount();
  } catch (error) {
    ElMessage.error(error.message || "审核失败");
  }
}
async function showDetail(row) {
  try {
    detailDialog.value = await request.get(`/api/admin/task/${row.id}`);
  } catch (error) {
    ElMessage.error(error.message || "详情加载失败");
  }
}
function imageUrl(url) {
  return url?.startsWith("http") ? url : portalBase + url;
}
function detailTitle() {
  const t = detailDialog.value?.task;
  if (!t) return "任务详情";
  return {
    1: "实名认证资料",
    4: "订单审核详情",
    5: "退款审核详情",
    6: "贷款审核详情",
  }[t.bizType] || "商品审核详情";
}
onMounted(load);
</script>
<template>
  <section>
    <div class="page-heading">
      <h2>任务管理</h2>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>
    <el-tabs v-model="active">
      <el-tab-pane label="我的待办" name="pending"
        ><el-table :data="pending" v-loading="loading" stripe
          ><el-table-column
            prop="bizTypeName"
            label="业务类型"
            width="140"
          /><el-table-column
            prop="bizSummary"
            label="事项"
            min-width="240"
          /><el-table-column
            prop="nodeName"
            label="审核节点"
            width="140"
          /><el-table-column
            prop="applyTime"
            label="申请时间"
            width="180"
          /><el-table-column label="操作" width="220"
            ><template #default="scope"
              ><el-button link @click="showDetail(scope.row)">查看</el-button
              ><el-button type="success" link @click="audit(scope.row, true)"
                >通过</el-button
              ><el-button type="danger" link @click="audit(scope.row, false)"
                >驳回</el-button
              ></template
            ></el-table-column
          ></el-table
        >
        <ListPagination :pagination="pendingPagination" :disabled="loading" @change="load" />
      </el-tab-pane
      >
      <el-tab-pane label="我的已办" name="done"
        ><el-table :data="done" v-loading="loading" stripe
          ><el-table-column
            prop="bizTypeName"
            label="业务类型"
            width="140"
          /><el-table-column
            prop="bizSummary"
            label="事项"
            min-width="240"
          /><el-table-column prop="status" label="结果" width="100"
            ><template #default="scope">{{
              auditStatusNames[scope.row.status] || (scope.row.status === 1 ? "通过" : "驳回")
            }}</template></el-table-column
          ><el-table-column
            prop="auditTime"
            label="处理时间"
            width="180"
          /><el-table-column label="操作" width="90"
            ><template #default="scope"
              ><el-button link @click="showDetail(scope.row)"
                >查看</el-button
              ></template
            ></el-table-column
          ></el-table
        >
        <ListPagination :pagination="donePagination" :disabled="loading" @change="load" />
      </el-tab-pane
      >
    </el-tabs>
    <el-dialog
      :model-value="Boolean(detailDialog)"
      :title="detailTitle()"
      width="680px"
      @close="detailDialog = null"
      ><template v-if="detailDialog?.task?.bizType === 1"
        ><el-descriptions v-if="detailDialog?.bizDetail" :column="2" border
          ><el-descriptions-item label="姓名">{{
            detailDialog.bizDetail.realName
          }}</el-descriptions-item
          ><el-descriptions-item label="手机号">{{
            detailDialog.bizDetail.phone
          }}</el-descriptions-item
          ><el-descriptions-item label="身份证">{{
            detailDialog.bizDetail.idCard
          }}</el-descriptions-item
          ><el-descriptions-item label="银行卡">{{
            detailDialog.bizDetail.bankCard
          }}</el-descriptions-item
          ><el-descriptions-item label="开户行">{{
            detailDialog.bizDetail.bankName
          }}</el-descriptions-item
          ><el-descriptions-item label="营业执照号">{{
            detailDialog.bizDetail.businessLicense || "-"
          }}</el-descriptions-item
          ><el-descriptions-item label="营业执照"
            ><a
              v-if="detailDialog.bizDetail.businessLicenseImg"
              :href="imageUrl(detailDialog.bizDetail.businessLicenseImg)"
              target="_blank"
              >查看图片</a
            ><span v-else>-</span
            ></el-descriptions-item
          ><el-descriptions-item label="身份证正面"
            ><a
              :href="imageUrl(detailDialog.bizDetail.idCardFront)"
              target="_blank"
              >查看图片</a
            ></el-descriptions-item
          ><el-descriptions-item label="身份证反面"
            ><a
              :href="imageUrl(detailDialog.bizDetail.idCardBack)"
              target="_blank"
              >查看图片</a
            ></el-descriptions-item
          ></el-descriptions
        ><el-empty v-else description="暂无实名认证资料" /></template
      ><template v-else-if="detailDialog?.task?.bizType === 4"
        ><el-descriptions v-if="detailDialog?.bizDetail" :column="2" border
          ><el-descriptions-item label="订单号">{{
            detailDialog.bizDetail.orderNo
          }}</el-descriptions-item
          ><el-descriptions-item label="订单状态">{{
            orderStatusNames[detailDialog.bizDetail.orderStatus] ?? "-"
          }}</el-descriptions-item
          ><el-descriptions-item label="商品总额">{{
            detailDialog.bizDetail.totalAmount ?? "-"
          }}</el-descriptions-item
          ><el-descriptions-item label="实付金额">{{
            detailDialog.bizDetail.payAmount ?? "-"
          }}</el-descriptions-item
          ><el-descriptions-item label="支付方式">{{
            payMethodNames[detailDialog.bizDetail.payMethod] || detailDialog.bizDetail.payMethod || "-"
          }}</el-descriptions-item
          ><el-descriptions-item label="收货人">{{
            detailDialog.bizDetail.receiverName ?? "-"
          }}</el-descriptions-item
          ><el-descriptions-item label="收货电话">{{
            detailDialog.bizDetail.receiverPhone ?? "-"
          }}</el-descriptions-item
          ><el-descriptions-item label="收货地址">{{
            detailDialog.bizDetail.receiverAddress ?? "-"
          }}</el-descriptions-item
          ><el-descriptions-item label="商品明细" :span="2"
            ><el-table :data="detailDialog.bizDetail.details || []" size="small" border
              ><el-table-column prop="productName" label="商品名称" min-width="160" />
              <el-table-column prop="categoryName" label="分类" width="110" />
              <el-table-column label="单价" width="100">
                <template #default="s">{{ s.row.productPrice }}</template>
              </el-table-column>
              <el-table-column label="数量" width="80">
                <template #default="s">{{ s.row.quantity }} {{ s.row.unit || "" }}</template>
              </el-table-column>
              <el-table-column label="小计" width="110">
                <template #default="s">{{ s.row.totalPrice }}</template>
              </el-table-column>
            </el-table></el-descriptions-item
          ></el-descriptions
        ><el-empty v-else description="暂无订单详情" /></template
      ><template v-else-if="detailDialog?.task?.bizType === 5"
        ><el-descriptions v-if="detailDialog?.bizDetail" :column="2" border
          ><el-descriptions-item label="退款单号">{{
            detailDialog.bizDetail.refundNo ?? "-"
          }}</el-descriptions-item
          ><el-descriptions-item label="订单号">{{
            detailDialog.bizDetail.orderId ?? "-"
          }}</el-descriptions-item
          ><el-descriptions-item label="商品">{{
            detailDialog.bizDetail.productName || "-"
          }}</el-descriptions-item
          ><el-descriptions-item label="订单金额">{{
            detailDialog.bizDetail.orderAmount ?? "-"
          }}</el-descriptions-item
          ><el-descriptions-item label="退款金额">{{
            detailDialog.bizDetail.refundAmount ?? "-"
          }}</el-descriptions-item
          ><el-descriptions-item label="退款渠道">{{
            detailDialog.bizDetail.refundChannel || "-"
          }}</el-descriptions-item
          ><el-descriptions-item label="状态">{{
            refundStatusNames[detailDialog.bizDetail.status] ?? "-"
          }}</el-descriptions-item
          ><el-descriptions-item label="申请时间">{{
            detailDialog.bizDetail.applyTime ?? "-"
          }}</el-descriptions-item
          ><el-descriptions-item label="退款原因" :span="2">{{
            detailDialog.bizDetail.reason || "-"
          }}</el-descriptions-item
          ><el-descriptions-item label="审核备注" :span="2">{{
            detailDialog.bizDetail.remark || "-"
          }}</el-descriptions-item
          ></el-descriptions
        ><el-empty v-else description="暂无退款详情" /></template
      ><template v-else-if="detailDialog?.task?.bizType === 6"
        ><el-descriptions v-if="detailDialog?.bizDetail" :column="2" border
          ><el-descriptions-item label="贷款编号">{{
            detailDialog.bizDetail.loanNo ?? "-"
          }}</el-descriptions-item
          ><el-descriptions-item label="状态">{{
            loanStatusNames[detailDialog.bizDetail.status] ?? "-"
          }}</el-descriptions-item
          ><el-descriptions-item label="贷款金额">{{
            detailDialog.bizDetail.amount ?? "-"
          }}</el-descriptions-item
          ><el-descriptions-item label="占用授信额度">{{
            detailDialog.bizDetail.creditLimitUsed ?? "-"
          }}</el-descriptions-item
          ><el-descriptions-item label="申请时间">{{
            detailDialog.bizDetail.applyTime ?? "-"
          }}</el-descriptions-item
          ><el-descriptions-item label="审核时间">{{
            detailDialog.bizDetail.auditTime || "-"
          }}</el-descriptions-item
          ><el-descriptions-item label="放款时间">{{
            detailDialog.bizDetail.releaseTime || "-"
          }}</el-descriptions-item
          ><el-descriptions-item label="还款时间">{{
            detailDialog.bizDetail.repayTime || "-"
          }}</el-descriptions-item
          ><el-descriptions-item label="审核备注" :span="2">{{
            detailDialog.bizDetail.auditRemark || "-"
          }}</el-descriptions-item
          ></el-descriptions
        ><el-empty v-else description="暂无贷款详情" /></template
      ><template v-else
        ><el-descriptions v-if="detailDialog?.bizDetail" :column="2" border
          ><el-descriptions-item label="商品名称">{{ detailDialog.bizDetail.name }}</el-descriptions-item
          ><el-descriptions-item label="分类">{{ detailDialog.bizDetail.categoryName || '-' }}</el-descriptions-item
          ><el-descriptions-item label="价格">{{ detailDialog.bizDetail.price }}</el-descriptions-item
          ><el-descriptions-item label="库存">{{ detailDialog.bizDetail.stock }} {{ detailDialog.bizDetail.unit || '' }}</el-descriptions-item
          ><el-descriptions-item label="状态">{{ ['待审核', '已上架', '已下架', '审核驳回'][detailDialog.bizDetail.status] }}</el-descriptions-item
          ><el-descriptions-item label="描述">{{ detailDialog.bizDetail.description || '-' }}</el-descriptions-item
        ></el-descriptions
        ><el-empty v-else description="暂无详情数据" /></template
      ></el-dialog
    >
  </section>
</template>
