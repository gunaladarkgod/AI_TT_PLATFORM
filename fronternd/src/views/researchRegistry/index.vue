<template>
  <div class="registry-page">
    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :description="errorDetails" show-icon :closable="false" class="registry-alert" />
    <template v-else-if="loading"><el-skeleton :rows="8" animated /></template>

    <template v-else>
      <div class="registry-intro">
        <div>
          <h2>组件管理</h2>
          <p>先查看研究方向和基线，再登记或上传自己的算法。</p>
        </div>
        <el-tag type="info">仓库状态</el-tag>
      </div>

      <section class="registry-section">
        <div class="section-heading"><h3>研究方向</h3><div class="section-actions"><span>展开基线查看关联算法，或上传自己的算法</span><el-button size="small" :loading="componentLoading" @click="loadComponents">检查状态</el-button></div></div>
        <el-alert v-if="componentError" :title="componentError" type="warning" :closable="false" show-icon />
        <div v-loading="componentLoading" class="direction-tables">
          <article v-for="table in directionTables" :key="table.id" class="direction-table">
            <header class="direction-table-heading">
              <div><h4>{{ table.title }}</h4><span>{{ directionDescription(table.id) }}</span></div>
              <el-button type="primary" size="small" v-if="canManageComponents" @click="openUpload(table.id)">上传自定义算法</el-button>
            </header>
            <el-table :data="table.rows" stripe border size="small" row-key="treeKey" :expand-row-keys="expandedRows" :tree-props="{ children: 'children' }" class="repository-table" @expand-change="onExpandChange">
              <el-table-column label="基线 / 算法" min-width="260">
                <template #default="{ row }">
                  <span>{{ row.name }}</span>
                  <el-tag size="small" effect="plain" class="tree-label" :type="row.nodeType === 'algorithm' ? 'success' : 'info'">{{ row.nodeType === 'algorithm' ? '算法' : row.nodeType === 'group' ? '待关联' : '基线' }}</el-tag>
                  <el-tag v-if="row.custom" size="small" effect="plain" type="warning" class="tree-label">本地算法 · 开发中</el-tag>
                  <span v-if="row.children?.length" class="algorithm-count">{{ row.children.length }} 个算法</span>
                </template>
              </el-table-column>
              <el-table-column label="仓库" min-width="220" show-overflow-tooltip>
                <template #default="{ row }">
                  <span v-if="row.nodeType === 'group'" class="pending-text">尚未指定基线，或关联的基线未登记</span>
                  <a v-else-if="row.repository && row.repository.startsWith('http')" :href="row.repository" target="_blank" rel="noopener">{{ row.repository }}</a>
                  <span v-else-if="row.custom" class="pending-text">本地上传</span>
                  <span v-else class="pending-text">—</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" min-width="135">
                <template #default="{ row }"><span v-if="row.nodeType === 'group'" class="pending-text">待关联基线</span><el-tag v-else size="small" :type="rowStatusType(row)">{{ rowStatusLabel(row) }}</el-tag></template>
              </el-table-column>
              <el-table-column label="操作" width="300">
                <template #default="{ row }">
                  <template v-if="row.nodeType === 'group'"><span class="pending-text">—</span></template>
                  <template v-else-if="row.custom">
                    <template v-if="canManageComponents">
                      <el-button size="small" @click="openConfiguration(row)">训练配置</el-button>
                      <el-button size="small" type="danger" plain @click="removeCustom(row)">移除</el-button>
                    </template>
                  </template>
                  <template v-else-if="!row.placeholder">
                    <div class="component-actions">
                      <el-button v-if="row.localStatus === 'not-downloaded'" size="small" :loading="isTaskRunning(row.id)" @click="installComponent(row)">下载</el-button>
                      <template v-else>
                        <el-button size="small" :disabled="row.localStatus !== 'downloaded' || isTaskRunning(row.id)" @click="openComponentPath(row)">打开路径</el-button>
                        <el-button size="small" type="danger" plain :loading="isTaskRunning(row.id)" @click="removeComponent(row)">{{ row.localStatus === 'partial' ? '清理' : '移除' }}</el-button>
                      </template>
                      <el-button v-if="row.nodeType === 'baseline'" size="small" type="primary" plain @click="openUpload(table.id, row.id)">上传算法</el-button>
                    </div>
                  </template>
                  <span v-else class="pending-text">预留位置</span>
                </template>
              </el-table-column>
            </el-table>
          </article>
        </div>

        <div v-if="componentTask.status" class="component-task">
          <div class="component-task-heading"><strong>{{ componentTask.message }}</strong><el-tag size="small" :type="taskStatusType">{{ taskStatusLabel }}</el-tag></div>
          <el-progress :percentage="Number(componentTask.progress || 0)" :status="taskProgressStatus" :stroke-width="12" />
          <el-collapse v-if="componentTask.logs?.length" class="component-logs"><el-collapse-item title="查看下载任务日志" name="component-logs"><div v-for="(log, index) in componentTask.logs" :key="`${index}-${log}`" class="component-log-line">{{ log }}</div></el-collapse-item></el-collapse>
        </div>
      </section>

      <el-dialog v-model="uploadDialogVisible" :title="legacyAlgorithm ? '本地算法训练配置' : '上传自定义算法'"
        width="640px" destroy-on-close :show-close="!uploadBusy" :close-on-click-modal="false"
        :close-on-press-escape="!uploadBusy" @close="cancelPendingUpload">
        <el-form label-width="105px">
          <el-form-item label="研究方向">{{ uploadDirectionTitle }}</el-form-item>
          <el-form-item v-if="legacyAlgorithm" label="算法名称">{{ legacyAlgorithm.name }}</el-form-item>
          <template v-else-if="!uploadChoices && uploadTask.status !== 'succeeded'">
            <el-form-item label="关联基线">
              <el-select v-model="uploadForm.baselineId" placeholder="可暂不选择" clearable style="width: 100%" :disabled="uploadBusy">
                <el-option v-for="baseline in uploadBaselines" :key="baseline.id" :label="baseline.name" :value="baseline.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="算法名称">
              <el-input v-model="uploadForm.name" maxlength="80" placeholder="例如：多尺度特征增强" :disabled="uploadBusy" />
            </el-form-item>
            <el-form-item label="算法 ZIP">
              <el-upload :auto-upload="false" :limit="1" accept=".zip" :disabled="uploadBusy"
                :on-change="handleUploadFile" :on-remove="clearUploadFile">
                <el-button>选择 ZIP 文件</el-button>
                <template #tip><div class="el-upload__tip">请选择不超过 500 MB 的 Ultralytics 源码 ZIP。</div></template>
              </el-upload>
            </el-form-item>
          </template>
          <template v-if="uploadChoices">
            <el-form-item label="模型 YAML" required>
              <el-select v-model="trainingSelection.model" filterable placeholder="选择模型配置" :disabled="uploadBusy" style="width: 100%">
                <el-option v-for="model in uploadChoices.models" :key="model" :label="model" :value="model" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="uploadChoices.weights?.length" label="初始权重">
              <el-select v-model="trainingSelection.weight" filterable clearable placeholder="可选，留空从头训练"
                :disabled="uploadBusy" style="width: 100%">
                <el-option v-for="weight in uploadChoices.weights" :key="weight" :label="weight" :value="weight" />
              </el-select>
            </el-form-item>
          </template>
          <el-alert v-if="uploadError" :title="uploadError" type="error" :closable="false" />
          <div v-if="uploadTask.message" class="upload-progress">
            <strong>{{ uploadTask.message }}</strong>
            <el-progress v-if="uploadBusy && !legacyAlgorithm" :percentage="uploadTask.progress" />
          </div>
        </el-form>
        <template #footer>
          <el-button :disabled="uploadBusy" @click="uploadDialogVisible = false">关闭</el-button>
          <el-button v-if="uploadChoices" type="primary" :loading="uploadBusy" @click="confirmTraining">确认训练配置</el-button>
          <el-button v-else-if="!legacyAlgorithm && uploadTask.status !== 'succeeded'" type="primary" :loading="uploadBusy" @click="submitUpload">开始上传</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { AlgorithmComponentService, CustomAlgorithmService, ResearchRegistryService } from '@/api/api'
