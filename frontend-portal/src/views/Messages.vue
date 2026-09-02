<script setup>
import { onMounted, ref } from "vue";
import { showToast } from "vant";
import request from "../api/request";
const rows = ref([]); const loading = ref(false);
async function load() { loading.value = true; try { rows.value = (await request.get("/api/portal/message/page", { params: { pageSize: 100 } })).list; } catch (error) { showToast(error.message || "消息加载失败"); } finally { loading.value = false; } }
async function read(row) { if (row.isRead === 1) return; try { await request.put(`/api/portal/message/${row.id}/read`); row.isRead = 1; } catch (error) { showToast(error.message || "操作失败"); } }
async function readAll() { try { await request.put("/api/portal/message/read-all"); rows.value.forEach((row) => { row.isRead = 1; }); } catch (error) { showToast(error.message || "操作失败"); } }
onMounted(load);
</script>
<template><section><van-nav-bar title="消息中心" left-arrow @click-left="$router.back()" right-text="全部已读" @click-right="readAll" /><van-loading v-if="loading" /><van-empty v-else-if="!rows.length" description="暂无消息" /><van-cell-group v-else inset><van-cell v-for="row in rows" :key="row.id" :title="row.title" :label="row.content" :value="row.createdAt || ''" :class="{ unread: row.isRead === 0 }" is-link @click="read(row)" /></van-cell-group></section></template>
<style scoped>.unread :deep(.van-cell__title) { font-weight: 600; }</style>
