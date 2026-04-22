<template>
  <div class="content" :class="{ 'content--embed': embedMode }">
    <!-- 统合内嵌：与「原始数据集」同构 — el-card 内工具行 + 可滚动主区域，分页在 card footer -->
    <el-card
      v-if="useInstanceUnifiedPanel"
      class="original-dataset-panel original-dataset-panel--embed instance-embed-outer-card"
      shadow="never"
    >
      <div class="original-dataset-panel__main original-dataset-panel__main--embed">
        <div class="original-dataset-toolbar-row flex-between">
          <div class="flex-start gap-8">
            <el-button size="small" @click="clearInstanceToolbarFilters">清除列筛选</el-button>
            <el-button size="small" @click="clearInstanceToolbarSort">清除列排序</el-button>
          </div>
          <div class="flex-start gap-8 original-dataset-toolbar-row__right">
            <el-input
              v-model="instanceToolbarSearch"
              size="small"
              clearable
              placeholder="Type to search"
              class="original-dataset-toolbar-search"
            />
          </div>
        </div>
        <div class="split-layout__main instance-embed-card-scroll">
          <div
            v-if="effectiveTaskViewAsTable"
            class="table-div table-div--embed-scroll original-dataset-panel__scroll"
          >
            <el-table
              ref="instanceTaskTableRef"
              class="my-table"
              :data="instanceTasksForTable"
              stripe
              size="small"
              style="width: 100%"
              :height="embedMode ? '100%' : undefined"
              @sort-change="onInstanceTableSortChange"
              @filter-change="onInstanceTableFilterChange"
              @row-click="handleTaskRowClick"
              :row-class-name="getRowClassName"
              v-el-height-adaptive-table="{ bottomOffset: 70, isUse: !embedMode }"
            >
          <el-table-column label="序号" width="56" align="center" fixed="left">
            <template #default="scope">
              {{ (currentPage - 1) * pageSize + scope.$index + 1 }}
            </template>
          </el-table-column>
          <el-table-column
            prop="name"
            column-key="name"
            label="数据集名称"
            min-width="140"
            align="center"
            fixed="left"
            sortable="custom"
            show-overflow-tooltip
          />
          <el-table-column
            prop="sensorType"
            column-key="sensorType"
            label="传感器类型"
            min-width="120"
            align="center"
            sortable="custom"
            :filters="instanceSensorFilterOptions"
            :filter-method="instanceTableFilterPassAll"
            filter-placement="bottom-end"
          />
          <el-table-column
            prop="targetType"
            column-key="targetType"
            label="目标类型"
            min-width="120"
            align="center"
            sortable="custom"
            :filters="instanceTargetFilterOptions"
            :filter-method="instanceTableFilterPassAll"
            filter-placement="bottom-end"
          />
          <el-table-column
            prop="classNum"
            column-key="classNum"
            label="类别数"
            align="center"
            width="88"
            sortable="custom"
          >
            <template #default="{ row }">
              {{ displayCoreClassNum(row) }}
            </template>
          </el-table-column>
          <el-table-column label="类别名称" align="center" min-width="200">
            <template #default="{ row }">
              <div class="category-tags-container">
                <el-tag
                  v-for="(item, index) in getTaskCategoryList(row)"
                  :key="`${row.id}-${index}`"
                  size="small"
                  :type="getTagType(index)"
                  style="margin: 2px; white-space: nowrap;"
                >
                  {{ item.name }}: {{ item.count ?? 0 }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            prop="username"
            column-key="username"
            label="创建用户"
            min-width="100"
            align="center"
            sortable="custom"
            show-overflow-tooltip
          />
        </el-table>
      </div>
      <div v-else class="instance-task-card-grid instance-task-card-grid--embed original-dataset-panel__scroll">
        <el-card
          v-for="row in instanceTasksAfterSearch"
          :key="row.id"
          class="instance-task-card"
          shadow="hover"
          :class="{ 'is-selected': selectedTaskId === row.id }"
          @click="handleTaskRowClick(row)"
        >
          <template #header>
            <div class="instance-task-card__header">
              <span class="instance-task-card__name">{{ row.name }}</span>
            </div>
          </template>
          <el-descriptions :column="1" size="small" border>
            <el-descriptions-item label="传感器类型">{{ row.sensorType || '-' }}</el-descriptions-item>
            <el-descriptions-item label="目标类型">{{ row.targetType || '-' }}</el-descriptions-item>
            <el-descriptions-item label="类别数">{{ displayCoreClassNum(row) }}</el-descriptions-item>
            <el-descriptions-item label="创建用户">{{ row.username || '-' }}</el-descriptions-item>
          </el-descriptions>
          <div class="category-tags-container instance-task-card__tags">
            <el-tag
              v-for="(item, index) in getTaskCategoryList(row)"
              :key="`${row.id}-${index}`"
              size="small"
              :type="getTagType(index)"
              style="margin: 2px; white-space: nowrap;"
            >
              {{ item.name }}: {{ item.count ?? 0 }}
            </el-tag>
          </div>
        </el-card>
      </div>
        </div>
      </div>
      <template #footer>
        <div class="original-dataset-panel__footer">
          <el-pagination
            v-if="effectiveTaskViewAsTable"
            background
            size="small"
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            :total="total"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
          />
          <el-pagination
            v-else
            background
            size="small"
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="[6, 12, 18, 24]"
            layout="total, sizes, prev, pager, next, jumper"
            :total="total"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
          />
        </div>
      </template>
    </el-card>

    <div
      v-else
      class="split-layout instance-split-embed"
      :class="{
        'split-layout--embed': embedMode
      }"
    >
      <div v-if="!embedUnifiedToolbar" class="task-title-row split-layout__toolbar">
        <h4 class="panel-title">任务数据集（请点选）</h4>
        <div class="task-title-toolbar">
          <el-switch
            v-model="effectiveTaskViewAsTable"
            inline-prompt
            active-text="列表"
            inactive-text="卡片"
          />
        </div>
      </div>
      <div class="split-layout__main">
        <div v-if="effectiveTaskViewAsTable" class="table-div table-div--embed-scroll">
          <el-table
            ref="instanceTaskTableRef"
            class="my-table"
            :data="instanceTasksForTable"
            stripe
            size="small"
            style="width: 100%"
            @sort-change="onInstanceTableSortChange"
            @filter-change="onInstanceTableFilterChange"
            @row-click="handleTaskRowClick"
            :row-class-name="getRowClassName"
            v-el-height-adaptive-table="{ bottomOffset: 70, isUse: !embedMode }"
          >
            <el-table-column label="序号" width="56" align="center" fixed="left">
              <template #default="scope">
                {{ (currentPage - 1) * pageSize + scope.$index + 1 }}
              </template>
            </el-table-column>
            <el-table-column
              prop="name"
              column-key="name"
              label="数据集名称"
              min-width="140"
              align="center"
              fixed="left"
              sortable="custom"
              show-overflow-tooltip
            />
            <el-table-column
              prop="sensorType"
              column-key="sensorType"
              label="传感器类型"
              min-width="120"
              align="center"
              sortable="custom"
              :filters="instanceSensorFilterOptions"
              :filter-method="instanceTableFilterPassAll"
              filter-placement="bottom-end"
            />
            <el-table-column
              prop="targetType"
              column-key="targetType"
              label="目标类型"
              min-width="120"
              align="center"
              sortable="custom"
              :filters="instanceTargetFilterOptions"
              :filter-method="instanceTableFilterPassAll"
              filter-placement="bottom-end"
            />
            <el-table-column
              prop="classNum"
              column-key="classNum"
              label="类别数"
              align="center"
              width="88"
              sortable="custom"
            >
              <template #default="{ row }">
                {{ displayCoreClassNum(row) }}
              </template>
            </el-table-column>
            <el-table-column label="类别名称" align="center" min-width="200">
              <template #default="{ row }">
                <div class="category-tags-container">
                  <el-tag
                    v-for="(item, index) in getTaskCategoryList(row)"
                    :key="`${row.id}-${index}`"
                    size="small"
                    :type="getTagType(index)"
                    style="margin: 2px; white-space: nowrap;"
                  >
                    {{ item.name }}: {{ item.count ?? 0 }}
                  </el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              prop="username"
              column-key="username"
              label="创建用户"
              min-width="100"
              align="center"
              sortable="custom"
              show-overflow-tooltip
            />
          </el-table>
        </div>
        <div v-else class="instance-task-card-grid">
          <el-card
            v-for="row in instanceTasksAfterSearch"
            :key="row.id"
            class="instance-task-card"
            shadow="hover"
            :class="{ 'is-selected': selectedTaskId === row.id }"
            @click="handleTaskRowClick(row)"
          >
            <template #header>
              <div class="instance-task-card__header">
                <span class="instance-task-card__name">{{ row.name }}</span>
              </div>
            </template>
            <el-descriptions :column="1" size="small" border>
              <el-descriptions-item label="传感器类型">{{ row.sensorType || '-' }}</el-descriptions-item>
              <el-descriptions-item label="目标类型">{{ row.targetType || '-' }}</el-descriptions-item>
              <el-descriptions-item label="类别数">{{ displayCoreClassNum(row) }}</el-descriptions-item>
              <el-descriptions-item label="创建用户">{{ row.username || '-' }}</el-descriptions-item>
            </el-descriptions>
            <div class="category-tags-container instance-task-card__tags">
              <el-tag
                v-for="(item, index) in getTaskCategoryList(row)"
                :key="`${row.id}-${index}`"
                size="small"
                :type="getTagType(index)"
                style="margin: 2px; white-space: nowrap;"
              >
                {{ item.name }}: {{ item.count ?? 0 }}
              </el-tag>
            </div>
          </el-card>
        </div>
      </div>
      <div class="split-layout__pager">
        <el-pagination
          background
          size="small"
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[5, 10, 20, 30]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>

    <!-- 右侧 Drawer：实例数据集列表 -->
    <el-drawer
      v-model="instanceDrawerVisible"
      direction="btt"
      size="70%"
      :with-header="false"
    >
      <div class="right-panel">
        <h4>实例数据集（{{ selectedTask?.name || '未选择任务' }}）</h4>
        <p v-if="selectedTask" class="instance-drawer-task-split-hint">
          <el-button link type="primary" size="small" @click="openTrainTestDrawer(selectedTask)">
            任务级训测划分（目标子集 / 多方案）
          </el-button>
          <span class="instance-drawer-task-split-hint__text">与下表「随机训测划分」不同，后者按图片比例拆分到 train/test 目录。</span>
        </p>
        <el-table
          class="my-table"
          :data="instanceDatasetList"
          stripe
          size="small"
          v-loading="instanceLoading"
          style="width: 100%"
        >
          <el-table-column type="index" label="序号" width="60" align="center" />
          <el-table-column prop="name" label="数据集名称" align="center" />
          <el-table-column prop="imgNum" label="图片数" align="center" />
          <el-table-column prop="annoNum" label="样本数" align="center" />
          <el-table-column prop="configList" label="预处理描述" align="center" width="300" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="!row.configList || row.configList === '[]'">-</span>
              <span v-else>
                {{
                  parseConfigList(row.configList)
                    .map(item => item.name)
                    .join(' → ')
                }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="username" label="创建用户" align="center" />
          <el-table-column label="训测划分" align="center" width="100">
            <template #default="{ row }">
              <el-tooltip
                content="按训练集占全部图片的比例，将图像与成对标注随机拆分到训练目录与测试目录；若测试侧已有文件，会先合并回训练集再按新比例划分"
                placement="top"
                :show-after="200"
              >
                <el-button type="primary" link size="small" @click.stop="openInstanceSplitDialog(row)">
                  训测划分
                </el-button>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="示例" align="center" width="80">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openInstancePreview(row)">
                查看
              </el-button>
            </template>
          </el-table-column>
          <!-- 恢复删除操作列 -->
          <el-table-column label="操作" align="center" width="80">
            <template #default="{ row }">
              <el-popconfirm
                title="确定要删除该实例数据集吗？"
                confirm-button-text="确认"
                cancel-button-text="取消"
                @confirm="handleDelete(row.id)"
              >
                <template #reference>
                  <el-button link type="danger" size="small" style="padding: 0; height: auto;">
                    <el-tag size="small" type="danger" class="fontSpan">
                      删除
                    </el-tag>
                  </el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>

    <el-dialog
      v-model="instanceSplitDialogVisible"
      title="随机训测划分"
      width="440px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <p class="instance-split-dialog-hint">
        训练集占比：划分后保留在 <code>images/train</code> 侧的图片比例，其余随机进入
        <code>images/test</code>，并尽量移动同名标注。若此前全部在训练集，会先合并测试目录到训练集再划分。
      </p>
      <el-form label-width="120px">
        <el-form-item label="训练集占比">
          <el-input-number
            v-model="instanceSplitTrainRatio"
            :min="0.01"
            :max="0.99"
            :step="0.05"
            :precision="2"
            style="width: 180px"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="instanceSplitDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="instanceSplitLoading" @click="submitInstanceRandomSplit">
          确定划分
        </el-button>
      </template>
    </el-dialog>

    <!-- 训测划分 Drawer -->
    <el-drawer
      v-model="drawerVisible"
      title="训测划分配置"
      direction="rtl"
      size="50%"
      :before-close="handleCloseDrawer"
    >
      <div class="right-panel" v-if="selectedTrainTestDataset">
        <h4>训测划分配置：{{ selectedTrainTestDataset.name }}</h4>
        <el-card class="mt-4">
          <template #header>
            <div>目标子集 - 选择测试集</div>
          </template>
          <el-form-item label="划分参数" style="margin-bottom: 12px;">
            <el-space>
              <el-input-number
                v-model="trainRatio"
                :min="0"
                :max="1"
                :step="0.1"
                placeholder="训测比"
                style="width: 150px"
              />
              <el-input-number
                v-model="splitLimit"
                :min="1"
                :max="20"
                placeholder="划分上限"
                style="width: 150px"
              />
            </el-space>
          </el-form-item>
          <el-checkbox-group v-model="selectedTestIds">
            <el-checkbox
              v-for="item in targetSubsets"
              :key="item.id"
              :label="item.id"
              :disabled="!!trainRatio"
            >
              {{ item.name }}
              <el-tag size="small" :type="{
                train: 'primary',
                test: 'warning',
                common: 'success'
              }[item.purpose]">
                {{
                  { train: '训练', test: '测试', common: '通用' }[item.purpose] || '未知'
                }}
              </el-tag>
            </el-checkbox>
          </el-checkbox-group>
        </el-card>
        <el-card class="mt-4">
          <template #header>
            <div>预训练子集 - 选择训练集</div>
          </template>
          <el-checkbox-group v-model="selectedOriginalIds">
            <el-checkbox
              v-for="item in pretrainDatasets"
              :key="item.id"
              :label="item.id"
            >
              {{ item.name }}
            </el-checkbox>
          </el-checkbox-group>
        </el-card>
        <el-button type="primary" @click="saveTrainTestSplit" class="mt-16" style="width: 100%">
          保存训测划分
        </el-button>
      </div>
    </el-drawer>

    <!-- 预览弹窗 -->
    <el-dialog
      v-model="previewDialogVisible"
      :title="previewRow ? `示例预览 - ${previewRow.name}` : '示例预览'"
      width="60%"
      top="5vh"
    >
      <div v-if="previewLoading" class="image-preview-dialog">
        <div style="padding: 24px; text-align: center; color: #909399;">
          图片加载中…
        </div>
      </div>
      <div v-else-if="!previewGroups.length" class="image-preview-dialog">
        <div style="padding: 24px; text-align: center; color: #909399;">
          暂无可展示的示例图片
        </div>
      </div>
      <div v-else class="image-preview-dialog">
        <div
          class="category-section"
          v-for="group in previewGroups"
          :key="group.name"
        >
          <div class="category-header">
            <h3 class="category-title">
              <el-tag style="margin-right: 8px; opacity: 0.7;">
                <el-text style="color: black; opacity: 1;">
                  {{ group.name }}
                </el-text>
              </el-tag>
              <span>示例图片 ({{ group.images.length }} 张)</span>
            </h3>
            <el-button
              size="small"
              type="primary"
              plain
              :loading="groupRefreshing[group.name] === true"
              @click="refreshPreviewGroup(group.name)"
            >
              换一换
            </el-button>
          </div>
          <div class="image-row">
            <div
              v-for="(src, index) in group.images"
              :key="group.name + '-' + index"
              class="image-item-square"
            >
              <svg
                v-if="objectsMeta[src]?.width && objectsMeta[src]?.height"
                class="anno-svg-square"
                :viewBox="`0 0 ${objectsMeta[src].width} ${objectsMeta[src].height}`"
                preserveAspectRatio="xMidYMid meet"
              >
                <image
                  :href="src"
                  :width="objectsMeta[src].width"
                  :height="objectsMeta[src].height"
                />
                <g v-for="(obj, idx2) in objectsMeta[src].objects || []" :key="idx2">
                  <polygon :points="pointsAttr(obj.points)" class="anno-poly" />
                </g>
              </svg>
              <img
                v-else
                :src="src"
                alt="示例图片"
                class="anno-fallback-square"
                @error="onPreviewImgError($event)"
                @load="ensurePreviewAnno(src)"
              />
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="previewDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { TaskDatasetService, InstanceDatasetService, SourceInstanceDatasetService } from '@/api/api'
import { useRouter } from 'vue-router'
import { request } from '@/api/axios'

