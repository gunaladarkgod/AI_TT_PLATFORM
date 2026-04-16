<template>
  <div class="content">
    <!-- 顶部筛选区：沿用原来的逻辑 + 结果查询的布局风格 -->
    <div class="search-div flex-between">
      <div class="flex-start gap-8">
        <el-select
          v-model="sensorSel"
          placeholder="传感器：全部"
          size="small"
          class="width-160"
          @change="applyFilters"
          clearable
        >
          <el-option
            v-for="s in sensorOptions"
            :key="s"
            :label="s"
            :value="s"
          />
        </el-select>

        <el-select
          v-model="targetSel"
          placeholder="目标类型：全部"
          size="small"
          class="width-160"
          @change="applyFilters"
          clearable
        >
          <el-option
            v-for="t in targetOptions"
            :key="t"
            :label="t"
            :value="t"
          />
        </el-select>

        <el-input
          v-model="keyword"
          placeholder="按名称/用户/标签搜索…"
          size="small"
          class="width-260"
          @keyup.enter="applyFilters"
          clearable
        />
      </div>

      <div class="flex-start gap-8">
        <el-button
          size="small"
          @click="resetFilters"
        >
          重置筛选
        </el-button>
        <el-button
          size="small"
          type="success"
          plain
          @click="openImportDialog"
        >
          导入外来数据集
        </el-button>
        <el-button
          size="small"
          @click="goCvatHome"
        >
          查看CVAT主页
        </el-button>
        <el-button
          type="primary"
          size="small"
          :loading="loading"
          @click="reload"
        >
          刷新
        </el-button>
      </div>
    </div>

    <!-- 表格区域：Element Plus el-table 风格 -->
    <div class="table-div">
      <el-table
        class="my-table"
        :data="pageData"
        stripe
        size="small"
        v-loading="loading"
        v-el-height-adaptive-table="{ bottomOffset: 120, isUse: true }"
      >
        <!-- 序号 -->
        <el-table-column
          label="序号"
          width="70"
          align="center"
          fixed="left"
        >
          <template #default="scope">
            <!-- 这里用分页后的真实序号 -->
            <el-text size="small">
              {{ (currentPage - 1) * pageSize + scope.$index + 1 }}
            </el-text>
          </template>
        </el-table-column>

        <!-- 数据集名称 -->
        <el-table-column
          prop="name"
          label="数据集名称"
          min-width="120"
          align="left"
          fixed="left"
        >
          <template #default="scope">
            <el-text size="small" truncated>{{ scope.row.name }}</el-text>
          </template>
        </el-table-column>

        <el-table-column
          prop="source"
          label="数据来源"
          width="120"
          align="center"
        >
          <template #default="scope">
            <el-tag size="small" :type="scope.row.source === '外部导入' ? 'success' : 'info'">
              {{ scope.row.source || 'CVAT' }}
            </el-tag>
          </template>
        </el-table-column>

        <!-- 传感器类型 -->
        <el-table-column
          prop="sensor"
          label="传感器类型"
          width="120"
          align="center"
        >
          <template #default="scope">
            <el-text size="small">
              {{ scope.row.sensor || '-' }}
            </el-text>
          </template>
        </el-table-column>

        <!-- 目标类型 -->
        <el-table-column
          prop="targets"
          label="目标类型"
          min-width="100"
          align="center"
        >
          <template #default="scope">
            <el-text size="small">
              {{ scope.row.targets && scope.row.targets.length ? scope.row.targets.join('、') : '-' }}
            </el-text>
          </template>
        </el-table-column>

        <!-- 类别数 -->
        <el-table-column
          label="类别数"
          width="90"
          align="center"
        >
          <template #default="scope">
            <el-text size="small">
              {{ scope.row.classes ? scope.row.classes.length : 0 }}
            </el-text>
          </template>
        </el-table-column>

        <!-- 类别名称（标签 + 更多） -->
        <el-table-column
          label="类别名称"
          min-width="360"
          align="left"
        >
          <template #default="scope">
            <div class="class-tags">
              <!-- 前 5 个 -->
              <span
                v-for="cls in scope.row.classes.slice(0, 5)"
                :key="scope.row.id + '-' + cls.name"
                class="tag"
                :title="cls.name + '：' + cls.count"
              >
                {{ cls.name }}：{{ cls.count }}
              </span>

              <!-- 展开后剩余 -->
              <template v-if="isMoreOpen(scope.row.id)">
                <span
                  v-for="cls in scope.row.classes.slice(5)"
                  :key="scope.row.id + '-more-' + cls.name"
                  class="tag"
                  :title="cls.name + '：' + cls.count"
                >
                  {{ cls.name }}：{{ cls.count }}
                </span>
              </template>

              <!-- “更多/收起”按钮 -->
              <el-button
                v-if="scope.row.classes.length > 5"
                class="more-btn"
                text
                size="small"
                @click="toggleMore(scope.row.id)"
              >
                <span v-if="!isMoreOpen(scope.row.id)">
                  更多 ({{ scope.row.classes.length - 5 }})
                </span>
                <span v-else>收起</span>
              </el-button>
            </div>
          </template>
        </el-table-column>

        <!-- 图片数 -->
        <el-table-column
          prop="imageCount"
          label="图片数"
          width="100"
          align="center"
        >
          <template #default="scope">
            <el-text size="small">
              {{ fmtNum(scope.row.imageCount) }}
            </el-text>
          </template>
        </el-table-column>

        <!-- 样本数 -->
        <el-table-column
          prop="sampleCount"
          label="样本数"
          width="100"
          align="center"
        >
          <template #default="scope">
            <el-text size="small">
              {{ fmtNum(scope.row.sampleCount) }}
            </el-text>
          </template>
        </el-table-column>

        <!-- 创建用户 -->
        <el-table-column
          prop="user"
          label="创建用户"
          width="120"
          align="center"
        >
          <template #default="scope">
            <el-text size="small">
              {{ safeUser(scope.row) }}
            </el-text>
          </template>
        </el-table-column>

        <!-- 操作 -->
        <el-table-column
          label="操作"
          width="150"
          align="center"
          fixed="right"
        >
          <template #default="scope">
            <el-button
              size="small"
              type="primary"
              link
              @click="openPreview(scope.row)"
            >
              示例
            </el-button>
            <el-button
              size="small"
              type="danger"
              link
              @click="handleDeleteDataset(scope.row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 分页：风格参考结果查询页面 -->
    <div class="flex-end">
      <el-pagination
        background
        size="small"
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <el-dialog
      v-model="importDialogVisible"
      title="导入外来数据集"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form label-width="110px">
        <el-form-item label="数据集路径">
          <el-input
            v-model="importForm.path"
            readonly
            placeholder="请输入绝对路径，例如 /mnt/data/my_dataset"
          >
            <template #append>
              <el-button @click="pickLocalDir">选择目录</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="显示名称">
          <el-input
            v-model="importForm.name"
            placeholder="例如：自定义项目_V1"
            clearable
          />
        </el-form-item>
      </el-form>

      <div v-if="importStatus.msg" :style="{ color: importStatus.ok ? '#67c23a' : '#f56c6c', marginBottom: '8px' }">
        {{ importStatus.msg }}
      </div>
      <div v-if="importStatus.ok" style="font-size: 12px; color: #606266;">
        读取结果：图片 {{ importStatus.imgNum }}，标注框 {{ importStatus.annoNum }}，类别 {{ importStatus.classNum }}
      </div>

      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button :loading="importChecking" @click="checkImportPath">校验路径</el-button>
        <el-button type="primary" :loading="importing" @click="confirmImport">确认导入</el-button>
      </template>
    </el-dialog>

    <!-- 示例弹窗（保留原有样式与功能） -->
    <div v-if="showPreview" class="modal-mask" @click.self="closePreview">
      <div class="modal">
        <div class="modal-header">
          <div class="modal-title">
            示例预览：{{ (previewRow && previewRow.name) || '' }}
            <span class="muted">（共 {{ previewCount }} 张）</span>
          </div>
          <div class="modal-actions">
            <button class="btn btn-primary" :disabled="previewLoading" @click="refreshPreviewSamples">换一换</button>
            <button class="modal-close" @click="closePreview" aria-label="关闭">×</button>
          </div>
        </div>

        <div class="modal-body">
          <div v-if="previewLoading" class="modal-loading">加载中…</div>

          <div v-else-if="!previewGroups.length" class="modal-empty">
            暂无可展示的示例图片
          </div>

          <div v-else class="cat-list">
            <div v-for="group in previewGroups" :key="group.name" class="cat-row">
              <div class="cat-label">{{ group.name }}</div>
              <div class="cat-images">
                <div
                  v-for="(src, i) in group.images"
                  :key="group.name + '-' + i"
                  class="imgbox"
                >
                  <!-- 有标注：SVG 叠加多边形（去掉文字标签） -->
                  <svg
                    v-if="objectsMeta[src]?.width && objectsMeta[src]?.height"
                    class="anno-svg"
                    :viewBox="`0 0 ${objectsMeta[src].width} ${objectsMeta[src].height}`"
                    preserveAspectRatio="xMidYMid meet"
                  >
                    <image
                      :href="src"
                      :width="objectsMeta[src].width"
                      :height="objectsMeta[src].height"
                    />
                    <g
                      v-for="(obj, idx2) in objectsMeta[src].objects || []"
                      :key="idx2"
                    >
                      <polygon
                        :points="pointsAttr(obj.points)"
                        class="anno-poly"
                      />
                    </g>
                  </svg>

                  <!-- 暂无标注数据：先出图，再异步拉取 -->
                  <img
                    v-else
                    :src="src"
                    alt="示例图片"
                    class="anno-fallback"
                    @error="onImgError($event)"
                    @load="ensureAnnoFor(src)"
                  />
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="modal-footer">
          <button class="btn" @click="closePreview">关闭</button>
        </div>
      </div>
    </div>
    <!-- /示例弹窗 -->
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { EngineProjectService, OriginalDatasetService } from '@/api/api'

