<template>
  <div class="page">
    <div class="layout">
      <!-- 左侧：原始数据集表格（来源：original_dataset 列表） -->
      <section class="center panel">
        <div class="toolbar">
          <div class="filters-row" :key="'filters-' + filterKey">
            <select v-model="sensorSel" @change="applyFilters">
              <option value="">传感器：全部</option>
              <option v-for="s in sensorOptions" :key="s" :value="s">{{ s }}</option>
            </select>
            <select v-model="targetSel" @change="applyFilters">
              <option value="">目标类型：全部</option>
              <option v-for="t in targetOptions" :key="t" :value="t">{{ t }}</option>
            </select>
          </div>

          <div class="search" :key="'search-' + filterKey">
            <input v-model="keyword" placeholder="按名称/用户/标签搜索…" @keyup.enter="applyFilters" />
          </div>

          <div class="tools">
            <label class="chk small">
              <input
                type="checkbox"
                v-model="selectAll"
                @change="onToggleSelectAll"
                :disabled="!selectionMode || !pagedDatasets.length || loading"
              />
              <span>全选当前页</span>
            </label>
            <button class="btn ghost" :disabled="loading" @click="resetFilters">重置筛选</button>
          </div>
        </div>

        <div class="table-wrap" :key="'table-' + stateKey">
          <div v-if="loading" class="loading">加载中…</div>
          <table v-else>
            <thead>
              <tr>
                <th style="width:60px">序号</th>
                <th style="min-width:90px">数据集名称</th>
                <th style="width:120px">传感器类型</th>
                <th style="min-width:90px">目标类型</th>
                <th style="width:90px">类别数</th>
                <th style="min-width:330px">类别名称</th>
                <th style="width:100px">图片数</th>
                <th style="width:100px">样本数</th>
                <th style="width:120px">创建用户</th>
                <th style="width:90px">示例按钮</th>
                <th style="width:110px">所属子集</th>
                <th style="width:56px">选择</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, idx) in pagedDatasets" :key="row.id">
                <td>{{ (pagination.currentPage - 1) * pagination.pageSize + idx + 1 }}</td>
                <td class="cell-name">
                  <div class="name">{{ row.name }}</div>
                </td>
                <td>{{ row.sensor || '-' }}</td>
                <td>{{ row.targets && row.targets.length ? row.targets.join('、') : '-' }}</td>
                <td>{{ row.classes ? row.classes.length : 0 }}</td>
                <td>
                  <div class="class-tags">
                    <!-- 前 5 个 -->
                    <span
                      v-for="cls in row.classes.slice(0, 5)"
                      :key="row.id + '-' + cls.name"
                      class="tag"
                      :title="cls.name + '：' + cls.count"
                    >
                      {{ cls.name }}：{{ cls.count }}
                    </span>

                    <!-- 展开后剩余 -->
                    <template v-if="isMoreOpen(row.id)">
                      <span
                        v-for="cls in row.classes.slice(5)"
                        :key="row.id + '-more-' + cls.name"
                        class="tag"
                        :title="cls.name + '：' + cls.count"
                      >
                        {{ cls.name }}：{{ cls.count }}
                      </span>
                    </template>

                    <!-- "更多/收起"按钮放在最后 -->
                    <button
                      v-if="row.classes.length > 5"
                      class="more-btn"
                      @click="toggleMore(row.id)"
                      :aria-expanded="isMoreOpen(row.id) ? 'true' : 'false'"
                    >
                      <span v-if="!isMoreOpen(row.id)">更多 ({{ row.classes.length - 5 }})</span>
                      <span v-else>收起</span>
                    </button>
                  </div>
                </td>
                <td>{{ fmtNum(row.imageCount) }}</td>
                <td>{{ fmtNum(row.sampleCount) }}</td>
                <td>{{ safeUser(row) }}</td>
                <td>
                  <button class="btn xsmall" @click="openPreview(row)">示例</button>
                </td>
                <td>
                  <span class="aff" :data-type="affOf(row)">
                    {{ affLabel(affOf(row)) }}
                  </span>
                </td>
                <td>
                  <input
                    :key="'chk-' + row.id + '-' + affOf(row) + '-' + selectionMode"
                    type="checkbox"
                    :checked="isChecked(row.id)"
                    :disabled="isDisabled(row.id) || loading"
                    :title="disabledReason(row.id)"
                    @change="onToggleRow(row, $event.target.checked)"
                  />
                </td>
              </tr>

              <tr v-if="!pagedDatasets.length">
                <td colspan="12" class="empty">无匹配数据集</td>
              </tr>
            </tbody>
          </table>

          <!-- 分页控件 -->
          <div v-if="!loading && filtered.length > 0" class="pagination">
            <div class="pagination-info">
              显示第 {{ paginationStart }} - {{ paginationEnd }} 条，共 {{ filtered.length }} 条记录
            </div>
            
            <div class="pagination-controls">
              <button 
                class="page-btn" 
                :disabled="pagination.currentPage === 1" 
                @click="goToPage(pagination.currentPage - 1)"
              >
                上一页
              </button>
              
              <div class="page-numbers">
                <button
                  v-for="page in visiblePages"
                  :key="page"
                  class="page-number"
                  :class="{ active: page === pagination.currentPage }"
                  @click="goToPage(page)"
                >
                  {{ page }}
                </button>
              </div>
              
              <button 
                class="page-btn" 
                :disabled="pagination.currentPage >= totalPages" 
                @click="goToPage(pagination.currentPage + 1)"
              >
                下一页
              </button>
            </div>
            
            <div class="page-size-selector">
              <span>每页显示：</span>
              <select v-model="pagination.pageSize" @change="onPageSizeChange">
                <option value="10">10</option>
                <option value="20">20</option>
                <option value="50">50</option>
              </select>
            </div>
          </div>
        </div>
      </section>

      <!-- 右侧：任务数据集设置 -->
      <aside class="form panel">
        <!-- 右侧内容保持不变 -->
        <h3>任务数据集设置</h3>

        <div class="mode">
          <button class="btn" :data-active="selectionMode === 'target'" @click="toggleMode('target')">
            选择任务目标子集
          </button>
          <button class="btn" :data-active="selectionMode === 'train'" @click="toggleMode('train')">
            选择预训练子集
          </button>
        </div>

        <div class="subset">
          <div class="subset-head">
            <strong>任务目标子集</strong>
            <span class="muted">{{ sub.target.length }} 个</span>
          </div>

          <div v-if="sub.target.length" class="vlist">
            <div
              v-for="r in targetVisible"
              :key="'g-' + r.id"
              class="vitem"
            >
              <span class="vlabel">{{ r.name }}</span>
              <button class="x" @click="removeFrom('target', r.id)">×</button>
            </div>

            <button
              v-if="sub.target.length > 5"
              class="more-link"
              @click="showAllTarget = !showAllTarget"
            >
              <span v-if="!showAllTarget">更多 ({{ sub.target.length - 5 }})</span>
              <span v-else>收起</span>
            </button>
          </div>
          <div v-else class="muted" style="padding:6px 8px;">从左侧表格勾选后显示</div>
        </div>

        <div class="subset">
          <div class="subset-head">
            <strong>预训练子集</strong>
            <span class="muted">{{ sub.train.length }} 个</span>
          </div>

          <div v-if="sub.train.length" class="vlist">
            <div
              v-for="r in trainVisible"
              :key="'t-' + r.id"
              class="vitem"
            >
              <span class="vlabel">{{ r.name }}</span>
              <button class="x" @click="removeFrom('train', r.id)">×</button>
            </div>

            <button
              v-if="sub.train.length > 5"
              class="more-link"
              @click="showAllTrain = !showAllTrain"
            >
              <span v-if="!showAllTrain">更多 ({{ sub.train.length - 5 }})</span>
              <span v-else>收起</span>
            </button>
          </div>
          <div v-else class="muted" style="padding:6px 8px;">从左侧表格勾选后显示</div>
        </div>

        <div class="divider"></div>

        <button class="btn primary full" :disabled="!canBuild || building" @click="build">
          {{ building ? '生成中…' : '生成任务数据集' }}
        </button>
      </aside>
    </div>

    <!-- 示例弹窗保持不变 -->
    <div v-if="showPreview" class="modal-mask" @click.self="closePreview">
      <!-- 弹窗内容保持不变 -->
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus'
import { EngineProjectService, OriginalDatasetService } from '@/api/api'