const router = useRouter()

const props = defineProps({
  /** 嵌入「数据集管理（dev）」时由外层卡片承担边距，略收紧内边距 */
  embedMode: { type: Boolean, default: false },
  /** 统合页已提供标题栏时隐藏内层任务列表标题与排序/视图切换 */
  embedUnifiedToolbar: { type: Boolean, default: false },
  /** 与统合页 v-model:task-view-as-table 同步 */
  taskViewAsTable: { type: Boolean, default: undefined }
})

const emit = defineEmits(['update:taskViewAsTable'])

const localTaskViewAsTable = ref(true)

const effectiveTaskViewAsTable = computed({
  get: () => (props.taskViewAsTable !== undefined ? props.taskViewAsTable : localTaskViewAsTable.value),
  set: (v) => {
    if (props.taskViewAsTable !== undefined) emit('update:taskViewAsTable', v)
    else localTaskViewAsTable.value = v
  }
})

// 左侧任务数据集
const taskDatasetList = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const instanceTaskTableRef = ref(null)
const instanceToolbarSearch = ref('')

const useInstanceUnifiedPanel = computed(
  () => props.embedMode && props.embedUnifiedToolbar
)
const selectedTask = ref(null)
// ✅ 修改：使用 selectedTaskId (number 类型)
const selectedTaskId = ref(null)

