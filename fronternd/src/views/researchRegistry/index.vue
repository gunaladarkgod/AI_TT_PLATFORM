<template>
  <div class="registry-page">
    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :description="errorDetails" show-icon :closable="false" class="registry-alert" />
    <template v-else-if="loading"><el-skeleton :rows="8" animated /></template>

    <template v-else>
      <div class="registry-intro">
        <div>
          <h2>研究基线公约</h2>
          <p>先查看研究方向和基线，再登记或上传自己的算法。</p>
        </div>
        <el-tag type="info">仓库状态</el-tag>
      </div>

      <section v-if="registry.registry?.platform_engines?.supported?.length" class="registry-section">
        <div class="section-heading"><h3>支持的训练引擎</h3><span>平台统一提供训练环境和调度能力</span></div>
        <div class="engine-list"><el-tag v-for="engine in registry.registry.platform_engines.supported" :key="engine.id" effect="plain">{{ engine.name }} · {{ engine.versions.join(' / ') }}</el-tag></div>
      </section>

      <section class="registry-section">
        <div class="section-heading"><h3>研究方向</h3><span>基线仓库状态和下载入口</span></div>
        <el-alert v-if="componentError" :title="componentError" type="warning" :closable="false" show-icon />
        <div v-loading="componentLoading" class="direction-tables">
          <article v-for="table in directionTables" :key="table.id" class="direction-table">
            <header class="direction-table-heading"><h4>{{ table.title }}</h4><span>{{ directionDescription(table.id) }}</span></header>
            <el-table :data="baselineRows(table)" stripe border size="small" row-key="id" class="repository-table">
              <el-table-column prop="name" label="基线" min-width="135" />
              <el-table-column label="仓库" min-width="220" show-overflow-tooltip>
                <template #default="{ row }">
                  <template v-if="row.repository"><a :href="row.repository" target="_blank" rel="noopener">{{ row.repository }}</a><div v-if="isIncomplete(row)" class="pending-text">开发中/待登记</div></template>
                  <span v-else class="pending-text">开发中/待登记</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" min-width="145">
                <template #default="{ row }"><el-tag size="small" :type="rowStatusType(row)">{{ rowStatusLabel(row) }}</el-tag></template>
              </el-table-column>
              <el-table-column label="操作" width="112">
                <template #default="{ row }">
                  <template v-if="!row.placeholder"><el-button v-if="row.localStatus === 'not-downloaded'" size="small" :loading="isTaskRunning(row.id)" @click="installComponent(row)">下载</el-button><el-button v-else size="small" type="danger" plain :loading="isTaskRunning(row.id)" @click="removeComponent(row)">移除</el-button></template>
                  <span v-else class="pending-text">预留位置</span>
                </template>
              </el-table-column>
            </el-table>
          </article>
        </div>
      </section>

      <section class="registry-section">
        <div class="section-heading"><h3>算法登记</h3><span>登记已有算法，或上传 ZIP 创建本地算法草稿</span></div>
        <div class="direction-tables">
          <article v-for="table in directionTables" :key="`algorithms-${table.id}`" class="direction-table">
            <header class="direction-table-heading algorithm-heading"><h4>{{ table.title }}</h4><el-button type="primary" size="small" @click="openUpload(table.id)">上传自定义算法</el-button></header>
            <el-table :data="algorithmRows(table)" stripe border size="small" row-key="id" class="repository-table">
              <el-table-column prop="name" label="算法" min-width="145" />
              <el-table-column label="仓库" min-width="220" show-overflow-tooltip>
                <template #default="{ row }">
                  <template v-if="row.repository && row.repository.startsWith('http')"><a :href="row.repository" target="_blank" rel="noopener">{{ row.repository }}</a><div v-if="isIncomplete(row)" class="pending-text">开发中/待登记</div></template>
                  <span v-else class="pending-text">{{ row.repository || '开发中/待登记' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" min-width="135"><template #default="{ row }"><el-tag size="small" :type="rowStatusType(row)">{{ rowStatusLabel(row) }}</el-tag></template></el-table-column>
              <el-table-column label="操作" width="125">
                <template #default="{ row }">
                  <template v-if="row.custom"><el-button size="small" type="danger" plain @click="removeCustom(row)">移除</el-button></template>
                  <template v-else-if="!row.placeholder"><el-button v-if="row.localStatus === 'not-downloaded'" size="small" :loading="isTaskRunning(row.id)" @click="installComponent(row)">下载</el-button><el-button v-else size="small" type="danger" plain :loading="isTaskRunning(row.id)" @click="removeComponent(row)">移除</el-button></template>
                  <span v-else class="pending-text">预留位置</span>
                </template>
              </el-table-column>
            </el-table>
          </article>
        </div>

        <div v-if="componentTask.status" class="component-task">
          <div class="component-task-heading"><strong>{{ componentTask.message }}</strong><el-tag size="small" :type="taskStatusType">{{ taskStatusLabel }}</el-tag></div>
          <el-progress :percentage="Number(componentTask.progress || 0)" :status="taskProgressStatus" :stroke-width="12" />
          <el-collapse v-if="componentTask.logs?.length" class="component-logs"><el-collapse-item title="查看下载任务日志" name="component-logs"><div v-for="(log, index) in componentTask.logs" :key="`${index}-${log}`">{{ log }}</div></el-collapse-item></el-collapse>
        </div>
      </section>

      <el-dialog v-model="uploadDialogVisible" title="上传自定义算法" width="520px" destroy-on-close>
        <el-form label-width="100px">
          <el-form-item label="研究方向"><span>{{ uploadDirectionTitle }}</span></el-form-item>
          <el-form-item label="基线"><el-select v-model="uploadForm.baselineId" placeholder="可暂不选择" clearable style="width: 100%"><el-option label="待登记基线" value="" /><el-option v-for="baseline in uploadBaselines" :key="baseline.id" :label="baseline.name" :value="baseline.id" /></el-select></el-form-item>
          <el-form-item label="算法名称"><el-input v-model="uploadForm.name" maxlength="80" placeholder="例如：多尺度特征增强" /></el-form-item>
          <el-form-item label="算法 ZIP"><el-upload :auto-upload="false" :limit="1" accept=".zip" :on-change="handleUploadFile" :on-remove="clearUploadFile"><el-button>选择 ZIP 文件</el-button><template #tip><div class="el-upload__tip">上传源码包，大小不超过 500 MB；平台只保存到本机待登记目录。</div></template></el-upload></el-form-item>
          <el-alert v-if="uploadError" :title="uploadError" type="error" :closable="false" />
          <div v-if="uploadTask.status !== 'idle'" class="upload-progress"><div class="component-task-heading"><strong>{{ uploadTask.message }}</strong><el-tag size="small" :type="uploadTask.status === 'failed' ? 'danger' : uploadTask.status === 'succeeded' ? 'success' : 'warning'">{{ uploadTask.status === 'running' ? '上传中' : uploadTask.status === 'succeeded' ? '已完成' : '失败' }}</el-tag></div><el-progress :percentage="uploadTask.progress" :status="uploadTask.status === 'failed' ? 'exception' : uploadTask.status === 'succeeded' ? 'success' : undefined" /></div>
        </el-form>
        <template #footer><el-button @click="uploadDialogVisible = false">关闭</el-button><el-button type="primary" :loading="uploadTask.status === 'running'" @click="submitUpload">开始上传</el-button></template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { AlgorithmComponentService, CustomAlgorithmService, ResearchRegistryService } from '@/api/api'

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
const uploadDialogVisible = ref(false)
const uploadDirection = ref('')
const uploadForm = ref({ baselineId: '', name: '', file: null })
const uploadTask = ref({ status: 'idle', progress: 0, message: '' })
const uploadError = ref('')
let componentTimer

const directionDefinitions = [
  { id: 'small-object', title: '1. 小目标' },
  { id: 'knowledge-distillation', title: '2. 知识蒸馏' },
  { id: 'spatiotemporal', title: '3. 时空融合' },
]
const baselinePlaceholders = ['YOLOv5', 'YOLOv8', 'YOLO11', 'DETR']
const algorithmPlaceholders = ['算法1', '算法2', '算法3']
const statusLabels = { draft: '开发中/待登记', review: '评审中', published: '已登记', '待登记': '开发中/待登记', uploaded: '开发中/待登记' }
const statusLabel = status => statusLabels[status] || status || '未登记'
const statusType = status => ({ draft: 'warning', review: 'warning', published: 'success', '待登记': 'warning', uploaded: 'warning' }[status] || 'info')
const componentStatusLabel = status => ({ 'not-downloaded': '未下载', downloaded: '已下载', uploaded: '已上传' }[status] || '未知')
const componentStatusType = status => ({ 'not-downloaded': 'warning', downloaded: 'success', uploaded: 'success' }[status] || 'info')
const directionDescription = direction => ({ 'small-object': '面向小尺度目标检测的实验方向', 'knowledge-distillation': '面向知识蒸馏研究的实验方向', spatiotemporal: '面向视频、序列和时空关联建模的实验方向' }[direction] || '该研究方向的仓库登记信息')
const directionTables = computed(() => directionDefinitions.map(definition => ({
  ...definition,
  direction: registry.value.directions?.find(item => item.id === definition.id),
  baselines: baselineComponents.value.filter(item => item.direction === definition.id),
  algorithms: [...algorithmComponents.value, ...customAlgorithms.value].filter(item => item.direction === definition.id),
})))
const baselineRows = table => [...table.baselines, ...baselinePlaceholders.filter(name => !table.baselines.some(row => row.name?.toLowerCase().includes(name.toLowerCase()))).map((name, index) => ({ id: `${table.id}/placeholder-${index}`, name, direction: table.id, placeholder: true, registrationStatus: '待登记', localStatus: 'not-downloaded' }))]
const algorithmRows = table => table.algorithms.length ? [...table.algorithms, ...algorithmPlaceholders.slice(table.algorithms.length).map((name, index) => ({ id: `${table.id}/algorithm-placeholder-${index}`, name, direction: table.id, placeholder: true, registrationStatus: '待登记' }))] : algorithmPlaceholders.map((name, index) => ({ id: `${table.id}/algorithm-placeholder-${index}`, name, direction: table.id, placeholder: true, registrationStatus: '待登记' }))
const isIncomplete = row => row.placeholder || row.registrationStatus === 'draft' || row.registrationStatus === '待登记' || row.registrationStatus === 'uploaded'
const rowStatusLabel = row => row.placeholder ? '开发中/待登记' : row.custom ? '已上传/待登记' : row.localStatus === 'partial' ? '下载不完整' : row.registrationStatus === 'draft' ? '开发中/待登记' : row.localStatus === 'downloaded' ? '已下载' : statusLabel(row.registrationStatus)
const rowStatusType = row => row.localStatus === 'partial' ? 'danger' : row.placeholder || isIncomplete(row) ? 'warning' : row.localStatus === 'downloaded' || row.localStatus === 'uploaded' ? 'success' : statusType(row.registrationStatus)
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
    if (componentTask.value.status === 'running') { clearTimeout(componentTimer); componentTimer = setTimeout(loadComponents, 1500) }
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

const removeCustom = async row => {
  try {
    await ElMessageBox.confirm(`确定移除本地算法“${row.name}”吗？`, '移除算法', { type: 'warning' })
    const response = await CustomAlgorithmService.remove(row.id)
    if (response?.code !== 0) { componentError.value = response?.msg || '移除失败'; return }
    await loadComponents()
  } catch (error) { if (error !== 'cancel' && error !== 'close') componentError.value = error?.message || '移除已取消' }
}

const openUpload = direction => { uploadDirection.value = direction; uploadForm.value = { baselineId: '', name: '', file: null }; uploadTask.value = { status: 'idle', progress: 0, message: '' }; uploadError.value = ''; uploadDialogVisible.value = true }
const handleUploadFile = file => { uploadForm.value.file = file.raw || null }
const clearUploadFile = () => { uploadForm.value.file = null }
const submitUpload = async () => {
  uploadError.value = ''
  if (!uploadForm.value.name.trim()) { uploadError.value = '请填写算法名称'; return }
  if (!uploadForm.value.file) { uploadError.value = '请选择 ZIP 算法包'; return }
  const form = new FormData()
  form.append('direction', uploadDirection.value); form.append('baselineId', uploadForm.value.baselineId || ''); form.append('name', uploadForm.value.name.trim()); form.append('file', uploadForm.value.file)
  uploadTask.value = { status: 'running', progress: 0, message: '正在上传算法包' }
  try {
    const response = await CustomAlgorithmService.upload(form, event => { if (event.total) uploadTask.value.progress = Math.round(event.loaded * 100 / event.total) })
    if (response?.code !== 0) { uploadTask.value = { status: 'failed', progress: 0, message: '上传失败' }; uploadError.value = response?.msg || '上传失败'; return }
    uploadTask.value = { status: 'succeeded', progress: 100, message: '算法包已上传，等待登记' }
    await loadComponents()
  } catch (error) { uploadTask.value = { status: 'failed', progress: 0, message: '上传失败' }; uploadError.value = error?.msg || error?.message || '上传失败' }
}

onMounted(async () => {
  try {
    const response = await ResearchRegistryService.get()
    if (response?.code !== 0) { registry.value = response?.data || registry.value; errorMessage.value = response?.msg || '研究基线登记读取失败'; errorDetails.value = (response?.data?.errors || []).join('；') }
    else registry.value = response.data || registry.value
  } catch (error) { registry.value = error?.data || registry.value; errorMessage.value = error?.msg || '研究基线登记读取失败'; errorDetails.value = (error?.data?.errors || []).join('；') }
  finally { loading.value = false; loadComponents() }
})
onBeforeUnmount(() => clearTimeout(componentTimer))
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
.engine-list { display: flex; flex-wrap: wrap; gap: 8px; }
.direction-tables { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; align-items: start; }
.direction-table { overflow: hidden; border: 1px solid var(--el-border-color); border-radius: 6px; background: var(--el-bg-color); }
.direction-table-heading { min-height: 88px; padding: 16px; background: var(--el-fill-color-light); }
.direction-table-heading h4 { margin: 0; color: var(--el-text-color-primary); font-size: 19px; }
.direction-table-heading span { display: block; margin-top: 8px; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.5; }
.algorithm-heading { display: flex; align-items: center; justify-content: space-between; gap: 10px; min-height: 62px; }
.repository-table a { overflow: hidden; color: var(--el-color-primary); text-overflow: ellipsis; white-space: nowrap; }
.pending-text { color: var(--el-text-color-secondary); font-size: 12px; }
.component-blocker { margin-top: 4px; color: var(--el-text-color-secondary); font-size: 12px; }
.component-task, .upload-progress { margin-top: 16px; padding: 14px 16px; border: 1px solid var(--el-border-color); border-radius: 6px; background: var(--el-fill-color-lighter); }
.component-task-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 10px; }
.component-logs { margin-top: 10px; }
.component-logs :deep(.el-collapse-item__content) { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.7; }
.el-upload__tip { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.5; }
@media (max-width: 1050px) { .direction-tables { grid-template-columns: 1fr; } }
@media (max-width: 700px) { .section-heading { display: block; } .section-heading span { display: block; margin-top: 5px; } .registry-intro { display: block; } .registry-intro .el-tag { margin-top: 10px; } }
</style>
