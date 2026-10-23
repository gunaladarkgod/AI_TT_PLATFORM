<template>
  <el-dialog
    :model-value="modelValue"
    width="min(1420px, 96vw)"
    top="3vh"
    destroy-on-close
    append-to-body
    @update:model-value="value => emit('update:modelValue', value)"
  >
    <template #header>
      <div class="comparison-header">
        <div>
          <h2>跨实验对比</h2>
          <p>在同一视图中比较训练效果、资源占用和实验配置</p>
        </div>
        <el-tag type="info" effect="plain">{{ experiments.length }} 个实验</el-tag>
      </div>
    </template>

    <div v-loading="loading" class="comparison-dialog">
      <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon />
      <template v-if="!loading && !error">
        <section class="comparison-section">
          <header class="comparison-section__header">
            <div><h3>实验摘要</h3><p>最高指标取训练结果记录中的最终汇总值</p></div>
          </header>
          <el-table :data="experiments" border stripe size="small" class="comparison-table">
            <el-table-column prop="name" label="实验" min-width="190" fixed="left" show-overflow-tooltip />
            <el-table-column prop="engine" label="引擎" width="105" />
            <el-table-column prop="dataset" label="数据集" min-width="150" show-overflow-tooltip />
            <el-table-column prop="network" label="模型" min-width="140" show-overflow-tooltip />
            <el-table-column v-for="metric in summaryMetrics" :key="metric.key" :label="metric.label" width="92">
              <template #default="{ row }">{{ metric.key === 'durationSeconds' ? formatDuration(row.summary?.[metric.key]) : formatMetric(row.summary?.[metric.key]) }}</template>
            </el-table-column>
            <el-table-column label="峰值显存" width="105">
              <template #default="{ row }">{{ formatMemory(row.peakMemory) }}</template>
            </el-table-column>
          </el-table>
        </section>

        <section class="comparison-charts">
          <div v-for="chart in charts" :key="chart.key" class="comparison-section comparison-chart">
            <header class="comparison-section__header">
              <div><h3>{{ chart.title }}</h3><p>{{ chart.description }}</p></div>
            </header>
            <MetricLineChart
              v-if="chart.series.length"
              :title="chart.title"
              :axis="chart.axis"
              :series="chart.series"
              :color-domain="chart.series.map(item => item.label)"
              :unit="chart.unit"
            />
            <el-empty v-else :image-size="72" description="所选结果没有可用曲线" />
          </div>
        </section>

        <section class="comparison-section">
          <header class="comparison-section__header">
            <div><h3>各类别 AP</h3><p>仅展示训练产物明确提供的类别级 AP，不从汇总 mAP 推测</p></div>
          </header>
          <el-alert v-if="!classApKeys.length" type="info" :closable="false" show-icon
            title="当前训练结果只提供汇总 AP，未发现可比较的类别级 AP 数据。" />
          <el-table v-else :data="classApRows" border stripe size="small" class="comparison-table">
            <el-table-column prop="label" label="类别 / 指标" min-width="220" fixed="left" />
            <el-table-column v-for="experiment in experiments" :key="experiment.id" :label="experiment.name" min-width="150">
              <template #default="{ row }">{{ formatMetric(experiment.classAp?.[row.label]) }}</template>
            </el-table-column>
          </el-table>
        </section>

        <section class="comparison-section">
          <header class="comparison-section__header">
            <div><h3>实验配置差异</h3><p>只列出所选实验之间存在差异的字段</p></div>
          </header>
          <el-table v-if="parameterRows.length" :data="parameterRows" border stripe size="small" class="comparison-table">
            <el-table-column prop="label" label="字段" width="220" fixed="left" />
            <el-table-column v-for="experiment in experiments" :key="experiment.id" :label="experiment.name" min-width="190">
              <template #default="{ row }"><span class="parameter-value">{{ displayValue(row.values[experiment.id]) }}</span></template>
            </el-table-column>
          </el-table>
          <el-empty v-else :image-size="64" description="所选实验的关键参数一致" />
        </section>
      </template>
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import MetricLineChart from './MetricLineChart.vue'
import { TrainingMetricsService } from '@/api/api'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  ids: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:modelValue'])
const loading = ref(false)
const error = ref('')
const experiments = ref([])

const summaryMetrics = [
  { key: 'map', label: 'mAP' }, { key: 'ap50', label: 'AP50' }, { key: 'ap75', label: 'AP75' },
  { key: 'aps', label: 'APs' }, { key: 'apm', label: 'APm' }, { key: 'apl', label: 'APl' },
  { key: 'durationSeconds', label: '训练时长' },
]

