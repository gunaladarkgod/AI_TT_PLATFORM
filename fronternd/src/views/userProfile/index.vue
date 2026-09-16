<template>
  <div class="profile-page" v-loading="loading">
    <div class="profile-heading"><el-avatar :size="56">{{ (form.nickname || user.username || '我').slice(0, 1) }}</el-avatar><div><h1>个人中心</h1><p>{{ user.username }} · {{ role.name }}</p></div></div>
    <div class="profile-grid">
      <el-card shadow="never">
        <template #header>个人信息</template>
        <el-form :model="form" label-width="80px" @submit.prevent="save">
          <el-form-item label="账号"><el-input :model-value="user.username" disabled /></el-form-item>
          <el-form-item label="人员姓名" required><el-input v-model="form.nickname" maxlength="30" show-word-limit /></el-form-item>
          <el-form-item label="部门"><el-input v-model="form.part" maxlength="30" /></el-form-item>
          <el-form-item label="手机号"><el-input v-model="form.phone" maxlength="20" /></el-form-item>
          <el-form-item label="个人简介"><el-input v-model="form.remark" type="textarea" :rows="4" maxlength="100" show-word-limit /></el-form-item>
          <el-form-item><el-button type="primary" :loading="saving" :disabled="!loaded" native-type="submit">保存资料</el-button></el-form-item>
        </el-form>
      </el-card>
      <el-card shadow="never">
        <template #header>我的权限</template>
        <el-tag>{{ role.name }}</el-tag><p class="role-code">{{ role.code }}</p>
        <ul><li v-for="item in role.permissions" :key="item">{{ item }}</li></ul>
        <el-alert v-if="role.code === 'ExperimentOperator'" title="数据集仅供查询和预览，不可导入、修改、删除或重新划分；结果仅供查看。" type="info" :closable="false" />
        <p class="profile-note">权限由维护人员分配，个人中心不能修改角色。基线、改进包与平台源码通过 GitHub PR 审查维护。</p>
      </el-card>
    </div>
    <el-alert v-if="!loaded && !loading" title="资料加载失败，请重试。" type="warning" :closable="false"><el-button text @click="load">重新加载</el-button></el-alert>
  </div>
</template>
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores'
import { UserService } from '@/api/api'
import { getRole } from '@/config/permissions'
const store = useUserStore()
const user = computed(() => store.user)
const role = computed(() => getRole(user.value))
const form = reactive({ nickname: '', phone: '', part: '', remark: '' })
const loading = ref(false), saving = ref(false), loaded = ref(false)
function accept(data) {
  store.setUser(data)
  for (const key of Object.keys(form)) form[key] = data[key] || ''
  loaded.value = true
}
async function load() {
  loading.value = true
  try { const res = await UserService.profile(); if (res.code === 0) accept(res.data); else ElMessage.warning(res.msg) }
  catch { ElMessage.error('个人资料加载失败') }
  finally { loading.value = false }
}
async function save() {
  if (!form.nickname.trim()) return ElMessage.warning('请填写人员姓名')
  saving.value = true
  try { const res = await UserService.saveProfile({ ...form }); if (res.code === 0) { accept(res.data); ElMessage.success('资料已保存') } else ElMessage.warning(res.msg) }
  catch { ElMessage.error('资料保存失败，请重试') }
  finally { saving.value = false }
}
onMounted(load)
</script>
<style scoped>
.profile-page { max-width: 1100px; margin: 24px auto; padding: 0 20px; }
.profile-heading { display: flex; align-items: center; gap: 18px; margin-bottom: 28px; }
h1 { margin: 0; font-size: 26px; } p { line-height: 1.7; }
.profile-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }
.role-code, .profile-note { color: var(--el-text-color-secondary); }
ul { padding-left: 22px; } li { margin: 16px 0; line-height: 1.7; }
@media (max-width: 760px) { .profile-grid { grid-template-columns: 1fr; } }
</style>