import { buildComponentTree } from '@/utils/componentTree.js'
import { useUserStore } from '@/stores'
import { canMaintain } from '@/config/permissions'

const canManageComponents = computed(() => canMaintain(useUserStore().user))
const loading = ref(true)
const registry = ref({ directions: [], algorithms: [], conventions: [], registry: {} })
const errorMessage = ref('')
const errorDetails = ref('')
const componentLoading = ref(false)
const componentError = ref('')
const baselineComponents = ref([])
const algorithmComponents = ref([])
const customAlgorithms = ref([])
const componentTask = ref({})
const expandedRows = ref([])
const onExpandChange = (row, expanded) => {
  expandedRows.value = expanded
    ? [...new Set([...expandedRows.value, row.treeKey])]
    : expandedRows.value.filter(key => key !== row.treeKey)
}
const uploadDialogVisible = ref(false)
const uploadDirection = ref('')
const uploadForm = ref({ baselineId: '', name: '', file: null })
const uploadTask = ref({ status: 'idle', progress: 0, message: '' })
const uploadError = ref('')
const uploadChoices = ref(null)
const legacyAlgorithm = ref(null)
const trainingSelection = ref({ model: '', weight: '' })
const uploadBusy = computed(() => uploadTask.value.status === 'running')
let uploadDialogVersion = 0
let componentTimer

