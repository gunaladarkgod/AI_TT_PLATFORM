<template>
  <el-dialog
    :model-value="modelValue"
    :title="title || '查看示例'"
    width="min(1380px, 96vw)"
    top="3vh"
    class="dataset-preview-dialog"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="dataset-preview-content">
      <div v-if="loading" class="dataset-preview-loading">加载中…</div>
      <el-empty v-else-if="!groups.length" :description="emptyDescription" />

      <div v-else class="dataset-preview-list">
        <div v-for="group in groups" :key="group.name" class="dataset-preview-group">
          <div class="dataset-preview-group__header">
            <div>
              <el-tag>{{ group.name }}</el-tag>
              <span class="dataset-preview-group__count">共 {{ group.count }} 张相关图片</span>
            </div>
            <el-button
              size="small"
              type="primary"
              plain
              :loading="refreshing[group.name] === true"
              @click="refreshGroup(group.name)"
            >
              换一换
            </el-button>
          </div>

          <div class="dataset-preview-images">
            <div
              v-for="(image, i) in group.images"
              :key="group.name + '-' + i + '-' + image.src"
              class="dataset-preview-image-box"
            >
              <svg
                v-if="imageMeta(image).width && imageMeta(image).height"
                class="dataset-preview-svg"
                :viewBox="`0 0 ${imageMeta(image).width} ${imageMeta(image).height}`"
                preserveAspectRatio="xMidYMid meet"
              >
                <image
                  :href="image.src"
                  :width="imageMeta(image).width"
                  :height="imageMeta(image).height"
                />
                <g v-for="(obj, idx) in imageMeta(image).objects || []" :key="idx">
                  <polygon
                    v-if="pointsAttr(obj).length"
                    :points="pointsAttr(obj)"
                    class="dataset-preview-poly"
                  />
                  <rect
                    v-else-if="bboxOf(obj)"
                    v-bind="bboxOf(obj)"
                    class="dataset-preview-poly"
                  />
                  <text
                    v-if="labelPoint(obj)"
                    :x="labelPoint(obj)[0]"
                    :y="Math.max(14, labelPoint(obj)[1] - 4)"
                    class="dataset-preview-label"
                  >
                    {{ obj.label || obj.name || obj.className || group.name }}
                  </text>
                </g>
              </svg>

              <img
                v-else
                :src="image.src"
                alt="示例图片"
                class="dataset-preview-photo"
                @load="ensureObjects(image)"
                @error="onImageError"
              />
            </div>
          </div>
        </div>
      </div>
    </div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { baseHost } from '@/api/axios'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '查看示例' },
  emptyDescription: { type: String, default: '暂无可展示图片' },
  loadPreview: { type: Function, required: true },
  perLabel: { type: Number, default: 3 }
})

const emit = defineEmits(['update:modelValue'])

const loading = ref(false)
const groups = ref([])
const metaBySrc = ref({})
const refreshing = reactive({})

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) loadAll()
  }
)

async function loadAll() {
  loading.value = true
  groups.value = []
  metaBySrc.value = {}
  try {
    const raw = await props.loadPreview({ perLabel: props.perLabel, refreshKey: Date.now() })
    const data = unwrapPreviewData(raw)
    const normalized = normalizeGroups(data?.items, Date.now())
    groups.value = normalized.groups
    metaBySrc.value = normalized.meta
  } catch (e) {
    console.error('加载示例失败', e)
    ElMessage.error('加载示例失败：' + (e?.message || e))
  } finally {
    loading.value = false
  }
}

async function refreshGroup(groupName) {
  refreshing[groupName] = true
  try {
    const raw = await props.loadPreview({ perLabel: props.perLabel, refreshKey: Date.now() })
    const data = unwrapPreviewData(raw)
    const normalized = normalizeGroups(data?.items, Date.now())
    const refreshed = normalized.groups.find(g => g.name === groupName)
    if (refreshed) {
      groups.value = groups.value.map(g => g.name === groupName ? refreshed : g)
      metaBySrc.value = { ...metaBySrc.value, ...normalized.meta }
    }
  } catch (e) {
    ElMessage.error('换一换失败：' + (e?.message || e))
  } finally {
    refreshing[groupName] = false
  }
}

function unwrapPreviewData(raw) {
  let obj = raw
  if (typeof obj === 'string') obj = JSON.parse(obj)
  return obj?.data || obj || {}
}

function normalizeGroups(items, refreshKey) {
  const meta = {}
  const groups = (Array.isArray(items) ? items : []).map(item => {
    const rawImages = Array.isArray(item?.images)
      ? item.images
      : Array.isArray(item?.urls)
        ? item.urls
        : []
    const images = rawImages.map(image => normalizeImage(image, refreshKey)).filter(img => img.src)
    images.forEach(image => {
      if (image.width || image.height || image.objects.length) {
        meta[image.src] = {
          width: Number(image.width || 0),
          height: Number(image.height || 0),
          objects: image.objects
        }
      }
    })
    return {
      name: item?.label || item?.className || item?.name || '未命名类别',
      count: Number(item?.count || images.length || 0),
      images
    }
  }).filter(group => group.images.length)
  return { groups, meta }
}