const route = useRoute();

const router = useRouter();

/* 分页状态 */
const pagination = ref({
  currentPage: 1,
  pageSize: 10
})

/* 分页计算属性 */
const totalPages = computed(() => 
  Math.ceil(filtered.value.length / pagination.value.pageSize)
)

const pagedDatasets = computed(() => {
  const startIndex = (pagination.value.currentPage - 1) * pagination.value.pageSize
  const endIndex = startIndex + pagination.value.pageSize
  return filtered.value.slice(startIndex, endIndex)
})

const paginationStart = computed(() => 
  filtered.value.length === 0 ? 0 : (pagination.value.currentPage - 1) * pagination.value.pageSize + 1
)

const paginationEnd = computed(() => 
  Math.min(pagination.value.currentPage * pagination.value.pageSize, filtered.value.length)
)

const visiblePages = computed(() => {
  const current = pagination.value.currentPage
  const total = totalPages.value
  const maxVisible = 5
  
  if (total <= maxVisible) {
    return Array.from({ length: total }, (_, i) => i + 1)
  }
  
  let start = Math.max(1, current - Math.floor(maxVisible / 2))
  let end = Math.min(total, start + maxVisible - 1)
  
  if (end - start + 1 < maxVisible) {
    start = Math.max(1, end - maxVisible + 1)
  }
  
  return Array.from({ length: end - start + 1 }, (_, i) => start + i)
})