// 右侧实例数据集
const instanceDatasetList = ref([])
const instanceLoading = ref(false)
const instanceDrawerVisible = ref(false)

const instanceSplitDialogVisible = ref(false)
const instanceSplitRow = ref(null)
const instanceSplitTrainRatio = ref(0.8)
const instanceSplitLoading = ref(false)

/** 默认按最近更新（与原先下拉「最近更新优先」一致）；表头再叠自定义排序 */
const sortedTaskDatasetList = computed(() => {
  const src = Array.isArray(taskDatasetList.value) ? [...taskDatasetList.value] : []
  const toTs = (row) => {
    const raw =
      row?.devUpdatedTime ??
      row?.dev_updated_time ??
      row?.updatedTime ??
      row?.updated_time ??
      row?.createdTime ??
      row?.created_time ??
      ''
    const ts = raw ? Date.parse(String(raw).trim()) : 0
    return Number.isFinite(ts) ? ts : 0
  }
  src.sort((a, b) => {
    const dt = toTs(b) - toTs(a)
    if (dt !== 0) return dt
    return String(a?.name || '').localeCompare(String(b?.name || ''), 'zh-CN')
  })
  return src
})

const colFilterSensor = ref([])
const colFilterTarget = ref([])
const instanceTableColumnSort = ref(null)

