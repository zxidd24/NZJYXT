<script setup>
import { onMounted, reactive, ref } from "vue";
import { showToast } from "vant";
import request from "../api/request";
const profile = ref(null);
const editing = ref(false);
const form = reactive({ contactName: "", email: "" });
async function load() {
  try {
    profile.value = await request.get("/api/portal/profile");
    form.contactName = profile.value.contactName || "";
    form.email = profile.value.email || "";
  } catch (error) {
    showToast(error.message || "加载失败");
  }
}
async function save() {
  try {
    await request.put("/api/portal/profile", form);
    showToast("已保存");
    editing.value = false;
    await load();
  } catch (error) {
    showToast(error.message || "保存失败");
  }
}
onMounted(load);
</script>
<template>
  <section>
    <van-nav-bar title="基本信息" left-arrow @click-left="$router.back()"
      ><template #right
        ><van-button
          size="small"
          plain
          type="primary"
          @click="editing = !editing"
          >{{ editing ? "取消" : "编辑" }}</van-button
        ></template
      ></van-nav-bar
    ><van-cell-group v-if="profile" inset
      ><van-cell
        title="用户类型"
        :value="profile.userType === 1 ? '自然人' : '法人'" /><van-cell
        title="手机号"
        :value="profile.phone" /><van-cell
        title="银行卡"
        :value="profile.bankCard || '未提交'" /><van-cell
        title="信用等级"
        :value="profile.creditGrade || '未评级'" /><van-cell
        title="授信额度"
        :value="profile.creditLimit" /><template v-if="editing"
        ><van-field v-model="form.contactName" label="联系人" /><van-field
          v-model="form.email"
          label="邮箱"
        /><van-button block type="primary" @click="save"
          >保存</van-button
        ></template
      ><template v-else
        ><van-cell
          title="联系人"
          :value="profile.contactName || '-'" /><van-cell
          title="邮箱"
          :value="profile.email || '-'" /></template
    ></van-cell-group>
  </section>
</template>
