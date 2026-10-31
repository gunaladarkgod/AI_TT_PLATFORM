<template>
  <el-dialog v-model="visible" title="按需组件" width="min(860px, 94vw)" destroy-on-close>
    <div class="component-toolbar">
      <el-select v-model="engine" style="width: 240px" :disabled="running" @change="loadComponents">
        <el-option label="Ultralytics 对应研究方向" value="ultralytics" />
        <el-option label="MMDetection 对应研究方向" value="mmdet" />
      </el-select>
      <el-button size="small" :icon="Refresh" :loading="loading" @click="loadComponents">刷新</el-button>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane label="训练引擎" name="engine" />
      <el-tab-pane label="研究方向" name="direction" />
    </el-tabs>

    <el-alert v-if="workspaceMessage" type="info" :closable="false" :title="workspaceMessage" />
    <el-alert v-if="errorText" type="warning" :closable="false" show-icon :title="errorText" />
    <el-table v-else-if="filteredItems.length" :data="filteredItems" size="small" row-key="id">
      <el-table-column label="组件" min-width="210">
        <template #default="{ row }">
          <div class="component-name">{{ row.name }}</div>
          <div class="component-path">{{ row.path || row.relativePath || '项目目录' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="本机状态" width="130">
        <template #default="{ row }">
          <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="270">
        <template #default="{ row }">
          <el-button
            v-if="row.status !== 'ready'"
            size="small"
            :disabled="!downloadSupported || running || !isAdmin"
            :loading="running && task.componentId === row.id"
            @click="download(row)"
          >{{ row.status === 'partial' ? '补充下载' : '下载' }}</el-button>
          <span v-else class="component-command-missing">文件齐全</span>
          <el-tooltip v-if="row.message" :content="row.message" placement="top">
            <el-button link size="small" :icon="InfoFilled" aria-label="组件详情" />
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-else-if="!loading" :description="emptyText" :image-size="72" />
    <div v-if="task.id" class="component-footnote" aria-live="polite">
      <el-tag :type="task.status === 'failed' ? 'danger' : task.status === 'succeeded' ? 'success' : 'info'">
        {{ task.status === 'running' ? '下载中' : task.status === 'succeeded' ? '下载完成' : '下载失败' }}
      </el-tag>
      <pre class="download-log">{{ (task.logs || []).join('\n') || task.message }}</pre>
    </div>
    <div class="component-footnote">下载到后端所在机器的当前工作区。研究方向会同时补充对应引擎；下载文件后仍需配置训练 Python 环境。</div>
    <div v-if="!isAdmin" class="component-footnote">请由平台管理员下载组件。</div>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { InfoFilled, Refresh } from '@element-plus/icons-vue'
import { WorkspaceComponentService } from '@/api/api'
import { useUserStore } from '@/stores/index'

const props = defineProps({ modelValue: { type: Boolean, default: false } })
const emit = defineEmits(['update:modelValue'])
const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})
const activeTab = ref('all')
const items = ref([])
const loading = ref(false)
const engine = ref('ultralytics')
const task = ref({})
const downloadSupported = ref(false)
const workspaceMessage = ref('')
const isAdmin = computed(() => Number(useUserStore().user?.type) === 1)
const running = computed(() => task.value.status === 'running')
let timer
const errorText = ref('')
const filteredItems = computed(() => activeTab.value === 'all'
  ? items.value
  : items.value.filter(item => item.kind === activeTab.value))
const emptyText = computed(() => activeTab.value === 'all'
  ? '后端未返回可选组件'
  : activeTab.value === 'engine' ? '没有可用的训练引擎' : '没有可用的研究方向')

const loadComponents = async () => {
  if (loading.value) return
  loading.value = true
  errorText.value = ''
  try {
    const response = await WorkspaceComponentService.list(engine.value)
    if (response?.code !== 0) throw new Error(response?.msg || '组件状态查询失败')
    const data = response.data
    items.value = Array.isArray(data) ? data : Array.isArray(data?.components) ? data.components : []
    downloadSupported.value = data?.downloadSupported === true
    workspaceMessage.value = data?.message || ''
    task.value = data?.task || {}
  } catch (error) {
    items.value = []
    errorText.value = error?.message || '组件状态接口暂不可用'
  } finally {
    loading.value = false
    clearTimeout(timer)
    if (visible.value && running.value) timer = setTimeout(loadComponents, 2000)
  }
}

watch(visible, isVisible => {
  if (isVisible) loadComponents()
  else clearTimeout(timer)
})
onBeforeUnmount(() => clearTimeout(timer))

const statusLabel = status => ({ ready: '文件齐全', missing: '未下载', partial: '不完整' }[status] || '未知')
const statusType = status => ({ ready: 'success', missing: 'warning', partial: 'danger' }[status] || 'info')

const download = async row => {
  try {
    const response = await WorkspaceComponentService.download(row.id, row.kind === 'engine' ? row.id : engine.value)
    if (response?.code !== 0) throw new Error(response?.msg || '下载启动失败')
    task.value = response.data
    await loadComponents()
  } catch (error) {
    ElMessage.error(error?.message || '下载启动失败')
  }
}
</script>

<style scoped>
.download-log { white-space: pre-wrap; overflow-wrap: anywhere; max-height: 180px; overflow: auto; }
.component-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.component-name {
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.component-path,
.component-command-missing {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  overflow-wrap: anywhere;
}

.component-footnote {
  margin-top: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
