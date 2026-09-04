<script setup>
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { showToast } from "vant";
import request from "../api/request";

const router = useRouter();
const products = ref([]);
const articles = ref([]);
const categories = ref([]);
const keyword = ref("");
const addingProductId = ref(null);
const quantities = ref({});
const portalBase = import.meta.env.VITE_API_BASE_URL || "http://localhost:8081";
function imageUrl(images) { const url = images?.split(",")[0]; return url ? (url.startsWith("http") ? url : portalBase + url) : ""; }
async function load() {
  try {
    const [recommend, tree, news] = await Promise.all([request.get("/api/portal/product/recommend"), request.get("/api/portal/category/tree"), request.get("/api/portal/article/list", { params: { limit: 5 } })]);
    products.value = recommend;
    articles.value = news;
    categories.value = tree.flatMap((item) => [item, ...(item.children || [])]).slice(0, 8);
  } catch (error) { showToast(error.message || "商品加载失败"); }
}
function search() { router.push({ path: "/category", query: { keyword: keyword.value } }); }
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
onMounted(load);
</script>

<template>
  <section><van-nav-bar title="农资现货商城" /><van-search v-model="keyword" placeholder="搜索商品" @search="search" /><van-cell title="行业资讯" value="更多" is-link /><van-cell v-for="article in articles" :key="article.id" :title="article.title" :label="article.publishTime || ''" is-link @click="router.push(`/article/${article.id}`)" /><van-cell title="商品分类" value="查看全部" is-link to="/category" /><van-grid :column-num="4" :gutter="8" clickable><van-grid-item v-for="item in categories" :key="item.id" :text="item.name" @click="router.push({ path: '/category', query: { categoryId: item.id } })" /></van-grid><van-cell title="推荐商品" /><van-empty v-if="!products.length" description="暂无推荐商品" /><van-card v-for="product in products" :key="product.id" :thumb="imageUrl(product.images)" :price="product.price" :desc="product.subTitle || product.description || '现货供应'" :title="product.name" @click="router.push(`/product/${product.id}`)"><template #footer><div class="product-cart-actions" @click.stop><van-stepper v-model="quantities[product.id]" :min="1" :max="product.stock || 1" integer :disabled="!product.stock" /><van-button size="small" type="primary" :loading="addingProductId === product.id" :disabled="!product.stock" @click="addCart(product)">{{ product.stock ? '加入购物车' : '暂时缺货' }}</van-button></div></template></van-card></section>
</template>