/* 动态筛选项 */
const sensorOptions = ref([])
const targetOptions = ref([])

const sensorSel = ref('')
const targetSel = ref('')
const keyword = ref('')

/* “更多”展开状态（类名列） */
const moreOpenMap = ref({}) // { [rowId]: true/false }
function isMoreOpen(id) {
  return !!moreOpenMap.value[id]
}
function toggleMore(id) {
  moreOpenMap.value = { ...moreOpenMap.value, [id]: !moreOpenMap.value[id] }
}

/* 刷新 key（保留，必要时可强制触发一些依赖重算） */
const filterKey = ref(0)
const stateKey = ref(0)

/* 数据源 */
const allDatasets = ref([])
const loading = ref(false)

/* 外部导入弹窗 */
const importDialogVisible = ref(false)
const importChecking = ref(false)
const importing = ref(false)
const importForm = ref({
  path: '',
  name: ''
})
const importStatus = ref({
  ok: false,
  msg: '',
  imgNum: 0,
  annoNum: 0,
  classNum: 0
})

/* 分页（前端分页） */
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

/* 安全解析 JSON */
function safeParse(objOrStr) {
  if (!objOrStr) return {}
  if (typeof objOrStr === 'object') return objOrStr
  try {
    return JSON.parse(objOrStr)
  } catch {
    return {}
  }
}

