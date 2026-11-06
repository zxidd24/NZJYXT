<script setup>
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import request from "../api/request";
import ListPagination from "../components/ListPagination.vue";
import { usePagination } from "../composables/usePagination";

const { pagination, fetchPage } = usePagination();
const rows = ref([]);
const loading = ref(false);
const unreadOnly = ref(false);
const unreadCount = ref(0);

// 与后端 MessageService.bizTypeName 保持一致
const bizTypeNames = { 1: "实名认证", 2: "商品上架", 3: "商品量价修改", 4: "订单", 5: "退款", 6: "贷款" };

const badgeText = computed(() => (unreadCount.value > 0 ? `未读 ${unreadCount.value}` : "已全部阅读"));

async function load() {
  loading.value = true;
  try {
    const [data, count] = await Promise.all([
      fetchPage((params) =>
        request.get("/api/admin/message/page", {
          params: { ...params, unreadOnly: unreadOnly.value ? 1 : undefined },
        }),
      ),
      request.get("/api/admin/message/unread-count"),
    ]);
    rows.value = data.list;
    unreadCount.value = Number(count || 0);
  } catch (error) {
    ElMessage.error(error.message || "消息加载失败");
  } finally {
    loading.value = false;
  }
}

function search() {
  pagination.pageNum = 1;
  return load();
}

async function read(row) {
  if (row.isRead === 1) return;
  try {
    await request.put(`/api/admin/message/${row.id}/read`);
    row.isRead = 1;
    unreadCount.value = Math.max(0, unreadCount.value - 1);
  } catch (error) {
    ElMessage.error(error.message || "标记已读失败");
  }
}

async function readAll() {
  try {
    await request.put("/api/admin/message/read-all");
    ElMessage.success("已全部标记为已读");
    await load();
  } catch (error) {
    ElMessage.error(error.message || "操作失败");
  }
}

onMounted(load);
</script>

<template>
  <section>
    <div class="page-heading">
      <h2>消息中心</h2>
      <div>
        <el-tag :type="unreadCount > 0 ? 'danger' : 'success'" class="badge">{{ badgeText }}</el-tag>
        <el-button @click="readAll">全部已读</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>
    <el-form inline class="filter-bar">
      <el-form-item label="筛选">
        <el-switch v-model="unreadOnly" active-text="仅看未读" @change="search" />
      </el-form-item>
    </el-form>
    <el-table :data="rows" v-loading="loading" stripe>
      <el-table-column label="标题" min-width="180">
        <template #default="s">
          <el-badge :hidden="s.row.isRead === 1" is-dot>
            <span :class="{ unread: s.row.isRead === 0 }">{{ s.row.title }}</span>
          </el-badge>
        </template>
      </el-table-column>
      <el-table-column prop="content" label="内容" min-width="320" show-overflow-tooltip />
      <el-table-column label="关联业务" width="120">
        <template #default="s">{{ bizTypeNames[s.row.bizType] || "-" }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="时间" width="180" />
      <el-table-column label="操作" width="100">
        <template #default="s">
          <el-button v-if="s.row.isRead === 0" link type="primary" @click="read(s.row)">标记已读</el-button>
          <span v-else class="muted">已读</span>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!rows.length && !loading" description="暂无消息" />
    <ListPagination :pagination="pagination" :disabled="loading" @change="load" />
  </section>
</template>

<style scoped>
.badge { margin-right: 10px; }
.unread { font-weight: 600; }
.muted { color: #909399; font-size: 13px; }
</style>
