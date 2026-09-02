<script setup>
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { showToast } from "vant";
import request from "../api/request";
const router = useRouter();
const loading = ref(false);
const form = reactive({
  phone: "",
  password: "",
  confirmPassword: "",
  userType: 1,
  companyName: "",
});
async function submit() {
  if (form.password !== form.confirmPassword)
    return showToast("两次输入的密码不一致");
  loading.value = true;
  try {
    await request.post("/api/portal/register", form);
    showToast("注册成功，请登录");
    router.push("/login");
  } catch (error) {
    showToast(error.message || "注册失败");
  } finally {
    loading.value = false;
  }
}
</script>
<template>
  <main class="register-page">
    <van-nav-bar
      title="注册账户"
      left-arrow
      @click-left="router.back()"
    /><van-form @submit="submit"
      ><van-cell-group inset
        ><van-field
          v-model="form.phone"
          label="手机号"
          placeholder="请输入手机号"
          autocomplete="username" /><van-field
          v-model="form.password"
          type="password"
          label="密码"
          placeholder="请输入密码"
          autocomplete="new-password" /><van-field
          v-model="form.confirmPassword"
          type="password"
          label="确认密码"
          placeholder="请再次输入密码"
          autocomplete="new-password" /><van-field
          name="userType"
          label="用户类型"
          ><template #input
            ><van-radio-group v-model="form.userType" direction="horizontal"
              ><van-radio :name="1">自然人</van-radio
              ><van-radio :name="2">法人</van-radio></van-radio-group
            ></template
          ></van-field
        ><van-field
          v-if="form.userType === 2"
          v-model="form.companyName"
          label="企业名称"
          placeholder="请输入企业名称"
      /></van-cell-group>
      <div class="form-action">
        <van-button block type="primary" native-type="submit" :loading="loading"
          >注册</van-button
        >
      </div></van-form
    >
  </main>
</template>