const load = async () => {
  if (!props.modelValue) return
  loading.value = true
  error.value = ''
  try {
    const response = await TrainingMetricsService.compare(props.ids)
    if (response.code !== 0 || !response.data?.experiments) throw new Error(response.msg || '读取对比数据失败')
    experiments.value = response.data.experiments
  } catch (cause) {
    experiments.value = []
    error.value = cause?.message || '读取对比数据失败'
  } finally {
    loading.value = false
  }
}

const formatMetric = value => {
  if (value === null || value === undefined || value === '') return '—'
  const number = Number(value)
  return Number.isFinite(number) ? number.toFixed(4) : String(value)
}
const formatMemory = value => {
  if (value === null || value === undefined) return '—'
  const number = Number(value)
  return Number.isFinite(number) ? `${number.toFixed(0)} MB` : '—'
}
const formatDuration = value => {
  const seconds = Number(value)
  if (!Number.isFinite(seconds)) return '—'
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const rest = Math.floor(seconds % 60)
  return [hours, minutes, rest].map(item => String(item).padStart(2, '0')).join(':')
}
const displayValue = value => {
  if (value === null || value === undefined || value === '') return '未记录'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

const charts = computed(() => {
  const definitions = [
    { key: 'loss', title: '损失曲线', description: '叠加比较各实验的训练与验证损失', unit: '' },
    { key: 'accuracy', title: '精度曲线', description: '叠加比较训练过程中可用的精度指标', unit: '' },
  ]
  return definitions.map(definition => {
    const series = []
    let axis = 'epoch'
    for (const experiment of experiments.value) {
      axis = experiment.metrics?.axis || axis
      for (const line of experiment.metrics?.series || []) {
        if (line.group !== definition.key || !line.points?.length) continue
        series.push({ ...line, key: `${experiment.id}-${line.key}`, label: `${experiment.name} · ${line.label}` })
      }
    }
    return { ...definition, axis, series }
  })
})

const classApKeys = computed(() => [...new Set(experiments.value.flatMap(item => Object.keys(item.classAp || {})))])
const classApRows = computed(() => classApKeys.value.map(label => ({ label })))

const parameterRows = computed(() => {
  const valuesByKey = new Map()
  for (const experiment of experiments.value) {
    const context = experiment.context || {}
    const parameters = context.parameters || {}
    const fields = {
      '数据集': experiment.dataset,
      '研究方向': context.direction,
      '研究基线': context.baseline,
      '改进包': context.improvements,
      '模型': experiment.network,
      ...Object.fromEntries(Object.entries(parameters).map(([key, value]) => [`参数 · ${key}`, value])),
    }
    for (const [key, value] of Object.entries(fields)) {
      if (!valuesByKey.has(key)) valuesByKey.set(key, {})
      valuesByKey.get(key)[experiment.id] = value
    }
  }
  return [...valuesByKey.entries()]
    .map(([label, values]) => {
      const complete = { ...values }
      for (const experiment of experiments.value) {
        if (!Object.prototype.hasOwnProperty.call(complete, experiment.id)) complete[experiment.id] = null
      }
      return { label, values: complete }
    })
    .filter(row => new Set(Object.values(row.values).map(displayValue)).size > 1)
})

watch(() => [props.modelValue, props.ids], load, { immediate: true, deep: true })
</script>

<style scoped>
.comparison-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.comparison-header h2, .comparison-header p { margin: 0; }
.comparison-header h2 { color: #1f2937; font-size: 18px; }
.comparison-header p { margin-top: 4px; color: #8a94a6; font-size: 12px; }
.comparison-dialog { min-height: 300px; max-height: 78vh; overflow: auto; padding: 2px 4px 10px; }
.comparison-section { margin-top: 16px; padding: 14px; border: 1px solid #e5eaf0; border-radius: 8px; background: #fff; }
.comparison-section__header { display: flex; justify-content: space-between; margin-bottom: 10px; }
.comparison-section__header h3, .comparison-section__header p { margin: 0; }
.comparison-section__header h3 { color: #303133; font-size: 15px; }
.comparison-section__header p { margin-top: 3px; color: #9aa3b2; font-size: 11px; }
.comparison-charts { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.comparison-charts .comparison-section { min-width: 0; }
.comparison-chart :deep(.metric-plot) { height: 300px; }
.parameter-value { white-space: pre-wrap; word-break: break-word; }
@media (max-width: 920px) { .comparison-charts { grid-template-columns: 1fr; } }
</style>