/* original_dataset → 行映射 */
function mapDatasetRow(r) {
  const sensorType = r.sensor_type ?? r.sensorType ?? ''
  const targetType = r.target_type ?? r.targetType ?? ''
  const imgNum = r.img_num ?? r.imgNum ?? 0
  const annoNum = r.anno_num ?? r.annoNum ?? 0
  const username = r.username ?? r.user ?? '-'
  const classListRaw = r.class_list ?? r.classList ?? null

  const clsObj = safeParse(classListRaw) // { 类名: 数量 }
  const classes = Object.keys(clsObj).map(name => ({
    name,
    count: clsObj[name]
  }))

  return {
    id: r.id,
    name: r.name || '',
    sensor: sensorType || '-',
    targets: targetType ? [targetType] : [],
    classes,
    imageCount: Number.isFinite(imgNum) ? imgNum : 0,
    sampleCount: Number.isFinite(annoNum) ? annoNum : 0,
    user: username,
    source: r.data_source ?? r.dataSource ?? (r.is_external ? '外部导入' : 'CVAT'),
    isExternal: !!r.is_external,
    externalPath: r.external_path ?? r.externalPath ?? r.path ?? '',
    error: r.error || ''
  }
}

/* 推导筛选项（传入行数据） */
function deriveOptionsFromDatasets(items) {
  const sset = new Set()
  const tset = new Set()
  items.forEach(r => {
    const s = r.sensor
    const t = Array.isArray(r.targets) && r.targets.length ? r.targets[0] : null
    if (s) sset.add(s)
    if (t) tset.add(t)
  })
  sensorOptions.value = Array.from(sset)
  targetOptions.value = Array.from(tset)
}