function normalizeImage(image, refreshKey) {
  if (typeof image === 'string') {
    return { src: previewImageUrl(image, refreshKey), width: 0, height: 0, objects: [] }
  }
  const src = previewImageUrl(image?.url || image?.src || '', refreshKey)
  return {
    ...image,
    src,
    width: Number(image?.width || 0),
    height: Number(image?.height || 0),
    objects: Array.isArray(image?.objects) ? image.objects : []
  }
}

function previewImageUrl(path, refreshKey = Date.now()) {
  if (!path) return ''
  const raw = String(path)
  const sep = raw.includes('?') ? '&' : '?'
  if (/^https?:\/\//i.test(raw)) return `${raw}${sep}_=${refreshKey}`
  const clean = raw.startsWith('/') ? raw : `/${raw}`
  const origin = `${window.location.protocol}//${baseHost}`
  return `${origin}${clean}${sep}_=${refreshKey}`
}

function objectsUrlFromImageUrl(imgUrl) {
  return String(imgUrl || '').replace('/image?', '/objects?')
}

function imageMeta(image) {
  return metaBySrc.value[image.src] || image || {}
}

async function ensureObjects(image) {
  if (!image?.src || metaBySrc.value[image.src]) return
  try {
    const resp = await fetch(objectsUrlFromImageUrl(image.src), { cache: 'no-store' })
    if (!resp.ok) return
    const res = await resp.json()
    if (res?.code !== undefined && res.code !== 0) return
    const data = res?.data || res
    if (data && (data.width || data.height || Array.isArray(data.objects))) {
      metaBySrc.value = {
        ...metaBySrc.value,
        [image.src]: {
          width: Number(data.width || 0),
          height: Number(data.height || 0),
          objects: Array.isArray(data.objects) ? data.objects : []
        }
      }
    }
  } catch (_) {
    // 图片仍可显示，标注接口失败时不打断预览。
  }
}

function onImageError(event) {
  const target = event?.target
  if (target) {
    target.style.display = 'none'
    target.parentElement?.classList.add('dataset-preview-image-box--error')
  }
}

function normalizePoint(point) {
  if (Array.isArray(point)) return [Number(point[0] || 0), Number(point[1] || 0)]
  if (typeof point === 'string') {
    const parts = point.trim().split(/[\s,]+/).map(Number)
    if (parts.length >= 2 && parts.every(Number.isFinite)) return [parts[0], parts[1]]
  }
  if (point && typeof point === 'object') return [Number(point.x || 0), Number(point.y || 0)]
  return null
}

function objectPoints(obj) {
  if (Array.isArray(obj?.points)) return obj.points
  if (Array.isArray(obj?.polygon)) return obj.polygon
  if (Array.isArray(obj?.segmentation?.[0])) {
    const arr = obj.segmentation[0]
    const pts = []
    for (let i = 0; i + 1 < arr.length; i += 2) pts.push([arr[i], arr[i + 1]])
    return pts
  }
  return []
}

function pointsAttr(obj) {
  return objectPoints(obj)
    .map(normalizePoint)
    .filter(Boolean)
    .map(point => point.join(','))
    .join(' ')
}

function bboxOf(obj) {
  const box = obj?.bbox || obj?.box
  if (!box) return null
  const x = Number(box.x ?? box.left ?? (Array.isArray(box) ? box[0] : NaN))
  const y = Number(box.y ?? box.top ?? (Array.isArray(box) ? box[1] : NaN))
  const width = Number(box.w ?? box.width ?? (Array.isArray(box) ? box[2] : NaN))
  const height = Number(box.h ?? box.height ?? (Array.isArray(box) ? box[3] : NaN))
  if (![x, y, width, height].every(Number.isFinite) || width <= 0 || height <= 0) return null
  return { x, y, width, height }
}

function labelPoint(obj) {
  const first = normalizePoint(objectPoints(obj)[0])
  if (first) return first
  const box = bboxOf(obj)
  return box ? [box.x, box.y] : null
}
</script>

<style scoped>
:deep(.el-dialog__body) {
  padding-top: 12px;
  padding-bottom: 12px;
}

.dataset-preview-content {
  min-height: 420px;
  max-height: calc(94vh - 150px);
  overflow-y: auto;
}
.dataset-preview-loading {
  padding: 48px 0;
  text-align: center;
  color: #909399;
}
.dataset-preview-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.dataset-preview-group {
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 16px;
}
.dataset-preview-group:last-child {
  border-bottom: none;
}
.dataset-preview-group__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}
.dataset-preview-group__count {
  margin-left: 8px;
  color: #909399;
  font-size: 13px;
}
.dataset-preview-images {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  width: 100%;
}
.dataset-preview-image-box {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 11;
  overflow: hidden;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #f5f7fa;
}
.dataset-preview-image-box--error::after {
  content: '图片加载失败';
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #f56c6c;
  font-size: 13px;
}
.dataset-preview-svg,
.dataset-preview-photo {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: contain;
}
.dataset-preview-poly {
  fill: rgba(103, 194, 58, 0.12);
  stroke: #67c23a;
  stroke-width: 2;
  vector-effect: non-scaling-stroke;
}
.dataset-preview-label {
  fill: #fff;
  stroke: rgba(0, 0, 0, 0.55);
  stroke-width: 3;
  paint-order: stroke;
  font-size: 14px;
  font-weight: 600;
}
@media (max-width: 1100px) {
  .dataset-preview-images {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 720px) {
  .dataset-preview-images {
    grid-template-columns: 1fr;
  }
}
</style>