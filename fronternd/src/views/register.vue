<template>
  <div class="login-bg">
    <div class="flex-center login-div"><el-space><el-icon class="text-white font-size-24"><el-image :src="logoPath" /></el-icon><el-text class="text-white font-size-24">AI训练平台</el-text></el-space></div>
    <div class="flex-center"><div class="login-box"><div class="flex-center msg-div"><el-text>注册账号</el-text></div><div class="login-form">
      <el-form label-position="top" :model="form">
        <el-form-item label="用户名"><el-input v-model="form.username" clearable placeholder="字母、数字和下划线"><template #prefix><i class="iconfont icon-yonghu" /></template></el-input></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" clearable type="password" show-password><template #prefix><i class="iconfont icon-key-fill" /></template></el-input></el-form-item>
        <el-form-item label="确认密码"><el-input v-model="form.confirmPassword" clearable type="password" show-password><template #prefix><i class="iconfont icon-key-fill" /></template></el-input></el-form-item>
      </el-form>
      <el-button class="login-btn" type="primary" :loading="loading" @click="submit">注册</el-button><el-button class="back-btn" text type="primary" @click="router.replace('/login')">返回登录</el-button>
    </div></div></div>
  </div>
</template>
<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import md5 from 'js-md5'
import { useRouter } from 'vue-router'
import { AuthService } from '../api/api'
import { logoPath } from '../api/axios'
import { isEN } from '../utils/regex'
const router = useRouter(); const form = reactive({ username: '', password: '', confirmPassword: '' }); const loading = ref(false)
async function submit() {
  const username = form.username.trim()
  if (!username || !isEN(username)) return ElMessage.warning('用户名只允许包含大小写字母、数字和下划线')
  if (form.password.length < 8) return ElMessage.warning('密码长度不能少于8位')
  if (form.password !== form.confirmPassword) return ElMessage.warning('两次输入的密码不一致')
  loading.value = true
  try { const res = await AuthService.register({ username, pmd: md5(form.password) }); if (res.code !== 0) return ElMessage.warning(res.msg || '注册失败'); ElMessage.success('注册成功，请登录'); await router.replace({ path: '/login', query: { username } }) } catch (_) { ElMessage.error('注册请求失败，请确认后端服务已正常启动') } finally { loading.value = false }
}
</script>
<style scoped>
.login-div { padding-top: 15vh; }.login-bg { background: url('/imgs/bg.png'); background-size: cover; height: 100vh; }.login-box { width: 350px; margin: 20px; background: #f0f0f0; border-radius: 4px; text-align: center; }.login-form { padding: 20px; background: #fff; border-radius: 12px 12px 8px 8px; }.msg-div { padding: 10px 0; }.login-btn { width: 100%; }.back-btn { margin-top: 8px; }
</style>
