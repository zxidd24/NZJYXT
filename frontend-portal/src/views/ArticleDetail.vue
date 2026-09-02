<script setup>
import { onMounted, ref } from "vue";
import { showToast } from "vant";
import request from "../api/request";
import { useRoute } from "vue-router";
const route = useRoute(); const article = ref(null);
onMounted(async () => { try { article.value = await request.get(`/api/portal/article/${route.params.id}`); } catch (e) { showToast(e.message || "资讯加载失败"); } });
</script>
<template><section><van-nav-bar title="资讯详情" left-arrow @click-left="$router.back()" /><van-skeleton title :row="8" :loading="!article" /><article v-if="article" class="article-detail"><h2>{{ article.title }}</h2><p class="article-meta">{{ article.categoryName || "行业资讯" }} · {{ article.publishTime || "" }} · 浏览 {{ article.viewCount }}</p><div class="article-content">{{ article.content }}</div></article></section></template>
