<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter, useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import request from "../api/request";

const router = useRouter();
const route = useRoute();
const info = ref(null);
const loading = ref(true);
const passwordDialog = reactive({
  visible: false,
  oldPassword: "",
  newPassword: "",
});
const routeMap = {
  "admin:home": [{ path: "/dashboard", label: "首页看板" }],
  "admin:task": [{ path: "/tasks", label: "任务管理" }],
  "admin:product": [{ path: "/products", label: "商品管理" }],
  "admin:order": [{ path: "/orders", label: "订单管理" }],
  "admin:finance": [{ path: "/finance", label: "财务管理" }],
  "admin:article": [{ path: "/articles", label: "资讯管理" }],
  "admin:portal-user": [{ path: "/portal-users", label: "门户用户" }],
  "admin:system": [
    { path: "/categories", label: "商品分类" },
    { path: "/roles", label: "角色管理" },
    { path: "/admin-users", label: "账户管理" },
    { path: "/flows", label: "流程管理" },
    { path: "/message-configs", label: "消息配置" },
  ],
};
const menus = computed(() => {
  return (info.value?.menus || []).flatMap(
    (menu) => routeMap[menu.perms] || [],
  );
});

onMounted(async () => {
  try {
    info.value = await request.get("/api/admin/info");
  } catch (error) {
    ElMessage.error(error.message || "读取用户信息失败");
  } finally {
    loading.value = false;
  }
});

async function logout() {
  try {
    await request.post("/api/admin/logout");
  } catch (_) {
    /* token may already be expired */
  }
  localStorage.removeItem("admin_token");
  router.push("/login");
}
async function changePassword() {
  try {
    await request.put("/api/admin/password", {
      oldPassword: passwordDialog.oldPassword,
      newPassword: passwordDialog.newPassword,
    });
    localStorage.removeItem("admin_token");
    ElMessage.success("密码已修改，请重新登录");
    router.push("/login");
  } catch (error) {
    ElMessage.error(error.message || "修改失败");
  }
}
</script>

<template>
  <el-container class="admin-shell">
    <el-aside width="220px" class="sidebar">
      <div class="brand">农资现货交易系统</div>
      <el-menu :default-active="route.path" router>
        <el-menu-item
          v-for="menu in menus"
          :key="menu.path"
          :index="menu.path"
          >{{ menu.label }}</el-menu-item
        >
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="topbar"
        ><span>{{ info?.realName || "后台管理" }}</span>
        <div>
          <el-button text @click="passwordDialog.visible = true"
            >修改密码</el-button
          ><el-button text :loading="loading" @click="logout"
            >退出登录</el-button
          >
        </div></el-header
      >
      <el-main><RouterView /></el-main>
    </el-container>
    <el-dialog v-model="passwordDialog.visible" title="修改密码" width="380px"
      ><el-form label-width="80px"
        ><el-form-item label="原密码"
          ><el-input
            v-model="passwordDialog.oldPassword"
            type="password"
            show-password /></el-form-item
        ><el-form-item label="新密码"
          ><el-input
            v-model="passwordDialog.newPassword"
            type="password"
            show-password /></el-form-item></el-form
      ><template #footer
        ><el-button @click="passwordDialog.visible = false">取消</el-button
        ><el-button type="primary" @click="changePassword"
          >确认修改</el-button
        ></template
      ></el-dialog
    >
  </el-container>
</template>
