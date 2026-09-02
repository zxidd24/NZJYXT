<script setup>
import { onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import request from "../api/request";

const active = ref("pending");
const pending = ref([]);
const done = ref([]);
const loading = ref(false);
const detailDialog = ref(null);
const portalBase =
  import.meta.env.VITE_PORTAL_BASE_URL || "http://localhost:8081";

async function load() {
  loading.value = true;
  try {
    const [pendingData, doneData] = await Promise.all([
      request.get("/api/admin/task/pending"),
      request.get("/api/admin/task/done"),
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
        ></el-tab-pane
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
              scope.row.status === 1 ? "通过" : "驳回"
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
        ></el-tab-pane
      >
    </el-tabs>
    <el-dialog
      :model-value="Boolean(detailDialog)"
      :title="detailDialog?.task?.bizType === 1 ? '实名认证资料' : detailDialog?.task?.bizType === 4 ? '订单审核详情' : '商品审核详情'"
      width="640px"
      @close="detailDialog = null"
      ><el-descriptions v-if="detailDialog?.task?.bizType === 1 && detailDialog?.bizDetail" :column="2" border
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
      ><el-descriptions v-else-if="detailDialog?.bizDetail" :column="2" border
        ><el-descriptions-item label="商品名称">{{ detailDialog.bizDetail.name }}</el-descriptions-item
        ><el-descriptions-item label="分类">{{ detailDialog.bizDetail.categoryName || '-' }}</el-descriptions-item
        ><el-descriptions-item label="价格">{{ detailDialog.bizDetail.price }}</el-descriptions-item
        ><el-descriptions-item label="库存">{{ detailDialog.bizDetail.stock }} {{ detailDialog.bizDetail.unit || '' }}</el-descriptions-item
        ><el-descriptions-item label="状态">{{ ['待审核', '已上架', '已下架', '审核驳回'][detailDialog.bizDetail.status] }}</el-descriptions-item
        ><el-descriptions-item label="描述">{{ detailDialog.bizDetail.description || '-' }}</el-descriptions-item
      ></el-descriptions
      ></el-dialog
    >
  </section>
</template>