const instanceSensorFilterOptions = computed(() => {
  const set = new Set()
  for (const r of taskDatasetList.value) {
    const s = r.sensorType
    if (s != null && String(s).trim() !== '') set.add(String(s))
  }
  return [...set]
    .sort((a, b) => a.localeCompare(b, 'zh-CN'))
    .map((s) => ({ text: s, value: s }))
})

const instanceTargetFilterOptions = computed(() => {
  const set = new Set()
  for (const r of taskDatasetList.value) {
    const t = r.targetType
    if (t != null && String(t).trim() !== '') set.add(String(t))
  }
  return [...set]
    .sort((a, b) => a.localeCompare(b, 'zh-CN'))
    .map((t) => ({ text: t, value: t }))
})

function instanceTableFilterPassAll() {
  return true
}

function onInstanceTableFilterChange(filters) {
  const f = filters || {}
  colFilterSensor.value = f.sensorType || []
  colFilterTarget.value = f.targetType || []
  if (useInstanceUnifiedPanel.value) currentPage.value = 1
}

function compareInstanceRowsForSort(a, b, prop) {
  if (prop === 'name') return String(a?.name || '').localeCompare(String(b?.name || ''), 'zh-CN')
  if (prop === 'sensorType') {
    return String(a?.sensorType || '').localeCompare(String(b?.sensorType || ''), 'zh-CN')
  }
  if (prop === 'targetType') {
    return String(a?.targetType || '').localeCompare(String(b?.targetType || ''), 'zh-CN')
  }
  if (prop === 'username') {
    return String(a?.username || '').localeCompare(String(b?.username || ''), 'zh-CN')
  }
  if (prop === 'classNum') {
    return displayCoreClassNum(a) - displayCoreClassNum(b)
  }
  return 0
}

function onInstanceTableSortChange({ prop, order }) {
  if (!order) {
    instanceTableColumnSort.value = null
  } else {
    instanceTableColumnSort.value = { prop, order }
  }
  if (useInstanceUnifiedPanel.value) currentPage.value = 1
}

const instanceTasksAfterColFilters = computed(() => {
  return sortedTaskDatasetList.value.filter((row) => {
    const okS =
      !colFilterSensor.value.length || colFilterSensor.value.includes(row.sensorType)
    const okT =
      !colFilterTarget.value.length || colFilterTarget.value.includes(row.targetType)
    return okS && okT
  })
})

/** 工具栏搜索（在列筛选之后） */
const instanceTasksAfterSearch = computed(() => {
  const list = instanceTasksAfterColFilters.value
  const q = (instanceToolbarSearch.value || '').trim().toLowerCase()
  if (!q) return list
  return list.filter((row) => {
    const n = (row?.name || '').toLowerCase()
    const s = (row?.sensorType || '').toLowerCase()
    const t = (row?.targetType || '').toLowerCase()
    const u = (row?.username || '').toLowerCase()
    return n.includes(q) || s.includes(q) || t.includes(q) || u.includes(q)
  })
})

/** 列表模式下叠表头排序 */
const instanceTasksForTable = computed(() => {
  const list = [...instanceTasksAfterSearch.value]
  if (!effectiveTaskViewAsTable.value) return list
  const ts = instanceTableColumnSort.value
  if (!ts?.prop || !ts?.order) return list
  const mul = ts.order === 'descending' ? -1 : 1
  list.sort((a, b) => compareInstanceRowsForSort(a, b, ts.prop) * mul)
  return list
})

function clearInstanceToolbarFilters() {
  instanceToolbarSearch.value = ''
  colFilterSensor.value = []
  colFilterTarget.value = []
  if (useInstanceUnifiedPanel.value) currentPage.value = 1
  nextTick(() => instanceTaskTableRef.value?.clearFilter?.())
}

function clearInstanceToolbarSort() {
  instanceTableColumnSort.value = null
  if (useInstanceUnifiedPanel.value) currentPage.value = 1
  nextTick(() => instanceTaskTableRef.value?.clearSort?.())
}

watch(instanceToolbarSearch, () => {
  if (useInstanceUnifiedPanel.value) currentPage.value = 1
})

