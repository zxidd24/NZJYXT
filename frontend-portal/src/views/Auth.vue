<script setup>
import { reactive, ref, onMounted } from "vue";
import { showToast } from "vant";
import request from "../api/request";
const loading = ref(false);
const status = ref(null);
const userType = ref(1);
const files = reactive({
  businessLicenseImg: [],
  idCardFront: [],
  idCardBack: [],
});
const form = reactive({
  realName: "",
  idCard: "",
  businessLicense: "",
  businessLicenseImg: "",
  idCardFront: "",
  idCardBack: "",
  bankCard: "",
  bankName: "",
  phone: "",
});
const apiBase = import.meta.env.VITE_API_BASE_URL || "http://localhost:8081";
async function load() {
  try {
    status.value = await request.get("/api/portal/auth/status");
    const profile = await request.get("/api/portal/profile");
    form.phone = profile.phone;
    userType.value = profile.userType;
  } catch (error) {
    showToast(error.message || "加载失败");
  }
}
async function upload(field, item) {
  item.status = "uploading";
  item.message = "上传中";
  const body = new FormData();
  body.append("file", item.file);
  try {
    const data = await request.post("/api/portal/upload", body);
    form[field] = data.url;
    files[field] = [{ url: apiBase + data.url, isImage: true }];
    item.status = "done";
  } catch (error) {
    item.status = "failed";
    item.message = "上传失败";
    showToast(error.message || "上传失败");
  }
}
async function submit() {
  loading.value = true;
  try {
    await request.post("/api/portal/auth/submit", form);
    showToast("已提交审核");
    await load();
  } catch (error) {
    showToast(error.message || "提交失败");
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>
<template>
  <section>
    <van-nav-bar
      title="实名认证"
      left-arrow
      @click-left="$router.back()"
    /><van-tag v-if="status" type="primary" class="status-tag">{{
      ["未认证", "审核中", "已认证", "已驳回"][status.status]
    }}</van-tag
    ><van-form v-if="status?.status !== 2" @submit="submit"
      ><van-cell-group inset
        ><van-field v-model="form.realName" label="真实姓名" /><van-field
          v-model="form.idCard"
          label="身份证号" /><van-field
          v-if="userType === 2"
          v-model="form.businessLicense"
          label="营业执照号" /><van-field v-if="userType === 2" label="营业执照"
          ><template #input
            ><van-uploader
              v-model="files.businessLicenseImg"
              :max-count="1"
              :after-read="
                (item) => upload('businessLicenseImg', item)
              " /></template></van-field
        ><van-field label="身份证正面"
          ><template #input
            ><van-uploader
              v-model="files.idCardFront"
              :max-count="1"
              :after-read="
                (item) => upload('idCardFront', item)
              " /></template></van-field
        ><van-field label="身份证反面"
          ><template #input
            ><van-uploader
              v-model="files.idCardBack"
              :max-count="1"
              :after-read="
                (item) => upload('idCardBack', item)
              " /></template></van-field
        ><van-field v-model="form.bankCard" label="银行卡号" /><van-field
          v-model="form.bankName"
          label="开户行" /><van-field
          v-model="form.phone"
          label="认证手机号"
          readonly
      /></van-cell-group>
      <div class="form-action">
        <van-button
          block
          type="primary"
          native-type="submit"
          :loading="loading"
          :disabled="status?.status === 1"
          >提交认证</van-button
        >
      </div></van-form
    ><van-cell-group v-else inset
      ><van-cell title="认证结果" value="已认证"
    /></van-cell-group>
  </section>
</template>
