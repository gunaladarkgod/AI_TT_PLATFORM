<template>
  <div class="registry-page">
    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      :description="errorDetails"
      show-icon
      :closable="false"
      class="registry-alert"
    />

    <template v-else-if="loading">
      <el-skeleton :rows="8" animated />
    </template>

    <template v-else>
      <div class="registry-intro">
        <div>
          <h2>{{ registry.registry?.name || '研究基线公约' }}</h2>
          <p>{{ registry.registry?.description }}</p>
        </div>
        <el-tag type="info">只读登记</el-tag>
      </div>

      <el-empty v-if="!registry.directions?.length && !registry.algorithms?.length" description="当前没有可展示的登记内容" />

      <section v-if="registry.registry?.platform_engines?.supported?.length" class="registry-section">
        <div class="section-heading">
          <h3>A 层公共引擎</h3>
          <span>{{ registry.registry.platform_engines.description }}</span>
        </div>
        <div class="engine-list">
          <el-tag v-for="engine in registry.registry.platform_engines.supported" :key="engine.id" effect="plain">
            {{ engine.name }} · {{ engine.versions.join(' / ') }}
          </el-tag>
        </div>
      </section>

      <section v-if="registry.directions?.length" class="registry-section">
        <div class="section-heading">
          <h3>研究方向</h3>
          <span>公共数据格式、评测指标和引擎兼容条件</span>
        </div>
        <div class="direction-grid">
          <el-card v-for="direction in registry.directions" :key="direction.id" shadow="never" class="direction-card">
            <template #header>
              <div class="card-heading">
                <strong>{{ direction.code }} · {{ direction.name }}</strong>
                <el-tag size="small" :type="direction.status === 'active' ? 'success' : 'info'">{{ direction.status }}</el-tag>
              </div>
            </template>
            <p>{{ direction.description }}</p>
            <div class="registry-meta"><b>数据格式</b><span>{{ (direction.data_formats || []).join('、') || '—' }}</span></div>
            <div class="registry-meta"><b>评测指标</b><span>{{ (direction.metrics || []).join('、') || '—' }}</span></div>
            <div class="registry-meta"><b>引擎兼容</b><span>{{ compatibilityText(direction.engine_compatibility) }}</span></div>
          </el-card>
        </div>
      </section>

      <section v-if="registry.registry?.baselines?.length" class="registry-section">
        <div class="section-heading">
          <h3>已登记基线</h3>
          <span>选择并下载需要的基线；状态为草稿时表示尚未通过平台清单验证</span>
        </div>
        <el-table :data="registry.registry.baselines" stripe border size="small">
          <el-table-column prop="code" label="方向" width="82" />
          <el-table-column prop="name" label="基线" min-width="210" />
          <el-table-column label="引擎" width="130"><template #default="{ row }">{{ row.engine?.id || '—' }}</template></el-table-column>
          <el-table-column label="版本" width="110"><template #default="{ row }">{{ row.engine?.version || '—' }}</template></el-table-column>
          <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
          <el-table-column label="仓库" min-width="260" show-overflow-tooltip><template #default="{ row }"><a :href="row.repository?.url" target="_blank" rel="noopener">{{ row.repository?.url || '未登记' }}</a></template></el-table-column>
        </el-table>
      </section>

      <section class="registry-section">
        <div class="section-heading">
          <h3>B 基线与 C 算法</h3>
          <span>每个基线单独管理；C 算法只显示在它派生的基线下面</span>
        </div>
        <el-alert v-if="componentError" :title="componentError" type="warning" :closable="false" show-icon />
        <div v-loading="componentLoading" class="baseline-groups">
          <div v-for="group in baselineGroups" :key="group.id" class="baseline-group">
            <div class="baseline-group-heading">
              <div>
                <span class="baseline-code">{{ group.code || directionCode(group.direction) }}</span>
                <strong>{{ group.name }}</strong>
              </div>
              <span class="baseline-group-direction">{{ group.direction }}</span>
            </div>
            <el-table
              :data="rowsForGroup(group)"
              stripe
              border
              size="small"
              row-key="id"
              class="component-table"
              :row-class-name="componentRowClass"
            >
              <el-table-column label="组件" min-width="330">
                <template #default="{ row }">
                  <div v-if="row.kind === 'baseline'" class="baseline-row-title">基线下载 · {{ row.name }}</div>
                  <div v-else class="algorithm-row-title">{{ row.code || 'C 算法' }} · {{ row.name }}</div>
                  <div class="component-id">{{ row.id }}</div>
                </template>
              </el-table-column>
              <el-table-column label="仓库" min-width="270" show-overflow-tooltip>
                <template #default="{ row }"><a :href="row.repository" target="_blank" rel="noopener">{{ row.repository || '未登记' }}</a></template>
              </el-table-column>
              <el-table-column label="本机状态" min-width="190">
                <template #default="{ row }">
                  <el-tag size="small" :type="componentStatusType(row.localStatus)">{{ componentStatusLabel(row.localStatus) }}</el-tag>
                  <div v-if="row.blockers?.length" class="component-blocker">{{ row.blockers.join('；') }}</div>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="150">
                <template #default="{ row }">
                  <el-button v-if="row.localStatus === 'not-downloaded'" size="small" :loading="componentTask.componentId === row.id && componentTask.status === 'running'" @click="installComponent(row)">下载</el-button>
                  <el-button v-else size="small" type="danger" plain :loading="componentTask.componentId === row.id && componentTask.status === 'running'" @click="removeComponent(row)">移除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <div v-if="unboundAlgorithms.length" class="baseline-group">
            <div class="baseline-group-heading"><div><span class="baseline-code">C</span><strong>未关联基线的算法</strong></div></div>
            <el-table :data="unboundAlgorithms" stripe border size="small" class="component-table" :row-class-name="componentRowClass">
              <el-table-column label="组件" min-width="330"><template #default="{ row }"><div class="algorithm-row-title">{{ row.code || 'C 算法' }} · {{ row.name }}</div><div class="component-id">{{ row.id }}</div></template></el-table-column>
              <el-table-column label="状态" min-width="190"><template #default="{ row }"><el-tag size="small" :type="componentStatusType(row.localStatus)">{{ componentStatusLabel(row.localStatus) }}</el-tag><div v-if="row.blockers?.length" class="component-blocker">{{ row.blockers.join('；') }}</div></template></el-table-column>
              <el-table-column label="操作" width="150"><template #default="{ row }"><el-button v-if="row.localStatus === 'not-downloaded'" size="small" @click="installComponent(row)">下载</el-button><el-button v-else size="small" type="danger" plain @click="removeComponent(row)">移除</el-button></template></el-table-column>
            </el-table>
          </div>
        </div>
        <div v-if="componentTask.status" class="component-task">
          <div class="component-task-heading"><strong>{{ componentTask.message }}</strong><el-tag size="small" :type="taskStatusType">{{ taskStatusLabel }}</el-tag></div>
          <el-progress :percentage="Number(componentTask.progress || 0)" :status="taskProgressStatus" :stroke-width="12" />
          <el-collapse v-if="componentTask.logs?.length" class="component-logs">
            <el-collapse-item title="查看任务日志" name="component-logs"><div v-for="(log, index) in componentTask.logs" :key="`${index}-${log}`">{{ log }}</div></el-collapse-item>
          </el-collapse>
        </div>
      </section>

      <section class="registry-section">
        <div class="section-heading">
          <h3>算法登记</h3>
          <span>登记信息用于识别兼容条件；源码访问仍由独立算法仓库控制</span>
        </div>
        <el-table :data="registry.algorithms || []" stripe border size="small" empty-text="当前没有已登记算法">
          <el-table-column prop="name" label="名称" min-width="230" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }"><el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="engine.id" label="引擎" width="120"><template #default="{ row }">{{ row.engine?.id || '—' }}</template></el-table-column>
          <el-table-column label="版本" width="110"><template #default="{ row }">{{ row.engine?.version || '—' }}</template></el-table-column>
          <el-table-column prop="data_format" label="数据格式" width="110" />
          <el-table-column label="最低平台版本" width="140"><template #default="{ row }">{{ row.platform?.min_version || '未指定' }}</template></el-table-column>
          <el-table-column label="仓库" min-width="220" show-overflow-tooltip>
            <template #default="{ row }"><a :href="row.repository?.url" target="_blank" rel="noopener">{{ row.repository?.url || '未登记' }}</a></template>
          </el-table-column>
        </el-table>
      </section>

      <section class="registry-section">
        <div class="section-heading"><h3>公约文档</h3><span>登记字段和实验约束</span></div>
        <el-collapse>
          <el-collapse-item v-for="document in registry.conventions || []" :key="document.id" :name="document.id" :title="document.title">
            <pre class="convention-content">{{ document.content }}</pre>
          </el-collapse-item>
        </el-collapse>
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

