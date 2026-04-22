<template>
  <div class="content" :class="{ 'content--embed': embedMode }">
    <el-card
      class="original-dataset-panel"
      :class="{ 'original-dataset-panel--embed': embedMode }"
      shadow="never"
    >
      <div
        class="original-dataset-panel__main"
        :class="{ 'original-dataset-panel__main--embed': embedMode }"
      >
      <div class="original-dataset-toolbar-row flex-between">
        <div class="flex-start gap-8">
          <el-button size="small" @click="clearTableColumnFilters">清除列筛选</el-button>
          <el-button size="small" @click="clearTableSort">清除列排序</el-button>
        </div>
        <div class="flex-start gap-8 original-dataset-toolbar-row__right">
          <el-input
            v-model="tableSearch"
            size="small"
            clearable
            placeholder="Type to search"
            class="original-dataset-toolbar-search"
          />
          <el-dropdown trigger="click">
            <el-button type="primary" size="small">
              数据操作
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item :disabled="loading" @click="reload">
                  CVAT数据刷新
                </el-dropdown-item>
                <el-dropdown-item @click="openImportDialog">
                  导入外来数据集
                </el-dropdown-item>
                <el-dropdown-item @click="goCvatHome">
                  查看CVAT主页
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

    <!-- 表格区域：Element Plus 表头筛选 + 自定义排序（与官方示例一致的风格） -->
    <div
      v-if="effectiveViewAsTable"
      class="table-div"
      :class="{
        'table-div--embed-scroll': embedMode,
        'original-dataset-panel__scroll': embedMode
      }"
    >
      <!-- 列宽：本表各 el-table-column 的 width / min-width（像素，字符串数字） -->
      <el-table
        ref="tableRef"
        row-key="id"
        class="my-table my-table--dataset-detail"
        :data="pageData"
        table-layout="fixed"
        style="width: 100%"
        stripe
        size="small"
        v-loading="loading"
        :height="embedMode ? '100%' : undefined"
        @filter-change="onElTableFilterChange"
        @sort-change="onTableSortChange"
        @row-click="handleOriginalDatasetTableRowClick"
        v-el-height-adaptive-table="{ bottomOffset: 120, isUse: !embedMode }"
      >
        <!-- 序号 -->
        <el-table-column
          label="序号"
          width="40"
          align="center"
          fixed="left"
        >
          <template #default="scope">
            <!-- 这里用分页后的真实序号 -->
            <el-text size="small">
              {{ (tableCurrentPage - 1) * tablePageSize + scope.$index + 1 }}
            </el-text>
          </template>
        </el-table-column>

        <!-- 数据集名称：固定列 + 固定宽度，避免被中间列撑开 -->
        <el-table-column
          prop="name"
          label="数据集名称"
          width="150"
          align="left"
          fixed="left"
          sortable="custom"
          column-key="name"
          show-overflow-tooltip
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
          column-key="source"
          sortable="custom"
          :filters="sourceFilterOptions"
          :filter-method="tableColumnFilterPassAll"
          filter-placement="bottom-end"
        >
          <template #default="scope">
            <el-tag
              size="small"
              disable-transitions
              :type="scope.row.source === '外部导入' ? 'success' : 'info'"
            >
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
          column-key="sensor"
          sortable="custom"
          :filters="sensorColumnFilters"
          :filter-method="tableColumnFilterPassAll"
        >
          <template #default="scope">
            <el-text size="small">
              {{ scope.row.sensor || '-' }}
            </el-text>
          </template>
        </el-table-column>

        <!-- 目标类型（须容纳标题 + 排序 + 筛选图标同一行） -->
        <el-table-column
          prop="targets"
          label="目标类型"
          min-width="120"
          align="center"
          column-key="targets"
          sortable="custom"
          :filters="targetColumnFilters"
          :filter-method="tableColumnFilterPassAll"
          filter-placement="bottom-end"
        >
          <template #default="scope">
            <el-text size="small">
              {{ scope.row.targets && scope.row.targets.length ? scope.row.targets.join('、') : '-' }}
            </el-text>
          </template>
        </el-table-column>

        <!-- 类别数 -->
        <el-table-column
          prop="classCount"
          label="类别数"
          width="80"
          align="center"
          column-key="classCount"
          sortable="custom"
        >
          <template #default="scope">
            <el-text size="small">
              {{ scope.row.classes ? scope.row.classes.length : 0 }}
            </el-text>
          </template>
        </el-table-column>

        <!-- 类别名称（标签 + 更多）：限制列宽，避免整表随标签无限变宽 -->
        <el-table-column
          label="类别名称"
          min-width="200"
          align="left"
        >
          <template #default="scope">
            <div class="class-tags class-tags--table-cell">
              <span
                v-for="cls in scope.row.classes.slice(0, 5)"
                :key="scope.row.id + '-' + cls.name"
                class="tag"
                :title="cls.name + '：' + cls.count"
              >
                {{ cls.name }}：{{ cls.count }}
              </span>

              <el-button
                v-if="scope.row.classes.length > 5"
                class="more-btn"
                text
                size="small"
                @click="openClassNamesDialog(scope.row)"
              >
                更多 ({{ scope.row.classes.length - 5 }})
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
          column-key="imageCount"
          sortable="custom"
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
          column-key="sampleCount"
          sortable="custom"
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
          column-key="user"
          sortable="custom"
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
          width="180"
          align="center"
          fixed="right"
        >
          <template #default="scope">
            <div class="original-dataset-row-actions">
              <el-button
                size="small"
                type="primary"
                @click="openPreview(scope.row)"
              >
                示例
              </el-button>
              <el-button
                size="small"
                type="danger"
                @click="handleDeleteDataset(scope.row)"
              >
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div
      v-else
      class="original-dataset-card-grid"
      :class="{ 'original-dataset-panel__scroll': embedMode }"
    >
      <div
        v-for="row in pageData"
        :key="row.id"
        class="original-dataset-card"
        @click="handleOriginalDatasetCardClick(row, $event)"
      >
        <el-descriptions
          class="original-dataset-card-descriptions"
          :column="cardDescColumns"
          size="small"
          border
        >
          <template #title>
            <span class="original-dataset-card-title-text">{{ row.name }}</span>
          </template>
          <el-descriptions-item label="数据来源" :span="1">
            <el-tag
              size="small"
              disable-transitions
              :type="row.source === '外部导入' ? 'success' : 'info'"
            >
              {{ row.source || 'CVAT' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="传感器" :span="1">{{ row.sensor || '-' }}</el-descriptions-item>
          <el-descriptions-item label="类别数" :span="1">
            {{ row.classes ? row.classes.length : 0 }}
          </el-descriptions-item>
          <el-descriptions-item label="目标类型" :span="cardDescColumns">
            {{ row.targets && row.targets.length ? row.targets.join('、') : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="图片数" :span="1">{{ fmtNum(row.imageCount) }}</el-descriptions-item>
          <el-descriptions-item label="样本数" :span="1">{{ fmtNum(row.sampleCount) }}</el-descriptions-item>
          <el-descriptions-item label="用户" :span="1">{{ safeUser(row) }}</el-descriptions-item>
        </el-descriptions>

        <div class="original-dataset-card-footer" @click.stop>
          <div class="original-dataset-card-footer-left" />
          <div class="original-dataset-card-footer-right">
            <el-button type="primary" plain size="small" @click="openPreview(row)">
              示例
            </el-button>
            <el-button type="danger" plain size="small" @click="handleDeleteDataset(row)">
              删除
            </el-button>
          </div>
        </div>
      </div>
    </div>
      </div>

      <template #footer>
        <div class="original-dataset-panel__footer">
          <el-pagination
            v-if="effectiveViewAsTable"
            background
            size="small"
            v-model:current-page="tableCurrentPage"
            v-model:page-size="tablePageSize"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            :total="total"
            @size-change="onTablePageSizeChange"
          />
          <el-pagination
            v-else
            background
            size="small"
            v-model:current-page="cardCurrentPage"
            v-model:page-size="cardPageSize"
            :page-sizes="[6, 12, 18, 24]"
            layout="total, sizes, prev, pager, next, jumper"
            :total="total"
            @size-change="onCardPageSizeChange"
          />
        </div>
      </template>
    </el-card>

    <el-dialog
      v-model="datasetDetailVisible"
      title="数据集详情"
      class="original-dataset-detail-dialog"
      width="1000px"
      destroy-on-close
      align-center
      @closed="onDatasetDetailClosed"
    >
      <div v-if="datasetDetailRow" class="original-dataset-detail-dialog__body">
        <el-descriptions
          :column="2"
          border
          size="small"
          class="original-dataset-detail-descriptions"
        >
          <el-descriptions-item label="数据集名称" :span="2">
            {{ datasetDetailRow.name || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="数据来源" :span="1">
            <el-tag
              size="small"
              disable-transitions
              :type="datasetDetailRow.source === '外部导入' ? 'success' : 'info'"
            >
              {{ datasetDetailRow.source || 'CVAT' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="传感器" :span="1">
            {{ datasetDetailRow.sensor || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="目标类型" :span="2">
            {{
              datasetDetailRow.targets && datasetDetailRow.targets.length
                ? datasetDetailRow.targets.join('、')
                : '-'
            }}
          </el-descriptions-item>
          <el-descriptions-item label="类别数" :span="1">
            {{ datasetDetailRow.classes ? datasetDetailRow.classes.length : 0 }}
          </el-descriptions-item>
          <el-descriptions-item label="图片数" :span="1">
            {{ fmtNum(datasetDetailRow.imageCount) }}
          </el-descriptions-item>
          <el-descriptions-item label="样本数" :span="1">
            {{ fmtNum(datasetDetailRow.sampleCount) }}
          </el-descriptions-item>
          <el-descriptions-item label="创建用户" :span="1">
            {{ safeUser(datasetDetailRow) }}
          </el-descriptions-item>
          <el-descriptions-item label="类别明细" :span="2">
            <div
              v-if="datasetDetailRow.classes && datasetDetailRow.classes.length"
              class="class-tags class-tags--dialog"
            >
              <span
                v-for="cls in datasetDetailRow.classes"
                :key="'detail-' + datasetDetailRow.id + '-' + cls.name"
                class="tag"
                :title="cls.name + '：' + cls.count"
              >
                {{ cls.name }}：{{ cls.count }}
              </span>
            </div>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item
            v-if="datasetDetailRow.isExternal && datasetDetailRow.externalPath"
            label="本地路径"
            :span="2"
          >
            <span class="original-dataset-detail-path">{{ datasetDetailRow.externalPath }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="datasetDetailRow.error" label="状态" :span="2">
            <el-text type="warning" size="small">{{ datasetDetailRow.error }}</el-text>
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="datasetDetailVisible = false">关闭</el-button>
        <el-button type="danger" plain @click="onDatasetDetailDelete">删除</el-button>
        <el-button type="primary" @click="onDatasetDetailPreview">查看示例</el-button>
      </template>
    </el-dialog>

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

    <el-dialog
      v-model="classNamesDialogVisible"
      :title="classNamesDialogTitle"
      width="520px"
      destroy-on-close
      @closed="onClassNamesDialogClosed"
    >
      <div class="class-names-dialog-body">
        <div v-if="classNamesDialogClasses.length" class="class-tags class-tags--dialog">
          <span
            v-for="cls in classNamesDialogClasses"
            :key="'dlg-' + cls.name"
            class="tag"
            :title="cls.name + '：' + cls.count"
          >
            {{ cls.name }}：{{ cls.count }}
          </span>
        </div>
        <el-empty v-else description="暂无类别" />
      </div>
      <template #footer>
        <el-button type="primary" @click="classNamesDialogVisible = false">关闭</el-button>
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
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import { EngineProjectService, OriginalDatasetService } from '@/api/api'

const props = defineProps({
  /** 嵌入「数据集管理（dev）」时：横向滚动托管在 .table-div，避免 fixed 列与父级 overflow 错位 */
  embedMode: { type: Boolean, default: false },
  /** 统合页工具栏绑定：true=表格，false=卡片；不传则内部默认表格 */
  viewAsTable: { type: Boolean, default: undefined },
  /** 统合页 v-model:sort-mode：`created_desc` | `name` */
  sortMode: { type: String, default: undefined }
})

const emit = defineEmits(['update:viewAsTable', 'update:sortMode'])

const internalViewAsTable = ref(true)
const effectiveViewAsTable = computed({
  get: () => (props.viewAsTable !== undefined ? props.viewAsTable : internalViewAsTable.value),
  set: (v) => {
    if (props.viewAsTable !== undefined) emit('update:viewAsTable', v)
    else internalViewAsTable.value = v
  }
})

const internalSortMode = ref('created_desc')
const effectiveSortMode = computed({
  get: () => (props.sortMode !== undefined ? props.sortMode : internalSortMode.value),
  set: (v) => {
    if (props.sortMode !== undefined) emit('update:sortMode', v)
    else internalSortMode.value = v
  }
})

/* 动态筛选项 */
const sensorOptions = ref([])
const targetOptions = ref([])

/** 表头列筛选（与 el-table filters 同步，在 filtered 中生效，避免只对当前页过滤） */
const tableRef = ref(null)
const tableSearch = ref('')
const colFilterSource = ref([])
const colFilterSensor = ref([])
const colFilterTarget = ref([])
/** 表头自定义排序（在 sortedFiltered 之后应用） */
const tableColumnSort = ref(null) // { prop, order } | null

const sourceFilterOptions = [
  { text: 'CVAT', value: 'CVAT' },
  { text: '外部导入', value: '外部导入' }
]

const sensorColumnFilters = computed(() =>
  sensorOptions.value.map(s => ({ text: s, value: s }))
)

const targetColumnFilters = computed(() =>
  targetOptions.value.map(t => ({ text: t, value: t }))
)

function tableColumnFilterPassAll() {
  return true
}

function onElTableFilterChange(filters) {
  const f = filters || {}
  colFilterSource.value = f.source || []
  colFilterSensor.value = f.sensor || []
  colFilterTarget.value = f.targets || []
  tableCurrentPage.value = 1
  cardCurrentPage.value = 1
}

function compareRowsForTableSort(a, b, prop) {
  if (prop === 'name') return String(a.name || '').localeCompare(String(b.name || ''), 'zh-CN')
  if (prop === 'source') return String(a.source || '').localeCompare(String(b.source || ''), 'zh-CN')
  if (prop === 'sensor') return String(a.sensor || '').localeCompare(String(b.sensor || ''), 'zh-CN')
  if (prop === 'imageCount') return (a.imageCount || 0) - (b.imageCount || 0)
  if (prop === 'sampleCount') return (a.sampleCount || 0) - (b.sampleCount || 0)
  if (prop === 'classCount') return (a.classes?.length || 0) - (b.classes?.length || 0)
  if (prop === 'user') return String(a.user || '').localeCompare(String(b.user || ''), 'zh-CN')
  if (prop === 'targets') {
    const ta = (a.targets && a.targets[0]) || ''
    const tb = (b.targets && b.targets[0]) || ''
    return String(ta).localeCompare(String(tb), 'zh-CN')
  }
  return 0
}

function onTableSortChange({ prop, order }) {
  if (!order) {
    tableColumnSort.value = null
    return
  }
  tableColumnSort.value = { prop, order }
  tableCurrentPage.value = 1
  cardCurrentPage.value = 1
}

function clearTableColumnFilters() {
  colFilterSource.value = []
  colFilterSensor.value = []
  colFilterTarget.value = []
  tableCurrentPage.value = 1
  cardCurrentPage.value = 1
  nextTick(() => tableRef.value?.clearFilter?.())
}

function clearTableSort() {
  tableColumnSort.value = null
  tableCurrentPage.value = 1
  cardCurrentPage.value = 1
  nextTick(() => tableRef.value?.clearSort?.())
}

/** 类别名称「更多」：弹窗展示全部标签 */
const classNamesDialogVisible = ref(false)
const classNamesDialogRow = ref(null)

const classNamesDialogTitle = computed(() => {
  const r = classNamesDialogRow.value
  if (!r) return '类别名称'
  return `类别名称 — ${r.name || ''}`
})

const classNamesDialogClasses = computed(() => {
  const r = classNamesDialogRow.value
  if (!r || !Array.isArray(r.classes)) return []
  return r.classes
})

function openClassNamesDialog(row) {
  classNamesDialogRow.value = row
  classNamesDialogVisible.value = true
}

function onClassNamesDialogClosed() {
  classNamesDialogRow.value = null
}

/** 卡片视图：点击卡片查看详情（与任务管理卡片交互一致） */
const datasetDetailVisible = ref(false)
const datasetDetailRow = ref(null)

function openDatasetDetail(row) {
  if (!row) return
  datasetDetailRow.value = row
  datasetDetailVisible.value = true
}

function onDatasetDetailClosed() {
  datasetDetailRow.value = null
}

function handleOriginalDatasetCardClick(row, e) {
  if (!row || !e?.target) return
  if (e.target.closest('.original-dataset-card-footer')) return
  if (e.target.closest('button, .el-button')) return
  if (e.target.closest('.el-tag')) return
  openDatasetDetail(row)
}

/** 列表模式：点行打开详情（排除操作/筛选/排序/类标签/「更多」等） */
function handleOriginalDatasetTableRowClick(row, _column, event) {
  if (!row || !event?.target) return
  if (event.target.closest('button, .el-button, a, .el-dropdown')) return
  if (event.target.closest('.el-tag')) return
  if (event.target.closest('.original-dataset-row-actions')) return
  if (event.target.closest('.class-tags, .more-btn')) return
  if (event.target.closest('.el-table__column-filter-trigger')) return
  if (event.target.closest('.el-popper')) return
  openDatasetDetail(row)
}

function onDatasetDetailPreview() {
  const row = datasetDetailRow.value
  if (!row) return
  datasetDetailVisible.value = false
  openPreview(row)
}

async function onDatasetDetailDelete() {
  const row = datasetDetailRow.value
  if (!row) return
  const rid = row.id
  await handleDeleteDataset(row)
  const stillThere = allDatasets.value.some(it => it.id === rid)
  if (!stillThere) datasetDetailVisible.value = false
}

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

/* 分页（前端分页）：列表与卡片各自页码、每页条数，互不影响 */
const tableCurrentPage = ref(1)
const tablePageSize = ref(10)
const cardCurrentPage = ref(1)
const cardPageSize = ref(6)
const total = ref(0)

/** 卡片描述列数：多列紧凑；窄屏降为 2/1 列避免挤压 */
const windowWidth = ref(
  typeof window !== 'undefined' ? window.innerWidth : 1200
)
const cardDescColumns = computed(() => {
  const w = windowWidth.value
  if (w < 480) return 1
  if (w < 720) return 2
  return 3
})
function onWindowResize() {
  windowWidth.value = window.innerWidth
}

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

  const rawTs =
    r.created_time ?? r.createdTime ?? r.updated_time ?? r.updatedTime ?? r.created_at ?? ''
  const parsed = rawTs ? Date.parse(String(rawTs).trim()) : NaN
  const sortTs = Number.isFinite(parsed) ? parsed : 0

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
    error: r.error || '',
    sortTs
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

/* 前端筛选（表头列筛选 + 工具栏关键词，与 Element Plus「Type to search」示例一致） */
const filtered = computed(() => {
  const q = tableSearch.value.trim().toLowerCase()
  return allDatasets.value.filter(r => {
    const src = r.source || 'CVAT'
    const colSourceOk =
      !colFilterSource.value.length || colFilterSource.value.includes(src)
    const colSensorOk =
      !colFilterSensor.value.length || colFilterSensor.value.includes(r.sensor)
    const t0 = r.targets?.[0]
    const colTargetOk =
      !colFilterTarget.value.length ||
      (t0 && colFilterTarget.value.includes(t0))
    const clsNames = (r.classes || []).map(c => c.name)
    const hay = [
      r.id,
      r.name,
      r.user,
      r.sensor,
      (r.targets || []).join(','),
      clsNames.join(',')
    ]
      .join(' ')
      .toLowerCase()
    const searchOk = !q || hay.includes(q)
    return colSourceOk && colSensorOk && colTargetOk && searchOk
  })
})

/** 筛选后再排序（与统合页 / 独立页排序下拉一致） */
const sortedFiltered = computed(() => {
  const list = [...filtered.value]
  if (effectiveSortMode.value === 'name') {
    list.sort((a, b) => String(a.name || '').localeCompare(String(b.name || ''), 'zh-CN'))
    return list
  }
  list.sort((a, b) => (b.sortTs || 0) - (a.sortTs || 0))
  return list
})

/** 全局排序后再应用表头列排序（custom sort） */
const orderedForDisplay = computed(() => {
  const list = [...sortedFiltered.value]
  const ts = tableColumnSort.value
  if (!ts?.prop || !ts?.order) return list
  const mul = ts.order === 'descending' ? -1 : 1
  list.sort(
    (a, b) => compareRowsForTableSort(a, b, ts.prop) * mul
  )
  return list
})

/* 分页数据：对排序后的列表切片 */
const pageData = computed(() => {
  const list = orderedForDisplay.value
  if (effectiveViewAsTable.value) {
    const start = (tableCurrentPage.value - 1) * tablePageSize.value
    return list.slice(start, start + tablePageSize.value)
  }
  const start = (cardCurrentPage.value - 1) * cardPageSize.value
  return list.slice(start, start + cardPageSize.value)
})

/* 过滤条件变化时，重置页码到 1，并更新 total */
watch(
  filtered,
  (val) => {
    total.value = val.length
    tableCurrentPage.value = 1
    cardCurrentPage.value = 1
  },
  { immediate: true }
)

watch(
  () => effectiveSortMode.value,
  () => {
    tableCurrentPage.value = 1
    cardCurrentPage.value = 1
  }
)

function onTablePageSizeChange() {
  tableCurrentPage.value = 1
}

function onCardPageSizeChange() {
  cardCurrentPage.value = 1
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
  window.addEventListener('resize', onWindowResize)
  await loadDatasets()
})
onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('resize', onWindowResize)
})

defineExpose({
  openImportDialog,
  goCvatHome,
  reload
})
</script>

<style scoped>
.original-dataset-toolbar-row {
  margin-bottom: 8px;
}

.original-dataset-toolbar-row__right {
  flex-shrink: 0;
  align-items: center;
}

.original-dataset-toolbar-search {
  width: 220px;
}
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

/* 表格容器（与筛选区间距收紧） */
.table-div {
  padding-top: 2px;
  padding-bottom: 4px;
}

/* Element Plus 表格风格：带圆角和阴影，跟结果查询页一致 */
.my-table.el-table--small {
  border-radius: 4px;
}

.content:not(.content--embed) :deep(.el-table) {
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  border-radius: 8px;
  overflow: hidden;
}

/*
 * 嵌入时：不要用 overflow:hidden 包住表格，否则 fixed 列与中间列错位叠层。
 * 勿用 min-width: min-content：会按「所有单元格内容的最小宽度」撑开整表，类别标签等会导致表格被不断横向拉长。
 * 用 width:100% 铺满滚动容器；超出宽度由 .table-div--embed-scroll / 表体内滚动条处理。
 */
.content--embed :deep(.el-table) {
  box-shadow: 0 1px 8px rgba(0, 0, 0, 0.06);
  border-radius: 8px;
  overflow: visible;
  width: 100%;
  min-width: 0;
}

/* 横向滚动容器（外观见文件末尾「非 scoped」块，避免与全局 style.css 及 el-table 内部滚动条错位） */
.table-div--embed-scroll {
  overflow-x: auto;
  max-width: 100%;
  -webkit-overflow-scrolling: touch;
  padding-bottom: 16px;
  margin-bottom: 4px;
}

/*
 * 嵌入统合页：表格区占满卡片 body 剩余高度，纵向滚动在表体；避免外层整块 div 再出一层竖向滚动条。
 */
.original-dataset-panel__main--embed > .table-div.table-div--embed-scroll.original-dataset-panel__scroll {
  display: flex;
  flex-direction: column;
  flex: 1 1 0;
  min-height: 0;
  overflow: hidden;
  padding-bottom: 4px;
  margin-bottom: 0;
}

.original-dataset-row-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: default;
}

/* 列表行可点出详情，操作区恢复默认手型不误导 */
.my-table--dataset-detail :deep(.el-table__body tr) {
  cursor: pointer;
}

.content--embed {
  padding-left: 0;
  padding-right: 0;
}

.original-dataset-panel {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  overflow: hidden;
}

.original-dataset-panel :deep(.el-card__header) {
  padding: 12px 16px 8px;
  border-bottom: 1px solid #ebeef5;
}

.original-dataset-panel__header-inner.search-div {
  padding: 0;
  margin: 0;
}

.original-dataset-panel :deep(.el-card__body) {
  padding: 6px 16px 0;
}

.original-dataset-panel :deep(.el-card__footer) {
  padding: 12px 16px;
  border-top: 1px solid #ebeef5;
  background: #fafafa;
}

.original-dataset-panel__footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px 16px;
  row-gap: 10px;
  width: 100%;
  min-height: 40px;
  box-sizing: border-box;
  padding: 2px 0 4px;
}

/* 统合页嵌入：中间区域滚动，分页条固定在 el-card footer */
.original-dataset-panel.original-dataset-panel--embed {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.original-dataset-panel.original-dataset-panel--embed :deep(.el-card__body) {
  flex: 1 1 0;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.original-dataset-panel.original-dataset-panel--embed :deep(.el-card__footer) {
  flex-shrink: 0;
}

.original-dataset-panel__main--embed {
  flex: 1 1 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.original-dataset-panel__main--embed > .original-dataset-toolbar-row {
  flex-shrink: 0;
}

.original-dataset-panel__scroll {
  flex: 1 1 0;
  min-height: 0;
  overflow: auto;
}

.content--embed .original-dataset-panel {
  border: none;
  border-radius: 0;
}

.content--embed .original-dataset-panel :deep(.el-card__header) {
  padding: 8px 0 6px;
}

.content--embed .original-dataset-panel :deep(.el-card__body) {
  padding: 4px 0 0;
}

.content--embed .original-dataset-panel :deep(.el-card__footer) {
  padding: 12px 0 0;
  background: transparent;
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
  max-height: 120px;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 2px 0;
}

/* 表格内：不出现单元格内纵向滚动条（完整列表见「更多」弹窗） */
.class-tags.class-tags--table-cell {
  min-width: 0;
  max-width: 100%;
  max-height: none;
  overflow: visible;
}

.class-tags.class-tags--table-cell .tag {
  white-space: normal;
  word-break: break-word;
  max-width: 100%;
}

.class-names-dialog-body {
  max-height: min(420px, 65vh);
  overflow-y: auto;
}

.class-tags.class-tags--dialog {
  max-height: none;
  overflow: visible;
  padding: 0;
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

.original-dataset-card-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
  padding-top: 2px;
  padding-bottom: 4px;
  /* 默认同行等高会把矮卡块纵向拉长，在描述表格下形成大块留白 */
  align-items: start;
}

@media (min-width: 1280px) {
  .original-dataset-card-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

/* 卡片视图：与任务管理 task-card 同源布局；高度随内容、不拉伸填空；长内容只在表体区滚动 */
.original-dataset-card {
  --od-card-body-max: 200px;
  width: 100%;
  border: 1px solid #e6e8ee;
  border-radius: 12px;
  background: #ffffff;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-width: 0;
  align-self: start;
  cursor: pointer;
  transition:
    box-shadow 0.25s ease,
    transform 0.25s ease,
    border-color 0.25s ease,
    background-color 0.25s ease;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.05);
}

.original-dataset-card:hover {
  box-shadow: 0 12px 28px rgba(37, 99, 235, 0.12);
  transform: translateY(-2px);
  border-color: #c7d2fe;
  background: #fff;
}

.original-dataset-card-descriptions {
  /* 不要 flex:1：避免被行高/父 flex 拉满后在表格下方留空 */
  flex: 0 0 auto;
  min-width: 0;
  margin: 0;
  --el-descriptions-label-bg-color: #fafafa;
}

.original-dataset-card-descriptions :deep(.el-descriptions__header) {
  flex-shrink: 0;
  align-items: flex-start;
  gap: 6px;
  margin-bottom: 0;
  padding: 6px 8px 4px 10px;
  width: 100%;
  box-sizing: border-box;
}

/* 仅表体可滚：行数多/字段长时卡高度有上限，避免单卡占满一屏 */
.original-dataset-card-descriptions :deep(.el-descriptions__body) {
  max-height: var(--od-card-body-max);
  overflow-x: hidden;
  overflow-y: auto;
}

.original-dataset-card-descriptions :deep(.el-descriptions__title) {
  font-size: 14px;
  font-weight: 600;
  color: #222;
  line-height: 22px;
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
}

.original-dataset-card-title-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.original-dataset-card-descriptions :deep(.el-descriptions__body .el-descriptions__table) {
  table-layout: fixed;
  width: 100%;
}

.original-dataset-card-descriptions :deep(.el-descriptions__content) {
  min-width: 0;
  max-width: 100%;
  word-break: break-word;
  font-size: 12px;
  line-height: 18px;
}

.original-dataset-card-descriptions :deep(.el-descriptions__label) {
  min-width: 0;
  padding: 2px 6px !important;
  font-size: 12px;
}

.original-dataset-card-descriptions :deep(.el-descriptions__cell) {
  padding: 2px 6px !important;
  vertical-align: top;
}

.original-dataset-card-footer {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  padding: 8px 12px 10px;
  border-top: 1px solid #ebeef5;
  background: #fff;
  cursor: default;
  border-radius: 0 0 12px 12px;
}

.original-dataset-card-footer-left,
.original-dataset-card-footer-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.original-dataset-detail-descriptions {
  --el-descriptions-label-bg-color: #fafafa;
  width: 100%;
}

.original-dataset-detail-descriptions :deep(.el-descriptions__cell) {
  padding: 6px 12px;
}

.original-dataset-detail-path {
  word-break: break-all;
  font-size: 12px;
  color: #606266;
}

.original-dataset-detail-dialog__body {
  max-height: min(75vh, 780px);
  overflow-y: auto;
  padding-right: 4px;
  box-sizing: border-box;
}

@media (max-width: 768px) {
  .original-dataset-card-descriptions :deep(.el-descriptions__title) {
    align-items: flex-start;
  }

  .original-dataset-card-title-text {
    white-space: normal;
  }

  .original-dataset-card-footer {
    flex-direction: column;
    align-items: stretch;
  }

  .original-dataset-card-footer-left,
  .original-dataset-card-footer-right {
    justify-content: flex-end;
  }
}
</style>

<style>
/* 详情弹窗：teleport 到 body 时用非 scoped 才能稳定覆盖宽度 */
.el-dialog.original-dataset-detail-dialog {
  width: min(1000px, 96vw) !important;
  max-width: 96vw;
  box-sizing: border-box;
  margin: 0 auto 4vh;
}

/* 非 scoped：覆盖全局 style.css；表体横条多在 .el-scrollbar__wrap。调粗细改下方两处 height（建议 6～12px）。 */
.original-dataset-panel .table-div--embed-scroll::-webkit-scrollbar {
  height: 8px;
}

.original-dataset-panel .table-div--embed-scroll::-webkit-scrollbar-track {
  background: #eef0f3;
  border-radius: 4px;
}

.original-dataset-panel .table-div--embed-scroll::-webkit-scrollbar-thumb {
  background: #b8bcc4;
  border-radius: 4px;
}

.original-dataset-panel .table-div--embed-scroll::-webkit-scrollbar-thumb:hover {
  background: #909399;
}

.original-dataset-panel .el-table__body-wrapper .el-scrollbar__wrap::-webkit-scrollbar {
  height: 8px;
}

.original-dataset-panel .el-table__body-wrapper .el-scrollbar__wrap::-webkit-scrollbar-track {
  background: #eef0f3;
  border-radius: 4px;
}

.original-dataset-panel .el-table__body-wrapper .el-scrollbar__wrap::-webkit-scrollbar-thumb {
  background: #b8bcc4;
  border-radius: 4px;
}

.original-dataset-panel .el-table__body-wrapper .el-scrollbar__wrap::-webkit-scrollbar-thumb:hover {
  background: #909399;
}

.original-dataset-panel .table-div--embed-scroll {
  scrollbar-width: thin;
  scrollbar-color: #b8bcc4 #eef0f3;
}

.original-dataset-panel .el-table__body-wrapper .el-scrollbar__wrap {
  scrollbar-width: thin;
  scrollbar-color: #b8bcc4 #eef0f3;
}
</style>
