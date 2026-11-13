<template>
  <div class="registry-page">
    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :description="errorDetails" show-icon :closable="false" class="registry-alert" />
    <template v-else-if="loading"><el-skeleton :rows="8" animated /></template>

    <template v-else>
      <div class="registry-intro">
        <div>
          <h2>研究基线公约</h2>
          <p>按研究方向选择基线和算法仓库，下载后再选择数据集进行训练。</p>
        </div>
        <el-tag type="info">只读登记</el-tag>
      </div>

      <section v-if="registry.registry?.platform_engines?.supported?.length" class="registry-section">
        <div class="section-heading"><h3>支持的训练引擎</h3><span>平台统一提供训练环境和调度能力</span></div>
        <div class="engine-list">
          <el-tag v-for="engine in registry.registry.platform_engines.supported" :key="engine.id" effect="plain">{{ engine.name }} · {{ engine.versions.join(' / ') }}</el-tag>
        </div>
      </section>

      <section class="registry-section">
        <div class="section-heading"><h3>研究方向与算法仓库</h3><span>基线仓库显示在表格标题，算法仓库列在对应方向下方</span></div>
        <el-alert v-if="componentError" :title="componentError" type="warning" :closable="false" show-icon />
        <div v-loading="componentLoading" class="direction-tables">
          <article v-for="table in directionTables" :key="table.id" class="direction-table">
            <header class="direction-table-heading">
              <div class="direction-table-title">
                <h4>{{ table.title }}</h4>
                <span>{{ directionDescription(table.id) }}</span>
              </div>
              <div class="baseline-repositories">
                <span class="baseline-label">基线仓库</span>
                <template v-if="table.baselines.length">
                  <div v-for="baseline in table.baselines" :key="baseline.id" class="baseline-repository">
                    <a :href="baseline.repository" target="_blank" rel="noopener">{{ baseline.repository || '未登记' }}</a>
                    <el-tag size="small" :type="componentStatusType(baseline.localStatus)">{{ componentStatusLabel(baseline.localStatus) }}</el-tag>
                    <el-button v-if="baseline.localStatus === 'not-downloaded'" size="small" :loading="isTaskRunning(baseline.id)" @click="installComponent(baseline)">下载基线</el-button>
                    <el-button v-else size="small" type="danger" plain :loading="isTaskRunning(baseline.id)" @click="removeComponent(baseline)">移除基线</el-button>
                  </div>
                </template>
                <span v-else class="unregistered">未登记</span>
              </div>
            </header>

            <el-table :data="algorithmRows(table)" stripe border size="small" row-key="id" class="algorithm-table">
              <el-table-column label="算法仓库" min-width="350">
                <template #default="{ row }">
                  <template v-if="row.placeholder"><span class="unregistered">未登记</span></template>
                  <template v-else><a :href="row.repository" target="_blank" rel="noopener">{{ row.repository || '未登记' }}</a><div class="algorithm-name">{{ row.name }}</div></template>
                </template>
              </el-table-column>
              <el-table-column label="登记状态" width="120">
                <template #default="{ row }"><span v-if="row.placeholder" class="unregistered">未登记</span><el-tag v-else size="small" :type="statusType(row.registrationStatus)">{{ statusLabel(row.registrationStatus) }}</el-tag></template>
              </el-table-column>
              <el-table-column label="本机状态" min-width="190">
                <template #default="{ row }">
                  <span v-if="row.placeholder" class="unregistered">未登记</span>
                  <template v-else><el-tag size="small" :type="componentStatusType(row.localStatus)">{{ componentStatusLabel(row.localStatus) }}</el-tag><div v-if="row.blockers?.length" class="component-blocker">{{ row.blockers.join('；') }}</div></template>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="140">
                <template #default="{ row }">
                  <template v-if="!row.placeholder"><el-button v-if="row.localStatus === 'not-downloaded'" size="small" :loading="isTaskRunning(row.id)" @click="installComponent(row)">下载算法</el-button><el-button v-else size="small" type="danger" plain :loading="isTaskRunning(row.id)" @click="removeComponent(row)">移除算法</el-button></template>
                  <span v-else class="unregistered">预留位置</span>
                </template>
              </el-table-column>
            </el-table>
          </article>
        </div>

        <div v-if="componentTask.status" class="component-task">
          <div class="component-task-heading"><strong>{{ componentTask.message }}</strong><el-tag size="small" :type="taskStatusType">{{ taskStatusLabel }}</el-tag></div>
          <el-progress :percentage="Number(componentTask.progress || 0)" :status="taskProgressStatus" :stroke-width="12" />
          <el-collapse v-if="componentTask.logs?.length" class="component-logs"><el-collapse-item title="查看任务日志" name="component-logs"><div v-for="(log, index) in componentTask.logs" :key="`${index}-${log}`">{{ log }}</div></el-collapse-item></el-collapse>
        </div>
      </section>

    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { AlgorithmComponentService, ResearchRegistryService } from '@/api/api'