/* 分页方法 */
function goToPage(page) {
  if (page >= 1 && page <= totalPages.value) {
    pagination.value.currentPage = page
    selectAll.value = false // 切换页面时重置全选状态
  }
}

function onPageSizeChange() {
  pagination.value.currentPage = 1 // 切换页面大小时回到第一页
  selectAll.value = false
}

/* 修改全选功能，只对当前页生效 */
function onToggleSelectAll() {
  const which = selectionMode.value
  if (!which) { selectAll.value = false; return }
  
  const otherSet = which === 'target' ? trainIdSet.value : targetIdSet.value
  const pool = new Map(pagedDatasets.value.map(x => [x.id, x]))
  
  if (selectAll.value) {
    // 选中当前页所有未被禁用的项目
    const currentSet = new Set(sub.value[which].map(x => x.id))
    const toAdd = pagedDatasets.value
      .filter(row => !otherSet.has(row.id) && !currentSet.has(row.id))
      .map(row => pool.get(row.id))
    
    if (toAdd.length) sub.value[which] = [...sub.value[which], ...toAdd]
  } else {
    // 取消选中当前页所有项目
    const currentPageIds = new Set(pagedDatasets.value.map(x => x.id))
    sub.value[which] = sub.value[which].filter(x => !currentPageIds.has(x.id))
  }
  stateKey.value++
}

/* 修改筛选函数，重置到第一页 */
function applyFilters() { 
  pagination.value.currentPage = 1
  selectAll.value = false
  filterKey.value++ 
}

function resetFilters() {
  sensorSel.value = ''
  targetSel.value = ''
  keyword.value = ''
  selectAll.value = false
  sub.value.target = []
  sub.value.train = []
  showAllTarget.value = false
  showAllTrain.value = false
  selectionMode.value = ''
  pagination.value.currentPage = 1
  filterKey.value++
  stateKey.value++
}

/* 原有的其他代码保持不变 */
const sensorOptions = ref([])
const targetOptions = ref([])
const sensorSel = ref('')
const targetSel = ref('')
const keyword = ref('')
const taskName = ref('')
const selectionMode = ref('')
const moreOpenMap = ref({})
const showAllTarget = ref(false)
const showAllTrain = ref(false)
const filterKey = ref(0)
const stateKey = ref(0)
const allDatasets = ref([])
const loading = ref(false)
const building = ref(false)
const sub = ref({ target: [], train: [] })
const targetIdSet = computed(() => new Set(sub.value.target.map(x => x.id)))
const trainIdSet = computed(() => new Set(sub.value.train.map(x => x.id)))
const targetVisible = computed(() =>
  showAllTarget.value ? sub.value.target : sub.value.target.slice(0, 5)
)
const trainVisible = computed(() =>
  showAllTrain.value ? sub.value.train : sub.value.train.slice(0, 5)
)
const selectAll = ref(false)

function isMoreOpen(id) { return !!moreOpenMap.value[id] }
function toggleMore(id) { moreOpenMap.value = { ...moreOpenMap.value, [id]: !moreOpenMap.value[id] } }

