<script setup>
import { onMounted, ref } from "vue";
import { showToast } from "vant";
import request from "../api/request";
const rows = ref([]); const title = ref("");
async function load() { try { rows.value = await request.get("/api/portal/invoice-info/list"); } catch (e) { showToast(e.message || "加载失败"); } }
async function add() { if (!title.value) return; try { await request.post("/api/portal/invoice-info", { title: title.value, titleType: 2 }); title.value = ""; showToast("已添加"); await load(); } catch (e) { showToast(e.message || "添加失败"); } }
async function remove(id) { try { await request.delete(`/api/portal/invoice-info/${id}`); await load(); } catch (e) { showToast(e.message || "删除失败"); } }
onMounted(load);
</script>
<template><section><van-nav-bar title="发票信息" left-arrow @click-left="$router.back()" /><van-cell-group inset><van-field v-model="title" label="发票抬头" placeholder="请输入抬头" /><van-button block type="primary" @click="add">添加抬头</van-button></van-cell-group><van-cell-group inset><van-cell v-for="row in rows" :key="row.id" :title="row.title" :label="row.taxNo || '未填写税号'"><template #value><van-button size="small" type="danger" plain @click="remove(row.id)">删除</van-button></template></van-cell></van-cell-group></section></template>
