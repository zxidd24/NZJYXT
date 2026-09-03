<script setup>
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { showToast } from "vant";
import request from "../api/request";
const router = useRouter();
const profile = ref(null);
const unreadCount = ref(0);
onMounted(async () => {
  try {
    profile.value = await request.get("/api/portal/profile");
  } catch (error) {
    showToast(error.message || "加载失败");
  }
  try {
    unreadCount.value = await request.get("/api/portal/message/unread-count");
  } catch (_) {
    unreadCount.value = 0;
  }
});
async function logout() {
  try {
    await request.post("/api/portal/logout");
  } catch (_) {}
  localStorage.removeItem("portal_token");
  router.push("/login");
}
</script>
<template>
  <section>
    <van-nav-bar title="个人中心" /><van-cell-group inset
      ><van-cell to="/messages" is-link
        ><template #title
          ><van-badge :dot="unreadCount > 0"><span>消息中心</span></van-badge></template
        ></van-cell
      ><van-cell title="我的订单" to="/orders" is-link /><van-cell
        title="我的钱包"
        to="/wallet"
        is-link /><van-cell title="发票信息" to="/invoice" is-link /><van-cell title="我的贷款" to="/loan" is-link /><van-cell
        title="认证状态"
        :value="
          profile
            ? ['未认证', '审核中', '已认证', '已驳回'][profile.authStatus]
            : '加载中'
        "
        to="/auth"
        is-link /><van-cell title="基本信息" to="/profile" is-link /><van-cell
        title="收货地址"
        to="/addresses"
        is-link /><van-cell title="修改密码" to="/password" is-link
    /></van-cell-group>
    <div class="form-action">
      <van-button block plain type="primary" @click="logout"
        >退出登录</van-button
      >
    </div>
  </section>
</template>
