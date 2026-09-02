import { createRouter, createWebHistory } from "vue-router";
import Login from "../views/Login.vue";
import Layout from "../views/Layout.vue";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/login", name: "admin-login", component: Login },
    {
      path: "/",
      component: Layout,
      redirect: "/dashboard",
      children: [
        {
          path: "dashboard",
          name: "admin-dashboard",
          component: () => import("../views/Dashboard.vue"),
        },
        {
          path: "tasks",
          name: "admin-tasks",
          component: () => import("../views/Tasks.vue"),
        },
        {
          path: "portal-users",
          name: "admin-portal-users",
          component: () => import("../views/PortalUsers.vue"),
        },
        {
          path: "roles",
          name: "admin-roles",
          component: () => import("../views/Roles.vue"),
        },
        {
          path: "admin-users",
          name: "admin-users",
          component: () => import("../views/AdminUsers.vue"),
        },
        {
          path: "categories",
          name: "admin-categories",
          component: () => import("../views/Categories.vue"),
        },
        {
          path: "products",
          name: "admin-products",
          component: () => import("../views/Products.vue"),
        },
        {
          path: "orders",
          name: "admin-orders",
          component: () => import("../views/Orders.vue"),
        },
        {
          path: "finance",
          name: "admin-finance",
          component: () => import("../views/Finance.vue"),
        },
        {
          path: "articles",
          name: "admin-articles",
          component: () => import("../views/Articles.vue"),
        },
        {
          path: "flows",
          name: "admin-flows",
          component: () => import("../views/Flows.vue"),
        },
        {
          path: "message-configs",
          name: "admin-message-configs",
          component: () => import("../views/MessageConfigs.vue"),
        },
      ],
    },
  ],
});

router.beforeEach((to) => {
  if (to.path !== "/login" && !localStorage.getItem("admin_token"))
    return "/login";
  if (to.path === "/login" && localStorage.getItem("admin_token")) return "/";
});

export default router;