const statusLabels = { draft: '草稿', review: '评审中', published: '已发布' }
const statusLabel = (status) => statusLabels[status] || status || '未知'
const statusType = (status) => ({ draft: 'info', review: 'warning', published: 'success' }[status] || 'danger')
const compatibilityText = (items = []) => (items || []).map(item => `${item.engine} ${item.versions?.join('/') || ''}`).join('、') || '—'
const componentStatusLabel = status => ({ 'not-downloaded': '未下载', downloaded: '已下载', invalid: '不可用' }[status] || '未知')
const componentStatusType = status => ({ 'not-downloaded': 'warning', downloaded: 'success', invalid: 'danger' }[status] || 'info')
const directionCode = direction => registry.value.directions?.find(item => item.id === direction)?.code || direction || 'B'
const baselineGroups = computed(() => {
  const groups = baselineComponents.value.map(baseline => ({
    ...baseline,
    algorithms: algorithmComponents.value.filter(algorithm => algorithm.baselineId === baseline.id),
  }))
  return groups
})
const unboundAlgorithms = computed(() => {
  const baselineIds = new Set(baselineComponents.value.map(item => item.id))
  return algorithmComponents.value.filter(item => !item.baselineId || !baselineIds.has(item.baselineId))
})
const rowsForGroup = group => [group, ...group.algorithms]
const componentRowClass = ({ row }) => row.kind === 'baseline' ? 'baseline-row' : 'algorithm-row'
const taskStatusLabel = computed(() => ({ running: '进行中', succeeded: '已完成', failed: '失败' }[componentTask.value.status] || '未开始'))
const taskStatusType = computed(() => ({ running: 'warning', succeeded: 'success', failed: 'danger' }[componentTask.value.status] || 'info'))
const taskProgressStatus = computed(() => componentTask.value.status === 'failed' ? 'exception' : componentTask.value.status === 'succeeded' ? 'success' : undefined)