const loading = ref(true)
const registry = ref({ directions: [], algorithms: [], conventions: [], registry: {} })
const errorMessage = ref('')
const errorDetails = ref('')
const componentLoading = ref(false)
const componentError = ref('')
const baselineComponents = ref([])
const algorithmComponents = ref([])
const componentTask = ref({})
let componentTimer

const directionDefinitions = [
  { id: 'small-object', title: '1. 小目标' },
  { id: 'knowledge-distillation', title: '2. 知识蒸馏' },
  { id: 'spatiotemporal', title: '3. 时空融合' },
]
const statusLabels = { draft: '草稿', review: '评审中', published: '已发布' }
const statusLabel = status => statusLabels[status] || status || '未知'
const statusType = status => ({ draft: 'info', review: 'warning', published: 'success' }[status] || 'danger')
const componentStatusLabel = status => ({ 'not-downloaded': '未下载', downloaded: '已下载' }[status] || '未知')
const componentStatusType = status => ({ 'not-downloaded': 'warning', downloaded: 'success' }[status] || 'info')
const directionDescription = direction => ({
  'small-object': '面向小尺度目标检测的实验方向',
  'knowledge-distillation': '面向知识蒸馏研究的实验方向',
  spatiotemporal: '面向视频、序列和时空关联建模的实验方向',
}[direction] || '该研究方向的仓库登记信息')
const directionTables = computed(() => directionDefinitions.map(definition => ({
  ...definition,
  direction: registry.value.directions?.find(item => item.id === definition.id),
  baselines: baselineComponents.value.filter(item => item.direction === definition.id),
  algorithms: algorithmComponents.value.filter(item => item.direction === definition.id),
})))
const algorithmRows = table => table.algorithms.length ? table.algorithms : [{ id: `${table.id}-unregistered`, placeholder: true }]
const isTaskRunning = componentId => componentTask.value.status === 'running' && componentTask.value.componentId === componentId
const taskStatusLabel = computed(() => ({ running: '进行中', succeeded: '已完成', failed: '失败' }[componentTask.value.status] || '未开始'))
const taskStatusType = computed(() => ({ running: 'warning', succeeded: 'success', failed: 'danger' }[componentTask.value.status] || 'info'))
const taskProgressStatus = computed(() => componentTask.value.status === 'failed' ? 'exception' : componentTask.value.status === 'succeeded' ? 'success' : undefined)

const loadComponents = async () => {
  componentLoading.value = true
  componentError.value = ''
  try {
    const [baseline, algorithm] = await Promise.all([AlgorithmComponentService.listBaselines(), AlgorithmComponentService.listAlgorithms()])
    if (baseline?.code !== 0 || algorithm?.code !== 0) throw new Error(baseline?.msg || algorithm?.msg || '组件状态读取失败')
    baselineComponents.value = baseline.data?.components || []
    algorithmComponents.value = algorithm.data?.components || []
    componentTask.value = baseline.data?.task?.status === 'running' ? baseline.data.task : algorithm.data?.task || {}
    if (componentTask.value.status === 'running') {
      clearTimeout(componentTimer)
      componentTimer = setTimeout(loadComponents, 1500)
    }
  } catch (error) {
    componentError.value = error?.message || '组件状态读取失败'
  } finally { componentLoading.value = false }
}