// 训测划分 Drawer
const drawerVisible = ref(false)
const selectedTrainTestDataset = ref(null)
const targetSubsets = ref([])
const pretrainDatasets = ref([])
const selectedTestIds = ref([])
const selectedOriginalIds = ref([])
const trainRatio = ref(null)
const splitLimit = ref(1)

// 预览相关
const previewDialogVisible = ref(false)
const previewRow = ref(null)
const previewGroups = ref([])
const previewLoading = ref(false)
const objectsMeta = ref({})
const groupRefreshing = ref({})

/** 任务名 → 中间实例数据集 class_list 解析后的 { 类别名: 数量 }（与预处理页一致） */
const midClassByFather = ref({})

function normalizeClsKey(s) {
  return String(s || '')
    .trim()
    .toLowerCase()
    .replace(/[_\s-]/g, '')
}

function parseClassListObject(raw) {
  if (raw == null || raw === '') return {}
  if (typeof raw === 'object' && !Array.isArray(raw)) return { ...raw }
  if (typeof raw === 'string') {
    try {
      const o = JSON.parse(raw)
      if (typeof o === 'object' && !Array.isArray(o)) return o
    } catch {
      /* ignore */
    }
  }
  return {}
}

function countsFromMid(midObj, targetName) {
  if (!midObj || typeof midObj !== 'object') return 0
  const n = normalizeClsKey(targetName)
  for (const k of Object.keys(midObj)) {
    if (normalizeClsKey(k) === n) return Number(midObj[k]) || 0
  }
  return 0
}

function parseTargetSchemaKeys(raw) {
  if (!raw) return []
  if (Array.isArray(raw)) return raw.map(String)
  if (typeof raw === 'string') {
    try {
      const p = JSON.parse(raw)
      if (Array.isArray(p)) return p.map(String)
    } catch {
      if (raw.includes(',')) {
        return raw
          .split(',')
          .map((s) => s.trim())
          .filter(Boolean)
      }
    }
  }
  return []
}

async function loadMidClassSummaries() {
  try {
    const res = await SourceInstanceDatasetService.list({ presentOnDisk: false })
    const raw = typeof res === 'string' ? JSON.parse(res) : res
    const list = Array.isArray(raw?.data) ? raw.data : Array.isArray(raw) ? raw : []
    const byFather = new Map()
    for (const m of list) {
      const fn = m.fatherName ?? m.father_name
      if (!fn) continue
      const id = Number(m.id) || 0
      const prev = byFather.get(fn)
      if (!prev || id > prev.id) {
        byFather.set(fn, { id, classList: m.classList ?? m.class_list })
      }
    }
    const out = {}
    for (const [fn, { classList }] of byFather) {
      out[fn] = parseClassListObject(classList)
    }
    midClassByFather.value = out
  } catch (e) {
    console.warn('loadMidClassSummaries', e)
  }
}

// 新增：用于左侧类别名称解析
const getTagType = (index) => {
  const types = ['primary', 'success', 'warning', 'danger', 'info']
  return types[index % types.length]
}

const parseClassListSource = (src) => {
  if (!src || src === '-' || src === '[]') return []
  if (typeof src === 'string' && src.trim().startsWith('{')) {
    try {
      const parsed = JSON.parse(src)
      if (typeof parsed === 'object' && !Array.isArray(parsed)) {
        return Object.entries(parsed).map(([name, count]) => ({
          name,
          count: Number.isFinite(Number(count)) ? parseInt(count, 10) : 0
        }))
      }
    } catch (e) {
      // continue
    }
  }
  if (typeof src === 'string') {
    try {
      const parsed = JSON.parse(src)
      if (Array.isArray(parsed)) {
        return parsed.map(name => ({ name: String(name), count: 0 }))
      }
    } catch (e) {
      // continue
    }
  }
  if (Array.isArray(src)) {
    return src.map(name => ({ name: String(name), count: 0 }))
  }
  if (typeof src === 'string') {
    const names = src.split(',').map(s => s.trim()).filter(Boolean)
    if (names.length > 0) {
      return names.map(name => ({ name, count: 0 }))
    }
  }
  if (typeof src === 'object' && !Array.isArray(src)) {
    return Object.entries(src).map(([name, count]) => ({
      name,
      count: Number.isFinite(Number(count)) ? parseInt(count, 10) : 0
    }))
  }
  return []
}

const getTaskCategoryList = (row) => {
  const coreRaw = row?.coreClassList ?? row?.core_class_list
  const targetRaw = row?.targetSchema ?? row?.target_schema
  const fromCore = parseClassListSource(coreRaw)
  const midCounts = row?.name ? midClassByFather.value[row.name] : null
  const keys = parseTargetSchemaKeys(targetRaw)
  const coreHasNonZero = fromCore.some((x) => Number(x.count) > 0)

  function countForKey(k) {
    if (coreHasNonZero) {
      const hit = fromCore.find((x) => normalizeClsKey(x.name) === normalizeClsKey(k))
      return hit ? Number(hit.count) || 0 : 0
    }
    const fromMid = countsFromMid(midCounts, k)
    if (fromMid > 0) return fromMid
    const hit = fromCore.find((x) => normalizeClsKey(x.name) === normalizeClsKey(k))
    return hit ? Number(hit.count) || 0 : 0
  }

  if (keys.length) {
    return keys.map((k) => ({ name: k, count: countForKey(k) }))
  }
  if (fromCore.length) {
    if (coreHasNonZero) return fromCore
    return fromCore.map((x) => ({
      name: x.name,
      count: countsFromMid(midCounts, x.name) || Number(x.count) || 0
    }))
  }
  return []
}

function displayCoreClassNum(row) {
  const raw = row?.coreClassNum ?? row?.core_class_num
  const num = Number(raw)
  if (Number.isFinite(num) && num > 0) return num
  const list = getTaskCategoryList(row)
  const fromList = Array.isArray(list) ? list.length : 0
  if (fromList > 0) return fromList
  return Number.isFinite(num) ? num : 0
}

