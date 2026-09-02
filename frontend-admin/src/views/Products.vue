<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import request from "../api/request";

const rows = ref([]);
const categories = ref([]);
const loading = ref(false);
const filters = reactive({ categoryId: undefined, status: undefined, name: "" });
const dialog = reactive({ visible: false, id: null, categoryId: null, name: "", subTitle: "", brand: "", price: 0, stock: 0, unit: "", description: "", images: "" });
const priceDialog = reactive({ visible: false, id: null, name: "", price: 0, stock: 0 });
const flatCategories = computed(() => {
  const result = [];
  const walk = (items, prefix = "") => items.forEach((item) => {
    result.push({ id: item.id, name: prefix + item.name });
    walk(item.children || [], prefix + "　");
  });
  walk(categories.value);
  return result;
});
const statusText = { 0: "待审核", 1: "已上架", 2: "已下架", 3: "审核驳回" };

async function load() {
  loading.value = true;
  try {
    const [productData, categoryData] = await Promise.all([
      request.get("/api/admin/product/page", { params: filters }),
      request.get("/api/admin/category"),
    ]);
    rows.value = productData.list;
    categories.value = categoryData;
  } catch (error) { ElMessage.error(error.message || "商品加载失败"); }
  finally { loading.value = false; }
}
function resetForm(product = null) {
  Object.assign(dialog, product ? { visible: true, id: product.id, categoryId: product.categoryId, name: product.name, subTitle: product.subTitle || "", brand: product.brand || "", price: product.price, stock: product.stock, unit: product.unit || "", description: product.description || "", images: product.images || "" } : { visible: true, id: null, categoryId: null, name: "", subTitle: "", brand: "", price: 0, stock: 0, unit: "", description: "", images: "" });
}
async function save() {
  const body = { categoryId: dialog.categoryId, name: dialog.name, subTitle: dialog.subTitle, brand: dialog.brand, price: dialog.price, stock: dialog.stock, unit: dialog.unit, description: dialog.description, images: dialog.images };
  try { if (dialog.id) await request.put(`/api/admin/product/${dialog.id}`, body); else await request.post("/api/admin/product", body); dialog.visible = false; ElMessage.success("已提交审核"); await load(); }
  catch (error) { ElMessage.error(error.message || "保存失败"); }
}
async function remove(row) {
  try { await ElMessageBox.confirm(`确认删除商品“${row.name}”？`, "提示", { type: "warning" }); await request.delete(`/api/admin/product/${row.id}`); ElMessage.success("已删除"); await load(); } catch (error) { if (error !== "cancel" && error !== "close") ElMessage.error(error.message || "删除失败"); }
}
function openPrice(row) { Object.assign(priceDialog, { visible: true, id: row.id, name: row.name, price: row.price, stock: row.stock }); }
async function savePrice() { try { await request.put(`/api/admin/product/${priceDialog.id}/price-stock`, { price: priceDialog.price, stock: priceDialog.stock }); priceDialog.visible = false; ElMessage.success("量价已提交审核"); await load(); } catch (error) { ElMessage.error(error.message || "提交失败"); } }
async function shelf(row) { const status = row.status === 1 ? 2 : 1; try { await request.put(`/api/admin/product/${row.id}/shelf`, { status }); ElMessage.success(status === 1 ? "已上架" : "已下架"); await load(); } catch (error) { ElMessage.error(error.message || "操作失败"); } }
async function recommend(row) { const recommend = row.isRecommend === 1 ? 0 : 1; try { await request.put(`/api/admin/product/${row.id}/recommend`, { recommend, sort: row.sort || 0 }); ElMessage.success(recommend ? "已加入推荐" : "已取消推荐"); await load(); } catch (error) { ElMessage.error(error.message || "操作失败"); } }
onMounted(load);
</script>