function safeParse(objOrStr) {
  if (!objOrStr) return {}
  if (typeof objOrStr === 'object') return objOrStr
  try { return JSON.parse(objOrStr) } catch { return {} }
}

function mapDatasetRow(r) {
  const sensorType = r.sensor_type ?? r.sensorType ?? ''
  const targetType = r.target_type ?? r.targetType ?? ''
  const imgNum = r.img_num ?? r.imgNum ?? 0
  const annoNum = r.anno_num ?? r.annoNum ?? 0
  const username = r.username ?? r.user ?? '-'
  const classListRaw = r.class_list ?? r.classList ?? null
  const typeMark = r.type_mark ?? r.typeMark ?? null

  const clsObj = safeParse(classListRaw)
  const classes = Object.keys(clsObj).map(name => ({ name, count: clsObj[name] }))

  return {
    id: r.id,
    name: r.name || '',
    sensor: sensorType || '-',
    targets: targetType ? [targetType] : [],
    classes,
    imageCount: Number.isFinite(imgNum) ? imgNum : 0,
    sampleCount: Number.isFinite(annoNum) ? annoNum : 0,
    user: username,
    typeMark
  }
}

function deriveOptionsFromDatasets(items) {
  const sset = new Set()
  const tset = new Set()
  items.forEach(r => {
    const s = r.sensor_type ?? r.sensorType
    const t = r.target_type ?? r.targetType
    if (s) sset.add(s)
    if (t) tset.add(t)
  })
  sensorOptions.value = Array.from(sset)
  targetOptions.value = Array.from(tset)
}

async function loadDatasets() {
  loading.value = true
  try {
    const params = { page: 1, size: 1000, sortBy: 'created_time', order: 'desc' }
    const raw = await OriginalDatasetService.list(params)

    const obj = typeof raw === 'string' ? JSON.parse(raw) : raw
    const dataNode = (obj && obj.data !== undefined) ? obj.data : obj
    const items = Array.isArray(dataNode?.items)
      ? dataNode.items
      : Array.isArray(obj?.items)
        ? obj.items
        : Array.isArray(obj)
          ? obj
          : []

    deriveOptionsFromDatasets(items)
    allDatasets.value = items.map(mapDatasetRow)
  } catch (e) {
    console.error('[loadDatasets error]', e)
    allDatasets.value = []
    sensorOptions.value = []
    targetOptions.value = []
  } finally {
    loading.value = false
  }
}

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return allDatasets.value.filter(r => {
    const sensorOk = !sensorSel.value || r.sensor === sensorSel.value
    const targetOk = !targetSel.value || r.targets.includes(targetSel.value)
    const clsNames = (r.classes || []).map(c => c.name)
    const hay = [r.id, r.name, r.user, r.sensor, r.targets.join(','), clsNames.join(',')].join(' ').toLowerCase()
    const kwOk = !kw || hay.includes(kw)
    return sensorOk && targetOk && kwOk
  })
})

function affOf(row) {
  if (targetIdSet.value.has(row.id)) return 'target'
  if (trainIdSet.value.has(row.id)) return 'train'
  if (row.typeMark === 0) return 'target'
  if (row.typeMark === 1) return 'train'
  return ''
}

function affLabel(t) {
  return t === 'target' ? '任务目标' : t === 'train' ? '预训练' : '无'
}

function isChecked(id) {
  return selectionMode.value === 'target'
    ? targetIdSet.value.has(id)
    : selectionMode.value === 'train'
      ? trainIdSet.value.has(id)
      : false
}

function isDisabled(id) {
  if (!selectionMode.value) return true
  if (selectionMode.value === 'target') return trainIdSet.value.has(id)
  if (selectionMode.value === 'train') return targetIdSet.value.has(id)
  return true
}

function disabledReason(id) {
  if (!selectionMode.value) return '请先在右侧选择子集类型'
  if (selectionMode.value === 'target' && trainIdSet.value.has(id)) return '已在预训练子集，不能加入任务目标子集'
  if (selectionMode.value === 'train' && targetIdSet.value.has(id)) return '已在任务目标子集，不能加入预训练子集'
  return ''
}

function toggleMode(mode) {
  selectionMode.value = selectionMode.value === mode ? '' : mode
  selectAll.value = false
  stateKey.value++
}