const directionDefinitions = [
  { id: 'small-object', title: '1. 小目标' },
  { id: 'knowledge-distillation', title: '2. 知识蒸馏' },
  { id: 'spatiotemporal', title: '3. 时空融合' },
]
const statusLabels = { draft: '开发中/待登记', review: '评审中', published: '已登记', '待登记': '开发中/待登记', uploaded: '开发中/待登记' }
const statusLabel = status => statusLabels[status] || status || '未登记'
const statusType = status => ({ draft: 'warning', review: 'warning', published: 'success', '待登记': 'warning', uploaded: 'warning' }[status] || 'info')
const directionDescription = direction => ({ 'small-object': '面向小尺度目标检测的实验方向', 'knowledge-distillation': '面向知识蒸馏研究的实验方向', spatiotemporal: '面向视频、序列和时空关联建模的实验方向' }[direction] || '该研究方向的仓库登记信息')
const directionTables = computed(() => directionDefinitions.map(definition => ({
  ...definition,
  rows: buildComponentTree(definition.id, baselineComponents.value, [...algorithmComponents.value, ...customAlgorithms.value]),
})))
const rowStatusLabel = row => {
  if (row.custom) return row.trainingReady ? '可创建训练任务' : '待配置训练'
  if (row.localStatus === 'downloaded') return '已下载'
  if (row.localStatus === 'partial') return '下载不完整'
  if (row.placeholder || row.registrationStatus === '待登记') return '开发中/待登记'
  return '未下载'
}
const rowStatusType = row => row.localStatus === 'partial' ? 'danger'
  : row.custom ? 'warning'
    : row.localStatus === 'downloaded' ? 'success'
      : rowStatusLabel(row) === '未下载' ? 'info' : 'warning'
const isTaskRunning = componentId => componentTask.value.status === 'running' && componentTask.value.componentId === componentId
const taskStatusLabel = computed(() => ({ running: '进行中', succeeded: '已完成', failed: '失败' }[componentTask.value.status] || '未开始'))
const taskStatusType = computed(() => ({ running: 'warning', succeeded: 'success', failed: 'danger' }[componentTask.value.status] || 'info'))
const taskProgressStatus = computed(() => componentTask.value.status === 'failed' ? 'exception' : componentTask.value.status === 'succeeded' ? 'success' : undefined)
const uploadDirectionTitle = computed(() => directionDefinitions.find(item => item.id === uploadDirection.value)?.title || '')
const uploadBaselines = computed(() => baselineComponents.value.filter(item => item.direction === uploadDirection.value))

const loadComponents = async () => {
  componentLoading.value = true
  componentError.value = ''
  try {
    const [baseline, algorithm, custom] = await Promise.all([AlgorithmComponentService.listBaselines(), AlgorithmComponentService.listAlgorithms(), CustomAlgorithmService.list()])
    if (baseline?.code !== 0 || algorithm?.code !== 0 || custom?.code !== 0) throw new Error(baseline?.msg || algorithm?.msg || custom?.msg || '组件状态读取失败')
    baselineComponents.value = baseline.data?.components || []
    algorithmComponents.value = algorithm.data?.components || []
    customAlgorithms.value = custom.data || []
    componentTask.value = baseline.data?.task?.status === 'running' ? baseline.data.task : algorithm.data?.task || {}
    clearTimeout(componentTimer)
    if (componentTask.value.status === 'running') componentTimer = setTimeout(loadComponents, 1500)
  } catch (error) { componentError.value = error?.message || '组件状态读取失败' }
  finally { componentLoading.value = false }
}

const installComponent = async row => {
  try {
    const response = row.kind === 'baseline' ? await AlgorithmComponentService.installBaseline(row.id) : await AlgorithmComponentService.installAlgorithm(row.id)
    if (response?.code !== 0) { componentError.value = response?.msg || '下载启动失败'; return }
    componentTask.value = response.data || {}
    clearTimeout(componentTimer); componentTimer = setTimeout(loadComponents, 300)
  } catch (error) { componentError.value = error?.msg || error?.message || '下载启动失败' }
}

