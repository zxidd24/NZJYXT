<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import request from "../api/request";
import ListPagination from "../components/ListPagination.vue";
import { usePagination } from "../composables/usePagination";
const { pagination, fetchPage } = usePagination();
const tab = ref("all"); const rows = ref([]); const categories = ref([]); const loading = ref(false);
const dialog = reactive({ visible: false, id: null, title: "", content: "", categoryId: null, source: "", isPublished: 0, sort: 0 });
async function load() { loading.value = true; try { categories.value = await request.get("/api/admin/article-category"); const data = await fetchPage((params) => request.get(tab.value === "mine" ? "/api/admin/article/mine" : "/api/admin/article/page", { params })); rows.value = data.list; } catch (e) { ElMessage.error(e.message || "资讯加载失败"); } finally { loading.value = false; } }
function open(row = null) { Object.assign(dialog, row ? { visible: true, id: row.id, title: row.title, content: row.content, categoryId: row.categoryId, source: row.source || "", isPublished: row.isPublished, sort: row.sort || 0 } : { visible: true, id: null, title: "", content: "", categoryId: null, source: "", isPublished: 0, sort: 0 }); }
async function save() { try { await request.post("/api/admin/article", { ...dialog }); dialog.visible = false; ElMessage.success("已保存"); await load(); } catch (e) { ElMessage.error(e.message || "保存失败"); } }
async function remove(row) { try { await request.delete(`/api/admin/article/${row.id}`); ElMessage.success("已删除"); await load(); } catch (e) { ElMessage.error(e.message || "删除失败"); } }
// 栏目管理：独立弹窗，支持新增/修改/删除（后端 PUT /article-category/{id} 已具备）。
const categoryDialog = reactive({ visible: false, id: null, name: "", sort: 0 });
function openCategory() { categoryDialog.visible = true; }
function openCategoryEdit(row) { Object.assign(categoryDialog, { id: row.id, name: row.name, sort: row.sort || 0 }); }
function resetCategoryForm() { Object.assign(categoryDialog, { id: null, name: "", sort: 0 }); }
async function saveCategory() {
  if (!categoryDialog.name.trim()) return ElMessage.warning("请填写栏目名称");
  const body = { name: categoryDialog.name.trim(), sort: categoryDialog.sort };
  try {
    if (categoryDialog.id) await request.put(`/api/admin/article-category/${categoryDialog.id}`, body);
    else await request.post("/api/admin/article-category", body);
    ElMessage.success(categoryDialog.id ? "已修改" : "已新增");
    resetCategoryForm();
    await load();
  } catch (e) { ElMessage.error(e.message || "保存失败"); }
}
async function removeCategory(row) {
  try { await request.delete(`/api/admin/article-category/${row.id}`); ElMessage.success("已删除"); resetCategoryForm(); await load(); }
  catch (e) { ElMessage.error(e.message || "删除失败"); }
}
function search() {
  pagination.pageNum = 1;
  return load();
}
onMounted(load);
</script>
<template><section><div class="page-heading"><h2>资讯管理</h2><div><el-button type="primary" @click="open()">发布文章</el-button><el-button @click="openCategory">栏目管理</el-button></div></div><el-tabs v-model="tab" @tab-change="search"><el-tab-pane label="所有文章" name="all" /><el-tab-pane label="我的发布" name="mine" /></el-tabs><el-table :data="rows" v-loading="loading" stripe><el-table-column prop="title" label="标题" min-width="280" /><el-table-column prop="categoryName" label="栏目" width="130" /><el-table-column prop="author" label="发布人" width="120" /><el-table-column label="状态" width="100"><template #default="s">{{ s.row.isPublished === 1 ? "已发布" : "草稿" }}</template></el-table-column><el-table-column prop="viewCount" label="浏览量" width="90" /><el-table-column label="操作" width="150"><template #default="s"><el-button link type="primary" @click="open(s.row)">编辑</el-button><el-button link type="danger" @click="remove(s.row)">删除</el-button></template></el-table-column></el-table>
    <ListPagination :pagination="pagination" :disabled="loading" @change="load" />
    <el-dialog v-model="dialog.visible" title="文章编辑" width="620px"><el-form label-width="80px"><el-form-item label="标题"><el-input v-model="dialog.title" /></el-form-item><el-form-item label="栏目"><el-select v-model="dialog.categoryId" clearable><el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" /></el-select></el-form-item><el-form-item label="来源"><el-input v-model="dialog.source" /></el-form-item><el-form-item label="内容"><el-input v-model="dialog.content" type="textarea" :rows="10" /></el-form-item><el-form-item label="发布"><el-switch v-model="dialog.isPublished" :active-value="1" :inactive-value="0" /></el-form-item></el-form><template #footer><el-button @click="dialog.visible = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template></el-dialog>
    <el-dialog v-model="categoryDialog.visible" title="栏目管理" width="560px">
      <el-form inline>
        <el-form-item label="栏目名称"><el-input v-model="categoryDialog.name" placeholder="请输入栏目名称" style="width: 180px" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="categoryDialog.sort" :min="0" style="width: 110px" /></el-form-item>
        <el-form-item><el-button type="primary" @click="saveCategory">{{ categoryDialog.id ? "保存修改" : "新增栏目" }}</el-button><el-button v-if="categoryDialog.id" @click="resetCategoryForm">取消编辑</el-button></el-form-item>
      </el-form>
      <el-table :data="categories" size="small" stripe>
        <el-table-column prop="name" label="栏目名称" min-width="200" />
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="操作" width="140"><template #default="s"><el-button link type="primary" @click="openCategoryEdit(s.row)">修改</el-button><el-button link type="danger" @click="removeCategory(s.row)">删除</el-button></template></el-table-column>
      </el-table>
      <el-empty v-if="!categories.length" description="暂无栏目" />
    </el-dialog></section></template>
