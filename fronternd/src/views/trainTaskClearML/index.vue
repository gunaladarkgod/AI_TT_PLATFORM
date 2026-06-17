<template>
  <div class="content-div train-clearml-dev">
    <div class="search-div flex-between">
      <div class="flex-start">
        <el-text tag="b" size="large">模型训练（dev）</el-text>
        <el-tag size="small" type="info" class="ml-h">ClearML</el-tag>
      </div>
      <div class="flex-end">
        <el-space>
          <el-button size="small" @click="goTrainHome">标准模型训练</el-button>
          <el-button size="small" :loading="probeLoading" @click="runProbe">探测登录</el-button>
          <el-button type="primary" size="small" :loading="loading" @click="refreshAll">刷新</el-button>
        </el-space>
      </div>
    </div>

    <el-alert
      type="info"
      show-icon
      :closable="false"
      class="mt-block"
      title="说明"
      description="本页用于查看 ClearML 上「进行中」的训练实验，并在侧边栏提供与标准训练页的并列入口。访问密钥仅保存在服务端（sys.clearml / 环境变量）。训练任务的创建、排队与本地 Runner 仍使用「模型训练」页的原有流程。"
    />

    <el-alert v-if="probeHintOk" type="success" show-icon :closable="false" class="mt-block" :title="probeHintOk" />
    <el-alert
      v-if="probeErrMsg"
      type="error"
      show-icon
      :closable="false"
      class="mt-block"
      :title="probeErrMsg"
      :description="probeErrExtra || undefined"
    />

    <el-row :gutter="16" class="mt-block">
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header>
            <span>ClearML 连接</span>
          </template>
          <el-descriptions :column="1" size="small" border>
            <el-descriptions-item label="enabled">{{ status.enabled }}</el-descriptions-item>
            <el-descriptions-item label="configured">{{ status.configured }}</el-descriptions-item>
            <el-descriptions-item label="apiHost">{{ status.apiHost || '—' }}</el-descriptions-item>
            <el-descriptions-item label="webHost">{{ status.webHost || '—' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header>
            <span>Python Runner（本地）</span>
          </template>
          <template v-if="runnerHealth">
            <el-tag :type="runnerHealth.ok ? 'success' : 'danger'" size="small">
              {{ runnerHealth.ok ? '可达' : '异常' }}
            </el-tag>
            <el-text v-if="runnerHealth.healthUrl" size="small" class="ml-h">{{ runnerHealth.healthUrl }}</el-text>
            <pre v-if="runnerHealth.error" class="runner-pre">{{ runnerHealth.error }}</pre>
          </template>
          <el-text v-else size="small">未探测</el-text>
        </el-card>
      </el-col>
    </el-row>

    <div class="table-div mt-block">
      <div class="flex-between mb-h">
        <el-text tag="b">进行中实验（ClearML）</el-text>
      </div>
      <el-alert v-if="hint" type="warning" :closable="false" class="mb-h" :title="hint" />
      <el-alert v-if="err" type="error" :closable="false" class="mb-h" :title="err" />
      <el-table v-loading="loading" :data="tasks" stripe size="small" style="width: 100%">
        <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="projectName" label="项目" min-width="140" show-overflow-tooltip />
        <el-table-column prop="id" label="任务 ID" width="260" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column label="控制台" width="120" align="center">
          <template #default="{ row }">
            <el-link v-if="row.consoleUrl" type="primary" :href="row.consoleUrl" target="_blank" rel="noopener">
              打开
            </el-link>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ClearmlDevService, TrainTaskService } from '@/api/api'

const router = useRouter()
const status = ref({})
const runnerHealth = ref(null)
const loading = ref(false)
const tasks = ref([])
const hint = ref('')
const err = ref('')
const probeLoading = ref(false)
const probeHintOk = ref('')
const probeErrMsg = ref('')
const probeErrExtra = ref('')

function goTrainHome() {
  router.push('/trainTask')
}

async function runProbe() {
  probeLoading.value = true
  probeHintOk.value = ''
  probeErrMsg.value = ''
  probeErrExtra.value = ''
  try {
    const res = await ClearmlDevService.probe()
    if (res.code === 0) {
      probeHintOk.value = res.data?.hint || 'ClearML 登录探测成功'
      await refreshTasks()
      await loadStatus()
      return
    }
    probeErrMsg.value = res.msg || '探测失败'
    const d = res.data || {}
    if (d.hint) probeErrExtra.value = String(d.hint)
    if (d.detail) {
      probeErrExtra.value = probeErrExtra.value
        ? `${probeErrExtra.value}\n${String(d.detail)}`
        : String(d.detail)
    }
  } catch (e) {
    probeErrMsg.value = e?.message || String(e)
  } finally {
    probeLoading.value = false
  }
}

async function loadStatus() {
  try {
    const res = await ClearmlDevService.status()
    if (res && res.code === 0) status.value = res.data || {}
  } catch {
    status.value = {}
  }
}

async function loadRunnerHealth() {
  try {
    const res = await TrainTaskService.runnerHealth({})
    if (res && res.code === 0) runnerHealth.value = res.data
    else runnerHealth.value = { ok: false, error: res?.msg || 'unknown' }
  } catch (e) {
    runnerHealth.value = { ok: false, error: e?.message || String(e) }
  }
}

async function refreshTasks() {
  loading.value = true
  err.value = ''
  hint.value = ''
  try {
    const res = await ClearmlDevService.activeTasks({ page_size: 50 })
    if (res.code !== 0) {
      err.value = res.msg || '请求失败'
      const d = res.data
      if (d?.hint) hint.value = d.hint
      return
    }
    const data = res.data || {}
    tasks.value = data.tasks || []
    hint.value = data.hint || ''
  } catch (e) {
    err.value = e?.message || String(e)
  } finally {
    loading.value = false
  }
}

async function refreshAll() {
  await Promise.all([loadStatus(), loadRunnerHealth(), refreshTasks()])
}

onMounted(() => refreshAll())
</script>

<style scoped>
.mt-block {
  margin-top: 14px;
}
.mb-h {
  margin-bottom: 10px;
}
.ml-h {
  margin-left: 10px;
}
.runner-pre {
  margin-top: 8px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}
.train-clearml-dev .flex-between {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.train-clearml-dev .flex-start {
  display: flex;
  align-items: center;
  gap: 8px;
}
.train-clearml-dev .flex-end {
  display: flex;
  align-items: center;
}
</style>
