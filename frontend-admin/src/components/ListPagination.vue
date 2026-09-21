<script setup>
const props = defineProps({
  pagination: { type: Object, required: true },
  disabled: { type: Boolean, default: false },
});
const emit = defineEmits(["change"]);

function changePage(pageNum) {
  if (props.pagination.pageNum === pageNum) return;
  props.pagination.pageNum = pageNum;
  emit("change");
}

function changeSize(pageSize) {
  if (props.pagination.pageSize === pageSize) return;
  props.pagination.pageSize = pageSize;
  props.pagination.pageNum = 1;
  emit("change");
}
</script>

<template>
  <el-pagination
    class="list-pagination"
    background
    :current-page="pagination.pageNum"
    :page-size="pagination.pageSize"
    :page-sizes="[10, 20, 50, 100]"
    :total="pagination.total"
    :disabled="disabled"
    layout="total, sizes, prev, pager, next, jumper"
    @current-change="changePage"
    @size-change="changeSize"
  />
</template>

<style scoped>
.list-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