const removeComponent = async row => {
  try {
    await ElMessageBox.confirm(`确定移除本机的“${row.name}”吗？远程仓库不会受影响。`, '移除组件', { type: 'warning' })
    const response = row.kind === 'baseline' ? await AlgorithmComponentService.removeBaseline(row.id) : await AlgorithmComponentService.removeAlgorithm(row.id)
    if (response?.code !== 0) { componentError.value = response?.msg || '移除启动失败'; return }
    componentTask.value = response.data || {}; clearTimeout(componentTimer); componentTimer = setTimeout(loadComponents, 300)
  } catch (error) { if (error !== 'cancel' && error !== 'close') componentError.value = error?.message || '移除已取消' }
}

const openComponentPath = async row => {
  try {
    const response = await AlgorithmComponentService.openPath(row.kind, row.id)
    if (response?.code !== 0) throw new Error(response?.msg || '打开仓库路径失败')
  } catch (error) { componentError.value = error?.msg || error?.message || '打开仓库路径失败' }
}

const removeCustom = async row => {
  try {
    await ElMessageBox.confirm(`确定移除本地算法“${row.name}”吗？`, '移除算法', { type: 'warning' })
    const response = await CustomAlgorithmService.remove(row.id)
    if (response?.code !== 0) { componentError.value = response?.msg || '移除失败'; return }
    await loadComponents()
  } catch (error) { if (error !== 'cancel' && error !== 'close') componentError.value = error?.message || '移除已取消' }
}

const openUpload = (direction, baselineId = '', name = '') => {
  uploadDialogVersion += 1
  uploadDirection.value = direction
  uploadForm.value = { baselineId, name, file: null }
  uploadTask.value = { status: 'idle', progress: 0, message: '' }
  uploadError.value = ''
  uploadChoices.value = null
  legacyAlgorithm.value = null
  trainingSelection.value = { model: '', weight: '' }
  uploadDialogVisible.value = true
}

const setChoices = data => {
  uploadChoices.value = data
  trainingSelection.value = {
    model: data.model || (data.models.length === 1 ? data.models[0] : ''),
    weight: data.pretrained || '',
  }
}

const openConfiguration = async row => {
  openUpload(row.direction, row.baselineId, row.name)
  legacyAlgorithm.value = row
  uploadTask.value = { status: 'running', progress: 0, message: '正在读取算法配置' }
  try {
    const response = await CustomAlgorithmService.inspect(row.id)
    if (response?.code !== 0) throw new Error(response?.msg || '无法读取算法配置')
    setChoices(response.data)
    uploadTask.value = { status: 'idle', progress: 0, message: '' }
  } catch (error) {
    showUploadError(error, '无法读取算法配置')
  }
}

const handleUploadFile = file => { uploadForm.value.file = file.raw || null }
const clearUploadFile = () => { uploadForm.value.file = null }

const cancelPendingUpload = async () => {
  uploadDialogVersion += 1
  const token = uploadChoices.value?.uploadToken
  uploadChoices.value = null
  if (!token) return
  try {
    await CustomAlgorithmService.cancel(token)
  } catch {
    // The server also expires abandoned upload sessions.
  }
}

const showUploadError = (error, fallback) => {
  uploadTask.value = { status: 'failed', progress: 0, message: '' }
  uploadError.value = error?.msg || error?.message || fallback
}

const onPublished = async row => {
  const parent = baselineComponents.value.some(item => item.id === row.baselineId && item.direction === row.direction)
    ? 'baseline:' + row.baselineId : 'unlinked:' + row.direction
  expandedRows.value = [...new Set([...expandedRows.value, parent])]
  uploadChoices.value = null
  uploadTask.value = { status: 'succeeded', progress: 100, message: '算法已可在「创建训练任务 → 算法基线」中选择' }
  customAlgorithms.value = [...customAlgorithms.value.filter(item => item.id !== row.id), row]
  await loadComponents()
}