const loadComponents = async () => {
  componentLoading.value = true
  componentError.value = ''
  try {
    const [baseline, algorithm] = await Promise.all([
      AlgorithmComponentService.listBaselines(), AlgorithmComponentService.listAlgorithms(),
    ])
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
    const response = row.kind === 'baseline'
      ? await AlgorithmComponentService.installBaseline(row.id)
      : await AlgorithmComponentService.installAlgorithm(row.id)
    if (response?.code !== 0) { componentError.value = response?.msg || '下载启动失败'; return }
    componentTask.value = response.data || {}
    clearTimeout(componentTimer)
    componentTimer = setTimeout(loadComponents, 300)
  } catch (error) { componentError.value = error?.msg || error?.message || '下载启动失败' }
}

const removeComponent = async row => {
  try {
    await ElMessageBox.confirm(`确定移除本机的“${row.name}”吗？远程仓库不会受影响。`, '移除组件', { type: 'warning' })
    const response = row.kind === 'baseline'
      ? await AlgorithmComponentService.removeBaseline(row.id)
      : await AlgorithmComponentService.removeAlgorithm(row.id)
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
    } else {
      registry.value = response.data || registry.value
    }
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
.registry-intro p, .section-heading span, .direction-card p { color: var(--el-text-color-secondary); line-height: 1.6; }
.registry-intro p { margin: 8px 0 0; }
.registry-section { margin-top: 24px; }
.section-heading { display: flex; align-items: baseline; justify-content: space-between; gap: 16px; margin-bottom: 12px; }
.engine-list { display: flex; flex-wrap: wrap; gap: 8px; }
.direction-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 14px; }
.direction-card { border-radius: 6px; }
.card-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.direction-card p { margin: 0 0 14px; }
.registry-meta { display: grid; grid-template-columns: 78px 1fr; gap: 8px; margin-top: 8px; font-size: 13px; }
.registry-meta b { color: var(--el-text-color-primary); }
.registry-meta span { color: var(--el-text-color-secondary); }
.baseline-groups { display: grid; gap: 18px; }
.baseline-group { overflow: hidden; border: 1px solid var(--el-border-color); border-radius: 6px; background: var(--el-bg-color); }
.baseline-group-heading { display: flex; align-items: center; justify-content: space-between; gap: 14px; padding: 14px 16px; background: var(--el-fill-color-light); }
.baseline-group-heading > div { display: flex; align-items: center; gap: 10px; min-width: 0; }
.baseline-code { color: var(--el-color-primary); font-size: 17px; font-weight: 800; }
.baseline-group-heading strong { color: var(--el-text-color-primary); font-size: 16px; }
.baseline-group-direction { color: var(--el-text-color-secondary); font-size: 12px; }
.baseline-row-title, .algorithm-row-title { color: var(--el-text-color-primary); }
.baseline-row-title { font-size: 15px; font-weight: 800; }
.algorithm-row-title { font-weight: 600; }
.component-id { margin-top: 4px; color: var(--el-text-color-secondary); font-size: 12px; }
.component-blocker, .component-task { margin-top: 5px; color: var(--el-text-color-secondary); font-size: 12px; }
.component-task { padding: 14px 16px; border: 1px solid var(--el-border-color); border-radius: 6px; background: var(--el-fill-color-lighter); }
.component-task-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 10px; }
.component-logs { margin-top: 10px; }
.component-logs :deep(.el-collapse-item__content) { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.7; }
a { color: var(--el-color-primary); }
.convention-content { margin: 0; padding: 12px; background: var(--el-fill-color-light); color: var(--el-text-color-regular); white-space: pre-wrap; font: inherit; line-height: 1.6; }
@media (max-width: 700px) { .section-heading { display: block; } .section-heading span { display: block; margin-top: 5px; } .registry-intro { display: block; } .registry-intro .el-tag { margin-top: 10px; } }
</style>
