<template>
  <el-dialog v-model="visible" title="按需组件" width="min(860px, 94vw)" destroy-on-close>
    <div class="component-toolbar">
      <span>检查本地工作区中的引擎和研究方向</span>
      <el-button size="small" :icon="Refresh" :loading="loading" @click="loadComponents">刷新</el-button>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane label="训练引擎" name="engine" />
      <el-tab-pane label="研究方向" name="direction" />
    </el-tabs>

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
            v-if="row.status !== 'ready' && (row.downloadCommand || row.installCommand)"
            size="small"
            :icon="CopyDocument"
            @click="copyCommand(row.downloadCommand || row.installCommand)"
          >复制补充命令</el-button>
          <span v-else class="component-command-missing">{{ row.status === 'ready' ? '已就绪' : '暂无补充命令' }}</span>
          <el-tooltip v-if="row.message" :content="row.message" placement="top">
            <el-button link size="small" :icon="InfoFilled" aria-label="组件详情" />
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-else-if="!loading" :description="emptyText" :image-size="72" />
    <div v-if="!errorText" class="component-footnote">
      命令由后端根据当前工作区生成；复制后在项目根目录的终端执行。
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument, InfoFilled, Refresh } from '@element-plus/icons-vue'
import { WorkspaceComponentService } from '@/api/api'

const props = defineProps({ modelValue: { type: Boolean, default: false } })
const emit = defineEmits(['update:modelValue'])
const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})
const activeTab = ref('all')
const items = ref([])
const loading = ref(false)
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
    const response = await WorkspaceComponentService.list()
    if (response?.code !== 0) throw new Error(response?.msg || '组件状态查询失败')
    const data = response.data
    items.value = Array.isArray(data) ? data : Array.isArray(data?.components) ? data.components : []
  } catch (error) {
    items.value = []
    errorText.value = error?.message || '组件状态接口暂不可用'
  } finally {
    loading.value = false
  }
}

watch(visible, isVisible => {
  if (isVisible) loadComponents()
})

const statusLabel = status => ({ ready: '已就绪', missing: '未下载', partial: '不完整' }[status] || '未知')
const statusType = status => ({ ready: 'success', missing: 'warning', partial: 'danger' }[status] || 'info')

const copyCommand = async command => {
  try {
    await navigator.clipboard.writeText(command)
    ElMessage.success('补充命令已复制')
  } catch {
    ElMessage.error('复制失败，请检查浏览器剪贴板权限')
  }
}
</script>

<style scoped>
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