/* 加载 original_dataset 列表（一次拉到前端，前端分页） */
async function loadDatasets() {
  loading.value = true
  try {
    const params = {
      page: 1,
      size: 1000,
      sortBy: 'created_time',
      order: 'desc'
    }
    const raw = await OriginalDatasetService.list(params)
    const rawExternal = await OriginalDatasetService.listExternal()

    const obj = typeof raw === 'string' ? JSON.parse(raw) : raw
    const extObj = typeof rawExternal === 'string' ? JSON.parse(rawExternal) : rawExternal
    const dataNode = obj && obj.data !== undefined ? obj.data : obj
    const extNode = extObj && extObj.data !== undefined ? extObj.data : extObj
    const items = Array.isArray(dataNode?.items)
      ? dataNode.items
      : Array.isArray(obj?.items)
      ? obj.items
      : Array.isArray(obj)
      ? obj
      : []
    const extItems = Array.isArray(extNode) ? extNode : []

    const rows = [...items, ...extItems].map(mapDatasetRow)
    deriveOptionsFromDatasets(rows)
    allDatasets.value = rows
  } catch (e) {
    console.error('[loadDatasets error]', e)
    allDatasets.value = []
    sensorOptions.value = []
    targetOptions.value = []
  } finally {
    loading.value = false
  }
}

/* 刷新：触发拉取缺失 → 重载列表 */
async function reload() {
  loading.value = true
  try {
    const res = await EngineProjectService.pullMissing({})
    const ok = res?.code === 0 || res?.success || res?.ok
    const data = res?.data || {}
    const succ = data.success ?? 0
    const fail = data.fail ?? 0
    const skip = data.skip ?? data.skipped ?? 0
    if (ok) {
      ElMessage.success(`下载完成：成功 ${succ}，跳过 ${skip}，失败 ${fail}`)
    } else {
      ElMessage.error(res?.msg || '触发下载失败')
    }
  } catch (e) {
    ElMessage.error(`触发下载失败：${e?.message || e}`)
  } finally {
    await loadDatasets()
    loading.value = false
  }
}

/* 前端筛选 */
const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return allDatasets.value.filter(r => {
    const sensorOk = !sensorSel.value || r.sensor === sensorSel.value
    const targetOk = !targetSel.value || r.targets.includes(targetSel.value)
    const clsNames = (r.classes || []).map(c => c.name)
    const hay = [
      r.id,
      r.name,
      r.user,
      r.sensor,
      r.targets.join(','),
      clsNames.join(',')
    ]
      .join(' ')
      .toLowerCase()
    const kwOk = !kw || hay.includes(kw)
    return sensorOk && targetOk && kwOk
  })
})

/* 分页数据：对 filtered 做切片 */
const pageData = computed(() => {
  const list = filtered.value
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return list.slice(start, end)
})

/* 过滤条件变化时，重置页码到 1，并更新 total */
watch(
  filtered,
  (val) => {
    total.value = val.length
    currentPage.value = 1
  },
  { immediate: true }
)

function applyFilters() {
  filterKey.value++
}

/* 重置筛选 */
function resetFilters() {
  sensorSel.value = ''
  targetSel.value = ''
  keyword.value = ''
  filterKey.value++
  stateKey.value++
}

