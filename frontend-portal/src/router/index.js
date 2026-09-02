import { createRouter, createWebHistory } from "vue-router";
import Login from "../views/Login.vue";
import PortalLayout from "../views/PortalLayout.vue";
import Register from "../views/Register.vue";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/login", component: Login },
    { path: "/register", component: Register },
    { path: "/auth", component: () => import("../views/Auth.vue") },
    { path: "/profile", component: () => import("../views/Profile.vue") },
    { path: "/addresses", component: () => import("../views/Addresses.vue") },
    { path: "/password", component: () => import("../views/Password.vue") },
    { path: "/wallet", component: () => import("../views/Wallet.vue") },
    { path: "/invoice", component: () => import("../views/Invoice.vue") },
    { path: "/loan", component: () => import("../views/Loan.vue") },
    { path: "/article/:id", component: () => import("../views/ArticleDetail.vue") },
    {
      path: "/",
      component: PortalLayout,
      redirect: "/home",
      children: [
        { path: "home", component: () => import("../views/Home.vue") },
        { path: "category", component: () => import("../views/Category.vue") },
        { path: "cart", component: () => import("../views/Cart.vue") },
        { path: "orders", component: () => import("../views/Orders.vue") },
        { path: "mine", component: () => import("../views/Mine.vue") },
        { path: "messages", component: () => import("../views/Messages.vue") },
        { path: "product/:id", component: () => import("../views/ProductDetail.vue") },
      ],
    },
  ],
});

router.beforeEach((to) => {
  const publicPaths = ["/login", "/register"];
  if (!publicPaths.includes(to.path) && !localStorage.getItem("portal_token"))
    return "/login";
  if (publicPaths.includes(to.path) && localStorage.getItem("portal_token"))
    return "/home";
});

export default router;
