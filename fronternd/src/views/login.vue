<template>
  <div class="login-bg">
    <div class="flex-center login-div">
      <el-space>
        <el-icon class="text-white font-size-24"><el-image :src="logoPath" /></el-icon>
        <el-text class="text-white font-size-24">AI训练平台</el-text>
      </el-space>
    </div>
    <div class="flex-center">
      <div class="login-box">
        <div class="flex-center msg-div"><el-text>用户登录</el-text></div>
        <div class="login-form">
          <el-form label-position="top" :model="form">
            <el-form-item label="用户名">
              <el-select
                v-if="savedAccounts.length"
                v-model="selectedSavedAccount"
                class="saved-account-select"
                placeholder="选择已保存的账户"
                @change="applySavedAccount"
              >
                <el-option
                  v-for="account in savedAccounts"
                  :key="account.username"
                  :label="account.username + (account.autoLogin ? '（自动登录）' : '')"
                  :value="account.username"
                />
              </el-select>
              <el-input v-model="form.username" clearable @input="onUsernameInput" @keyup.enter="submit">
                <template #prefix><i class="iconfont icon-yonghu" /></template>
              </el-input>
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="form.pwd" clearable type="password" show-password @input="onPasswordInput" @keyup.enter="submit">
                <template #prefix><i class="iconfont icon-key-fill" /></template>
              </el-input>
              <div v-if="selectedAccountHasPassword && !form.pwd" class="saved-password-hint">
                已记住本机登录凭据，直接登录即可
              </div>
            </el-form-item>
            <div class="login-options">
              <el-checkbox v-model="rememberPassword">记住密码</el-checkbox>
              <el-checkbox v-model="autoLogin" :disabled="!rememberPassword">自动登录</el-checkbox>
            </div>
          </el-form>
          <el-button class="login-btn" type="primary" :loading="loginLoading" @click="submit">登录</el-button>
          <div class="account-actions">
            <el-button text type="primary" @click="clearCurrentAccount">切换账户</el-button>
            <div class="account-action-right">
              <el-button v-if="selectedSavedAccount" text type="danger" @click="forgetCurrentAccount">移除此账户</el-button>
              <el-button text type="primary" @click="router.push('/register')">注册账号</el-button>
            </div>
          </div>
          <p class="login-security-tip">“记住密码”仅保存本机登录凭据摘要，请勿在公共电脑启用。</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import md5 from 'js-md5'
import { useRoute, useRouter } from 'vue-router'
import { AuthService, UserService } from '../api/api'
import { useLoginStore, useMenuStore, useUserStore } from '@/stores/index'
import { uuid } from 'vue-uuid'
import { isEN } from '../utils/regex'
import { logoPath } from '../api/axios'
import {
  getAutoLoginAccount,
  getSavedLoginAccounts,
  removeSavedLoginAccount,
  saveLoginAccount,
} from '../utils/savedLoginAccounts'

const router = useRouter()
const route = useRoute()
const form = reactive({ username: '', pwd: '' })
const savedAccounts = ref(getSavedLoginAccounts())
const selectedSavedAccount = ref('')
const savedPmd = ref('')
const rememberPassword = ref(false)
const autoLogin = ref(false)
const loginLoading = ref(false)
const selectedAccountHasPassword = computed(() => Boolean(savedPmd.value))

document.documentElement.style.setProperty('--el-color-primary', '#409eff')

function refreshSavedAccounts() {
  savedAccounts.value = getSavedLoginAccounts()
}

function applySavedAccount(username) {
  const account = savedAccounts.value.find((item) => item.username === username)
  if (!account) return
  form.username = account.username
  form.pwd = ''
  savedPmd.value = account.pmd || ''
  rememberPassword.value = Boolean(account.rememberPassword)
  autoLogin.value = Boolean(account.autoLogin && account.pmd)
}

function onUsernameInput() {
  if (form.username !== selectedSavedAccount.value) {
    selectedSavedAccount.value = ''
    savedPmd.value = ''
  }
}

function onPasswordInput() {
  savedPmd.value = ''
}