function onToggleRow(row, checked) {
  const which = selectionMode.value
  if (!which) { alert('请先在右侧选择子集类型'); return }
  if (checked) {
    const set = which === 'target' ? targetIdSet.value : trainIdSet.value
    if (!set.has(row.id)) sub.value[which] = [...sub.value[which], row]
  } else {
    sub.value[which] = sub.value[which].filter(x => x.id !== row.id)
  }
  stateKey.value++
}

function removeFrom(which, id) {
  sub.value[which] = sub.value[which].filter(x => x.id !== id)
  stateKey.value++
}

const canBuild = computed(() =>
  taskName.value.trim() && (sub.value.target.length + sub.value.train.length) > 0
)

async function build() {
  if (!canBuild.value) return
  const payload = {
    target: sub.value.target.map(x => x.id),
    train:  sub.value.train.map(x => x.id),
    fatherName: taskName.value.trim()
  }
  try {
    building.value = true
    const res = await OriginalDatasetService.markSubsets(payload)
    const ok = res?.code === 0 || res?.success || res?.ok
    if (ok) {
      const data = res?.data || {}
      ElMessage.success(`已更新：任务目标 ${data.targetUpdated ?? 0} 个，预训练 ${data.trainUpdated ?? 0} 个`)
      await loadDatasets()
      sub.value.target = []
      sub.value.train = []
      showAllTarget.value = false
      showAllTrain.value = false
      selectionMode.value = ''
      stateKey.value++

      router.push({
        path: '/taskDatasetMerge',
        query: {
          taskName: taskName.value 
        }
      });
    } else {
      ElMessage.error(res?.msg || '更新失败')
    }
  } catch (e) {
    ElMessage.error(`更新失败：${e?.message || e}`)
  } finally {
    building.value = false
  }
}

// 示例弹窗相关代码保持不变
const showPreview = ref(false)
const previewRow = ref(null)
const previewGroups = ref([])
const previewCount = ref(0)
const previewLoading = ref(false)
const objectsMeta = ref({})

function objectsUrlFromImageUrl(imgUrl) {
  return imgUrl.replace('/image?', '/objects?')
}

async function ensureAnnoFor(src) {
  if (objectsMeta.value[src]) return
  try {
    const url = objectsUrlFromImageUrl(src)
    const resp = await fetch(url)
    const res = await resp.json()
    const data = res?.data || res
    if (data && (data.width || Array.isArray(data.objects))) {
      objectsMeta.value = { ...objectsMeta.value, [src]: data }
    }
  } catch (e) {
    // 忽略失败
  }
}

function pointsAttr(points) {
  return Array.isArray(points) ? points.map(p => p.join(',')).join(' ') : ''
}

async function openPreview(row) {
  showPreview.value = true
  previewLoading.value = true
  previewRow.value = row
  previewGroups.value = []
  previewCount.value = 0

  try {
    const raw = await OriginalDatasetService.preview(row.id, 3)
    const obj = typeof raw === 'string' ? JSON.parse(raw) : raw

    const items = Array.isArray(obj?.data?.items)
      ? obj.data.items
      : Array.isArray(obj?.items)
        ? obj.items
        : Array.isArray(obj)
          ? obj
          : []

    const groups = items.map(it => {
      const name = it.label || it.className || it.name || '未命名'
      const images = Array.isArray(it.images)
        ? it.images
        : Array.isArray(it.urls)
          ? it.urls
          : []
      return { name, images }
    })

    previewGroups.value = groups
    previewCount.value = groups.reduce((sum, g) => sum + (Array.isArray(g.images) ? g.images.length : 0), 0)

    setTimeout(() => {
      groups.forEach(g => g.images.forEach(src => ensureAnnoFor(src)))
    }, 0)

  } catch (e) {
    ElMessage.error(`加载示例失败：${e?.message || e}`)
  } finally {
    previewLoading.value = false
  }
}

function closePreview() { showPreview.value = false }
function onImgError(e) {
  e.target.style.display = 'none'
  e.target.parentElement.classList.add('imgbox--fallback')
}

function fmtNum(n) { return Number.isFinite(n) ? n.toLocaleString() : '0' }
function safeUser(r) { return (r && r.user) ? r.user : '-' }
function clsTitle(cls) { return cls ? (cls.count != null ? (cls.name + '：' + cls.count) : cls.name) : '' }