const submitUpload = async () => {
  uploadError.value = ''
  const { name, file, baselineId } = uploadForm.value
  if (!name.trim()) { uploadError.value = '请填写算法名称'; return }
  if (!file) { uploadError.value = '请选择 ZIP 算法包'; return }
  if (!file.name.toLowerCase().endsWith('.zip') || file.size > 500 * 1024 * 1024) {
    uploadError.value = '请选择不超过 500 MB 的 ZIP 算法包'
    return
  }

  const form = new FormData()
  form.append('direction', uploadDirection.value)
  form.append('baselineId', baselineId || '')
  form.append('name', name.trim())
  form.append('file', file)
  uploadTask.value = { status: 'running', progress: 0, message: '正在上传算法包' }
  const version = uploadDialogVersion
  try {
    const response = await CustomAlgorithmService.upload(form, event => {
      if (version !== uploadDialogVersion || !event.total) return
      uploadTask.value.progress = Math.round(event.loaded * 100 / event.total)
      if (uploadTask.value.progress === 100) uploadTask.value.message = '正在解压并检查算法包'
    })
    if (version !== uploadDialogVersion) {
      if (response?.data?.uploadToken) await CustomAlgorithmService.cancel(response.data.uploadToken)
      return
    }
    if (response?.code !== 0) throw new Error(response?.msg || '上传失败')
    if (response.data?.uploadToken) {
      setChoices(response.data)
      uploadTask.value = { status: 'idle', progress: 100, message: '' }
    } else {
      await onPublished(response.data)
    }
  } catch (error) {
    if (version === uploadDialogVersion) showUploadError(error, '上传失败')
  }
}

const confirmTraining = async () => {
  uploadError.value = ''
  const { model, weight } = trainingSelection.value
  if (!model) { uploadError.value = '请选择模型 YAML'; return }
  const token = uploadChoices.value?.uploadToken
  uploadTask.value = { status: 'running', progress: 100, message: '正在保存训练配置' }
  try {
    const response = token
      ? await CustomAlgorithmService.complete(token, model, weight)
      : await CustomAlgorithmService.configure(legacyAlgorithm.value.id, model, weight)
    if (response?.code !== 0) throw new Error(response?.msg || '无法保存训练配置')
    await onPublished(response.data)
  } catch (error) {
    if (token) await cancelPendingUpload()
    showUploadError(error, '无法保存训练配置')
  }
}

onMounted(async () => {
  try {
    const response = await ResearchRegistryService.get()
    if (response?.code !== 0) { registry.value = response?.data || registry.value; errorMessage.value = response?.msg || '研究基线登记读取失败'; errorDetails.value = (response?.data?.errors || []).join('；') }
    else registry.value = response.data || registry.value
  } catch (error) { registry.value = error?.data || registry.value; errorMessage.value = error?.msg || '研究基线登记读取失败'; errorDetails.value = (error?.data?.errors || []).join('；') }
  finally { loading.value = false; loadComponents() }
})
onBeforeUnmount(() => {
  clearTimeout(componentTimer)
  cancelPendingUpload()
})
</script>

<style scoped>
.registry-page { padding: 4px 2px 28px; }
.registry-alert { margin-bottom: 18px; }
.registry-intro { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 22px; }
.registry-intro h2, .section-heading h3 { margin: 0; color: var(--el-text-color-primary); }
.registry-intro p, .section-heading span { color: var(--el-text-color-secondary); line-height: 1.6; }
.registry-intro p { margin: 8px 0 0; }
.registry-section { margin-top: 24px; }
.section-heading { display: flex; align-items: baseline; justify-content: space-between; gap: 16px; margin-bottom: 12px; }
.direction-tables { display: grid; grid-template-columns: minmax(0, 1fr); gap: 16px; align-items: start; }
.direction-table { overflow: hidden; border: 1px solid var(--el-border-color); border-radius: 6px; background: var(--el-bg-color); }
.direction-table-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; min-height: 88px; padding: 16px; background: var(--el-fill-color-light); }
.direction-table-heading h4 { margin: 0; color: var(--el-text-color-primary); font-size: 19px; }
.direction-table-heading span { display: block; margin-top: 8px; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.5; }
.tree-label { margin-left: 8px; }
.algorithm-count { margin-left: 8px; color: var(--el-text-color-secondary); font-size: 12px; }
.repository-table a { overflow: hidden; color: var(--el-color-primary); text-overflow: ellipsis; white-space: nowrap; }
.pending-text { color: var(--el-text-color-secondary); font-size: 12px; }
.component-blocker { margin-top: 4px; color: var(--el-text-color-secondary); font-size: 12px; }
.component-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.component-actions .el-button { margin-left: 0; }
.component-task, .upload-progress { margin-top: 16px; padding: 14px 16px; border: 1px solid var(--el-border-color); border-radius: 6px; background: var(--el-fill-color-lighter); }
.component-task-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 10px; }
.component-logs { margin-top: 10px; }
.component-logs :deep(.el-collapse-item__content) { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.7; }
.component-log-line { overflow-wrap: anywhere; white-space: pre-wrap; }
.el-upload__tip { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.5; }
@media (max-width: 700px) { .direction-table-heading { align-items: flex-start; flex-direction: column; } .section-heading { display: block; } .section-heading span { display: block; margin-top: 5px; } .registry-intro { display: block; } .registry-intro .el-tag { margin-top: 10px; } }
</style>