// 删除功能
const handleDelete = async (id) => {
  if (!id || id <= 0) {
    ElMessage.warning('无效的数据集ID')
    return
  }
  try {
    const response = await InstanceDatasetService.deleteById(id)
    if (response && response.code === 0) {
      ElMessage.success('删除成功')
      if (selectedTask.value) {
        fetchInstanceList(selectedTask.value.name)
      }
    } else {
      ElMessage.error(response?.msg || '删除失败')
    }
  } catch (error) {
    console.error('Delete error:', error)
    ElMessage.error('删除失败：' + (error?.response?.data?.msg || error.message || '未知错误'))
  }
}

// 预览功能
const openInstancePreview = async (row) => {
  previewRow.value = row
  previewDialogVisible.value = true
  previewLoading.value = true
  previewGroups.value = []
  objectsMeta.value = {}
  try {
    const res = await request(
      `/instanceDataset/${row.id}/preview`,
      { perLabel: 3 },
      'get',
      'application/json'
    )
    const obj = typeof res === 'string' ? JSON.parse(res) : res
    const data = obj.data || obj
    const items = Array.isArray(data.items) ? data.items : []
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
    groupRefreshing.value = {}
    setTimeout(() => {
      groups.forEach(g => g.images.forEach(src => ensurePreviewAnno(src)))
    }, 0)
  } catch (e) {
    console.error('加载实例示例失败', e)
    ElMessage.error('加载示例失败：' + (e?.message || e))
  } finally {
    previewLoading.value = false
  }
}

const refreshPreviewGroup = async (groupName) => {
  if (!previewRow.value?.id || !groupName) return
  groupRefreshing.value = { ...groupRefreshing.value, [groupName]: true }
  try {
    const res = await request(
      `/instanceDataset/${previewRow.value.id}/preview`,
      { perLabel: 3 },
      'get',
      'application/json'
    )
    const obj = typeof res === 'string' ? JSON.parse(res) : res
    const data = obj.data || obj
    const items = Array.isArray(data.items) ? data.items : []
    const hit = items.find(it => {
      const name = it.label || it.className || it.name || '未命名'
      return name === groupName
    })
    const images = hit && Array.isArray(hit.images)
      ? hit.images
      : hit && Array.isArray(hit.urls)
        ? hit.urls
        : []
    previewGroups.value = previewGroups.value.map(g => (
      g.name === groupName ? { ...g, images } : g
    ))
    setTimeout(() => {
      images.forEach(src => ensurePreviewAnno(src))
    }, 0)
  } catch (e) {
    ElMessage.error('换一换失败：' + (e?.message || e))
  } finally {
    groupRefreshing.value = { ...groupRefreshing.value, [groupName]: false }
  }
}

function subsetObjectsUrlFromImageUrl(imgUrl) {
  return imgUrl.replace('/image?', '/objects?')
}

async function ensurePreviewAnno(src) {
  if (objectsMeta.value[src]) return
  try {
    const url = subsetObjectsUrlFromImageUrl(src)
    const resp = await fetch(url)
    const res = await resp.json()
    const data = res?.data || res
    if (data && (data.width || Array.isArray(data.objects))) {
      objectsMeta.value = { ...objectsMeta.value, [src]: data }
    }
  } catch (e) {
    console.warn('加载实例标注失败', src, e)
  }
}

function pointsAttr(points) {
  return Array.isArray(points) ? points.map(p => p.join(',')).join(' ') : ''
}

function onPreviewImgError(e) {
  e.target.style.display = 'none'
  if (e.target.parentElement) {
    e.target.parentElement.classList.add('imgbox--fallback')
  }
}

// 解析预处理描述
const parseConfigList = (configStr) => {
  if (!configStr || configStr === '[]') return [];
  try {
    const list = JSON.parse(configStr);
    return Array.isArray(list)
      ? list.sort((a, b) => (a.order || 0) - (b.order || 0))
      : [];
  } catch (e) {
    console.warn('解析 configList 失败:', configStr, e);
    return [];
  }
};

// 获取任务数据集列表
const fetchTaskList = async () => {
  const params = {
    current: currentPage.value,
    size: pageSize.value
  }
  const res = await TaskDatasetService.queryList(params)
  if (res.code === 0) {
    taskDatasetList.value = res.data?.records || []
    total.value = res.data?.total || 0
  } else {
    ElMessage.warning(res.msg || '加载失败')
    taskDatasetList.value = []
    total.value = 0
  }
}

// 获取实例数据集列表（根据 father_name 过滤）
const fetchInstanceList = async (fatherName) => {
  if (!fatherName) {
    instanceDatasetList.value = []
    return
  }
  instanceLoading.value = true
  try {
    const res = await InstanceDatasetService.queryList()
    const filtered = Array.isArray(res) 
      ? res.filter(item => item.fatherName === fatherName)
      : []
    instanceDatasetList.value = filtered
  } catch (error) {
    console.error('加载实例数据集失败:', error)
    instanceDatasetList.value = []
    ElMessage.error('实例数据集加载失败')
  } finally {
    instanceLoading.value = false
  }
}

// ✅ 点击任务数据集行（支持选中/取消；排除表头筛选/排序区域）
const handleTaskRowClick = (row, _column, event) => {
  if (event?.target) {
    if (event.target.closest('.el-table__column-filter-trigger')) return
    if (event.target.closest('.el-popper')) return
    if (event.target.closest('button, .el-button')) return
  }
  if (selectedTaskId.value === row.id) {
    selectedTask.value = null
    selectedTaskId.value = null
    instanceDatasetList.value = []
    instanceDrawerVisible.value = false
    return
  }
  handleTaskSelect(row)
}

// ✅ 处理单选按钮选择
const handleTaskSelect = (row) => {
  selectedTask.value = row
  selectedTaskId.value = row.id // number 类型
  fetchInstanceList(row.name)
  instanceDrawerVisible.value = true
}

function openInstanceSplitDialog(row) {
  instanceSplitRow.value = row
  instanceSplitTrainRatio.value = 0.8
  instanceSplitDialogVisible.value = true
}

