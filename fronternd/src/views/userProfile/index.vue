<template>
  <div class="profile-page" v-loading="loading">
    <div class="profile-heading"><el-avatar :size="56">{{ (form.nickname || user.username || '我').slice(0, 1) }}</el-avatar><div><h1>个人中心</h1><p>{{ user.username }} · {{ role.name }}</p></div></div>
    <div class="profile-grid">
      <el-card shadow="never">
        <template #header>个人信息</template>
        <el-form :model="form" label-width="80px" @submit.prevent="save">
          <el-form-item label="账号"><el-input :model-value="user.username" disabled /></el-form-item>
          <el-form-item label="人员姓名" required><el-input v-model="form.nickname" maxlength="30" show-word-limit /></el-form-item>
          <el-form-item label="研究方向"><el-input v-model="form.part" maxlength="30" placeholder="例如：小目标检测" /></el-form-item>
          <el-form-item label="手机号"><el-input v-model="form.phone" maxlength="20" /></el-form-item>
          <el-form-item label="个人简介"><el-input v-model="form.remark" type="textarea" :rows="4" maxlength="100" show-word-limit /></el-form-item>
          <el-form-item><el-button type="primary" :loading="saving" :disabled="!loaded" native-type="submit">保存资料</el-button></el-form-item>
        </el-form>
      </el-card>
      <el-card shadow="never">
        <template #header>我的权限</template>
        <el-tag>{{ role.name }}</el-tag>
        <ul><li v-for="item in role.permissions" :key="item">{{ item }}</li></ul>
        <el-alert v-if="Number(user.type) === 3" title="数据集仅供查询和预览，不可导入、修改、删除或重新划分；结果仅供查看。" type="info" :closable="false" />
        <p class="profile-note">权限由平台管理员分配，不能自行修改自己的等级。研究方向仅用于个人资料展示。基线、改进包与平台源码通过 GitHub PR 审查维护。</p>
      </el-card>
    </div>
    <el-card v-if="Number(user.type) === 1" class="role-management" shadow="never">
      <template #header><div class="management-heading"><span>用户权限管理</span><el-button :loading="rolesLoading" :disabled="savingRoleId !== null" @click="loadRoles">刷新列表</el-button></div></template>
      <p class="profile-note">选择其他用户的权限等级后点击保存。修改后，该用户需要重新登录。</p>
      <el-alert v-if="rolesError" title="用户权限列表加载失败" :description="rolesError" type="warning" :closable="false" />
      <el-table :data="users" v-loading="rolesLoading" empty-text="暂无用户">
        <el-table-column prop="username" label="账号" min-width="140" />
        <el-table-column prop="nickname" label="人员姓名" min-width="120" />
        <el-table-column prop="part" label="研究方向" min-width="140" />
        <el-table-column label="当前等级" min-width="120"><template #default="{ row }">{{ getRole(row).name }}</template></el-table-column>
        <el-table-column label="权限等级" min-width="180"><template #default="{ row }">
          <el-select v-model="draftRoles[row.id]" :disabled="isSelf(row) || savingRoleId !== null" aria-label="权限等级">
            <el-option v-for="(item, id) in ROLE_DEFINITIONS" :key="id" :value="Number(id)" :label="item.name" />
          </el-select>
        </template></el-table-column>
        <el-table-column label="操作" width="110"><template #default="{ row }">
          <span v-if="isSelf(row)" class="profile-note">当前用户</span>
          <el-button v-else type="primary" :loading="savingRoleId === row.id" :disabled="savingRoleId !== null || Number(draftRoles[row.id]) === Number(row.type)" @click="saveRole(row)">保存</el-button>
        </template></el-table-column>
      </el-table>
    </el-card>
    <el-alert v-if="profileError && !loading" title="个人资料加载失败" :description="profileError" type="warning" :closable="false"><el-button text @click="load">重新加载</el-button></el-alert>
  </div>
</template>
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores'
import { UserService } from '@/api/api'
import { getRole, ROLE_DEFINITIONS } from '@/config/permissions'
import { profileFailure } from '@/utils/profileErrors'
const store = useUserStore()
const user = computed(() => store.user)
const role = computed(() => getRole(user.value))
const form = reactive({ nickname: '', phone: '', part: '', remark: '' })
const loading = ref(false), saving = ref(false), loaded = ref(false)
const profileError = ref('')
const users = ref([]), rolesLoading = ref(false), rolesError = ref(''), savingRoleId = ref(null)
const draftRoles = reactive({})
const isSelf = row => String(row.id) === String(user.value.id)
async function loadRoles() {
  if (Number(user.value.type) !== 1) return
  rolesLoading.value = true
  rolesError.value = ''
  try {
    const res = await UserService.userRoles()
    if (res?.code !== 0 || !Array.isArray(res.data)) throw res || new Error('无效响应')
    users.value = res.data
    for (const row of users.value) draftRoles[row.id] = Number(row.type)
  } catch (error) { rolesError.value = profileFailure(error); users.value = [] }
  finally { rolesLoading.value = false }
}
async function saveRole(row) {
  if (isSelf(row) || savingRoleId.value !== null) return
  const type = Number(draftRoles[row.id])
  savingRoleId.value = row.id
  try {
    const res = await UserService.changeUserRole({ id: row.id, type })
    if (res.code === 0) { row.type = type; ElMessage.success('权限等级已更新，该用户需要重新登录') }
    else ElMessage.warning(res.msg)
  } catch { ElMessage.error('权限等级保存失败，请重试') }
  finally { savingRoleId.value = null }
}
function accept(data) {
  store.setUser(data)
  for (const key of Object.keys(form)) form[key] = data[key] || ''
  loaded.value = true
}
async function load() {
  loading.value = true
  profileError.value = ''
  try {
    const res = await UserService.profile()
    if (res?.code !== 0 || !res.data?.id) throw res || new Error('无效响应')
    accept(res.data)
  }
  catch (error) { profileError.value = profileFailure(error) }
  finally { loading.value = false }
  // 个人资料失败也独立请求权限列表；是否允许操作仍由后端鉴权决定。
  await loadRoles()
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
.role-management { margin-top: 24px; }
.management-heading { display: flex; align-items: center; justify-content: space-between; }
.profile-note { color: var(--el-text-color-secondary); }
ul { padding-left: 22px; } li { margin: 16px 0; line-height: 1.7; }
@media (max-width: 760px) { .profile-grid { grid-template-columns: 1fr; } }
</style>
