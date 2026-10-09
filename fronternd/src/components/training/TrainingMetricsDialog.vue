<template>
  <el-dialog :model-value="modelValue" :title="`训练看板 - ${target?.name || ''}`" width="min(980px, 94vw)"
    top="5vh" @update:model-value="value => emit('update:modelValue', value)">
    <div class="metrics-dialog" v-loading="loading && !metrics">
      <div class="metrics-toolbar">
        <div class="metrics-summary">
          <el-tag :type="target?.type === 'task' && (metrics?.running ?? target?.running) ? 'warning' : 'success'" size="small">
            {{ target?.type === 'task' && (metrics?.running ?? target?.running) ? '运行中' : '已结束' }}
          </el-tag>
          <span>{{ engineName }}</span>
          <span v-if="metrics?.current_step != null">
            {{ metrics.axis === 'epoch' ? '轮次' : '迭代' }} {{ metrics.current_step }}
          </span>
          <span v-if="metrics?.updated_at">更新于 {{ formatTime(metrics.updated_at) }}</span>
        </div>
        <el-tooltip content="刷新指标" placement="top">
          <el-button :icon="RefreshRight" circle :loading="loading" aria-label="刷新指标" @click="loadMetrics" />
        </el-tooltip>
      </div>
      <el-alert v-if="error" type="warning" :title="error" :closable="false" show-icon />
      <el-empty v-if="!hasSeries && !loading" :description="metrics?.message || '尚无可绘制的训练指标'" :image-size="88" />
      <div v-if="hasSeries" class="metrics-charts">
        <section v-for="group in groups" :key="group.key" class="metrics-section">
          <h3>{{ group.title }}</h3>
          <MetricLineChart :title="group.title" :axis="metrics.axis" :series="group.series" />
        </section>
      </div>
    </div>
    <template #footer><el-button @click="emit('update:modelValue', false)">关闭</el-button></template>
  </el-dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RefreshRight } from '@element-plus/icons-vue'
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
let refreshTimer = null
let requestVersion = 0

const engineName = computed(() => ({ mmdet: 'MMDetection', ultralytics: 'Ultralytics', custom: '自定义引擎' })[metrics.value?.engine] || '')
const hasSeries = computed(() => !!metrics.value?.series?.some(series => series.points?.length))
const groups = computed(() => [
  { key: 'loss', title: '损失', series: metrics.value?.series?.filter(item => item.group === 'loss') || [] },
  { key: 'accuracy', title: '精度', series: metrics.value?.series?.filter(item => item.group === 'accuracy') || [] },
  { key: 'learning_rate', title: '学习率', series: metrics.value?.series?.filter(item => item.group === 'learning_rate') || [] },
].filter(group => group.series.some(item => item.points?.length)))

const formatTime = value => {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN')
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
  if (!visible || !id) return
  loadMetrics()
  if (type === 'task') refreshTimer = window.setInterval(loadMetrics, 4000)
}, { immediate: true })
onBeforeUnmount(() => { requestVersion++; if (refreshTimer) window.clearInterval(refreshTimer) })
</script>

<style scoped>
.metrics-dialog { min-height: 180px; max-height: 70vh; overflow-y: auto; }
.metrics-toolbar { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 12px; }
.metrics-summary { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 18px; color: #505865; font-size: 13px; }
.metrics-charts { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 400px), 1fr)); gap: 18px 24px; }
.metrics-section { min-width: 0; border-top: 1px solid #e4e7ed; padding-top: 10px; }
.metrics-section h3 { margin: 0 0 8px; font-size: 14px; font-weight: 600; }
</style>
