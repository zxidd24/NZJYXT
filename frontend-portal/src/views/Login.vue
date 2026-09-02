<script setup>
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { showToast } from "vant";
import request from "../api/request";

const router = useRouter();
const loading = ref(false);
const form = reactive({ phone: "", password: "" });

async function submit() {
  loading.value = true;
  try {
    const data = await request.post("/api/portal/login", form);
    localStorage.setItem("portal_token", data.token);
    showToast("登录成功");
    router.push("/home");
  } catch (error) {
    showToast(error.message || "登录失败");
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <main class="portal-login">
    <van-nav-bar title="农资现货商城" />
    <van-form @submit="submit">
      <van-cell-group inset>
        <van-field
          v-model="form.phone"
          name="phone"
          label="手机号"
          placeholder="请输入手机号"
          autocomplete="username"
          :rules="[{ required: true, message: '请输入手机号' }]"
        />
        <van-field
          v-model="form.password"
          type="password"
          name="password"
          label="密码"
          placeholder="请输入密码"
          autocomplete="current-password"
          :rules="[{ required: true, message: '请输入密码' }]"
        />
      </van-cell-group>
      <div class="form-action">
        <van-button block type="primary" native-type="submit" :loading="loading"
          >登录</van-button
        >
      </div>
    </van-form>
    <div class="secondary-action">
      <van-button plain block type="primary" to="/register"
        >注册账户</van-button
      >
    </div>
  </main>
</template>
