<template>
  <div ref="plotElement" class="metric-plot" :aria-label="title" />
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
const points = computed(() => props.series.flatMap(item =>
  (item.points || []).map(point => ({ step: Number(point.step), value: Number(point.value), metric: item.label }))
))

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
    height: 230,
    xField: 'step',
    yField: 'value',
    seriesField: 'metric',
    color: ['#1677a8', '#d36b32', '#37936f', '#9b62a8', '#ca9a28'],
    legend: { position: 'bottom' },
    xAxis: { title: { text: props.axis === 'epoch' ? '轮次' : '迭代' } },
    yAxis: { title: { text: props.title } },
    point: { size: 2, shape: 'circle' },
    tooltip: { showMarkers: true },
    animation: false,
  })
  plot.render()
}

onMounted(renderPlot)
watch(() => [props.series, props.axis], renderPlot, { deep: true })
onBeforeUnmount(() => { plot?.destroy(); plot = null })
</script>

<style scoped>
.metric-plot { width: 100%; height: 230px; }
</style>
