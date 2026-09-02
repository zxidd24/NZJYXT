<script setup>
import { computed, onMounted, ref } from "vue";
import { showToast } from "vant";
import request from "../api/request";
const rows = ref([]); const addresses = ref([]); const addressId = ref(null); const loading = ref(false);
const selected = computed(() => rows.value.filter((item) => item.selected === 1));
const total = computed(() => selected.value.reduce((sum, item) => sum + Number(item.price || 0) * item.quantity, 0).toFixed(2));
async function load() { loading.value = true; try { [rows.value, addresses.value] = await Promise.all([request.get("/api/portal/cart/list"), request.get("/api/portal/address/list")]); addressId.value = addresses.value.find((item) => item.isDefault === 1)?.id || addresses.value[0]?.id || null; } catch (e) { showToast(e.message || "加载失败"); } finally { loading.value = false; } }
async function update(item) { try { await request.put("/api/portal/cart", { productId: item.productId, quantity: item.quantity, selected: item.selected }); } catch (e) { showToast(e.message || "更新失败"); await load(); } }
async function checkout() { if (!selected.value.length) return showToast("请选择商品"); if (!addressId.value) return showToast("请先维护收货地址"); try { const token = await request.get("/api/portal/order/token"); const order = await request.post("/api/portal/order/create", { token, addressId: addressId.value, items: selected.value.map((item) => ({ productId: item.productId, quantity: item.quantity })) }); await request.post("/api/portal/order/pay", { orderNo: order.orderNo, payMethod: "WALLET" }); showToast("下单支付成功"); await load(); } catch (e) { showToast(e.message || "下单失败"); } }
onMounted(load);
</script>
<template>
  <section><van-nav-bar title="购物车" /><van-loading v-if="loading" /><van-empty v-else-if="!rows.length" description="购物车为空" /><template v-else><van-cell-group inset><van-cell v-for="item in rows" :key="item.productId"><template #title><van-checkbox v-model="item.selected" :true-value="1" :false-value="0" @change="update(item)">{{ item.productName }}</van-checkbox></template><template #label><span class="product-price">¥{{ item.price }}</span><van-stepper v-model="item.quantity" :min="1" :max="item.stock || 1" integer @change="update(item)" /></template></van-cell></van-cell-group><van-cell v-if="addresses.length" title="收货地址"><template #value><select v-model="addressId" class="address-select"><option v-for="address in addresses" :key="address.id" :value="address.id">{{ address.receiverName }} {{ address.receiverPhone }} {{ address.detailAddress }}</option></select></template></van-cell><van-cell v-else title="收货地址" value="请先维护收货地址" /><van-submit-bar :price="Number(total) * 100" button-text="提交订单并支付" @submit="checkout" /></template></section>
</template>
