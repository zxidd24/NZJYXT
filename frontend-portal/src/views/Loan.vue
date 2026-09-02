<script setup>
import { onMounted, ref } from "vue";
import { showToast } from "vant";
import request from "../api/request";
const info = ref({ creditLimit: 0, used: 0, remaining: 0, records: [] }); const amount = ref("");
async function load() { try { info.value = await request.get("/api/portal/loan/info"); } catch (e) { showToast(e.message || "加载失败"); } }
async function apply() { if (!amount.value) return; try { await request.post("/api/portal/loan/apply", { amount: amount.value }); amount.value = ""; showToast("已提交申请"); await load(); } catch (e) { showToast(e.message || "申请失败"); } }
onMounted(load);
</script>
<template><section><van-nav-bar title="我的贷款" left-arrow @click-left="$router.back()" /><van-cell-group inset><van-cell title="授信额度" :value="`¥${info.creditLimit}`" /><van-cell title="已用额度" :value="`¥${info.used}`" /><van-cell title="剩余额度" :value="`¥${info.remaining}`" /></van-cell-group><van-cell-group inset><van-field v-model="amount" type="number" label="申请金额" /><van-button block type="primary" @click="apply">提交贷款申请</van-button></van-cell-group><van-cell-group inset><van-cell v-for="row in info.records" :key="row.id" :title="row.loanNo" :label="`申请金额 ¥${row.amount}`" :value="['申请中','已放款','已还款','驳回'][row.status]" /></van-cell-group></section></template>