function resetImportStatus() {
  importStatus.value = {
    ok: false,
    msg: '',
    imgNum: 0,
    annoNum: 0,
    classNum: 0
  }
}

function openImportDialog() {
  importDialogVisible.value = true
  importForm.value = { path: '', name: '' }
  resetImportStatus()
}

function inferDatasetNameFromPath(rawPath) {
  const p = String(rawPath || '').trim()
  if (!p) return ''
  const normalized = p.replace(/\\/g, '/').replace(/\/+$/, '')
  if (!normalized) return ''
  const idx = normalized.lastIndexOf('/')
  return idx >= 0 ? normalized.slice(idx + 1) : normalized
}

async function pickLocalDir() {
  try {
    const res = await OriginalDatasetService.pickExternalDir()
    if (res?.code === 0 && res?.data?.cancelled) {
      ElMessage.info(res?.msg || '已取消选择')
      return
    }
    if (res?.code === 0 && res?.data?.path) {
      const pickedPath = String(res.data.path)
      importForm.value.path = pickedPath
      importForm.value.name = inferDatasetNameFromPath(pickedPath)
      resetImportStatus()
      ElMessage.success('已获取目录路径')
    } else {
      ElMessage.error(res?.msg || '未选择目录')
    }
  } catch (e) {
    ElMessage.error(`打开本机文件选择器失败：${e?.message || e}`)
  }
}

async function checkImportPath() {
  const p = (importForm.value.path || '').trim()
  if (!p) {
    importStatus.value = { ok: false, msg: '请先填写数据集路径', imgNum: 0, annoNum: 0, classNum: 0 }
    return
  }
  importChecking.value = true
  try {
    const res = await OriginalDatasetService.validateExternal({ path: p })
    if (res?.code === 0) {
      const d = res.data || {}
      importStatus.value = {
        ok: true,
        msg: '路径有效，已检测到可用数据集',
        imgNum: d.imgNum ?? 0,
        annoNum: d.annoNum ?? 0,
        classNum: d.classNum ?? 0
      }
      if (!importForm.value.name) {
        importForm.value.name = d.suggestName || ''
      }
    } else {
      importStatus.value = { ok: false, msg: res?.msg || '路径校验失败', imgNum: 0, annoNum: 0, classNum: 0 }
    }
  } catch (e) {
    importStatus.value = { ok: false, msg: `路径校验失败：${e?.message || e}`, imgNum: 0, annoNum: 0, classNum: 0 }
  } finally {
    importChecking.value = false
  }
}

async function confirmImport() {
  const path = (importForm.value.path || '').trim()
  const name = (importForm.value.name || '').trim()
  if (!path || !name) {
    ElMessage.warning('请先填写路径和显示名称')
    return
  }
  importing.value = true
  try {
    const res = await OriginalDatasetService.importExternal({ name, path })
    if (res?.code === 0) {
      ElMessage.success(res?.msg || '导入成功')
      importDialogVisible.value = false
      await loadDatasets()
    } else {
      ElMessage.error(res?.msg || '导入失败')
    }
  } catch (e) {
    ElMessage.error(`导入失败：${e?.message || e}`)
  } finally {
    importing.value = false
  }
}

/* 跳转 CVAT 首页（可在 localStorage.cvatHomeUrl 覆盖默认地址） */
function goCvatHome() {
  const customUrl = String(localStorage.getItem('cvatHomeUrl') || '').trim()
  const fallback = `${window.location.protocol}//${window.location.hostname}:8080`
  const url = customUrl || fallback
  window.open(url, '_blank', 'noopener,noreferrer')
}

/* 分页事件 */
function handlePageChange(page) {
  currentPage.value = page
}
function handleSizeChange(size) {
  pageSize.value = size
  currentPage.value = 1
}

/* 示例弹窗 */
const showPreview = ref(false)
const previewRow = ref(null)
const previewGroups = ref([])
const previewCount = ref(0)
const previewLoading = ref(false)
const previewKeys = ref([])

