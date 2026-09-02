<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import request from '../api/request'

const router = useRouter()
const route = useRoute()
const unread = ref(0)
const tabs = [
  { path: '/home', text: '首页', icon: 'home-o' },
  { path: '/category', text: '分类', icon: 'apps-o' },
  { path: '/cart', text: '购物车', icon: 'cart-o' },
  { path: '/mine', text: '我的', icon: 'user-o' },
]

function changeTab(path) {
  router.push(path)
}
async function loadUnread() {
  try { unread.value = await request.get('/api/portal/message/unread-count') } catch (_) { unread.value = 0 }
}
onMounted(loadUnread)
watch(() => route.path, loadUnread)
</script>

<template>
  <div class="portal-shell">
    <main class="portal-content"><RouterView /></main>
    <van-tabbar :model-value="route.path" @change="changeTab">
      <van-tabbar-item v-for="tab in tabs" :key="tab.path" :name="tab.path" :icon="tab.icon" :badge="tab.path === '/mine' && unread ? unread : ''">{{ tab.text }}</van-tabbar-item>
    </van-tabbar>
  </div>
</template>
