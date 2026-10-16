<template>
  <div ref="plotElement" class="metric-plot" role="img" :aria-label="`${title}，共 ${series.length} 条曲线`" />
</template>

<script setup>
import { Line } from '@antv/g2plot'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  title: { type: String, required: true },
  axis: { type: String, required: true },
  series: { type: Array, required: true },
})

const plotElement = ref(null)
let plot = null
const colors = ['#3b82f6', '#f97316', '#10b981', '#8b5cf6', '#eab308', '#ec4899', '#06b6d4', '#64748b']
const points = computed(() => props.series.flatMap(item =>
  (item.points || []).map(point => ({
    step: Number(point.step),
    value: Number(point.value),
    metric: item.label,
  })).filter(point => Number.isFinite(point.step) && Number.isFinite(point.value))
))

const formatValue = value => {
  const number = Number(value)
  if (!Number.isFinite(number)) return value
  const absolute = Math.abs(number)
  if (absolute > 0 && absolute < 0.001) return number.toExponential(2)
  if (absolute >= 1000) return number.toLocaleString('zh-CN', { maximumFractionDigits: 2 })
  return Number(number.toPrecision(5)).toString()
}

const renderPlot = async () => {
  await nextTick()
  if (!plotElement.value) return
  if (plot) {
    plot.changeData(points.value)
    return
  }
  plot = new Line(plotElement.value, {
    data: points.value,
    autoFit: true,
    height: 280,
    padding: [42, 26, 44, 56],
    xField: 'step',
    yField: 'value',
    seriesField: 'metric',
    color: colors,
    lineStyle: { lineWidth: 2 },
    legend: {
      position: 'top-left',
      itemSpacing: 16,
      itemName: { style: { fill: '#606266', fontSize: 11 } },
    },
    xAxis: {
      title: { text: props.axis === 'epoch' ? '轮次' : '迭代', style: { fill: '#909399', fontSize: 11 } },
      line: { style: { stroke: '#dfe5ec' } },
      tickLine: null,
      label: { style: { fill: '#909399', fontSize: 10 }, autoHide: true, autoRotate: false },
      grid: null,
    },
    yAxis: {
      label: { formatter: formatValue, style: { fill: '#909399', fontSize: 10 } },
      grid: { line: { style: { stroke: '#edf1f6', lineDash: [4, 4] } } },
    },
    point: props.series.length <= 4 ? { size: 2, shape: 'circle' } : false,
    tooltip: {
      showMarkers: true,
      shared: true,
      formatter: datum => ({ name: datum.metric, value: formatValue(datum.value) }),
    },
    interactions: [{ type: 'legend-filter' }, { type: 'legend-highlight' }],
    animation: false,
  })
  plot.render()
}

onMounted(renderPlot)
watch(() => [props.series, props.axis], renderPlot, { deep: true })
onBeforeUnmount(() => { plot?.destroy(); plot = null })
</script>

<style scoped>
.metric-plot {
  width: 100%;
  height: 280px;
}

@media (max-width: 620px) {
  .metric-plot { height: 250px; }
}
</style>