/* 标注缓存与工具 */
const objectsMeta = ref({})

function objectsUrlFromImageUrl(imgUrl) {
  // http://.../original-dataset/{id}/image?img=xxx  →  http://.../original-dataset/{id}/objects?img=xxx
  return imgUrl.replace('/image?', '/objects?')
}

async function ensureAnnoFor(src) {
  if (objectsMeta.value[src]) return
  if (!src || src.indexOf('/original-dataset/external/image?') >= 0) return
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
  // [[x1,y1],[x2,y2],[x3,y3],[x4,y4]] -> "x1,y1 x2,y2 x3,y3 x4,y4"
  return Array.isArray(points)
    ? points.map(p => p.join(',')).join(' ')
    : ''
}

async function openPreview(row) {
  if (row?.error) {
    ElMessage.warning('当前数据集路径异常，无法预览')
    return
  }
  showPreview.value = true
  previewLoading.value = true
  previewRow.value = row
  previewGroups.value = []
  previewCount.value = 0
  previewKeys.value = []
  objectsMeta.value = {}

  try {
    const raw = await OriginalDatasetService.randomSample({
      id: row.id,
      isExternal: !!row.isExternal,
      path: row.externalPath || '',
      exclude: []
    })
    const obj = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (obj && obj.code !== undefined && obj.code !== 0) {
      throw new Error(obj.msg || '加载示例失败')
    }
    const data = obj?.data || obj || {}
    const images = Array.isArray(data.images) ? data.images : []
    const keys = Array.isArray(data.keys) ? data.keys : []
    const groups = images.length ? [{ name: '随机样例', images }] : []

    previewGroups.value = groups
    previewKeys.value = keys
    previewCount.value = images.length

    // 预取每张图片的标注（不阻塞显示）
    setTimeout(() => {
      groups.forEach(g =>
        g.images.forEach(src => ensureAnnoFor(src))
      )
    }, 0)
  } catch (e) {
    ElMessage.error(`加载示例失败：${e?.message || e}`)
  } finally {
    previewLoading.value = false
  }
}

async function refreshPreviewSamples() {
  const row = previewRow.value
  if (!row) return
  previewLoading.value = true
  try {
    const raw = await OriginalDatasetService.randomSample({
      id: row.id,
      isExternal: !!row.isExternal,
      path: row.externalPath || '',
      exclude: previewKeys.value || []
    })
    const obj = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (obj && obj.code !== undefined && obj.code !== 0) {
      throw new Error(obj.msg || '换一换失败')
    }
    const data = obj?.data || obj || {}
    const images = Array.isArray(data.images) ? data.images : []
    const keys = Array.isArray(data.keys) ? data.keys : []
    previewGroups.value = images.length ? [{ name: '随机样例', images }] : []
    previewKeys.value = keys
    previewCount.value = images.length
    objectsMeta.value = {}
    setTimeout(() => {
      images.forEach(src => ensureAnnoFor(src))
    }, 0)
  } catch (e) {
    ElMessage.error(`换一换失败：${e?.message || e}`)
  } finally {
    previewLoading.value = false
  }
}

async function handleDeleteDataset(row) {
  if (!row?.isExternal) {
    ElMessage.warning('目标来源为cvat，请在cvat主页进行操作')
    return
  }
  try {
    const res = await OriginalDatasetService.deleteExternal({
      name: row.name || '',
      path: row.externalPath || ''
    })
    if (res?.code === 0) {
      ElMessage.success('删除成功')
      allDatasets.value = allDatasets.value.filter(
        it => !(it.isExternal && it.name === row.name && it.externalPath === row.externalPath)
      )
      total.value = filtered.value.length
      return
    }
    ElMessage.error(res?.msg || '删除失败')
  } catch (e) {
    ElMessage.error(`删除失败：${e?.message || e}`)
  }
}

function closePreview() {
  showPreview.value = false
}
function onImgError(e) {
  e.target.style.display = 'none'
  e.target.parentElement.classList.add('imgbox--fallback')
}

/* 模板辅助函数 */
function fmtNum(n) {
  return Number.isFinite(n) ? n.toLocaleString() : '0'
}
function safeUser(r) {
  return r && r.user ? r.user : '-'
}