async function submitInstanceRandomSplit() {
  const row = instanceSplitRow.value
  if (!row?.id) {
    ElMessage.warning('无效的数据集')
    return
  }
  instanceSplitLoading.value = true
  try {
    const response = await InstanceDatasetService.splitTrainTestRandom({
      id: row.id,
      trainRatio: instanceSplitTrainRatio.value
    })
    if (response && response.code === 0) {
      const d = response.data || {}
      ElMessage.success(
        `划分完成：训练集 ${d.trainImages ?? 0} 张图，测试集 ${d.testImages ?? 0} 张图`
      )
      instanceSplitDialogVisible.value = false
      if (selectedTask.value) {
        await fetchInstanceList(selectedTask.value.name)
      }
    } else {
      ElMessage.error(response?.msg || '划分失败')
    }
  } catch (error) {
    console.error('splitTrainTestRandom', error)
    const errMsg =
      error?.msg ||
      error?.message ||
      error?.response?.data?.msg ||
      (typeof error === 'string' ? error : null) ||
      '未知错误'
    ElMessage.error('划分失败：' + errMsg)
  } finally {
    instanceSplitLoading.value = false
  }
}

// 打开训测划分 Drawer
const openTrainTestDrawer = (row) => {
  selectedTrainTestDataset.value = row
  handleConfigureForDrawer(row)
  drawerVisible.value = true
}

// 复用训测划分逻辑
const handleConfigureForDrawer = (row) => {
  selectedTestIds.value = []
  selectedOriginalIds.value = []
  trainRatio.value = null

  const promises = []
  promises.push(
    TaskDatasetService.getTargetSubsets(row.id)
      .then(res => {
        if (res?.code === 0) {
          targetSubsets.value = res.data || []
        } else {
          targetSubsets.value = []
        }
      })
      .catch(err => {
        console.error('加载目标子集失败:', err)
        ElMessage.error('加载目标子集失败')
        targetSubsets.value = []
      })
  )

  const supIds = (row.supId || "").split("_").filter(id => id.trim())
  const supNames = (row.supName || "").split("_").filter(name => name.trim())
  pretrainDatasets.value = supIds.map((id, i) => ({
    id: id.trim(),
    name: supNames[i] ? supNames[i].trim() : id,
    imgNum: row.supImgNum || 0
  }))
}

/// 保存训测划分
const saveTrainTestSplit = async () => {
  let allTestPlans = []
  let trainOriginalIds = [...selectedOriginalIds.value]

  if (trainRatio.value != null && splitLimit.value != null) {
    const testRatio = 1 - trainRatio.value
    const eligible = targetSubsets.value
    const k = Math.max(1, Math.min(eligible.length - 1, Math.floor(eligible.length * testRatio)))
    if (k > 0 && eligible.length >= k) {
      const items = eligible.map(item => item.id)
      const allCombs = getCombinations(items, k)
      allTestPlans = selectDiversePlans(allCombs, splitLimit.value)
    } else {
      allTestPlans = [[]]
    }
  } else {
    allTestPlans = selectedTestIds.value.length > 0 
      ? [[...selectedTestIds.value]] 
      : [[]]
  }

  const payload = {
    taskId: selectedTrainTestDataset.value.id,
    testPlans: allTestPlans,
    trainOriginalIds: trainOriginalIds
  }

  try {
    const res = await TaskDatasetService.saveTrainTestSplit(payload)
    if (res?.code === 0) {
      ElMessage.success(`成功保存 ${allTestPlans.length} 个划分方案`)
      fetchInstanceList(selectedTrainTestDataset.value.name)
      
      // 新增：保存成功后跳转到预处理页面
      drawerVisible.value = false
      router.push('/preprocess')
    } else {
      ElMessage.warning(res?.msg || '保存失败')
    }
  } catch (err) {
    ElMessage.error('提交失败')
  }
}

// 关闭 Drawer
const handleCloseDrawer = () => {
  drawerVisible.value = false
}

// ✅ 行高亮（基于 number 类型比较）
const getRowClassName = ({ row }) => {
  return row.id === selectedTaskId.value ? 'selected-row' : ''
}

// 分页控制
const handleSizeChange = (val) => {
  pageSize.value = val
  fetchTaskList()
}
const handleCurrentChange = (val) => {
  currentPage.value = val
  fetchTaskList()
}

// 组合生成函数
const getCombinations = (arr, k) => {
  if (k === 0) return [[]]
  if (arr.length === 0) return []
  const [first, ...rest] = arr
  const withFirst = getCombinations(rest, k - 1).map(c => [first, ...c])
  const withoutFirst = getCombinations(rest, k)
  return [...withFirst, ...withoutFirst]
}

const jaccardDistance = (a, b) => {
  const A = new Set(a), B = new Set(b)
  const inter = new Set([...A].filter(x => B.has(x)))
  const union = new Set([...A, ...B])
  return 1 - (inter.size / union.size)
}

const selectDiversePlans = (allPlans, limit) => {
  if (allPlans.length <= limit) return allPlans
  const selected = [allPlans[0]]
  const remaining = allPlans.slice(1)
  while (selected.length < limit && remaining.length > 0) {
    let bestIdx = 0, maxMinDist = -1
    for (let i = 0; i < remaining.length; i++) {
      const cand = remaining[i]
      let minDist = Infinity
      for (const sel of selected) {
        minDist = Math.min(minDist, jaccardDistance(cand, sel))
      }
      if (minDist > maxMinDist) {
        maxMinDist = minDist
        bestIdx = i
      }
    }
    selected.push(remaining[bestIdx])
    remaining.splice(bestIdx, 1)
  }
  return selected
}

// 初始化
onMounted(() => {
  fetchTaskList()
  loadMidClassSummaries()
})
</script>

<style scoped lang="scss">
/* 与 taskDatabaseManage / originalDatasetManage 页面对齐 */
.content {
  padding: 10px;
  background-color: #f5f7fa;
}

.content.content--embed {
  padding: 0;
  background: transparent;
  display: flex;
  flex-direction: column;
  flex: 1 1 0;
  min-height: 0;
  width: 100%;
}

.original-dataset-toolbar-row {
  margin-bottom: 8px;
  flex-wrap: wrap;
  gap: 8px 12px;
}

.original-dataset-toolbar-row__right {
  flex-shrink: 0;
  align-items: center;
}

.original-dataset-toolbar-search {
  width: 220px;
}

.flex-between {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px 12px;
  flex-wrap: wrap;
}