<template>
  <section>
    <div class="page-heading"><h2>商品管理</h2><div><el-button @click="load">刷新</el-button><el-button type="primary" @click="resetForm()">新增商品</el-button></div></div>
    <el-form inline class="filter-bar"><el-form-item label="分类"><el-select v-model="filters.categoryId" clearable placeholder="全部分类" style="width: 180px"><el-option v-for="item in flatCategories" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item><el-form-item label="状态"><el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 130px"><el-option v-for="(label, value) in statusText" :key="value" :label="label" :value="Number(value)" /></el-select></el-form-item><el-form-item><el-input v-model="filters.name" placeholder="商品名称" clearable @keyup.enter="load" /></el-form-item><el-form-item><el-button type="primary" @click="load">查询</el-button></el-form-item></el-form>
    <el-table :data="rows" v-loading="loading" stripe><el-table-column prop="name" label="商品名称" min-width="180" /><el-table-column prop="categoryName" label="分类" width="130" /><el-table-column prop="price" label="价格" width="110" /><el-table-column prop="stock" label="库存" width="90" /><el-table-column prop="unit" label="单位" width="80" /><el-table-column label="状态" width="100"><template #default="scope"><el-tag>{{ statusText[scope.row.status] }}</el-tag></template></el-table-column><el-table-column label="推荐" width="80"><template #default="scope">{{ scope.row.isRecommend === 1 ? "是" : "否" }}</template></el-table-column><el-table-column label="操作" min-width="300"><template #default="scope"><el-button link @click="resetForm(scope.row)">编辑</el-button><el-button v-if="scope.row.status === 1 || scope.row.status === 2" link @click="openPrice(scope.row)">改量价</el-button><el-button v-if="scope.row.status === 1 || scope.row.status === 2" link @click="shelf(scope.row)">{{ scope.row.status === 1 ? "下架" : "上架" }}</el-button><el-button v-if="scope.row.status === 1 || scope.row.status === 2" link @click="recommend(scope.row)">{{ scope.row.isRecommend === 1 ? "取消推荐" : "推荐" }}</el-button><el-button type="danger" link @click="remove(scope.row)">删除</el-button></template></el-table-column></el-table>
    <el-dialog v-model="dialog.visible" :title="dialog.id ? '编辑商品' : '新增商品'" width="560px"><el-form label-width="90px"><el-form-item label="分类"><el-select v-model="dialog.categoryId" placeholder="请选择分类" style="width: 100%"><el-option v-for="item in flatCategories" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item><el-form-item label="名称"><el-input v-model="dialog.name" /></el-form-item><el-form-item label="副标题"><el-input v-model="dialog.subTitle" /></el-form-item><el-form-item label="品牌"><el-input v-model="dialog.brand" /></el-form-item><el-form-item label="价格"><el-input-number v-model="dialog.price" :min="0" :precision="2" /></el-form-item><el-form-item label="库存"><el-input-number v-model="dialog.stock" :min="0" /></el-form-item><el-form-item label="单位"><el-input v-model="dialog.unit" placeholder="吨/袋/瓶" /></el-form-item><el-form-item label="图片地址"><el-input v-model="dialog.images" placeholder="多个地址用逗号分隔" /></el-form-item><el-form-item label="描述"><el-input v-model="dialog.description" type="textarea" :rows="3" /></el-form-item></el-form><template #footer><el-button @click="dialog.visible = false">取消</el-button><el-button type="primary" @click="save">提交审核</el-button></template></el-dialog>
    <el-dialog v-model="priceDialog.visible" title="修改商品量价" width="420px"><el-form label-width="80px"><el-form-item label="商品"><span>{{ priceDialog.name }}</span></el-form-item><el-form-item label="价格"><el-input-number v-model="priceDialog.price" :min="0" :precision="2" /></el-form-item><el-form-item label="库存"><el-input-number v-model="priceDialog.stock" :min="0" /></el-form-item></el-form><template #footer><el-button @click="priceDialog.visible = false">取消</el-button><el-button type="primary" @click="savePrice">提交审核</el-button></template></el-dialog>
  </section>
</template>
