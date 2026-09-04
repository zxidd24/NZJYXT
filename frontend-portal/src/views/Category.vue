<script setup>
import { onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { showToast } from "vant";
import request from "../api/request";

const route = useRoute();
const router = useRouter();
const tree = ref([]);
const activeCategory = ref(Number(route.query.categoryId) || 0);
const products = ref([]);
const keyword = ref(route.query.keyword || "");
const loading = ref(false);
const addingProductId = ref(null);
const quantities = ref({});
const portalBase = import.meta.env.VITE_API_BASE_URL || "http://localhost:8081";
const imageUrl = (images) => { const url = images?.split(",")[0]; return url ? (url.startsWith("http") ? url : portalBase + url) : ""; };
async function loadTree() { try { tree.value = await request.get("/api/portal/category/tree"); } catch (error) { showToast(error.message || "分类加载失败"); } }
async function loadProducts() { loading.value = true; try { const data = await request.get("/api/portal/product/page", { params: { pageNum: 1, pageSize: 50, categoryId: activeCategory.value || undefined, keyword: keyword.value || undefined } }); products.value = data.list; } catch (error) { showToast(error.message || "商品加载失败"); } finally { loading.value = false; } }
function choose(id) { activeCategory.value = id; loadProducts(); }
function search() { loadProducts(); }
async function addCart(product) {
  if (!product.stock) return showToast("商品库存不足");
  const quantity = quantities.value[product.id] || 1;
  if (quantity > product.stock) return showToast("购买数量超过库存");
  addingProductId.value = product.id;
  try {
    await request.post("/api/portal/cart", { productId: product.id, quantity });
    showToast("已加入购物车");
  } catch (error) {
    showToast(error.message || "加入购物车失败");
  } finally {
    addingProductId.value = null;
  }
}
watch(() => route.query, () => { activeCategory.value = Number(route.query.categoryId) || 0; keyword.value = route.query.keyword || ""; loadProducts(); });
onMounted(async () => { await loadTree(); await loadProducts(); });
</script>

<template>
  <section><van-nav-bar title="商品分类" /><van-search v-model="keyword" placeholder="搜索商品" @search="search" /><div class="category-layout"><van-sidebar v-model="activeCategory" @change="choose"><van-sidebar-item title="全部商品" :name="0" /><van-sidebar-item v-for="item in tree" :key="item.id" :title="item.name" :name="item.id" /></van-sidebar><div class="category-products"><van-empty v-if="!products.length && !loading" description="暂无商品" /><van-card v-for="product in products" :key="product.id" :thumb="imageUrl(product.images)" :price="product.price" :desc="product.subTitle || product.description || '现货供应'" :title="product.name" @click="router.push(`/product/${product.id}`)"><template #footer><div class="product-cart-actions" @click.stop><van-stepper v-model="quantities[product.id]" :min="1" :max="product.stock || 1" integer :disabled="!product.stock" /><van-button size="small" type="primary" :loading="addingProductId === product.id" :disabled="!product.stock" @click="addCart(product)">{{ product.stock ? '加入购物车' : '暂时缺货' }}</van-button></div></template></van-card></div></div></section>
</template>