function clearCurrentAccount() {
  selectedSavedAccount.value = ''
  form.username = ''
  form.pwd = ''
  savedPmd.value = ''
  rememberPassword.value = false
  autoLogin.value = false
}

function forgetCurrentAccount() {
  if (!selectedSavedAccount.value) return
  removeSavedLoginAccount(selectedSavedAccount.value)
  clearCurrentAccount()
  refreshSavedAccounts()
  ElMessage.success('已移除本机保存的账户')
}

async function completeLogin(token, username) {
  const payload = token.split('.')[1]
  const json = JSON.parse(atob(payload))
  useUserStore().$patch((state) => {
    state.user = { username, id: json.id, type: json.type }
    state.isSys = json.type == 1
  })
  useLoginStore().$patch((state) => {
    state.isLogin = true
    state.uuidLogin = uuid.v1()
    state.token = token
  })
  try {
    const profile = await UserService.profile()
    if (profile.code === 0) useUserStore().setUser(profile.data)
  } catch { /* 保留已有登录流程，个人中心可重新加载资料。 */ }
  const menuStore = useMenuStore()
  await menuStore.setMenuList()
  if (!menuStore.menuList.length) {
    ElMessage.warning('该用户暂无菜单权限，请联系系统管理员')
    return
  }
  await menuStore.registerRoutes()
  await router.replace(route.query.redirect || menuStore.getFirstMenu())
}

async function submit({ pmd: specifiedPmd } = {}) {
  const username = form.username.trim()
  if (!username) {
    ElMessage.warning('用户名不能为空')
    return
  }
  if (!isEN(username)) {
    ElMessage.warning('用户名只允许包含大小写字母、数字和下划线')
    return
  }
  const pmd = specifiedPmd || savedPmd.value || (form.pwd ? md5(form.pwd) : '')
  if (!pmd) {
    ElMessage.warning('密码不能为空')
    return
  }

  loginLoading.value = true
  try {
    const res = await AuthService.login({ username, pmd })
    if (res.code !== 0) {
      useLoginStore().$patch((state) => { state.isLogin = false; state.uuidLogin = ''; state.token = '' })
      ElMessage.warning(res.msg || '登录失败')
      return
    }
    saveLoginAccount({ username, pmd, rememberPassword: rememberPassword.value, autoLogin: autoLogin.value })
    refreshSavedAccounts()
    await completeLogin(res.data, username)
  } catch (_) {
    useLoginStore().$patch((state) => { state.isLogin = false })
  } finally {
    loginLoading.value = false
  }
}

onMounted(async () => {
  if (typeof route.query.username === 'string') form.username = route.query.username
  const loginStore = useLoginStore()
  // 主动退出或切换账户时，必须停留在登录页；自动登录只在正常打开系统时执行。
  if (route.query.switch === '1' || route.query.manualLogout === '1') return
  if (loginStore.token) {
    try {
      await completeLogin(loginStore.token, useUserStore().user?.username || '')
      return
    } catch (_) {
      loginStore.$patch((state) => { state.isLogin = false; state.uuidLogin = ''; state.token = '' })
    }
  }
  const autoAccount = getAutoLoginAccount()
  if (autoAccount) {
    selectedSavedAccount.value = autoAccount.username
    applySavedAccount(autoAccount.username)
    await submit({ pmd: autoAccount.pmd })
  }
})
</script>

<style scoped>
.login-div { padding-top: 15vh; }
.login-bg { background: url('/imgs/bg.png'); background-size: cover; height: 100vh; }
.login-box { width: 350px; margin: 20px; background: #f0f0f0; border-radius: 4px; text-align: center; }
.login-form { padding: 20px; background: #fff; border-radius: 12px 12px 8px 8px; }
.msg-div { padding: 10px 0; }
.login-btn, .saved-account-select { width: 100%; }
.saved-account-select { margin-bottom: 10px; }
.login-options, .account-actions { display: flex; align-items: center; justify-content: space-between; }
.login-options { margin: -2px 0 18px; }
.account-actions { margin-top: 8px; }
.account-action-right { display: flex; align-items: center; }
.saved-password-hint, .login-security-tip { margin: 7px 0 0; color: #909399; font-size: 12px; line-height: 1.5; text-align: left; }
</style>
