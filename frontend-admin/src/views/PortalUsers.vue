<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import request from "../api/request";

const rows = ref([]);
const loading = ref(false);
const filters = reactive({ userType: "", creditGrade: "" });
const dialog = reactive({ visible: false, row: null, grade: "C", limit: 0 });
async function load() {
  loading.value = true;
  try {
    const data = await request.get("/api/admin/portal-user/page", {
      params: {
        userType: filters.userType || undefined,
        creditGrade: filters.creditGrade || undefined,
      },
    });
    rows.value = data.list;
  } catch (error) {
    ElMessage.error(error.message || "用户加载失败");
  } finally {
    loading.value = false;
  }
}
function openCredit(row) {
  dialog.row = row;
  dialog.grade = row.creditGrade || "C";
  dialog.limit = 0;
  dialog.visible = true;
}
async function saveCredit() {
  try {
    await request.put(`/api/admin/portal-user/${dialog.row.id}/credit`, {
      creditGrade: dialog.grade,
      creditLimit: dialog.limit,
    });
    dialog.visible = false;
    ElMessage.success("评级已保存");
    await load();
  } catch (error) {
    ElMessage.error(error.message || "保存失败");
  }
}
async function detail(row) {
  const data = await request.get(`/api/admin/portal-user/${row.id}`);
  await ElMessageBox.alert(
    `认证状态：${["未认证", "审核中", "已认证", "已驳回"][data.authStatus]}\n订单量：${data.orderCount}\n完成交易额：${data.completedOrderAmount}`,
    "用户详情",
  );
}
onMounted(load);
</script>

<template>
  <section>
    <div class="page-heading">
      <h2>门户用户</h2>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>
    <el-form inline
      ><el-form-item label="用户类型"
        ><el-select v-model="filters.userType" clearable
          ><el-option label="自然人" value="1" /><el-option
            label="法人"
            value="2" /></el-select></el-form-item
      ><el-form-item label="信用等级"
        ><el-select v-model="filters.creditGrade" clearable
          ><el-option label="A" value="A" /><el-option
            label="B"
            value="B" /><el-option
            label="C"
            value="C" /></el-select></el-form-item
      ><el-button type="primary" @click="load">查询</el-button></el-form
    ><el-table :data="rows" v-loading="loading" stripe
      ><el-table-column
        prop="userName"
        label="用户名称"
        min-width="150"
      /><el-table-column prop="userType" label="类型" width="90"
        ><template #default="scope">{{
          scope.row.userType === 1 ? "自然人" : "法人"
        }}</template></el-table-column
      ><el-table-column
        prop="contactName"
        label="联系人"
        width="120"
      /><el-table-column
        prop="phone"
        label="联系电话"
        width="140"
      /><el-table-column
        prop="creditGrade"
        label="商家等级"
        width="100"
      /><el-table-column prop="authStatus" label="认证状态" width="100"
        ><template #default="scope">{{
          ["未认证", "审核中", "已认证", "已驳回"][scope.row.authStatus]
        }}</template></el-table-column
      ><el-table-column label="操作" width="180"
        ><template #default="scope"
          ><el-button link @click="detail(scope.row)">详情</el-button
          ><el-button type="primary" link @click="openCredit(scope.row)"
            >评级</el-button
          ></template
        ></el-table-column
      ></el-table
    ><el-dialog v-model="dialog.visible" title="用户评级" width="360px"
      ><el-form label-width="90px"
        ><el-form-item label="信用等级"
          ><el-select v-model="dialog.grade"
            ><el-option label="A" value="A" /><el-option
              label="B"
              value="B" /><el-option
              label="C"
              value="C" /></el-select></el-form-item
        ><el-form-item label="授信额度"
          ><el-input-number
            v-model="dialog.limit"
            :min="0"
            :precision="2" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="dialog.visible = false">取消</el-button
        ><el-button type="primary" @click="saveCredit"
          >保存</el-button
        ></template
      ></el-dialog
    >
  </section>
</template>
