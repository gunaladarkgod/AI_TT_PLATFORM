<template>
  <el-dialog
    :model-value="modelValue"
    width="min(1180px, 94vw)"
    top="4vh"
    class="metrics-dashboard-dialog"
    append-to-body
    destroy-on-close
    @update:model-value="value => emit('update:modelValue', value)"
  >
    <template #header>
      <div class="metrics-header">
        <div class="metrics-header__icon"><el-icon><DataLine /></el-icon></div>
        <div class="metrics-header__copy">
          <h2>训练看板</h2>
          <el-tooltip :content="target?.name || ''" placement="bottom" :disabled="!target?.name">
            <p>{{ target?.name || '训练任务' }}</p>
          </el-tooltip>
        </div>
      </div>
    </template>

    <div class="metrics-dialog">
      <div class="metrics-overview">
        <div class="metrics-meta">
          <el-tag :type="isRunning ? 'warning' : 'success'" effect="light" round>
            <span class="status-dot" :class="{ 'status-dot--running': isRunning }" />
            {{ isRunning ? '运行中' : '已结束' }}
          </el-tag>
          <span v-if="engineName" class="meta-item"><span class="meta-label">训练引擎</span>{{ engineName }}</span>
          <span v-if="metrics?.current_step != null" class="meta-item">
            <span class="meta-label">当前{{ axisLabel }}</span>{{ formatStep(metrics.current_step) }}
          </span>
          <span v-if="metrics?.updated_at" class="meta-item">
            <span class="meta-label">数据更新</span>{{ formatTime(metrics.updated_at) }}
          </span>
          <span v-if="isRunning" class="auto-refresh"><i />每 4 秒自动刷新</span>
        </div>
        <el-button :icon="RefreshRight" :loading="loading" @click="loadMetrics">刷新</el-button>
      </div>

      <el-alert
        v-if="error && metrics"
        class="metrics-notice"
        type="warning"
        title="指标刷新失败，当前保留上一次成功读取的数据"
        :description="error"
        :closable="false"
        show-icon
      />

      <div v-if="loading && !metrics && !error" class="metrics-loading" aria-label="正在读取训练指标">
        <el-skeleton :rows="5" animated />
      </div>

      <div v-else-if="error && !metrics" class="metrics-state">
        <el-result icon="warning" title="训练指标读取失败" :sub-title="error">
          <template #extra><el-button type="primary" :loading="loading" @click="loadMetrics">重新加载</el-button></template>
        </el-result>
      </div>

      <div v-else-if="!hasSeries" class="metrics-state">
        <el-empty :description="metrics?.message || '尚无可绘制的训练指标'" :image-size="96">
          <p v-if="isRunning" class="empty-hint">训练引擎写入首批指标后，页面会自动显示曲线。</p>
        </el-empty>
      </div>

      <template v-else>
        <div class="metrics-stats">
          <article v-for="card in summaryCards" :key="card.key" class="metric-stat-card">
            <span class="metric-stat-card__label">{{ card.label }}</span>
            <strong>{{ card.value }}</strong>
            <small>{{ card.detail }}</small>
          </article>
        </div>

        <div class="metrics-charts">
          <section
            v-for="group in groups"
            :key="group.key"
            class="metrics-section"
            :class="{ 'metrics-section--wide': group.key === 'learning_rate' }"
          >
            <header class="metrics-section__header">
              <div>
                <h3>{{ group.title }}</h3>
                <p>{{ group.description }}</p>
              </div>
              <el-popover placement="bottom-end" :width="300" trigger="click" popper-class="metric-series-popper">
                <template #reference>
                  <el-button class="series-picker-button" size="small" plain :icon="Operation">
                    选择曲线 {{ selectedCount(group) }}/{{ group.series.length }}
                  </el-button>
                </template>
                <div class="series-picker">
                  <div class="series-picker__header">
                    <div>
                      <strong>选择对比指标</strong>
                      <span>至少保留一条曲线</span>
                    </div>
                    <el-button link type="primary" :disabled="selectedCount(group) === group.series.length"
                      @click="selectAllSeries(group)">全选</el-button>
                  </div>
                  <el-checkbox-group :model-value="selectedSeriesKeys[group.key] || []"
                    @change="values => updateSeriesSelection(group, values)">
                    <el-checkbox v-for="series in group.series" :key="seriesId(series)" :value="seriesId(series)"
                      :disabled="isLastSelectedSeries(group, series)" :title="series.label">
                      {{ series.label }}
                    </el-checkbox>
                  </el-checkbox-group>
                </div>
              </el-popover>
            </header>
            <MetricLineChart :title="group.title" :axis="metrics.axis" :series="visibleSeries(group)"
              :color-domain="group.series.map(series => series.label)" />
          </section>
        </div>
      </template>
    </div>

    <template #footer><el-button @click="emit('update:modelValue', false)">关闭</el-button></template>
  </el-dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { DataLine, Operation, RefreshRight } from '@element-plus/icons-vue'