.flex-start {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  flex-wrap: wrap;
}

.gap-8 {
  gap: 8px;
}

.instance-embed-outer-card {
  width: 100%;
  min-height: 0;
  flex: 1 1 0;
  display: flex;
  flex-direction: column;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: none;
}

.instance-embed-outer-card :deep(.el-card__body) {
  flex: 1 1 0;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding: 4px 0 0;
}

.instance-embed-outer-card :deep(.el-card__footer) {
  flex-shrink: 0;
  border-top: 1px solid #ebeef5;
  /* 与「原始数据集」content--embed 一致，左右留白由统合页 section 提供 */
  padding: 12px 0 0;
  background: transparent;
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

.original-dataset-panel__main--embed {
  flex: 1 1 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.instance-embed-card-scroll {
  flex: 1 1 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.content.content--embed .instance-embed-outer-card {
  border: none;
  border-radius: 0;
}

.original-dataset-panel__scroll {
  flex: 1 1 0;
  min-height: 0;
  overflow: auto;
}

.split-layout {
  display: block;
  margin-top: 12px;
  height: calc(100vh - 180px);
  overflow-y: auto;
}
.panel-title {
  margin: 0;
  font-weight: 600;
  color: #333;
  font-size: 18px;
}
.task-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.content:not(.content--embed) :deep(.el-table) {
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  border-radius: 8px;
  overflow: hidden;
}

.content.content--embed :deep(.el-table) {
  box-shadow: 0 1px 8px rgba(0, 0, 0, 0.06);
  border-radius: 8px;
  overflow: visible;
  min-width: min-content;
}

.table-div {
  padding-top: 8px;
  padding-bottom: 8px;
  max-width: 100%;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.content ::v-deep(.el-table__header-wrapper) {
  background-color: #fafafa;
}
.right-panel {
  flex: 1;
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fafafa;
  overflow-y: auto;
}
.mt-12 { margin-top: 12px; }
.mt-16 { margin-top: 16px; }
.flex-end {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
::v-deep .selected-row {
  background-color: #ecf5ff !important;
}
.my-table.el-table--small {
  border-radius: 4px;
}

/* small 表格行高与另两页观感接近 */
.content :deep(.el-table--small .cell) {
  line-height: 20px;
}

/* 表格内单选：隐藏重复标签（卡片视图需显示名称） */
.table-div :deep(.el-radio__label) {
  display: none !important;
}

.task-title-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.instance-task-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 12px;
  padding-top: 8px;
  padding-bottom: 8px;
}

.instance-task-card.is-selected {
  border-color: #409eff;
  box-shadow: 0 0 0 1px rgba(64, 158, 255, 0.35);
}

.instance-task-card__name {
  font-weight: 600;
  color: #303133;
}

.instance-task-card__tags {
  margin-top: 10px;
}

.instance-task-card__actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.content--embed {
  padding: 0;
  background: transparent;
}

.content--embed .split-layout {
  height: auto;
  min-height: 360px;
  max-height: none;
}

/* 统合页嵌入：列表/卡片区滚动，分页条固定在区块底部 */
.content--embed .split-layout.split-layout--embed {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  margin-top: 0;
}

.content--embed .split-layout--embed .split-layout__toolbar {
  flex-shrink: 0;
}

.content--embed .split-layout--embed .split-layout__main {
  flex: 1 1 0;
  min-height: 0;
  overflow: auto;
  -webkit-overflow-scrolling: touch;
}

.content--embed .split-layout--embed .split-layout__pager {
  flex-shrink: 0;
  margin-top: 8px;
}

.content--embed .split-layout--embed .split-layout__pager.mt-12 {
  margin-top: 8px;
}

/* ========== 预览弹窗样式 ========== */
.image-preview-dialog {
  max-height: 70vh;
  overflow-y: auto;
  padding-right: 8px;
}
.category-section {
  margin-bottom: 24px;
  border-bottom: 1px solid #e8e8e8;
  padding-bottom: 16px;
}
.category-section:last-child {
  border-bottom: none;
}
.category-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
.category-title {
  display: flex;
  align-items: center;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}
.image-row {
  display: flex;
  gap: 16px;
  justify-content: space-between;
}
.image-item-square {
  flex: 1;
  max-width: calc(33.333% - 11px);
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  overflow: hidden;
  background: #fff;
  position: relative;
}
.anno-svg-square,
.anno-fallback-square {
  width: 100%;
  aspect-ratio: 1 / 1;
  display: block;
  object-fit: cover;
}
.anno-poly {
  fill: rgba(59, 130, 246, 0.12);
  stroke: #3b82f6;
  stroke-width: 2;
}
.image-item-square:hover {
  box-shadow: 0 4px 12px rgba(0,0,0,.1);
  transform: translateY(-2px);
  transition: all .3s ease;
}
@media (max-width: 768px) {
  .image-row {
    flex-direction: column;
  }
  .image-item-square {
    max-width: 100%;
  }
}

.instance-drawer-task-split-hint {
  margin: 0 0 10px;
  font-size: 13px;
  color: #606266;
  line-height: 1.5;
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
}

.instance-drawer-task-split-hint__text {
  color: #909399;
  font-size: 12px;
}

.instance-split-dialog-hint {
  margin: 0 0 16px;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}

.instance-split-dialog-hint code {
  font-size: 12px;
  background: #f4f4f5;
  padding: 0 4px;
  border-radius: 3px;
}

/* ========== 类别标签样式 ========== */
.category-tags-container {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  max-width: 100%;
  min-width: 0;
  align-items: flex-start;
  max-height: 96px;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 2px 0;
}
.category-tags-container .el-tag {
  flex-shrink: 0;
}

/* 辅助样式 */
.fontSpan {
  font-size: 12px;
}

::v-deep .el-radio__inner {
  width: 16px;
  height: 16px;
  border: 2px solid #dcdcdc;
}
::v-deep .el-radio__input.is-checked .el-radio__inner {
  border-color: #409EFF;
  background: #409EFF;
}
::v-deep .el-radio__input.is-checked .el-radio__inner::after {
  width: 6px;
  height: 6px;
  background-color: #fff;
}
</style>