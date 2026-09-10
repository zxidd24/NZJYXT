<script setup>
import { onMounted, ref } from "vue";
import { showToast } from "vant";
import { areaList } from "@vant/area-data";
import request from "../api/request";
const rows = ref([]);
const show = ref(false);
const showAreaPicker = ref(false);
const areaText = ref("");
const editingId = ref(null);
const form = ref({
  receiverName: "",
  receiverPhone: "",
  province: "",
  city: "",
  district: "",
  detailAddress: "",
  isDefault: 0,
});
async function load() {
  try {
    rows.value = await request.get("/api/portal/address/list");
  } catch (error) {
    showToast(error.message || "加载失败");
  }
}
function open(row = null) {
  editingId.value = row?.id || null;
  form.value = row
    ? { ...row }
    : {
        receiverName: "",
        receiverPhone: "",
        province: "",
        city: "",
        district: "",
        detailAddress: "",
        isDefault: 0,
      };
  areaText.value = row
    ? [row.province, row.city, row.district].filter(Boolean).join(" ")
    : "";
  show.value = true;
}
async function save() {
  try {
    if (!form.value.province || !form.value.city || !form.value.district) {
      showToast("请选择所在地区");
      return;
    }
    if (!form.value.receiverName.trim()) {
      showToast("请填写收货人");
      return;
    }
    if (!form.value.receiverPhone.trim()) {
      showToast("请填写电话");
      return;
    }
    if (!form.value.detailAddress.trim()) {
      showToast("请填写详细地址");
      return;
    }
    if (editingId.value)
      await request.put(`/api/portal/address/${editingId.value}`, form.value);
    else await request.post("/api/portal/address", form.value);
    show.value = false;
    await load();
  } catch (error) {
    showToast(error.message || "保存失败");
  }
}
async function remove(id) {
  try {
    await request.delete(`/api/portal/address/${id}`);
    await load();
  } catch (error) {
    showToast(error.message || "删除失败");
  }
}
async function setDefault(id) {
  try {
    await request.put(`/api/portal/address/${id}/default`);
    await load();
  } catch (error) {
    showToast(error.message || "设置失败");
  }
}
function onAreaConfirm(event = {}) {
  const options = event.selectedOptions || [];
  const getName = (opt) => (opt ? opt.text || opt.name || "" : "");
  form.value.province = getName(options[0]);
  form.value.city = getName(options[1]);
  form.value.district = getName(options[2]);
  areaText.value = [form.value.province, form.value.city, form.value.district]
    .filter(Boolean)
    .join(" ");
  showAreaPicker.value = false;
}
onMounted(load);
</script>
<template>
  <section>
    <van-nav-bar
      title="收货地址"
      left-arrow
      @click-left="$router.back()"
    /><van-address-list
      :list="
        rows.map((row) => ({
          id: row.id,
          name: row.receiverName,
          tel: row.receiverPhone,
          address: [row.province, row.city, row.district, row.detailAddress]
            .filter(Boolean)
            .join(' '),
          isDefault: row.isDefault === 1,
        }))
      "
      default-tag-text="默认"
      @add="open()"
      @edit="({ id }) => open(rows.find((row) => row.id === id))"
    /><van-cell-group inset
      ><van-cell
        v-for="row in rows"
        :key="row.id"
        :title="row.receiverName"
        :label="row.detailAddress"
        is-link
        @click="setDefault(row.id)"
        ><template #value
          ><van-button
            size="small"
            plain
            type="danger"
            @click.stop="remove(row.id)"
            >删除</van-button
          ></template
        ></van-cell
      ></van-cell-group
    ><van-popup v-model:show="show" position="bottom" round
      ><van-form @submit="save"
        ><van-cell-group inset
          ><van-field v-model="form.receiverName" label="收货人" /><van-field
            v-model="form.receiverPhone"
            label="电话" /><van-field
            v-model="areaText"
            label="所在地区"
            readonly
            placeholder="请选择省/市/区"
            @click="showAreaPicker = true" /><van-field
            v-model="form.detailAddress"
            label="详细地址" /><van-field label="默认地址"
            ><template #input
              ><van-switch
                v-model="form.isDefault"
                :active-value="1"
                :inactive-value="0" /></template></van-field
        ></van-cell-group>
        <div class="form-action">
          <van-button block type="primary" native-type="submit"
            >保存</van-button
          >
        </div></van-form
      ></van-popup
    ><van-popup v-model:show="showAreaPicker" position="bottom" round
      ><van-area
        :area-list="areaList"
        @confirm="onAreaConfirm"
        @cancel="showAreaPicker = false"
      /></van-popup
    >
  </section>
</template>
