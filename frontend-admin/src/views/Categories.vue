<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import request from "../api/request";
const rows = ref([]);
const dialog = reactive({
  visible: false,
  id: null,
  parentId: 0,
  name: "",
  sort: 0,
  status: 1,
});
async function load() {
  try {
    rows.value = await request.get("/api/admin/category");
  } catch (error) {
    ElMessage.error(error.message || "分类加载失败");
  }
}
function add(parentId = 0) {
  Object.assign(dialog, {
    visible: true,
    id: null,
    parentId,
    name: "",
    sort: 0,
    status: 1,
  });
}
function edit(row) {
  Object.assign(dialog, {
    visible: true,
    id: row.id,
    parentId: row.parentId,
    name: row.name,
    sort: row.sort,
    status: row.status,
  });
}
async function save() {
  const body = {
    parentId: dialog.parentId,
    name: dialog.name,
    sort: dialog.sort,
    status: dialog.status,
  };
  try {
    if (dialog.id) await request.put(`/api/admin/category/${dialog.id}`, body);
    else await request.post("/api/admin/category", body);
    dialog.visible = false;
    ElMessage.success("已保存");
    await load();
  } catch (error) {
    ElMessage.error(error.message || "保存失败");
  }
}
async function remove(row) {
  try {
    await request.delete(`/api/admin/category/${row.id}`);
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
      <h2>商品分类</h2>
      <el-button type="primary" @click="add()">新增顶级分类</el-button>
    </div>
    <el-table :data="rows" row-key="id" default-expand-all
      ><el-table-column prop="name" label="分类名称" /><el-table-column
        prop="sort"
        label="排序"
        width="90"
      /><el-table-column prop="status" label="状态" width="90"
        ><template #default="scope">{{
          scope.row.status === 1 ? "启用" : "停用"
        }}</template></el-table-column
      ><el-table-column label="操作" width="220"
        ><template #default="scope"
          ><el-button link @click="edit(scope.row)">编辑</el-button
          ><el-button
            v-if="scope.row.parentId === 0 || scope.row.children?.length === 0"
            type="primary"
            link
            @click="add(scope.row.id)"
            >新增下级</el-button
          ><el-button type="danger" link @click="remove(scope.row)"
            >删除</el-button
          ></template
        ></el-table-column
      ></el-table
    ><el-dialog v-model="dialog.visible" title="分类设置" width="400px"
      ><el-form label-width="80px"
        ><el-form-item label="上级ID"
          ><el-input-number v-model="dialog.parentId" :min="0" /></el-form-item
        ><el-form-item label="名称"
          ><el-input v-model="dialog.name" /></el-form-item
        ><el-form-item label="排序"
          ><el-input-number v-model="dialog.sort" /></el-form-item
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
