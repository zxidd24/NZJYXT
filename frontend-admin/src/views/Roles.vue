<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import request from "../api/request";
const rows = ref([]);
const permissions = ref([]);
const loading = ref(false);
const dialog = reactive({
  visible: false,
  id: null,
  roleName: "",
  description: "",
  permissionIds: [],
});
async function load() {
  loading.value = true;
  try {
    [rows.value, permissions.value] = await Promise.all([
      request.get("/api/admin/role"),
      request.get("/api/admin/role/permissions"),
    ]);
  } catch (error) {
    ElMessage.error(error.message || "角色加载失败");
  } finally {
    loading.value = false;
  }
}
function edit(row) {
  dialog.id = row.id;
  dialog.roleName = row.roleName;
  dialog.description = row.description;
  dialog.permissionIds = [...row.permissionIds];
  dialog.visible = true;
}
function add() {
  dialog.id = null;
  dialog.roleName = "";
  dialog.description = "";
  dialog.permissionIds = [];
  dialog.visible = true;
}
async function save() {
  try {
    const body = { roleName: dialog.roleName, description: dialog.description };
    const result = dialog.id
      ? { id: dialog.id }
      : await request.post("/api/admin/role", body);
    const id = dialog.id || result.id;
    if (dialog.id) await request.put(`/api/admin/role/${id}`, body);
    await request.put(`/api/admin/role/${id}/permissions`, {
      permissionIds: dialog.permissionIds,
    });
    dialog.visible = false;
    ElMessage.success("已保存");
    await load();
  } catch (error) {
    ElMessage.error(error.message || "保存失败");
  }
}
async function remove(row) {
  try {
    await request.delete(`/api/admin/role/${row.id}`);
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
      <h2>角色管理</h2>
      <el-button type="primary" @click="add">新增角色</el-button>
    </div>
    <el-table :data="rows" v-loading="loading"
      ><el-table-column
        prop="roleName"
        label="角色"
        width="160"
      /><el-table-column prop="description" label="说明" /><el-table-column
        label="操作"
        width="160"
        ><template #default="scope"
          ><el-button link @click="edit(scope.row)">编辑权限</el-button
          ><el-button type="danger" link @click="remove(scope.row)"
            >删除</el-button
          ></template
        ></el-table-column
      ></el-table
    ><el-dialog v-model="dialog.visible" title="角色设置"
      ><el-form label-width="80px"
        ><el-form-item label="名称"
          ><el-input v-model="dialog.roleName" /></el-form-item
        ><el-form-item label="说明"
          ><el-input v-model="dialog.description" /></el-form-item
        ><el-form-item label="权限"
          ><el-checkbox-group v-model="dialog.permissionIds"
            ><el-checkbox
              v-for="item in permissions"
              :key="item.id"
              :label="item.id"
              >{{ item.name }}</el-checkbox
            ></el-checkbox-group
          ></el-form-item
        ></el-form
      ><template #footer
        ><el-button @click="dialog.visible = false">取消</el-button
        ><el-button type="primary" @click="save">保存</el-button></template
      ></el-dialog
    >
  </section>
</template>
