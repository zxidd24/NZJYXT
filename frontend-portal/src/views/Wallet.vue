<script setup>
import { onMounted, ref } from "vue";
import { showToast } from "vant";
import request from "../api/request";
const wallet = ref({ balance: 0, frozenAmount: 0 }); const rows = ref([]); const amount = ref("");
async function load() { try { wallet.value = await request.get("/api/portal/wallet/info"); rows.value = (await request.get("/api/portal/wallet/transactions")).list; } catch (e) { showToast(e.message || "加载失败"); } }
async function money(path, label) { if (!amount.value) return; try { wallet.value = await request.post(path, { amount: amount.value }); amount.value = ""; showToast(label + "成功"); rows.value = (await request.get("/api/portal/wallet/transactions")).list; } catch (e) { showToast(e.message || label + "失败"); } }
onMounted(load);
</script>
<template><section><van-nav-bar title="我的钱包" left-arrow @click-left="$router.back()" /><van-cell-group inset><van-cell title="可用余额" :value="`¥${wallet.balance}`" /><van-cell title="冻结金额" :value="`¥${wallet.frozenAmount}`" /></van-cell-group><van-cell-group inset><van-field v-model="amount" type="number" label="金额" placeholder="请输入金额" /><van-row gutter="8" class="form-action"><van-col span="12"><van-button block type="primary" @click="money('/api/portal/wallet/deposit', '入金')">入金</van-button></van-col><van-col span="12"><van-button block plain type="primary" @click="money('/api/portal/wallet/withdraw', '出金')">出金</van-button></van-col></van-row></van-cell-group><van-divider>资金流水</van-divider><van-cell-group inset><van-cell v-for="row in rows" :key="row.id" :title="row.remark || row.transNo" :label="row.createdAt"><template #value>{{ row.direction === 1 ? '+' : '-' }}¥{{ row.amount }}</template></van-cell></van-cell-group></section></template>