const installComponent = async row => {
  try {
    const response = row.kind === 'baseline' ? await AlgorithmComponentService.installBaseline(row.id) : await AlgorithmComponentService.installAlgorithm(row.id)
    if (response?.code !== 0) { componentError.value = response?.msg || '下载启动失败'; return }
    componentTask.value = response.data || {}
    clearTimeout(componentTimer)
    componentTimer = setTimeout(loadComponents, 300)
  } catch (error) { componentError.value = error?.msg || error?.message || '下载启动失败' }
}

const removeComponent = async row => {
  try {
    await ElMessageBox.confirm(`确定移除本机的“${row.name}”吗？远程仓库不会受影响。`, '移除组件', { type: 'warning' })
    const response = row.kind === 'baseline' ? await AlgorithmComponentService.removeBaseline(row.id) : await AlgorithmComponentService.removeAlgorithm(row.id)
    if (response?.code !== 0) { componentError.value = response?.msg || '移除启动失败'; return }
    componentTask.value = response.data || {}
    clearTimeout(componentTimer)
    componentTimer = setTimeout(loadComponents, 300)
  } catch (error) { if (error !== 'cancel' && error !== 'close') componentError.value = error?.message || '移除已取消' }
}

onMounted(async () => {
  try {
    const response = await ResearchRegistryService.get()
    if (response?.code !== 0) {
      registry.value = response?.data || registry.value
      errorMessage.value = response?.msg || '研究基线登记读取失败'
      errorDetails.value = (response?.data?.errors || []).join('；')
    } else registry.value = response.data || registry.value
  } catch (error) {
    registry.value = error?.data || registry.value
    errorMessage.value = error?.msg || '研究基线登记读取失败'
    errorDetails.value = (error?.data?.errors || []).join('；')
  } finally {
    loading.value = false
    loadComponents()
  }
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
.direction-table-heading { min-height: 184px; padding: 16px; background: var(--el-fill-color-light); }
.direction-table-title h4 { margin: 0; color: var(--el-text-color-primary); font-size: 19px; }
.direction-table-title span { display: block; margin-top: 8px; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.5; }
.baseline-repositories { margin-top: 18px; }
.baseline-label { display: block; margin-bottom: 7px; color: var(--el-text-color-primary); font-size: 13px; font-weight: 700; }
.baseline-repository { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; align-items: center; margin-top: 7px; }
.baseline-repository a, .algorithm-table a { overflow: hidden; color: var(--el-color-primary); text-overflow: ellipsis; white-space: nowrap; }
.baseline-repository .el-button { grid-column: 1 / -1; justify-self: start; }
.algorithm-name { margin-top: 4px; color: var(--el-text-color-secondary); font-size: 12px; }
.unregistered { color: var(--el-text-color-secondary); font-size: 13px; }
.component-blocker, .component-task { margin-top: 5px; color: var(--el-text-color-secondary); font-size: 12px; }
.component-task { padding: 14px 16px; border: 1px solid var(--el-border-color); border-radius: 6px; background: var(--el-fill-color-lighter); }
.component-task-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 10px; }
.component-logs { margin-top: 10px; }
.component-logs :deep(.el-collapse-item__content) { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.7; }
a { color: var(--el-color-primary); }
.convention-content { margin: 0; padding: 12px; background: var(--el-fill-color-light); color: var(--el-text-color-regular); white-space: pre-wrap; font: inherit; line-height: 1.6; }
@media (max-width: 1050px) { .direction-tables { grid-template-columns: 1fr; } .direction-table-heading { min-height: 0; } }
@media (max-width: 700px) { .section-heading { display: block; } .section-heading span { display: block; margin-top: 5px; } .registry-intro { display: block; } .registry-intro .el-tag { margin-top: 10px; } }
</style>
