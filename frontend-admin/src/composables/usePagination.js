import { reactive } from "vue";

export function usePagination() {
  const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 });

  async function fetchPage(fetcher) {
    const data = await fetcher({ pageNum: pagination.pageNum, pageSize: pagination.pageSize });
    pagination.total = data.total;
    const lastPage = Math.max(1, Math.ceil(data.total / pagination.pageSize));
    // 删除或审核导致末页消失时，回到最后一个有效页。
    if (pagination.pageNum > lastPage) {
      pagination.pageNum = lastPage;
      return fetchPage(fetcher);
    }
    return data;
  }

  return { pagination, fetchPage };
}
