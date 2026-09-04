<script setup>
import { onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { showToast } from "vant";
import request from "../api/request";

const route = useRoute();
const product = ref(null);
const quantity = ref(1);
const adding = ref(false);
const portalBase = import.meta.env.VITE_API_BASE_URL || "http://localhost:8081";
const images = () => (product.value?.images || "").split(",").filter(Boolean).map((url) => url.startsWith("http") ? url : portalBase + url);
onMounted(async () => { try { product.value = await request.get(`/api/portal/product/${route.params.id}`); } catch (error) { showToast(error.message || "商品加载失败"); } });
async function addCart() {
  if (!product.value || !product.value.stock) return showToast("商品库存不足");
  if (quantity.value > product.value.stock) return showToast("购买数量超过库存");
  adding.value = true;
  try { await request.post("/api/portal/cart", { productId: product.value.id, quantity: quantity.value }); showToast("已加入购物车"); }
  catch (error) { showToast(error.message || "加入购物车失败"); }
  finally { adding.value = false; }
}
</script>

<template>
  <section class="product-detail-page"><van-nav-bar title="商品详情" left-arrow @click-left="$router.back()" /><van-swipe v-if="product && images().length" :autoplay="3000" class="product-swiper"><van-swipe-item v-for="image in images()" :key="image"><img :src="image" alt="商品图片" /></van-swipe-item></van-swipe><van-empty v-else-if="!product" description="商品不存在或暂未上架" /><template v-if="product"><van-cell-group inset><van-cell :title="product.name" :label="product.subTitle || product.brand || ''" /><van-cell><template #value><span class="product-price">¥{{ product.price }}</span> / {{ product.unit || '件' }}</template></van-cell><van-cell title="库存" :value="`${product.stock} ${product.unit || ''}`" /><van-cell title="购买数量"><template #value><van-stepper v-model="quantity" :min="1" :max="product.stock || 1" integer :disabled="!product.stock || adding" /></template></van-cell><van-cell title="分类" :value="product.categoryName || '-'" /><van-cell title="商品描述" :label="product.description || '暂无描述'" /></van-cell-group><van-action-bar><van-action-bar-icon icon="cart-o" text="购物车" @click="$router.push('/cart')" /><van-action-bar-button type="warning" text="加入购物车" :loading="adding" :disabled="!product.stock" @click="addCart" /><van-action-bar-button type="danger" text="立即购买" :disabled="!product.stock || adding" @click="addCart" /></van-action-bar></template></section>
</template>
