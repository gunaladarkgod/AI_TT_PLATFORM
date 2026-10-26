<template>
  <div class="inference-result">
    <div class="box-controls">
      <el-switch v-model="showBoxes" active-text="显示检测框" />
      <label for="inference-confidence">最低置信度</label>
      <el-slider id="inference-confidence" v-model="threshold" :min="0" :max="100" :step="1"
        :format-tooltip="value => `${value}%`" aria-label="最低置信度" class="confidence-slider" />
      <span>{{ threshold }}%</span>
      <el-text>显示 {{ boxes.length }} / {{ detections.length }} 个目标</el-text>
    </div>
    <el-alert v-if="error" type="error" :closable="false" title="图片加载失败，请重新选择图片并推理。" />
    <svg v-else-if="size.width" class="detection-image" :viewBox="`0 0 ${size.width} ${size.height}`"
      role="img" :aria-label="`推理图片，${boxes.length} 个目标${showBoxes ? '，已显示检测框' : '，已隐藏检测框'}`">
      <image :href="imageUrl" :width="size.width" :height="size.height" />
      <g v-if="showBoxes">
        <g v-for="box in boxes" :key="box.index">
          <title>{{ box.label }} {{ (box.score * 100).toFixed(2) }}%</title>
          <rect :x="box.x" :y="box.y" :width="box.width" :height="box.height"
            fill="none" :stroke="color(box.index)" stroke-width="2" vector-effect="non-scaling-stroke" />
          <rect :x="labelX(box)" :y="labelY(box)" :width="labelWidth(box)" :height="fontSize * 1.5"
            :fill="color(box.index)" fill-opacity="0.92" />
          <text :x="labelX(box) + fontSize * 0.25" :y="labelY(box) + fontSize * 1.1"
            fill="#fff" :font-size="fontSize" font-family="sans-serif">{{ label(box) }}</text>
        </g>
      </g>
    </svg>
    <el-text v-if="!error && !detections.length" type="info">模型未返回检测框。</el-text>
    <el-text v-else-if="!error && size.width && !boxes.length" type="info">
      当前阈值下没有可显示的检测框，可降低最低置信度查看。
    </el-text>
    <el-text type="info">默认显示全部返回框；低置信度结果仅供检查，可提高阈值过滤。</el-text>
    <el-table v-if="boxes.length" :data="boxes" border size="small" max-height="220">
      <el-table-column prop="label" label="类别" min-width="150" />
      <el-table-column label="置信度" width="110">
        <template #default="{ row }">{{ (row.score * 100).toFixed(2) }}%</template>
      </el-table-column>
      <el-table-column label="检测框 (x1, y1, x2, y2)" min-width="260">
        <template #default="{ row }">{{ row.bbox.join(', ') }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { visibleInferenceBoxes } from '@/utils/inferenceBoxes';

const props = defineProps({
  imageUrl: { type: String, required: true },
  detections: { type: Array, default: () => [] },
});
const size = ref({ width: 0, height: 0 });
const error = ref(false);
const showBoxes = ref(true);
const threshold = ref(0);
const boxes = computed(() => visibleInferenceBoxes(props.detections, size.value.width, size.value.height, threshold.value / 100));
const fontSize = computed(() => Math.max(size.value.width / 65, 8));
const palette = ['#d32f2f', '#1565c0', '#2e7d32', '#7b1fa2', '#b45309'];
const color = index => palette[index % palette.length];
const label = box => `${box.index + 1}. ${box.label} ${(box.score * 100).toFixed(2)}%`;
const labelWidth = box => Math.min(size.value.width, (label(box).length * 0.7 + 0.5) * fontSize.value);
const labelX = box => Math.max(0, Math.min(box.x, size.value.width - labelWidth(box)));
const labelY = box => Math.max(0, Math.min(box.y - fontSize.value * 1.5, size.value.height - fontSize.value * 1.5));

watch(() => props.imageUrl, (url, oldUrl, onCleanup) => {
  size.value = { width: 0, height: 0 };
  error.value = false;
  threshold.value = 0;
  showBoxes.value = true;
  const image = new Image();
  image.onload = () => { size.value = { width: image.naturalWidth, height: image.naturalHeight }; };
  image.onerror = () => { error.value = true; };
  image.src = url;
  onCleanup(() => { image.onload = null; image.onerror = null; });
}, { immediate: true });
</script>

<style scoped>
.inference-result { display: flex; flex-direction: column; gap: 12px; }
.box-controls { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; }
.confidence-slider { width: 180px; margin: 0 10px; }
.detection-image { width: 100%; height: auto; max-height: 520px; background: #f7f9fc; border: 1px solid #e4e7ed; border-radius: 8px; }
</style>