/* 生命周期 */
function onKeydown(e) {
  if (e.key === 'Escape' && showPreview.value) closePreview()
}
onMounted(async () => {
  window.addEventListener('keydown', onKeydown)
  await loadDatasets()
})
onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
/* 页面整体：引用结果查询页的浅灰背景风格 */
.content {
  padding: 10px;
  background-color: #f5f7fa;
}

/* 顶部筛选区 */
.search-div {
  margin-bottom: 8px;
}
.flex-between {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.flex-start {
  display: flex;
  justify-content: flex-start;
  align-items: center;
}
.flex-end {
  display: flex;
  justify-content: flex-end;
  align-items: center;
}
.gap-8 {
  gap: 8px;
}

.width-160 {
  width: 160px;
}
.width-260 {
  width: 260px;
}

/* 表格容器 */
.table-div {
  padding-top: 8px;
  padding-bottom: 8px;
}

/* Element Plus 表格风格：带圆角和阴影，跟结果查询页一致 */
.my-table.el-table--small {
  border-radius: 4px;
}

.content ::v-deep(.el-table) {
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  border-radius: 8px;
  overflow: hidden;
}

.content ::v-deep(.el-table__header-wrapper) {
  background-color: #fafafa;
}

/* 类别标签 */
.class-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: flex-start;
}
.tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 6px;
  border: 1px solid #e5e7eb;
  font-size: 12px;
  line-height: 18px;
  white-space: nowrap;
  color: #000;
  background: #fff;
}
.more-btn {
  padding: 0 4px;
  margin-left: 2px;
}

/* 弹窗样式（沿用你原来的） */
.btn {
  height: 34px;
  padding: 0 12px;
  border-radius: 10px;
  border: 1px solid #e5e7eb;
  background: #f3f4f6;
  color: #000;
  cursor: pointer;
}

.btn-primary {
  border-color: #409eff;
  color: #fff;
  background: #409eff;
}

.modal-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 50;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}
.modal {
  width: min(1000px, calc(100vw - 40px));
  max-height: calc(100vh - 120px);
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
}
.modal-title {
  font-weight: 600;
}
.modal-title .muted {
  font-weight: 400;
  font-size: 12px;
  margin-left: 8px;
  color: #6b7280;
}
.modal-close {
  border: 1px solid #e5e7eb;
  background: #f3f4f6;
  border-radius: 8px;
  width: 32px;
  height: 32px;
  cursor: pointer;
  font-size: 18px;
  line-height: 30px;
  text-align: center;
}
.modal-body {
  padding: 12px;
  overflow: auto;
  background: #fff;
}
.modal-loading {
  padding: 24px 0;
  text-align: center;
  color: #6b7280;
}
.modal-empty {
  padding: 24px 0;
  text-align: center;
  color: #9ca3af;
}

.imgbox {
  aspect-ratio: 4 / 3;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  overflow: hidden;
  background: linear-gradient(135deg, #eef2ff, #f8fafc);
}
.imgbox img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.imgbox--fallback {
  background: repeating-linear-gradient(
    45deg,
    #f3f4f6,
    #f3f4f6 8px,
    #e5e7eb 8px,
    #e5e7eb 16px
  );
}
.modal-footer {
  padding: 10px 12px;
  border-top: 1px solid #e5e7eb;
  background: #fff;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.cat-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.cat-row {
  display: grid;
  grid-template-columns: 120px 1fr;
  gap: 12px;
  align-items: center;
}
.cat-label {
  font-weight: 600;
  line-height: 32px;
  white-space: nowrap;
}
.cat-images {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.modal .imgbox {
  aspect-ratio: auto;
}
.modal .imgbox .anno-svg {
  width: 100%;
  height: auto;
  display: block;
}
.modal .imgbox .anno-fallback {
  width: 100%;
  height: auto;
  display: block;
}

.anno-poly {
  fill: rgba(59, 130, 246, 0.12);
  stroke: #3b82f6;
  stroke-width: 2;
}
</style>
