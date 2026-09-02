import axios from "axios";
import router from "../router";

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "http://localhost:8080",
  timeout: 10000,
});

request.interceptors.request.use((config) => {
  const token = localStorage.getItem("admin_token");
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

request.interceptors.response.use(
  (response) => {
    const result = response.data;
    if (result.code === 40100) {
      localStorage.removeItem("admin_token");
      router.push("/login");
      return Promise.reject(new Error(result.message));
    }
    if (result.code !== 0)
      return Promise.reject(new Error(result.message || "请求失败"));
    return result.data;
  },
  (error) => {
    const result = error.response?.data;
    if (result?.code === 40100 || error.response?.status === 401) {
      localStorage.removeItem("admin_token");
      router.push("/login");
      return Promise.reject(new Error(result?.message || "登录已过期"));
    }
    return Promise.reject(
      new Error(result?.message || error.message || "网络请求失败"),
    );
  },
);

export default request;
