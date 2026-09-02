<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import request from "../api/request";
const rows = ref([]);
const roles = ref([]);
const dialog = reactive({
  visible: false,
  id: null,
  username: "",
  password: "",
  realName: "",
  phone: "",
  email: "",
  status: 1,
  roleIds: [],
});
async function load() {
  try {
    const [page, roleList] = await Promise.all([
      request.get("/api/admin/user"),
      request.get("/api/admin/role"),
    ]);
    rows.value = page.list;
    roles.value = roleList;
  } catch (error) {
    ElMessage.error(error.message || "账户加载失败");
  }
}
function add() {
  Object.assign(dialog, {
    visible: true,
    id: null,
    username: "",
    password: "",
    realName: "",
    phone: "",
    email: "",
    status: 1,
    roleIds: [],
  });
}
function edit(row) {
  Object.assign(dialog, {
    visible: true,
    id: row.id,
    username: row.username,
    password: "",
    realName: row.realName,
    phone: row.phone,
    email: row.email,
    status: row.status,
    roleIds: [...row.roleIds],
  });
}
async function save() {
  const body = {
    username: dialog.username,
    password: dialog.password || (dialog.id ? undefined : ""),
    realName: dialog.realName,
    phone: dialog.phone,
    email: dialog.email,
    status: dialog.status,
    roleIds: dialog.roleIds,
  };
  try {
    if (dialog.id) await request.put(`/api/admin/user/${dialog.id}`, body);
    else await request.post("/api/admin/user", body);
    dialog.visible = false;
    ElMessage.success("已保存");
    await load();
  } catch (error) {
    ElMessage.error(error.message || "保存失败");
  }
}
async function remove(row) {
  try {
    await request.delete(`/api/admin/user/${row.id}`);
    ElMessage.success("已删除");
    await load();
  } catch (error) {
    ElMessage.error(error.message || "删除失败");
  }
}
onMounted(load);
</script>

<template>
  <section>
    <div class="page-heading">
      <h2>后台账户</h2>
      <el-button type="primary" @click="add">新增账户</el-button>
    </div>
    <el-table :data="rows"
      ><el-table-column prop="username" label="账号" /><el-table-column
        prop="realName"
        label="姓名"
      /><el-table-column prop="roleNames" label="角色"
        ><template #default="scope">{{
          scope.row.roleNames.join("、")
        }}</template></el-table-column
      ><el-table-column prop="status" label="状态"
        ><template #default="scope">{{
          scope.row.status === 1 ? "启用" : "禁用"
        }}</template></el-table-column
      ><el-table-column label="操作" width="150"
        ><template #default="scope"
          ><el-button link @click="edit(scope.row)">编辑</el-button
          ><el-button type="danger" link @click="remove(scope.row)"
            >删除</el-button
          ></template
        ></el-table-column
      ></el-table
    ><el-dialog v-model="dialog.visible" title="账户设置" width="480px"
      ><el-form label-width="90px"
        ><el-form-item label="登录账号"
          ><el-input v-model="dialog.username" /></el-form-item
        ><el-form-item :label="dialog.id ? '重置密码' : '初始密码'"
          ><el-input
            v-model="dialog.password"
            type="password"
            show-password /></el-form-item
        ><el-form-item label="姓名"
          ><el-input v-model="dialog.realName" /></el-form-item
        ><el-form-item label="手机"
          ><el-input v-model="dialog.phone" /></el-form-item
        ><el-form-item label="邮箱"
          ><el-input v-model="dialog.email" /></el-form-item
        ><el-form-item label="角色"
          ><el-select v-model="dialog.roleIds" multiple
            ><el-option
              v-for="role in roles"
              :key="role.id"
              :label="role.roleName"
              :value="role.id" /></el-select></el-form-item
        ><el-form-item label="状态"
          ><el-switch
            v-model="dialog.status"
            :active-value="1"
            :inactive-value="0" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="dialog.visible = false">取消</el-button
        ><el-button type="primary" @click="save">保存</el-button></template
      ></el-dialog
    >
  </section>
</template>