function onKeydown(e) { if (e.key === 'Escape' && showPreview.value) closePreview() }
onMounted(async () => { 
  window.addEventListener('keydown', onKeydown); 
  await loadDatasets() 

  if (route.query.taskName) {
    taskName.value = route.query.taskName;
    console.log('接收到的数据集名称:', taskName.value);
  }
})
onUnmounted(() => { window.removeEventListener('keydown', onKeydown) })
</script>

<style scoped>
/* 原有的样式保持不变，只添加分页相关样式 */

/* 分页样式 */
.pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-top: 1px solid #e5e7eb;
  background: #f9fafb;
  flex-wrap: wrap;
  gap: 12px;
}

.pagination-info {
  color: #6b7280;
  font-size: 14px;
  white-space: nowrap;
}

.pagination-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-btn {
  padding: 6px 12px;
  border: 1px solid #d1d5db;
  background: white;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  color: #374151;
}

.page-btn:hover:not(:disabled) {
  background: #f3f4f6;
}

.page-btn:disabled {
  color: #9ca3af;
  cursor: not-allowed;
  background: #f9fafb;
}

.page-numbers {
  display: flex;
  gap: 4px;
}

.page-number {
  min-width: 36px;
  height: 36px;
  padding: 0 8px;
  border: 1px solid #d1d5db;
  background: white;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.page-number:hover {
  background: #f3f4f6;
}

.page-number.active {
  background: #3b82f6;
  color: white;
  border-color: #3b82f6;
}

.page-size-selector {
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}

.page-size-selector select {
  padding: 6px 8px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: white;
  font-size: 14px;
}

/* 响应式调整 */
@media (max-width: 768px) {
  .pagination {
    flex-direction: column;
    align-items: stretch;
    text-align: center;
  }
  
  .pagination-controls {
    justify-content: center;
  }
}

/* 原有的其他样式保持不变 */
.page { min-height: 100vh; background: #ffffff; color: #000000; }
.layout { --form-w: clamp(320px, 24vw, 480px); width: calc(100% - 20px); margin: 10px; display: grid; grid-template-columns: 1fr var(--form-w); gap: 16px; }
.panel { border: 1px solid #e5e7eb; border-radius: 16px; background: #ffffff; padding: 14px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; gap: 10px; flex-wrap: wrap; }
.filters-row { display: flex; gap: 8px; align-items: center; }
.filters-row select { height: 34px; border-radius: 10px; border: 1px solid #e5e7eb; background: #ffffff; color: #000; padding: 0 10px; }
.search input { height: 34px; width: 260px; border-radius: 10px; border: 1px solid #e5e7eb; background: #fff; color: #000; padding: 0 12px; }
.tools { display: flex; align-items: center; gap: 8px; }
.chk { display: flex; gap: 8px; align-items: center; }
.chk.small span { font-size: 12px; }
.table-wrap { border: 1px solid #e5e7eb; border-radius: 14px; overflow: auto; background: #fff; }
.table-wrap .loading { padding: 16px; text-align: center; color: #6b7280; }
.table-wrap table { width: 100%; min-width: 1280px; border-collapse: collapse; }
.table-wrap th, .table-wrap td { padding: 10px 12px; border-bottom: 1px solid #e9edf2; text-align: left; vertical-align: top; }
.table-wrap thead th { font-size: 12px; opacity: .8; }
.name { font-weight: 600; }
.empty { height: 80px; text-align: center; color: #9ca3af; }
.class-tags { display: flex; flex-wrap: wrap; gap: 6px; align-items: flex-start; }
.tag { display: inline-block; padding: 2px 8px; border-radius: 6px; border: 1px solid #e5e7eb; font-size: 12px; line-height: 18px; white-space: nowrap; color: #000; background: #fff; }
.more-btn { height: 22px; padding: 0 8px; border-radius: 6px; border: 1px dashed #d1d5db; background: #fafafa; color: #374151; font-size: 12px; cursor: pointer; }
.aff { display: inline-block; padding: 2px 8px; border-radius: 999px; border: 1px solid #e5e7eb; font-size: 12px; }
.aff[data-type="target"] { background: #ecfdf5; border-color: #a7f3d0; }
.aff[data-type="train"]  { background: #eff6ff; border-color: #bfdbfe; }
.aff[data-type=""] { background: #f9fafb; border-color: #e5e7eb; color: #6b7280; }
input[type="checkbox"][disabled] { cursor: not-allowed; opacity: .5; }
.form .mode { display: flex; gap: 8px; margin-bottom: 10px; }
.form .mode .btn[data-active="true"] { outline: 2px solid #3b82f6; }
.form .row { display: flex; gap: 10px; align-items: center; margin-bottom: 10px; }
.form .row > span { width: 120px; opacity: .8; }
.form input { flex: 1; height: 34px; border-radius: 10px; border: 1px solid #e5e7eb; background: #fff; color: #000; padding: 0 10px; }
.form .row.stack { flex-direction: column; align-items: stretch; }
.form .row.stack > span { width: auto; margin-bottom: 6px; }
.subset { border: 1px solid #e5e7eb; border-radius: 12px; padding: 10px; margin-top: 10px; background: #f9fafb; }
.subset-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.vlist { display: flex; flex-direction: column; gap: 6px; max-height: none; overflow: visible; padding: 4px 2px; }
.vitem { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 6px 10px; border: 1px solid #e5e7eb; border-radius: 10px; background: #fff; }
.vlabel { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.more-link { margin-top: 6px; height: 28px; padding: 0 10px; border-radius: 8px; border: 1px dashed #d1d5db; background: #fafafa; color: #374151; font-size: 12px; cursor: pointer; }
.btn { height: 34px; padding: 0 12px; border-radius: 10px; border: 1px solid #e5e7eb; background: #f3f4f6; color: #000; cursor: pointer; }
.btn.ghost { background: transparent; }
.btn.xsmall { height: 24px; padding: 0 8px; border-radius: 6px; }
.btn.primary { background: #3b82f6; border-color: #3b82f6; color: #fff !important; }
.btn.full { width: 100%; }
.divider { height: 1px; background: #e5e7eb; margin: 12px 0; }
.modal-mask { position: fixed; inset: 0; z-index: 50; background: rgba(0, 0, 0, .5); display: flex; align-items: center; justify-content: center; padding: 20px; }
.modal { width: min(1000px, calc(100vw - 40px)); max-height: calc(100vh - 120px); background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; box-shadow: 0 10px 30px rgba(0, 0, 0, .15); display: flex; flex-direction: column; overflow: hidden; }
.modal-header { display: flex; align-items: center; justify-content: space-between; padding: 12px 14px; border-bottom: 1px solid #e5e7eb; background: #fff; }
.modal-title { font-weight: 600; }
.modal-title .muted { font-weight: 400; font-size: 12px; margin-left: 8px; color: #6b7280; }
.modal-close { border: 1px solid #e5e7eb; background: #f3f4f6; border-radius: 8px; width: 32px; height: 32px; cursor: pointer; font-size: 18px; line-height: 30px; text-align: center; }
.modal-body { padding: 12px; overflow: auto; background: #fff; }
.modal-loading { padding: 24px 0; text-align: center; color: #6b7280; }
.modal-empty { padding: 24px 0; text-align: center; color: #9ca3af; }
.imgbox { aspect-ratio: 4 / 3; border: 1px solid #e5e7eb; border-radius: 10px; overflow: hidden; background: linear-gradient(135deg, #eef2ff, #f8fafc); }
.imgbox img { width: 100%; height: 100%; object-fit: cover; display: block; }
.imgbox--fallback { background: repeating-linear-gradient(45deg, #f3f4f6, #f3f4f6 8px, #e5e7eb 8px, #e5e7eb 16px); }
.modal-footer { padding: 10px 12px; border-top: 1px solid #e5e7eb; background: #fff; display: flex; justify-content: flex-end; gap: 8px; }
.cat-list { display: flex; flex-direction: column; gap: 12px; }
.cat-row { display: grid; grid-template-columns: 120px 1fr; gap: 12px; align-items: center; }
.cat-label { font-weight: 600; line-height: 32px; white-space: nowrap; }
.cat-images { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.modal .imgbox { aspect-ratio: auto; }
.modal .imgbox .anno-svg { width: 100%; height: auto; display: block; }
.modal .imgbox .anno-fallback { width: 100%; height: auto; display: block; }
.anno-poly { fill: rgba(59,130,246,.12); stroke: #3b82f6; stroke-width: 2; }

@media (max-width: 1280px) {
  .layout { grid-template-columns: 1fr; }
}
</style>