import { TrainingMetricsService } from '@/api/api'
import MetricLineChart from './MetricLineChart.vue'

const props = defineProps({
  modelValue: { type: Boolean, required: true },
  target: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue'])
const metrics = ref(null)
const loading = ref(false)
const error = ref('')
const selectedSeriesKeys = ref({})
const knownSeriesKeys = ref({})
let refreshTimer = null
let requestVersion = 0

const isRunning = computed(() => props.target?.type === 'task' && (metrics.value?.running ?? props.target?.running))
const engineName = computed(() => ({
  mmdet: 'MMDetection',
  ultralytics: 'Ultralytics',
  custom: '自定义引擎',
})[metrics.value?.engine] || '')
const axisLabel = computed(() => metrics.value?.axis === 'epoch' ? '轮次' : '迭代')
const hasSeries = computed(() => !!metrics.value?.series?.some(series => series.points?.length))
const groups = computed(() => [
  { key: 'loss', title: '损失曲线', description: '观察训练收敛趋势与各项损失变化', series: seriesByGroup('loss') },
  { key: 'accuracy', title: '精度曲线', description: '查看 mAP、AP 等验证指标变化', series: seriesByGroup('accuracy') },
  { key: 'learning_rate', title: '学习率曲线', description: '检查优化器学习率调度过程', series: seriesByGroup('learning_rate') },
].filter(group => group.series.length))

const summaryCards = computed(() => {
  const lossSeries = seriesByGroup('loss')
  const accuracySeries = seriesByGroup('accuracy')
  const primaryLoss = lossSeries.find(item => /(^|[/_])loss($|[/_])|total.?loss/i.test(item.key || item.label || '')) || lossSeries[0]
  const preferredAccuracy = accuracySeries.find(item => /map(?!50|75)|coco\/bbox_mAP$/i.test(item.key || '')) || accuracySeries[0]
  const latestLoss = lastPoint(primaryLoss)
  const bestAccuracy = bestPoint(preferredAccuracy, 'max')
  const curveCount = metrics.value?.series?.filter(item => item.points?.length).length || 0
  return [
    { key: 'loss', label: '当前损失', value: formatMetric(latestLoss?.value), detail: primaryLoss?.label || '暂无损失指标' },
    {
      key: 'accuracy',
      label: '最佳精度',
      value: formatMetric(bestAccuracy?.value),
      detail: preferredAccuracy ? `${preferredAccuracy.label} · ${axisLabel.value} ${formatStep(bestAccuracy?.step)}` : '暂无精度指标',
    },
    {
      key: 'progress',
      label: `当前${axisLabel.value}`,
      value: metrics.value?.current_step == null ? '--' : formatStep(metrics.value.current_step),
      detail: isRunning.value ? '训练进行中' : '训练已结束',
    },
    { key: 'series', label: '有效曲线', value: String(curveCount), detail: `${groups.value.length} 个指标分组` },
  ]
})

function seriesByGroup(group) {
  return metrics.value?.series?.filter(item => item.group === group && item.points?.length) || []
}

function seriesId(series) {
  return series.key || series.label
}

function syncSeriesSelection() {
  const nextSelected = { ...selectedSeriesKeys.value }
  const nextKnown = { ...knownSeriesKeys.value }
  for (const group of groups.value) {
    const available = group.series.map(seriesId)
    const known = nextKnown[group.key] || []
    const selected = nextSelected[group.key]
    const added = available.filter(key => !known.includes(key))
    const preserved = selected == null
      ? available
      : available.filter(key => selected.includes(key) || added.includes(key))
    nextSelected[group.key] = preserved.length ? preserved : available.slice(0, 1)
    nextKnown[group.key] = available
  }
  selectedSeriesKeys.value = nextSelected
  knownSeriesKeys.value = nextKnown
}

function selectedCount(group) {
  return visibleSeries(group).length
}

function visibleSeries(group) {
  const selected = selectedSeriesKeys.value[group.key]
  if (!selected) return group.series
  return group.series.filter(series => selected.includes(seriesId(series)))
}

function updateSeriesSelection(group, values) {
  if (!values.length) return
  selectedSeriesKeys.value = { ...selectedSeriesKeys.value, [group.key]: values }
}

function selectAllSeries(group) {
  selectedSeriesKeys.value = {
    ...selectedSeriesKeys.value,
    [group.key]: group.series.map(seriesId),
  }
}

function isLastSelectedSeries(group, series) {
  const selected = selectedSeriesKeys.value[group.key] || []
  return selected.length === 1 && selected.includes(seriesId(series))
}

function lastPoint(series) {
  return series?.points?.length ? series.points[series.points.length - 1] : null
}

function bestPoint(series, direction) {
  if (!series?.points?.length) return null
  return series.points.reduce((best, point) => {
    const value = Number(point.value)
    if (!Number.isFinite(value)) return best
    if (!best) return point
    return direction === 'max' ? (value > Number(best.value) ? point : best) : (value < Number(best.value) ? point : best)
  }, null)
}

const formatMetric = value => {
  const number = Number(value)
  if (!Number.isFinite(number)) return '--'
  const absolute = Math.abs(number)
  if (absolute > 0 && absolute < 0.001) return number.toExponential(2)
  if (absolute >= 1000) return number.toLocaleString('zh-CN', { maximumFractionDigits: 2 })
  return Number(number.toPrecision(4)).toString()
}

const formatStep = value => {
  const number = Number(value)
  return Number.isFinite(number) ? number.toLocaleString('zh-CN') : (value ?? '--')
}

const formatTime = value => {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

const loadMetrics = async () => {
  if (!props.modelValue || !props.target?.id || loading.value) return
  const version = requestVersion
  const target = props.target
  loading.value = true
  try {
    const response = target.type === 'task'
      ? await TrainingMetricsService.forTask(target.id)
      : await TrainingMetricsService.forResult(target.id)
    if (version !== requestVersion) return
    if (response.code !== 0 || !response.data) throw new Error(response.msg || '读取训练指标失败')
    metrics.value = response.data
    syncSeriesSelection()
    error.value = ''
    if (target.type === 'task' && !response.data.running && refreshTimer) {
      window.clearInterval(refreshTimer)
      refreshTimer = null
    }
  } catch (cause) {
    if (version !== requestVersion) return
    error.value = cause?.message || '读取训练指标失败，请检查 Runner 状态'
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

watch(() => [props.modelValue, props.target?.type, props.target?.id], ([visible, type, id]) => {
  requestVersion++
  if (refreshTimer) window.clearInterval(refreshTimer)
  refreshTimer = null
  loading.value = false
  metrics.value = null
  error.value = ''
  selectedSeriesKeys.value = {}
  knownSeriesKeys.value = {}
  if (!visible || !id) return
  loadMetrics()
  if (type === 'task') refreshTimer = window.setInterval(loadMetrics, 4000)
}, { immediate: true })
onBeforeUnmount(() => { requestVersion++; if (refreshTimer) window.clearInterval(refreshTimer) })
</script>

<style scoped>
.metrics-dialog {
  min-height: 340px;
  max-height: 74vh;
  padding: 2px 4px 8px;
  overflow-y: auto;
}

.metrics-header { display: flex; align-items: center; min-width: 0; gap: 12px; }
.metrics-header__icon {
  display: grid;
  flex: 0 0 38px;
  width: 38px;
  height: 38px;
  place-items: center;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-radius: 10px;
  font-size: 20px;
}
.metrics-header__copy { min-width: 0; }
.metrics-header h2, .metrics-header p { margin: 0; }
.metrics-header h2 { color: #1f2937; font-size: 18px; line-height: 1.4; }
.metrics-header p {
  max-width: min(760px, 68vw);
  overflow: hidden;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metrics-overview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  padding: 12px 14px;
  background: #f7f9fc;
  border: 1px solid #ebeef5;
  border-radius: 10px;
}
.metrics-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 20px;
  min-width: 0;
  color: #303133;
  font-size: 13px;
}
.meta-item { white-space: nowrap; }
.meta-label { margin-right: 6px; color: #909399; }
.status-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 6px;
  background: var(--el-color-success);
  border-radius: 50%;
  vertical-align: 1px;
}
.status-dot--running { background: var(--el-color-warning); box-shadow: 0 0 0 4px rgb(230 162 60 / 14%); }
.auto-refresh { display: inline-flex; align-items: center; gap: 6px; color: #909399; white-space: nowrap; }
.auto-refresh i {
  width: 6px;
  height: 6px;
  background: var(--el-color-primary);
  border-radius: 50%;
  animation: refresh-pulse 1.8s ease-out infinite;
}
.metrics-notice { margin-bottom: 16px; }
.metrics-loading, .metrics-state { min-height: 300px; }
.metrics-loading { padding: 24px; background: #fff; border: 1px solid #ebeef5; border-radius: 10px; }
.metrics-state {
  display: grid;
  place-items: center;
  background: #fff;
  border: 1px dashed #dcdfe6;
  border-radius: 10px;
}
.empty-hint { margin: 8px 0 0; color: #909399; font-size: 12px; }

.metrics-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.metric-stat-card {
  display: flex;
  min-width: 0;
  min-height: 92px;
  box-sizing: border-box;
  flex-direction: column;
  justify-content: center;
  padding: 14px 16px;
  background: linear-gradient(145deg, #fff 35%, #f7faff 100%);
  border: 1px solid #e7edf5;
  border-radius: 10px;
}
.metric-stat-card__label { color: #909399; font-size: 12px; }
.metric-stat-card strong {
  margin-top: 3px;
  overflow: hidden;
  color: #1f2937;
  font-size: 24px;
  font-weight: 650;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.metric-stat-card small {
  margin-top: 4px;
  overflow: hidden;
  color: #a2a8b0;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metrics-charts { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.metrics-section {
  min-width: 0;
  padding: 16px 16px 10px;
  background: #fff;
  border: 1px solid #e7edf5;
  border-radius: 10px;
  box-shadow: 0 3px 14px rgb(31 45 61 / 5%);
}
.metrics-section--wide { grid-column: 1 / -1; }
.metrics-section__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 6px; }
.metrics-section h3, .metrics-section p { margin: 0; }
.metrics-section h3 { color: #303133; font-size: 15px; font-weight: 600; line-height: 1.5; }
.metrics-section p { margin-top: 2px; color: #a2a8b0; font-size: 11px; line-height: 1.5; }
.series-picker-button { flex: 0 0 auto; color: var(--el-color-primary); border-color: var(--el-color-primary-light-7); }
.series-picker { display: flex; flex-direction: column; gap: 12px; }
.series-picker__header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.series-picker__header > div { display: flex; min-width: 0; flex-direction: column; }
.series-picker__header strong { color: #303133; font-size: 14px; line-height: 1.5; }
.series-picker__header span { color: #a2a8b0; font-size: 11px; line-height: 1.5; }
.series-picker :deep(.el-checkbox-group) {
  display: flex;
  max-height: 260px;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
}
.series-picker :deep(.el-checkbox) {
  width: 100%;
  height: auto;
  min-height: 30px;
  margin-right: 0;
  padding: 4px 8px;
  border-radius: 6px;
}
.series-picker :deep(.el-checkbox:hover) { background: #f5f7fa; }
.series-picker :deep(.el-checkbox__label) { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

@keyframes refresh-pulse {
  0% { box-shadow: 0 0 0 0 rgb(64 158 255 / 35%); }
  70% { box-shadow: 0 0 0 5px rgb(64 158 255 / 0%); }
  100% { box-shadow: 0 0 0 0 rgb(64 158 255 / 0%); }
}

@media (max-width: 900px) {
  .metrics-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .metrics-charts { grid-template-columns: 1fr; }
  .metrics-section--wide { grid-column: auto; }
}
@media (max-width: 620px) {
  .metrics-overview { align-items: flex-start; }
  .metrics-meta { gap: 8px 12px; }
  .metrics-stats { grid-template-columns: 1fr; }
  .metric-stat-card { min-height: 78px; }
  .metrics-section { padding-right: 10px; padding-left: 10px; }
  .metrics-section__header { align-items: center; }
  .metrics-section p { display: none; }
}
@media (prefers-reduced-motion: reduce) { .auto-refresh i { animation: none; } }
</style>
