<script setup>
import { reactive } from "vue";
import { useRouter } from "vue-router";
import { showToast } from "vant";
import request from "../api/request";
const router = useRouter();
const form = reactive({
  oldPassword: "",
  newPassword: "",
  confirmPassword: "",
});
async function submit() {
  if (form.newPassword !== form.confirmPassword)
    return showToast("两次输入的密码不一致");
  try {
    await request.put("/api/portal/password", form);
    localStorage.removeItem("portal_token");
    showToast("密码已修改，请重新登录");
    router.push("/login");
  } catch (error) {
    showToast(error.message || "修改失败");
  }
}
</script>
<template>
  <section>
    <van-nav-bar
      title="修改密码"
      left-arrow
      @click-left="router.back()"
    /><van-form @submit="submit"
      ><van-cell-group inset
        ><van-field
          v-model="form.oldPassword"
          type="password"
          label="原密码" /><van-field
          v-model="form.newPassword"
          type="password"
          label="新密码" /><van-field
          v-model="form.confirmPassword"
          type="password"
          label="确认密码"
      /></van-cell-group>
      <div class="form-action">
        <van-button block type="primary" native-type="submit"
          >确认修改</van-button
        >
      </div></van-form
    >
  </section>
</template>
