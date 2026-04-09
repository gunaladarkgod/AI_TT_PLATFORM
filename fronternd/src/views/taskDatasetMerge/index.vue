<template>
  <div class="content-div">
    <!-- 类别合并界面（目标子集构建和预训练子集构建） -->
    <div v-if="showCreateForm" class="create-interface">
      <div class="create-header">
        <h2 class="create-title">{{ createPageTitle }}</h2>
        </div>
        
      

      <!-- 顶部表单：模板选择、新建模板 -->
      <div class="filter-row" style="margin-bottom: 12px;" v-if="!showPretrainForm">
        <div style="display:flex; align-items:center; gap:8px;">
          <span>任务数据集模板选择</span>
          <el-select 
            v-model="selectedTemplateId" 
            placeholder="请选择模板" 
            size="small" 
            style="width: 220px;" 
            @change="onTemplateChangeById" 
            @focus="onTemplateSelectFocus"
            @clear="onTemplateClear"
            :disabled="templateLocked"
            filterable
            clearable>
            <el-option 
              v-for="tpl in templateOptions" 
              :key="tpl.id" 
              :label="tpl.displayName || tpl.name" 
              :value="tpl.id" />
          </el-select>
        </div>
        <div style="display:flex; align-items:center; gap:8px;">
          <span>新建模板</span>
          <el-button size="small" type="primary" :disabled="templateLocked" @click="openNewTemplateDialog">新建模板</el-button>
        </div>
      </div>
      
      <!-- 三栏区域：原始数据集 / 任务数据集 / 已选数据集 -->
      <div class="triple-columns">
        <!-- 左：原始数据集（左右并排：名称 / 类别） -->
        <div class="panel original-panel">
          <div class="panel-title original-panel-header">
            <span class="panel-title-text">原始数据集</span>
            <div class="mid-filters inline-filters">
              <el-select v-model="midFilters.sensorType" placeholder="传感器类型" size="small" clearable class="filter-select sensor-select">
              <el-option v-for="opt in sensorTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
              <el-select v-model="midFilters.targetType" placeholder="目标类型" size="small" clearable class="filter-select target-select">
              <el-option v-for="opt in targetTypeOptions" :key="opt" :label="opt" :value="opt" />
            </el-select>
              <el-input v-model="midFilters.name" placeholder="名称" size="small" clearable class="filter-input name-input" />
              <el-input v-model="midFilters.user" placeholder="创建用户" size="small" clearable class="filter-input user-input" />
            </div>
      </div>
          <div class="mid-split">
            <!-- 左：名称（子集列表） -->
            <div class="mid-left">
              <div class="mid-left-scroll">
                <el-table 
                  :data="filteredParentDatasets" 
                  v-loading="parentDatasetsLoading" 
                size="small"
                  stripe 
                  class="subset-table"
                  style="width:100%;"
                  @row-click="selectParent"
                  :row-class-name="getParentRowClassName">
                  <el-table-column prop="name" label="名称">
                    <template #default="{ row, $index }">
                      {{ row.name || row.id }}
                    </template>
                  </el-table-column>
                </el-table>
            </div>
          </div>
            <!-- 右：类别（当前选中子集的类别列表） -->
            <div class="mid-right">
              <div class="mid-right-scroll" v-loading="midRightLoading">
        <el-table 
                  :data="currentParentTagsList" 
          size="small"
          stripe
                  class="subset-tags-table"
                  style="width:100%;"
                  @row-click="handleCategoryRowClick"
                  :row-class-name="getCategoryRowClassName">
                  <el-table-column label="类别">
                    <template #default="{ row }">{{ row?.category }}</template>
          </el-table-column>
                  <el-table-column label="选择" width="100">
            <template #default="{ row }">
                    <el-checkbox 
                        :model-value="isParentCategoryChosen(currentParent?.id, row?.category)" 
                        :disabled="isOriginalCategoryDisabled(currentParent?.id, row?.category)" 
                        @change="(v) => toggleChooseParentCategory(currentParent, row, v)"
                        @click.stop />
            </template>
          </el-table-column>
        </el-table>
      </div>
      </div>
    </div>
      </div>

        <!-- 中：任务数据集（类别选择，单选） -->
        <div class="panel with-sep task-panel">
          <div class="panel-title">任务数据集</div>
          <div class="task-scroll">
        <el-table 
              :data="taskCategoryRows" 
          size="small" 
              stripe 
              class="task-table" 
              style="width:100%;"
              :row-class-name="getTaskRowClassName">
              <el-table-column prop="name" label="类别" width="120" />
              <el-table-column label="选择" width="100">
            <template #default="{ row }">
                  <el-checkbox :model-value="row.checked" @change="() => selectOnlyThis(row)" :disabled="isTaskCategoryDisabled(row.name)" />
            </template>
          </el-table-column>
        </el-table>
    </div>
      </div>

      <!-- 右：已选数据集（分成上下两部分） -->
        <div class="panel with-sep selected-panel">
          <!-- 上：已合并类别 -->
          <div class="merged-categories-section">
            <div class="panel-title">
              <span>已合并类别</span>
              <el-button size="small" text type="primary" @click="viewAllMergedCategories" style="margin-left: 8px;">查看全部</el-button>
      </div>
            <div class="merged-scroll">
              <el-table :data="mergedCategoriesList" size="small" stripe style="width:100%;">
                <el-table-column type="index" label="序号" width="60" />
                <el-table-column prop="taskCategory" label="任务类别名" width="120" />
                <el-table-column label="操作一:重新编辑" width="130">
            <template #default="{ row }">
                    <el-button size="small" text type="warning" @click="reEditMergedCategory(row)">重新编辑</el-button>
            </template>
          </el-table-column>
                <el-table-column label="操作二:查看" width="110">
            <template #default="{ row }">
                    <el-button size="small" text type="primary" @click="viewMergedCategory(row)">查看</el-button>
            </template>
          </el-table-column>
                <el-table-column label="操作三:删除" width="110">
            <template #default="{ row }">
                    <el-button size="small" text type="danger" @click="deleteMergedCategory(row)">删除</el-button>
            </template>
          </el-table-column>
                <el-table-column label="" />
        </el-table>
      </div>
      </div>
          <!-- 下：正在合并类别 -->
          <div class="merging-categories-section">
            <div class="panel-title">
              <span>正在合并的类别</span>
              <span v-if="currentMergingTaskCategory" style="margin-left: 8px; color: #409eff; font-weight: normal;">{{ currentMergingTaskCategory }}</span>
              <span style="margin-left: 8px; color: #333; font-weight: normal;">已选数据集</span>
    </div>
            <div class="merging-scroll">
              <el-table :data="selectedItems" size="small" stripe style="width:100%;">
                <el-table-column type="index" label="序号" width="70" />
                <el-table-column prop="parentName" label="原始数据集" width="150" />
                <el-table-column prop="category" label="原始类别名" width="140" />
                <el-table-column label="操作" width="80">
              <template #default="{ row }">
                    <el-button size="small" text type="danger" @click="removeSelected(row)">移除</el-button>
              </template>
            </el-table-column>
                <el-table-column label="" />
          </el-table>
        </div>
          </div>
          <div class="merge-actions">
            <el-button type="primary" size="small" @click="handleMergeAndReset">合并</el-button>
          </div>
        </div>
      </div>
      
      <!-- 底部按钮 -->
      <div class="action-buttons">
        <el-button @click="backToOriginal" size="small">返回</el-button>
        <el-button type="primary" @click="confirmTripleLayout" size="small">确定</el-button>
      </div>
    </div>

    <!-- 修改模板文件名对话框 -->
    <el-dialog
      v-model="renameDialogVisible"
      title="修改文件名"
      width="420px"
      :close-on-click-modal="false"
      @close="handleRenameDialogClosed"
    >
      <div class="rename-dialog-body">
        <el-form label-width="140px" size="small">
          <el-form-item label="传感器类型">
            <el-select v-model="renameForm.sensorTypeCode" placeholder="请选择">
              <el-option
                v-for="opt in sensorTypeDialogOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="目标类型">
            <el-select v-model="renameForm.targetTypeCode" placeholder="请选择">
              <el-option
                v-for="opt in targetTypeDialogOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="文件名称">
            <el-input v-model="renameForm.customName" placeholder="请输入文件名称" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button size="small" @click="handleRenameDialogCancel">取消</el-button>
        <el-button size="small" type="primary" @click="handleRenameDialogConfirm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 查看已合并类别对话框 -->
    <el-dialog
      v-model="showViewMergedDialog"
      title="查看已合并类别详情"
      width="800px"
      :close-on-click-modal="false"
    >
      <div class="view-merged-scroll">
        <el-table :data="viewMergedDetails" size="small" stripe style="width:100%;" :span-method="handleViewMergedSpanMethod">
          <el-table-column prop="taskCategory" label="任务类别名" width="150" />
          <el-table-column prop="parentName" label="原始数据集" />
          <el-table-column prop="originalCategory" label="原始类别名" />
        </el-table>
      </div>
      <template #footer>
        <el-button @click="showViewMergedDialog = false" size="small">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 查看全部已合并类别对话框 -->
    <el-dialog
      v-model="showViewAllMergedDialog"
      title="查看全部已合并类别"
      width="800px"
      :close-on-click-modal="false"
    >
      <div class="view-all-merged-scroll">
        <el-table :data="allMergedCategoriesDetails" size="small" stripe style="width:100%;" :span-method="handleSpanMethod">
          <el-table-column prop="taskCategory" label="任务类别名" width="150" />
          <el-table-column prop="parentName" label="原始数据集" />
          <el-table-column prop="originalCategory" label="原始类别名" />
        </el-table>
      </div>
      <template #footer>
        <el-button @click="showViewAllMergedDialog = false" size="small">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 新建模板对话框 -->
    <el-dialog
      v-model="showNewTemplateDialog"
      title="新建模板"
      width="600px"
      :close-on-click-modal="false"
      @close="handleNewTemplateDialogClosed"
      class="new-template-dialog"
    >
      <div class="new-template-dialog-body">
        <el-form label-width="100px" size="small">
          <el-form-item label="传感器类型">
            <el-select v-model="newTemplateForm.sensorTypeCode" placeholder="请选择" style="width: 100%;">
              <el-option
                v-for="opt in sensorTypeDialogOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="目标类型">
            <el-select v-model="newTemplateForm.targetTypeCode" placeholder="请选择" style="width: 100%;">
              <el-option
                v-for="opt in targetTypeDialogOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="文件名称">
            <el-input v-model="newTemplateForm.customName" placeholder="请输入文件名称" />
          </el-form-item>
          <el-form-item label="任务数据集类型">
            <div style="display: flex; align-items: center; width: 100%;">
              <div style="flex: 1; border-bottom: 1px dashed #dcdfe6; margin-right: 12px;"></div>
              <el-button size="small" type="primary" @click="addNewType">新增类型</el-button>
            </div>
          </el-form-item>
        </el-form>
        <div class="type-list-container" ref="typeListContainerRef">
          <div 
            v-for="(type, index) in newTemplateForm.types" 
            :key="index" 
            class="type-item"
          >
            <div style="display: flex; align-items: center; gap: 8px;">
              <el-input 
                v-model="newTemplateForm.types[index]" 
                placeholder="请输入类型名称"
                size="small"
                style="flex: 1;"
              />
              <el-button 
                size="small" 
                type="danger" 
                text 
                @click="removeType(index)"
              >
                删除
              </el-button>
            </div>
          </div>
          <div v-if="newTemplateForm.types.length === 0" class="empty-type-hint">
            暂无类型，请点击"新增类型"添加
          </div>
        </div>
      </div>
      <template #footer>
        <el-button size="small" @click="handleNewTemplateDialogCancel">取消</el-button>
        <el-button size="small" type="primary" @click="handleNewTemplateDialogConfirm">确定</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus'
import { TaskDatasetMergeService, TemplateService } from '@/api/api.js'

const router = useRouter()
const typeListContainerRef = ref(null)

// 响应式数据
const showCreateForm = ref(true) // 直接显示类别合并界面
const showPretrainForm = ref(false) // 预训练子集构建界面

// 数据集选择相关
const parentDatasets = ref([])
const parentDatasetsLoading = ref(false)
const datasetDetail = ref(null)

const SENSOR_TYPE_MAP = Object.freeze({
  nature: '自然',
  sar: '遥感SAR',
  vl: '遥感可见光',
  inf: '遥感红外',
  mul: '遥感多光谱'
})

const TARGET_TYPE_MAP = Object.freeze({
  ship: '舰船',
  mix: '复合',
  plane: '飞机',
  car: '车辆',
  other: '其他'
})

const SENSOR_LABEL_TO_CODE = Object.freeze(
  Object.fromEntries(Object.entries(SENSOR_TYPE_MAP).map(([code, label]) => [label, code]))
)
const TARGET_LABEL_TO_CODE = Object.freeze(
  Object.fromEntries(Object.entries(TARGET_TYPE_MAP).map(([code, label]) => [label, code]))
)

const sensorTypeDialogOptions = Object.freeze(
  Object.entries(SENSOR_TYPE_MAP).map(([value, label]) => ({
    value,
    label: value.toUpperCase(),
    displayLabel: label
  }))
)
const targetTypeDialogOptions = Object.freeze(
  Object.entries(TARGET_TYPE_MAP).map(([value, label]) => ({
    value,
    label: value.toUpperCase(),
    displayLabel: label
  }))
)

const resolveSensorCode = (token) => {
  if (!token && token !== 0) return null
  const trimmed = String(token).trim()
  if (!trimmed) return null
  const lower = trimmed.toLowerCase()
  if (SENSOR_TYPE_MAP[lower]) {
    return lower
  }
  if (SENSOR_LABEL_TO_CODE[trimmed]) {
    return SENSOR_LABEL_TO_CODE[trimmed]
  }
  const upper = trimmed.toUpperCase()
  if (SENSOR_TYPE_MAP[upper.toLowerCase()]) {
    return upper.toLowerCase()
  }
  return null
}

const resolveTargetCode = (token) => {
  if (!token && token !== 0) return null
  const trimmed = String(token).trim()
  if (!trimmed) return null
  const lower = trimmed.toLowerCase()
  if (TARGET_TYPE_MAP[lower]) {
    return lower
  }
  if (TARGET_LABEL_TO_CODE[trimmed]) {
    return TARGET_LABEL_TO_CODE[trimmed]
  }
  const upper = trimmed.toUpperCase()
  if (TARGET_TYPE_MAP[upper.toLowerCase()]) {
    return upper.toLowerCase()
  }
  return null
}

const parseTemplateFileName = (baseName) => {
  if (!baseName) return null
  const rawParts = baseName.split('_').map(part => String(part).trim()).filter(Boolean)
  if (rawParts.length < 3) return null
  const [sensorPart, targetPart, ...rest] = rawParts
  const sensorCode = resolveSensorCode(sensorPart)
  const targetCode = resolveTargetCode(targetPart)
  const customName = rest.join('_').trim()
  if (!sensorCode || !targetCode || !customName) return null
  return {
    sensorCode,
    targetCode,
    sensorLabel: SENSOR_TYPE_MAP[sensorCode],
    targetLabel: TARGET_TYPE_MAP[targetCode],
    customName,
    displayName: `${sensorCode}_${targetCode}_${customName}`,
    backendName: `${SENSOR_TYPE_MAP[sensorCode]}_${TARGET_TYPE_MAP[targetCode]}_${customName}`,
    // 添加原始的三个字段
    fieldA: sensorCode,
    fieldB: targetCode,
    fieldC: customName
  }
}

const parseClassList = (source) => {
  if (!source) return []
  if (Array.isArray(source)) return source.map(v => String(v).trim()).filter(Boolean)
  if (typeof source === 'string') {
    const trimmed = source.trim()
    if (!trimmed) return []
    if (trimmed.startsWith('[') || trimmed.startsWith('{')) {
      try {
        const parsed = JSON.parse(trimmed)
        if (Array.isArray(parsed)) return parsed.map(v => String(v).trim()).filter(Boolean)
        if (parsed && typeof parsed === 'object') return Object.keys(parsed)
      } catch (e) {
        // fall through
      }
    }
    if (trimmed.includes(',')) {
      return trimmed.split(',').map(v => v.trim()).filter(Boolean)
    }
    if (trimmed.includes('_')) {
      return trimmed.split('_').map(v => v.trim()).filter(Boolean)
    }
    return [trimmed]
  }
  if (typeof source === 'object') {
    return Object.keys(source)
  }
  return []
}

const buildTemplateOption = (item) => {
  if (!item) return null
  const idRaw = item.id ?? item.templateId ?? item.template_id
  const id = idRaw !== undefined && idRaw !== null ? String(idRaw) : ''
  if (!id) return null
  const categories = parseClassList(item.classList ?? item.categories ?? item.class_list)
  const nameSource = String(item.name ?? item.backendName ?? '').trim()
  const displaySource = String(item.displayName ?? item.display_name ?? '').trim()
  const metaFromName = parseTemplateFileName(displaySource || nameSource)
  
  // 保留 A、B、C 字段
  const fieldA = metaFromName?.fieldA ?? ''
  const fieldB = metaFromName?.fieldB ?? ''
  const fieldC = metaFromName?.fieldC ?? ''
  
  const sensorLabel = item.sensorType ?? item.sensor_type ?? item.sensorTypeLabel ?? item.sensor_type_label ?? metaFromName?.sensorLabel ?? ''
  const targetLabel = item.targetType ?? item.target_type ?? item.targetTypeLabel ?? item.target_type_label ?? metaFromName?.targetLabel ?? ''
  const sensorCode = item.sensorTypeCode ?? item.sensor_type_code ?? (sensorLabel ? SENSOR_LABEL_TO_CODE[sensorLabel] : undefined) ?? metaFromName?.sensorCode ?? ''
  const targetCode = item.targetTypeCode ?? item.target_type_code ?? (targetLabel ? TARGET_LABEL_TO_CODE[targetLabel] : undefined) ?? metaFromName?.targetCode ?? ''
  const customName = metaFromName?.customName ?? (() => {
    const parts = (displaySource || nameSource).split('_').map(s => s.trim()).filter(Boolean)
    if (parts.length >= 3) return parts.slice(2).join('_')
    return ''
  })()
  
  const displayName = displaySource || metaFromName?.displayName || (sensorCode && targetCode && customName ? `${sensorCode}_${targetCode}_${customName}` : nameSource)
  const backendName = nameSource || (sensorLabel && targetLabel && customName ? `${sensorLabel}_${targetLabel}_${customName}` : displayName)
  
  return {
    id,
    name: backendName,
    displayName,
    rawName: nameSource,
    backendName,
    sensorType: item.sensorType ?? item.sensor_type ?? '',
    targetType: item.targetType ?? item.target_type ?? '',
    categories,
    sensorTypeCode: sensorCode,
    targetTypeCode: targetCode,
    sensorTypeLabel: sensorLabel,
    targetTypeLabel: targetLabel,
    // 添加 A、B、C 字段
    fieldA,
    fieldB,
    fieldC,
    customName: fieldC || customName
  }
}

const buildParentEntries = (detail, isPretrain) => {
  const coreSubsets = Array.isArray(detail?.coreSubsets) ? detail.coreSubsets : []
  const auxiliarySubsets = Array.isArray(detail?.auxiliarySubsets) ? detail.auxiliarySubsets : []
  const coreIds = parseClassList(detail?.taskDataset?.coreId).map(String)
  const auxiliaryIds = parseClassList(detail?.taskDataset?.supId).map(String)

  const toEntry = (subset, subsetType, fallback) => {
    const categories = parseClassList(subset?.class_list)
    const finalCategories = categories.length ? categories : fallback
    return {
      id: subset.id,
      name: subset.name || subset.subset_name || String(subset.id ?? ''),
      type: subset.sensor_type || subset.type,
      created_by: detail?.taskDataset?.username || subset.username || '',
      target_type: subsetType === 'core' ? detail?.taskDataset?.coreTargetType : detail?.taskDataset?.supTargetType,
      subsetType,
      categories: finalCategories
    }
  }

  const coreFallback = parseClassList(detail?.taskDataset?.coreClassList)
  const auxiliaryFallback = parseClassList(detail?.taskDataset?.supClassList)

  const filteredCore = coreSubsets.filter(subset => {
    if (!coreIds.length) return true
    return coreIds.includes(String(subset.id))
  }).map(subset => toEntry(subset, 'core', coreFallback))

  const filteredAuxiliary = auxiliarySubsets.filter(subset => {
    if (!auxiliaryIds.length) return true
    return auxiliaryIds.includes(String(subset.id))
  }).map(subset => toEntry(subset, 'auxiliary', auxiliaryFallback))

  return isPretrain ? filteredAuxiliary : filteredCore
}

// 三栏布局相关（符合截图）
const taskCategoryRows = ref([]) // [{name, checked}] 单选
const selectedItems = ref([]) // 已选的数据集子集 [{id, name, parentName, category}]
// 模板选择
const templateOptions = ref([])
const selectedTemplateId = ref(null)
const selectedTemplate = computed(() => {
  if (!selectedTemplateId.value) return null
  const idStr = String(selectedTemplateId.value)
  return (templateOptions.value || []).find(t => String(t.id) === idStr) || null
})
const templateSegments = ref({ sensor: '', target: '' })
const templateLocked = ref(false)
const mergedOnce = ref(false) // 必须至少合并过一次才能进入预训练
const previousOriginalDatasetNames = ref([]) // 进入预训练时的来源原始数据集名称

// 中间列状态：当前选中的原始数据集与其类别选择
const currentParent = ref(null)
const selectedCategoryMap = ref({}) // { [parentId]: Set<string> }
const currentParentTagsList = ref([]) // 右侧类别的稳定数据源
const midRightLoading = ref(false)
const coreCategoryEntries = ref([])
const auxiliaryCategoryEntries = ref([])
const taskDatasetId = ref(null)
const targetSubsetId = ref(null)
const pretrainSubsetId = ref(null)
const activeParentIdForTarget = ref(null)

// 合并后禁用再次选择的集合（目标子集阶段）
const mergedTaskDisabledSet = ref(new Set()) // Set<string>
const mergedOriginalDisabledMap = ref({}) // { [parentId]: Set<string> }
// 预训练阶段禁用集合（每个类别在预训练阶段也只能使用一次）
const pretrainTaskDisabledSet = ref(new Set())
const pretrainOriginalDisabledMap = ref({})
// 记录合并关系，便于后续提交或调试查看
const mergedRelations = ref([]) // { taskCategory, parentId, parentName, originalCategory, stage: 'target'|'pretrain' }
// 查看已合并类别对话框
const showViewMergedDialog = ref(false)
const viewMergedDetails = ref([]) // 查看对话框中的详情列表
// 查看全部已合并类别对话框
const showViewAllMergedDialog = ref(false)
const allMergedCategoriesDetails = ref([]) // 全部已合并类别详情（扁平化，用于表格显示）

// 原始数据集筛选条件（中部）
const sensorTypeOptions = ref([
  { label: '自然', value: 0 },
  { label: '遥感可见光', value: 1 },
  { label: '遥感SAR', value: 2 },
  { label: '遥感红外', value: 3 },
  { label: '遥感多光谱', value: 4 }
])
const targetTypeOptions = ref(['舰船','飞机','车辆','复合','其他'])
const midFilters = reactive({ sensorType: '', targetType: '', name: '', user: '' })

// 模板文件名修正弹窗
const renameDialogVisible = ref(false)
const renameDialogResolve = ref(null)
const renameForm = reactive({
  sensorTypeCode: '',
  targetTypeCode: '',
  customName: ''
})

// 新建模板弹窗
const showNewTemplateDialog = ref(false)
const newTemplateForm = reactive({
  sensorTypeCode: '',
  targetTypeCode: '',
  customName: '',
  types: [] // 任务数据集类型列表
})


// 标题
const createPageTitle = computed(() => showPretrainForm.value ? '任务数据集预训练子集构建' : '任务数据集任务目标子集构建')
const currentTaskDatasetName = computed(() => {
  const nameFromDetail = datasetDetail.value?.taskDataset?.name
  if (nameFromDetail && String(nameFromDetail).trim()) {
    return String(nameFromDetail).trim()
  }
  const displayName = datasetDetail.value?.taskDataset?.displayName
  if (displayName && String(displayName).trim()) {
    return String(displayName).trim()
  }
  return ''
})

// 已勾选的任务类别名称（单选返回 0..1 个）
const activeCategoryNames = computed(() => taskCategoryRows.value.filter(r => r.checked).map(r => r.name))

// 已合并类别列表（去重，按任务类别名，仅显示当前界面的合并记录）
const mergedCategoriesList = computed(() => {
  const unique = new Set()
  const result = []
  const currentStage = showPretrainForm.value ? 'pretrain' : 'target'
  mergedRelations.value.forEach(rel => {
    if (rel.taskCategory && !unique.has(rel.taskCategory) && rel.stage === currentStage) {
      unique.add(rel.taskCategory)
      result.push({ taskCategory: rel.taskCategory })
    }
  })
  return result
})

// 当前正在合并的任务类别名
const currentMergingTaskCategory = computed(() => {
  const checked = taskCategoryRows.value.find(r => r.checked)
  return checked ? checked.name : ''
})

// 筛选后的父数据集
const filteredParentDatasets = computed(() => {
  let filtered = parentDatasets.value || []
  
  // 中部筛选 - 名称&用户模糊
  if (midFilters.name && midFilters.name.trim()) {
    const key = midFilters.name.trim().toLowerCase()
    filtered = filtered.filter(item => (item.name || '').toLowerCase().includes(key))
  }
  if (midFilters.user && midFilters.user.trim()) {
    const key = midFilters.user.trim().toLowerCase()
    filtered = filtered.filter(item => (item.created_by || '').toLowerCase().includes(key))
  }
  // 中部筛选 - 传感器类型（匹配 type 数值）
  if (midFilters.sensorType !== '' && midFilters.sensorType !== null && midFilters.sensorType !== undefined) {
    filtered = filtered.filter(item => item.type === midFilters.sensorType)
  }
  // 中部筛选 - 目标类型
  if (midFilters.targetType) {
    filtered = filtered.filter(item => (item.target_type || '') === midFilters.targetType)
  }
  
  return filtered
})


// 方法定义
// 返回：
// - 若在预训练界面，返回到"目标子集构建"界面，保留当前原始数据集数据与筛选
// - 否则模拟返回到"原始数据集显示界面"（未实现），不清空当前数据，仅关闭当前页
const backToOriginal = async () => {
  if (showPretrainForm.value) {
    showPretrainForm.value = false
    // 切换回页面1时，重新加载核心数据集
    await loadParentDatasets()
    return
  }
  // 这里本应路由返回到"原始数据集显示界面"，为了测试仅关闭本页但不清空数据
  showCreateForm.value = false
}

const normalizeResponse = (response) => {
  if (response === null || response === undefined) {
    return {}
  }
  if (typeof response === 'string') {
    try {
      return JSON.parse(response)
    } catch (error) {
      console.error('[merge] JSON parse error:', error)
      return {}
    }
  }
  return response
}

const loadParentDatasets = async () => {
  parentDatasetsLoading.value = true
  try {
    const response = await TaskDatasetMergeService.getSubsetsInfo({ fetch_parent_list: true })
    const normalized = normalizeResponse(response)
    datasetDetail.value = normalizeResponse(normalized?.data)
    const entries = buildParentEntries(datasetDetail.value, showPretrainForm.value)
    parentDatasets.value = entries

    if (parentDatasets.value.length > 0) {
      const targetId = showPretrainForm.value ? activeParentIdForTarget.value : null
      const defaultEntry = showPretrainForm.value
        ? parentDatasets.value.find(item => String(item.id) === String(targetId)) || parentDatasets.value[0]
        : parentDatasets.value[0]
      if (defaultEntry) {
        if (showPretrainForm.value) {
          activeParentIdForTarget.value = defaultEntry.id
        }
        await selectParent(defaultEntry)
      }
    }
  } catch (error) {
    console.error('加载原始数据集列表失败:', error)
    parentDatasets.value = []
  } finally {
    parentDatasetsLoading.value = false
  }
}

const buildCategoryEntries = (row) => {
  const categories = Array.isArray(row?.categories) ? row.categories : []
  return categories.map(category => ({
    subsetId: row.id,
    subsetName: row.name,
    subsetType: row.subsetType || (showPretrainForm.value ? 'auxiliary' : 'core'),
    category: String(category)
  }))
}

const updateCurrentParentCategoryEntries = () => {
  const source = showPretrainForm.value ? auxiliaryCategoryEntries.value : coreCategoryEntries.value
  currentParentTagsList.value = source.map(entry => ({ ...entry }))
}

// 修改模板加载方法
const loadTemplates = async () => {
  try {
    const response = await TemplateService.list()
    const normalized = normalizeResponse(response)
    const data = Array.isArray(normalized?.data) ? normalized.data : []

    // 根据后端返回的数据结构调整映射
    templateOptions.value = data
      .map(item => buildTemplateOption(item))
      .filter(Boolean)

    if (templateOptions.value.length > 0 && !selectedTemplateId.value) {
      selectedTemplateId.value = templateOptions.value[0].id
      onTemplateChangeById(selectedTemplateId.value)
    }
  } catch (error) {
    console.error('加载模板失败:', error)
    templateOptions.value = []
  }
}

// 添加上传模板到后端的方法
const uploadTemplateToServer = async (meta, categories) => {
  try {
    const response = await TemplateService.upload({
      name: meta.backendName,
      classListJson: JSON.stringify(categories),
      sensorType: meta.sensorLabel,
      targetType: meta.targetLabel,
      sensorTypeCode: meta.sensorCode,
      targetTypeCode: meta.targetCode,
      displayName: meta.displayName
    })
    const normalized = normalizeResponse(response)
    if (normalized.code === 0 || normalized.code === 200 || normalized.success || normalized.data) {
      const newTemplate = normalized.data || {}
      const option = buildTemplateOption({
        ...newTemplate,
        id: newTemplate.id ?? `tmp_${Date.now()}`,
        name: newTemplate.name ?? meta.backendName,
        displayName: newTemplate.displayName ?? meta.displayName,
        classList: newTemplate.classList ?? newTemplate.categories ?? categories,
        sensorTypeCode: newTemplate.sensorTypeCode ?? meta.sensorCode,
        targetTypeCode: newTemplate.targetTypeCode ?? meta.targetCode,
        sensorType: newTemplate.sensorType ?? newTemplate.sensorTypeLabel ?? meta.sensorLabel,
        targetType: newTemplate.targetType ?? newTemplate.targetTypeLabel ?? meta.targetLabel
      })
      if (!option) {
        throw new Error('模板数据不完整')
      }
      return option
    } else {
      throw new Error(normalized.msg || '上传失败')
    }
  } catch (error) {
    console.error('上传模板到服务器失败:', error)
    throw error
  }
}

const resetRenameForm = () => {
  renameForm.sensorTypeCode = ''
  renameForm.targetTypeCode = ''
  renameForm.customName = ''
}

const resolveRenamePromise = (value) => {
  const resolver = renameDialogResolve.value
  renameDialogResolve.value = null
  if (resolver) {
    resolver(value)
  }
}

const handleRenameDialogConfirm = () => {
  const sensorCode = renameForm.sensorTypeCode
  const targetCode = renameForm.targetTypeCode
  const sensorLabel = SENSOR_TYPE_MAP[sensorCode]
  const targetLabel = TARGET_TYPE_MAP[targetCode]
  if (!sensorLabel) {
    ElMessage.warning('请选择传感器类型')
    return
  }
  if (!targetLabel) {
    ElMessage.warning('请选择目标类型')
    return
  }
  const custom = (renameForm.customName || '').trim()
  if (!custom) {
    ElMessage.warning('请输入文件名称')
    return
  }
  const displayName = `${sensorCode}_${targetCode}_${custom}`
  const backendName = `${sensorLabel}_${targetLabel}_${custom}`
  renameDialogVisible.value = false
  resolveRenamePromise({
    sensorCode,
    targetCode,
    sensorLabel,
    targetLabel,
    customName: custom,
    displayName,
    backendName
  })
  resetRenameForm()
}

const handleRenameDialogCancel = () => {
  renameDialogVisible.value = false
  resolveRenamePromise(null)
  resetRenameForm()
}

const handleRenameDialogClosed = () => {
  resolveRenamePromise(null)
  resetRenameForm()
}

// 新建模板相关方法
const openNewTemplateDialog = () => {
  resetNewTemplateForm()
  showNewTemplateDialog.value = true
}

const resetNewTemplateForm = () => {
  newTemplateForm.sensorTypeCode = ''
  newTemplateForm.targetTypeCode = ''
  newTemplateForm.customName = ''
  newTemplateForm.types = []
}

const addNewType = async () => {
  newTemplateForm.types.push('')
  // 等待 DOM 更新后滚动到底部
  await nextTick()
  if (typeListContainerRef.value) {
    typeListContainerRef.value.scrollTop = typeListContainerRef.value.scrollHeight
  }
}

const removeType = (index) => {
  newTemplateForm.types.splice(index, 1)
}

const handleNewTemplateDialogConfirm = async () => {
  const sensorCode = newTemplateForm.sensorTypeCode
  const targetCode = newTemplateForm.targetTypeCode
  const sensorLabel = SENSOR_TYPE_MAP[sensorCode]
  const targetLabel = TARGET_TYPE_MAP[targetCode]
  
  if (!sensorLabel) {
    ElMessage.warning('请选择传感器类型')
    return
  }
  if (!targetLabel) {
    ElMessage.warning('请选择目标类型')
    return
  }
  const custom = (newTemplateForm.customName || '').trim()
  if (!custom) {
    ElMessage.warning('请输入文件名称')
    return
  }
  if (newTemplateForm.types.length === 0) {
    ElMessage.warning('请至少添加一个任务数据集类型')
    return
  }
  
  // 过滤掉空字符串
  const validTypes = newTemplateForm.types.filter(t => t && t.trim())
  if (validTypes.length === 0) {
    ElMessage.warning('请至少添加一个有效的任务数据集类型')
    return
  }
  
  const displayName = `${sensorCode}_${targetCode}_${custom}`
  const backendName = `${sensorLabel}_${targetLabel}_${custom}`
  
  // 检查是否已存在同名模板
  const displayExists = (value) => (templateOptions.value || []).some(t => t.displayName === value)
  if (displayExists(displayName)) {
    ElMessage.warning('已存在同名模板，请修改文件名称')
    return
  }
  
  try {
    // 上传到后端
    const newTemplate = await uploadTemplateToServer({
      sensorCode,
      targetCode,
      sensorLabel,
      targetLabel,
      customName: custom,
      displayName,
      backendName
    }, validTypes)
    
    // 添加到模板列表并选中
    templateOptions.value = [...templateOptions.value, newTemplate]
    selectedTemplateId.value = newTemplate.id
    
    // 同步显示到任务类别
    onTemplateChangeById(newTemplate.id)
    ElMessage.success('模板创建成功并已自动选择')
    showNewTemplateDialog.value = false
    resetNewTemplateForm()
  } catch (error) {
    console.error('创建模板失败:', error)
    ElMessage.error('创建模板失败: ' + (error.message || '未知错误'))
  }
}

const handleNewTemplateDialogCancel = () => {
  showNewTemplateDialog.value = false
  resetNewTemplateForm()
}

const handleNewTemplateDialogClosed = () => {
  resetNewTemplateForm()
}

// 添加下拉框焦点事件处理
const onTemplateSelectFocus = async () => {
  if (templateOptions.value.length === 0) {
    await loadTemplates()
  }
}

const updateTemplateSegments = (tpl) => {
  const fallback = { sensor: '', target: '' }
  if (!tpl) {
    templateSegments.value = fallback
    console.debug('[template-segments] reset (tpl为空)', fallback)
    return fallback
  }
  const candidates = [
    tpl.displayName,
    tpl.name,
    tpl.backendName,
    tpl.rawName
  ]
  let sensor = ''
  let target = ''
  for (const value of candidates) {
    if (!value && value !== 0) continue
    const str = String(value).trim()
    if (!str) continue
    const parts = str.split('_').map(part => part.trim()).filter(Boolean)
    if (parts.length >= 3) {
      sensor = parts[0]
      target = parts[1]
      break
    }
    if (parts.length >= 2 && (!sensor || !target)) {
      sensor = parts[0]
      target = parts[1]
    }
  }
  if (!sensor) {
    sensor = tpl.sensorTypeCode || tpl.sensorTypeLabel || tpl.sensorType || ''
  }
  if (!target) {
    target = tpl.targetTypeCode || tpl.targetTypeLabel || tpl.targetType || ''
  }
  templateSegments.value = {
    sensor: sensor ? String(sensor).trim() : '',
    target: target ? String(target).trim() : ''
  }
  console.debug('[template-segments] 完整解析结果', {
    tplId: tpl.id,
    displayName: tpl.displayName,
    fieldA: tpl.fieldA,
    fieldB: tpl.fieldB,
    fieldC: tpl.fieldC,
    name: tpl.name,
    rawName: tpl.rawName,
    sensor,
    target,
    segments: templateSegments.value
  })
  return templateSegments.value
}

const onTemplateChange = (tpl) => {
  if (!tpl) {
    taskCategoryRows.value = []
    templateSegments.value = { sensor: '', target: '' }
    return
  }

  if (Array.isArray(tpl.categories) && tpl.categories.length) {
    const unique = Array.from(new Set(tpl.categories.map(s => String(s).trim()).filter(Boolean)))
    taskCategoryRows.value = unique.map((name) => ({ name, checked: false }))
  } else {
    taskCategoryRows.value = []
  }
  updateTemplateSegments(tpl)
}

const onTemplateChangeById = (id) => {
  if (id === undefined || id === null || id === '') {
    taskCategoryRows.value = []
    templateSegments.value = { sensor: '', target: '' }
    return
  }
  const idStr = String(id)
  const tpl = (templateOptions.value || []).find(t => String(t.id) === idStr)
  if (tpl) {
    onTemplateChange(tpl)
  } else {
    taskCategoryRows.value = []
    templateSegments.value = { sensor: '', target: '' }
  }
}

const onTemplateClear = () => {
  selectedTemplateId.value = null
  taskCategoryRows.value = []
  templateSegments.value = { sensor: '', target: '' }
}

// 选择一个原始数据集，加载其子集并展示聚合类别
// 选择父数据集，加载其子集和标签
const selectParent = async (row) => {
  if (currentParent.value?.id === row.id && currentParent.value?.subsetType === row.subsetType) {
    return
  }
  currentParent.value = row
  currentParentTagsList.value = []
  coreCategoryEntries.value = row.subsetType === 'core' ? buildCategoryEntries(row) : []
  auxiliaryCategoryEntries.value = row.subsetType === 'auxiliary' ? buildCategoryEntries(row) : []
  updateCurrentParentCategoryEntries()
}

// 判断某个类别是否已在该原始数据集中被选择
const isParentCategoryChosen = (parentId, cat) => {
  const s = selectedCategoryMap.value[parentId]
  return !!(s && s.has(cat))
}

// 切换选择：选中则加入右侧"已选数据集"；取消则移除（以原始数据集为维度）
const toggleChooseParentCategory = (parent, entry, checked) => {
  if (!parent || !parent.id || !entry) return
  const category = entry.category
  const expectedType = showPretrainForm.value ? 'auxiliary' : 'core'
  if (entry.subsetType && entry.subsetType !== expectedType) {
    return
  }
  if (!category) return
  if (!selectedCategoryMap.value[parent.id]) selectedCategoryMap.value[parent.id] = new Set()
  const set = selectedCategoryMap.value[parent.id]
  const key = `${parent.id}__${category}`
  if (checked) {
    set.add(category)
    if (!selectedItems.value.some(x => x._key === key)) {
      selectedItems.value.push({
        _key: key,
        id: parent.id,
        name: parent.name,
        parentName: parent.name,
        category,
        subsetId: entry.subsetId,
        subsetName: entry.subsetName,
        subsetType: entry.subsetType
      })
    }
  } else {
    set.delete(category)
    selectedItems.value = selectedItems.value.filter(x => x._key !== key)
  }
}

// 处理类别行点击（点击整行切换选择）
const handleCategoryRowClick = (entry) => {
  if (!currentParent.value || !currentParent.value.id) return
  if (!entry) return
  if (isOriginalCategoryDisabled(currentParent.value.id, entry.category)) return
  const isChosen = isParentCategoryChosen(currentParent.value.id, entry.category)
  toggleChooseParentCategory(currentParent.value, entry, !isChosen)
}

// 获取任务类别表格行的类名（用于禁用状态样式）
const getTaskRowClassName = ({ row, rowIndex }) => {
  if (isTaskCategoryDisabled(row.name)) {
    return 'is-disabled'
  }
  return ''
}

// 获取原始类别表格行的类名（用于禁用状态样式）
const getCategoryRowClassName = ({ row, rowIndex }) => {
  if (!currentParent.value || !currentParent.value.id) return ''
  if (isOriginalCategoryDisabled(currentParent.value.id, row?.category)) {
    return 'is-disabled'
  }
  return ''
}

// 获取原始数据集表格行的类名（用于高亮当前选中的数据集）
const getParentRowClassName = ({ row, rowIndex }) => {
  if (currentParent.value && currentParent.value.id === row.id) {
    return 'is-current-parent'
  }
  return ''
}

// 判断禁用
const isTaskCategoryDisabled = (name) => {
  if (showPretrainForm.value) return pretrainTaskDisabledSet.value.has(name)
  return mergedTaskDisabledSet.value.has(name)
}
const isOriginalCategoryDisabled = (parentId, cat) => {
  if (!parentId) return false
  const map = showPretrainForm.value ? pretrainOriginalDisabledMap.value : mergedOriginalDisabledMap.value
  const set = map[parentId]
  return !!(set && set.has(cat))
}

// 合并并重置：把当前选中的任务类别与原始数据集类别加入不可再选集合
const handleMergeAndReset = () => {
  // 必须选择一个任务数据集类别
  const selectedTask = taskCategoryRows.value.find(r => r.checked)
  if (!selectedTask) {
    ElMessage.warning('请先在左侧选择一个任务数据集类别，再点击合并')
    return
  }
  // 必须至少选择一个原始类别
  if (selectedItems.value.length === 0) {
    ElMessage.warning('请至少选择一个原始类别，再点击合并')
    return
  }
  
  // 将当前右侧已选原始类别合并到该任务类别下，记录映射
  const currentStage = showPretrainForm.value ? 'pretrain' : 'target'
  selectedItems.value.forEach(item => {
    mergedRelations.value.push({
      taskCategory: selectedTask.name,
      parentId: item.id,
      parentName: item.parentName,
      originalCategory: item.category,
      subsetId: item.subsetId,
      subsetName: item.subsetName,
      subsetType: item.subsetType,
      stage: currentStage
    })
  })

  // 根据阶段写入禁用集合（每阶段一次性使用）
  const taskSet = showPretrainForm.value ? pretrainTaskDisabledSet.value : mergedTaskDisabledSet.value
  const map = showPretrainForm.value ? pretrainOriginalDisabledMap.value : mergedOriginalDisabledMap.value
  taskCategoryRows.value.forEach(r => {
    if (r.checked) taskSet.add(r.name)
  })
  selectedItems.value.forEach(item => {
    if (!map[item.id]) map[item.id] = new Set()
    map[item.id].add(item.category)
  })

  // 清空当前选择，进入下一轮
  selectedItems.value = []
  Object.keys(selectedCategoryMap.value).forEach(k => delete selectedCategoryMap.value[k])
  taskCategoryRows.value = taskCategoryRows.value.map(r => ({ ...r, checked: false }))
  // 锁定模板，禁止继续更换
  templateLocked.value = true
  if (!showPretrainForm.value) mergedOnce.value = true
}

// —— 三栏布局交互 ——
const selectOnlyThis = (row) => {
  taskCategoryRows.value = taskCategoryRows.value.map(r => ({ ...r, checked: r.name === row.name }))
}

const removeSelected = (row) => {
  // 从右侧移除该条
  selectedItems.value = selectedItems.value.filter(s => s._key !== row._key)
  // 同步更新中间"类别"的勾选状态
  const [parentId, cat] = row._key.split('__')
  if (selectedCategoryMap.value[parentId]) {
    selectedCategoryMap.value[parentId].delete(cat)
  }
}

// 重新编辑已合并类别（移到正在合并类别）
const reEditMergedCategory = async (row) => {
  const taskCat = row.taskCategory
  const currentStage = showPretrainForm.value ? 'pretrain' : 'target'
  
  // 找到该任务类别对应的所有已合并关系
  const relations = mergedRelations.value.filter(rel => rel.taskCategory === taskCat && rel.stage === currentStage)
  
  if (relations.length === 0) {
    ElMessage.warning('未找到该类别对应的合并关系')
    return
  }
  
  // 找到第一个原始数据集，用于显示其类别
  const firstParent = relations.length > 0 ? parentDatasets.value.find(p => p.id === relations[0].parentId) : null
  
  // 如果找到了原始数据集，先切换到该数据集以加载类别列表
  if (firstParent) {
    await selectParent(firstParent)
  }
  
  // 将合并关系中的原始类别移到"正在合并类别"区域
  selectedItems.value = []
  selectedCategoryMap.value = {}
  
  relations.forEach(rel => {
    // 重新构建 selectedItems
    const key = `${rel.parentId}__${rel.originalCategory}`
    if (!selectedItems.value.some(x => x._key === key)) {
      selectedItems.value.push({
        _key: key,
        id: rel.parentId,
        name: rel.parentName,
        parentName: rel.parentName,
        category: rel.originalCategory,
        subsetId: rel.subsetId,
        subsetName: rel.subsetName,
        subsetType: rel.subsetType
      })
    }
    
    // 更新 selectedCategoryMap
    if (!selectedCategoryMap.value[rel.parentId]) {
      selectedCategoryMap.value[rel.parentId] = new Set()
    }
    selectedCategoryMap.value[rel.parentId].add(rel.originalCategory)
  })
  
  // 在左侧任务类别中选择该类别
  taskCategoryRows.value = taskCategoryRows.value.map(r => ({
    ...r,
    checked: r.name === taskCat
  }))
  
  // 从已合并关系中移除（但保留禁用状态，直到重新合并）
  // 注意：这里不移除合并关系，因为删除操作会移除，重新编辑只是移到待合并状态
  // 但为了显示效果，我们需要临时移除，重新合并时会重新添加
  
  // 从禁用集合中移除，允许重新选择
  if (showPretrainForm.value) {
    pretrainTaskDisabledSet.value.delete(taskCat)
    relations.forEach(rel => {
      if (pretrainOriginalDisabledMap.value[rel.parentId]) {
        pretrainOriginalDisabledMap.value[rel.parentId].delete(rel.originalCategory)
      }
    })
  } else {
    mergedTaskDisabledSet.value.delete(taskCat)
    relations.forEach(rel => {
      if (mergedOriginalDisabledMap.value[rel.parentId]) {
        mergedOriginalDisabledMap.value[rel.parentId].delete(rel.originalCategory)
      }
    })
  }
  
  // 从合并关系中移除（允许重新合并）
  mergedRelations.value = mergedRelations.value.filter(rel => !(rel.taskCategory === taskCat && rel.stage === currentStage))
  
  ElMessage.success(`已将"${taskCat}"移到正在合并类别，可以重新编辑`)
}

// 查看已合并类别详情
const viewMergedCategory = (row) => {
  const taskCat = row.taskCategory
  const currentStage = showPretrainForm.value ? 'pretrain' : 'target'
  viewMergedDetails.value = mergedRelations.value
    .filter(rel => rel.taskCategory === taskCat && rel.stage === currentStage)
    .map(rel => ({
      taskCategory: rel.taskCategory,
      parentName: rel.parentName,
      originalCategory: rel.originalCategory
    }))
  showViewMergedDialog.value = true
}

// 查看单个类别的表格单元格合并方法
const handleViewMergedSpanMethod = ({ row, column, rowIndex, columnIndex }) => {
  if (columnIndex === 0) { // 任务类别名列
    const taskCat = row.taskCategory
    const list = viewMergedDetails.value
    const firstIndex = list.findIndex(item => item.taskCategory === taskCat)
    const count = list.filter(item => item.taskCategory === taskCat).length
    if (rowIndex === firstIndex) {
      return {
        rowspan: count,
        colspan: 1
        }
      } else {
      return {
        rowspan: 0,
        colspan: 0
      }
    }
  }
  return {
    rowspan: 1,
    colspan: 1
  }
}

// 查看全部已合并类别
const viewAllMergedCategories = () => {
  const currentStage = showPretrainForm.value ? 'pretrain' : 'target'
  // 按任务类别分组，然后扁平化展开
  const grouped = {}
  mergedRelations.value
    .filter(rel => rel.stage === currentStage)
    .forEach(rel => {
      if (!grouped[rel.taskCategory]) {
        grouped[rel.taskCategory] = []
      }
      grouped[rel.taskCategory].push({
        taskCategory: rel.taskCategory,
        parentName: rel.parentName,
        originalCategory: rel.originalCategory
      })
    })
  // 扁平化：将每个任务类别下的所有记录展开为多行
  const flat = []
  Object.keys(grouped).sort().forEach(taskCat => {
    grouped[taskCat].forEach(item => {
      flat.push(item)
    })
  })
  allMergedCategoriesDetails.value = flat
  showViewAllMergedDialog.value = true
}

// 表格单元格合并方法（任务类别名列需要合并）
const handleSpanMethod = ({ row, column, rowIndex, columnIndex }) => {
  if (columnIndex === 0) { // 任务类别名列
    const taskCat = row.taskCategory
    const list = allMergedCategoriesDetails.value
    // 找到该任务类别第一次出现的行
    const firstIndex = list.findIndex(item => item.taskCategory === taskCat)
    // 计算该任务类别有多少行
    const count = list.filter(item => item.taskCategory === taskCat).length
    if (rowIndex === firstIndex) {
      return {
        rowspan: count,
        colspan: 1
      }
    } else {
      return {
        rowspan: 0,
        colspan: 0
      }
    }
  }
  return {
    rowspan: 1,
    colspan: 1
  }
}

// 删除已合并类别（允许重新合并）
const deleteMergedCategory = (row) => {
  const taskCat = row.taskCategory
  const currentStage = showPretrainForm.value ? 'pretrain' : 'target'
  ElMessageBox.confirm(`确认删除已合并的任务类别"${taskCat}"？删除后可以重新合并。`, '删除确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    // 先保存要删除的关系（用于解除禁用），只删除当前阶段的
    const toDelete = mergedRelations.value.filter(rel => rel.taskCategory === taskCat && rel.stage === currentStage)
    // 从合并关系中移除（只移除当前阶段的）
    mergedRelations.value = mergedRelations.value.filter(rel => !(rel.taskCategory === taskCat && rel.stage === currentStage))
    // 从禁用集合中移除（允许重新使用）
    if (showPretrainForm.value) {
      pretrainTaskDisabledSet.value.delete(taskCat)
      // 移除该任务类别对应的原始类别禁用
      toDelete.forEach(rel => {
        if (pretrainOriginalDisabledMap.value[rel.parentId]) {
          pretrainOriginalDisabledMap.value[rel.parentId].delete(rel.originalCategory)
        }
      })
      } else {
      mergedTaskDisabledSet.value.delete(taskCat)
      // 移除该任务类别对应的原始类别禁用
      toDelete.forEach(rel => {
        if (mergedOriginalDisabledMap.value[rel.parentId]) {
          mergedOriginalDisabledMap.value[rel.parentId].delete(rel.originalCategory)
        }
      })
    }
    // 检查当前阶段是否还有已合并的类别，如果没有则解锁模板
    checkAndUnlockTemplate()
    ElMessage.success('已删除，可以重新合并该类别')
  }).catch(() => {})
}

// 检查并解锁模板：如果当前阶段的所有已合并类别都被删除，则允许重新选择模板
const checkAndUnlockTemplate = () => {
  const currentStage = showPretrainForm.value ? 'pretrain' : 'target'
  const currentStageMergedCount = mergedRelations.value.filter(rel => rel.stage === currentStage).length
  // 如果当前阶段没有任何已合并的类别，且之前合并过（templateLocked为true），则解锁模板
  if (currentStageMergedCount === 0 && templateLocked.value) {
    templateLocked.value = false
    // 如果是在目标子集阶段，且没有合并过，则重置 mergedOnce
    if (!showPretrainForm.value && mergedOnce.value) {
      // 注意：这里不重置 mergedOnce，因为可能已经创建过任务数据集了
      // 但如果用户想要重新选择模板，应该是允许的
    }
  }
}

const resetTripleColumnSelections = () => {
      currentParent.value = null
      currentParentTagsList.value = []
      selectedItems.value = []
  selectedCategoryMap.value = {}
}

const prepareForPretrainStage = async (mergeResult = null) => {
  // 显示"正在合并中"弹出窗
  const loadingDialog = ElLoading.service({
    lock: true,
    text: '正在合并中',
    background: 'rgba(0, 0, 0, 0.7)'
  })
  
  try {
    if (mergeResult) {
      const payload = mergeResult?.data || mergeResult
      if (payload) {
        taskDatasetId.value = payload.task_dataset_id ?? taskDatasetId.value
        targetSubsetId.value = payload.target_subset_id ?? targetSubsetId.value
      }
    }
    pretrainSubsetId.value = null
    const currentTemplateId = selectedTemplateId.value
    const currentTemplate = selectedTemplate.value
    const cats = (currentTemplate?.categories || taskCategoryRows.value.map(r => r.name))
      .map(s => String(s).trim())
      .filter(Boolean)
    taskCategoryRows.value = cats.map(n => ({ name: n, checked: false }))
    selectedTemplateId.value = currentTemplateId
    previousOriginalDatasetNames.value = [
      ...new Set(
        mergedRelations.value
          .filter(m => m.stage === 'target')
          .map(m => m.parentName)
          .filter(Boolean)
      )
    ]
    showPretrainForm.value = true
    templateLocked.value = true
    pretrainTaskDisabledSet.value = new Set()
    pretrainOriginalDisabledMap.value = {}
    resetTripleColumnSelections()
    coreCategoryEntries.value = []
    auxiliaryCategoryEntries.value = []
    updateCurrentParentCategoryEntries()
    activeParentIdForTarget.value = collectParentIdsByStage('target')[0] || null
    await loadParentDatasets()

    if (currentTemplateId) {
      setTimeout(() => {
        onTemplateChangeById(currentTemplateId)
      }, 100)
    }
  } finally {
    // 关闭加载弹窗
    if (loadingDialog) {
      loadingDialog.close()
    }
  }
}

const getStageRelations = (stage) => mergedRelations.value.filter(rel => rel.stage === stage)

const collectParentIdsByStage = (stage) => {
  return Array.from(
    new Set(
      getStageRelations(stage)
        .map(rel => rel.parentId)
        .filter(id => id !== undefined && id !== null && id !== '')
    )
  )
}

const collectSubsetIdsByStage = (stage) => {
  const ids = new Set()
  getStageRelations(stage).forEach(rel => {
    if (rel.subsetId) {
      ids.add(rel.subsetId)
    }
  })
  return Array.from(ids)
}

const buildLabelMappingPayload = (stage) => {
  const dedupe = new Set()
  const result = []
  getStageRelations(stage).forEach(rel => {
    const sourceLabel = rel.originalCategory
    const targetLabel = rel.taskCategory
    if (!sourceLabel || !targetLabel) return
    const key = `${sourceLabel}::${targetLabel}`
    if (dedupe.has(key)) return
    dedupe.add(key)
    result.push({
      sourceLabel,
      targetLabel
    })
  })
  return result
}

const ensureTargetStageReady = () => {
  const targetRelations = getStageRelations('target')
  if (targetRelations.length === 0) {
    ElMessage.warning('请至少完成一次目标子集合并')
    return false
  }
  if (!mergedOnce.value) {
    ElMessage.warning('请先完成至少一次类别合并，再点击确定进入预训练构建')
    return false
  }
  if (taskCategoryRows.value.length === 0) {
    ElMessage.warning('请先通过模板或上传选择类别')
    return false
  }
    const allTaskCats = new Set(taskCategoryRows.value.map(r => r.name))
  const usedCats = new Set(
    mergedRelations.value
      .filter(m => m.stage === 'target')
      .map(m => m.taskCategory)
  )
    for (const name of allTaskCats) {
      if (!usedCats.has(name)) {
        ElMessage.warning(`任务类别"${name}"尚未使用，请将全部任务类别合并后再进入下一步`)
      return false
    }
  }
  const subsetIds = collectSubsetIdsByStage('target')
  if (subsetIds.length === 0) {
    ElMessage.warning('目标子集尚未选择任何核心子集')
    return false
  }
  const parentIds = collectParentIdsByStage('target')
  if (parentIds.length === 0) {
    ElMessage.warning('缺少原始数据集，请先选择原始数据集后再试')
    return false
  }
  return true
}

const ensurePretrainStageReady = () => {
    const pretrainMergedCount = mergedRelations.value.filter(m => m.stage === 'pretrain').length
    if (pretrainMergedCount === 0) {
      ElMessage.warning('请至少完成一次类别合并')
    return false
  }
  if (!taskDatasetId.value) {
    ElMessage.warning('缺少任务数据集ID，请先完成目标子集合并')
    return false
  }
  const subsetIds = collectSubsetIdsByStage('pretrain')
  if (subsetIds.length === 0) {
    ElMessage.warning('预训练子集尚未选择任何辅助子集')
    return false
  }
  return true
}

const confirmTripleLayout = async () => {
  if (!showPretrainForm.value) {
    if (!ensureTargetStageReady()) {
      return
    }
    const selectedCoreSubsetIds = collectSubsetIdsByStage('target')
    const labelMapping = buildLabelMappingPayload('target')
    
    const currentTemplate = selectedTemplate.value
    if (!currentTemplate) {
      ElMessage.warning('请先选择模板')
      return
    }
    
    const computedFromName = (() => {
      const base =
        currentTemplate?.displayName ||
        currentTemplate?.name ||
        currentTemplate?.backendName ||
        currentTemplate?.rawName ||
        ''
      const parsed = parseTemplateFileName(base)
      if (parsed) {
        return {
          fieldA: parsed.fieldA,
          fieldB: parsed.fieldB,
          fieldC: parsed.fieldC
        }
      }
      const segments = updateTemplateSegments(currentTemplate)
      if (segments?.sensor || segments?.target) {
        return {
          fieldA: segments?.sensor || '',
          fieldB: segments?.target || '',
          fieldC: currentTemplate?.customName || ''
        }
      }
      return { fieldA: '', fieldB: '', fieldC: '' }
    })()

    const fieldA =
      currentTemplate?.fieldA ||
      currentTemplate?.sensorTypeCode ||
      computedFromName.fieldA ||
      'nature'
    const fieldB =
      currentTemplate?.fieldB ||
      currentTemplate?.targetTypeCode ||
      computedFromName.fieldB ||
      'mix'
    const fieldC =
      currentTemplate?.fieldC ||
      currentTemplate?.customName ||
      computedFromName.fieldC ||
      ''
    
    const username = datasetDetail.value?.taskDataset?.username || ''
    const taskDatasetName = currentTaskDatasetName.value || fieldC || `task_dataset_${Date.now()}`
    
    // 显示加载弹窗
    let loadingInstance = null
    
    try {
      const resolveSensorLabel = (codeOrLabel) => {
        if (!codeOrLabel && codeOrLabel !== 0) return ''
        if (SENSOR_TYPE_MAP[codeOrLabel]) return SENSOR_TYPE_MAP[codeOrLabel]
        if (SENSOR_LABEL_TO_CODE[codeOrLabel]) {
          const code = SENSOR_LABEL_TO_CODE[codeOrLabel]
          return SENSOR_TYPE_MAP[code] || codeOrLabel
        }
        return codeOrLabel
      }

      const resolveTargetLabel = (codeOrLabel) => {
        if (!codeOrLabel && codeOrLabel !== 0) return ''
        if (TARGET_TYPE_MAP[codeOrLabel]) return TARGET_TYPE_MAP[codeOrLabel]
        if (TARGET_LABEL_TO_CODE[codeOrLabel]) {
          const code = TARGET_LABEL_TO_CODE[codeOrLabel]
          return TARGET_TYPE_MAP[code] || codeOrLabel
        }
        return codeOrLabel
      }

      const fieldAzh =
        currentTemplate?.sensorTypeLabel ||
        resolveSensorLabel(fieldA)
      const fieldBzh =
        currentTemplate?.targetTypeLabel ||
        resolveTargetLabel(fieldB)

      const requestData = {
        selectedCoreSubsetIds,
        taskDatasetName,
        username,
        labelMapping,
        sensorType: fieldAzh,
        targetType: fieldBzh,
        sensorTypeCode: fieldA,
        targetTypeCode: fieldB,
        template_info: {
          name: taskDatasetName,
          sensorType: fieldAzh,
          targetType: fieldBzh,
          customName: fieldC,
          supName: 'SRSDD',
          coreName: 'MSAR'
        }
      }
      
      console.debug('[merge-target] 模板字段', { fieldA, fieldB, fieldC, fieldAzh, fieldBzh, selectedTemplate: currentTemplate })
      console.log('界面1最终提交 payload:', JSON.stringify(requestData, null, 2))
      
      // 显示加载弹窗
      loadingInstance = ElLoading.service({
        lock: true,
        text: '正在合并中',
        background: 'rgba(0, 0, 0, 0.7)'
      })
      
      const response = await TaskDatasetMergeService.mergeTarget(requestData)
      const result = response?.data || response
      
      if (response?.code && response.code !== 200 && response.code !== 0) {
        if (loadingInstance) loadingInstance.close()
        ElMessage.error(response?.message || '合并目标子集失败')
        return
      }
      
      // 处理响应数据，确保 template_info 包含正确的字段
      if (result) {
        const nextTemplateInfo = {
          ...(result.template_info || {}),
          sensorType: fieldAzh,
          targetType: fieldBzh,
          customName: fieldC || taskDatasetName,
          supName: result.template_info?.supName || 'SRSDD',
          coreName: result.template_info?.coreName || 'MSAR'
        }
        
        result.template_info = nextTemplateInfo
        
        if (response && typeof response === 'object' && 'data' in response) {
          response.data = {
            ...(response.data || {}),
            template_info: nextTemplateInfo
          }
        }
        
        if (datasetDetail.value?.taskDataset) {
          datasetDetail.value.taskDataset.name = taskDatasetName || datasetDetail.value.taskDataset.name
          datasetDetail.value.taskDataset.sensorType = fieldAzh || datasetDetail.value.taskDataset.sensorType
          datasetDetail.value.taskDataset.targetType = fieldBzh || datasetDetail.value.taskDataset.targetType
        }
      }
      
      console.debug('[merge-target] 响应数据', result)
      console.debug('[merge-target] template_info', result?.template_info)
      
      if (result?.task_dataset_id) {
        taskDatasetId.value = result.task_dataset_id
      }
      if (result?.target_subset_id) {
        targetSubsetId.value = result.target_subset_id
      }
      
      // 关闭加载弹窗
      if (loadingInstance) loadingInstance.close()
      loadingInstance = null
      
      ElMessage.success('合并目标子集成功')
      await prepareForPretrainStage(result)
    } catch (error) {
      // 确保在错误时也关闭加载弹窗
      if (loadingInstance) {
        loadingInstance.close()
        loadingInstance = null
      }
      console.error('合并目标子集失败:', error)
      ElMessage.error('合并目标子集失败: ' + (error.message || '未知错误'))
    }
  } else {
    if (!ensurePretrainStageReady()) {
      return
    }
    try {
      const selectedAuxiliarySubsetIds = collectSubsetIdsByStage('pretrain')
      const labelMapping = buildLabelMappingPayload('pretrain')
      const mergeParams = {
        taskDatasetId: taskDatasetId.value,
        task_dataset_id: taskDatasetId.value,
        selectedAuxiliarySubsetIds,
        selected_auxiliary_subset_ids: selectedAuxiliarySubsetIds,
        labelMapping,
        label_mapping: labelMapping
      }
      console.log('界面2最终提交 payload:', JSON.stringify(mergeParams, null, 2))
      const response = await TaskDatasetMergeService.mergePretrain(mergeParams)
      const result = response?.data || response
      if (response?.code && response.code !== 200 && response.code !== 0) {
        ElMessage.error(response?.message || '合并预训练子集失败')
        return
      }
      if (result?.pretrain_subset_id) {
        pretrainSubsetId.value = result.pretrain_subset_id
      }
      await ElMessageBox.alert('任务数据集合并完成', '提示', {
        confirmButtonText: '确定'
      })
      // 跳转到任务数据集管理界面
      router.push('/taskDatabaseManage')
    } catch (error) {
      console.error('合并预训练子集失败:', error)
      ElMessage.error('合并预训练子集失败: ' + (error.message || '未知错误'))
    }
  }
}

// 监听已合并类别列表的变化，当为空时解锁模板
watch(mergedCategoriesList, (newList) => {
  // 如果已合并类别列表为空，且模板被锁定，则解锁模板
  if (newList.length === 0 && templateLocked.value) {
    templateLocked.value = false
  }
}, { immediate: false })

watch(showPretrainForm, () => {
  updateCurrentParentCategoryEntries()
})

// 生命周期
onMounted(() => {
  loadParentDatasets()
  // 移除 loadTemplates() 调用，改为点击下拉框时加载
})

defineExpose({
  parentDatasets,
  datasetDetail,
  coreCategoryEntries,
  auxiliaryCategoryEntries
})
</script>

<style scoped>
.content-div {
  padding: 16px;
  height: 100vh;
  display: flex;
  flex-direction: column;
  width: 100%;
  box-sizing: border-box;
  overflow: hidden;
  position: relative;
}

.search-div {
  margin-bottom: 16px;
  width: 100%;
}

.create-button-row {
  margin-bottom: 16px;
  width: 100%;
}

.create-interface {
  height: 100%;
  display: flex;
  flex-direction: column;
  width: 100%;
  box-sizing: border-box;
  overflow: hidden;
  min-height: 0;
}

.create-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  width: 100%;
  flex-shrink: 0;
}

.create-title {
  margin: 0;
  color: #333;
  font-size: 18px;
  font-weight: 600;
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-shrink: 0;
}

.triple-columns {
  display: grid;
  grid-template-columns: 1.3fr 260px 1.6fr;
  gap: 24px;
  margin-bottom: 12px;
  flex: 1;
  min-height: 0;
  max-height: calc(100vh - 300px);
  overflow: hidden;
}

.panel {
  border: 1px solid #c0c4cc;
  border-radius: 4px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  background: #fff;
  min-height: 0;
  overflow: hidden;
}

.panel.with-sep {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #333;
  margin-bottom: 8px;
  font-size: 14px;
  min-height: 38px;
}

.original-panel-header {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: nowrap;
  justify-content: space-between;
}

.panel-title-text {
  flex: 0 0 auto;
}

.with-sep {
  border-left: 2px solid #c0c4cc;
}

.task-panel {
  min-width: 240px;
}

.task-scroll {
  flex: 1;
  overflow-y: scroll;
  min-height: 0;
  max-height: 100%;
}

.original-panel {
  min-width: 0;
}

.mid-split {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  flex: 1;
  min-height: 0;
  padding-top: 0;
  overflow: hidden;
}

.mid-left,
.mid-right {
  display: flex;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
}

.mid-left-scroll,
.mid-right-scroll {
  flex: 1;
  overflow-y: scroll;
  min-height: 0;
  max-height: 100%;
}

.mid-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: nowrap;
}

.inline-filters {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex-wrap: nowrap;
}

.inline-filters .filter-select,
.inline-filters .filter-input {
  width: 140px;
}

.inline-filters .sensor-select,
.inline-filters .target-select {
  width: 150px;
}

.inline-filters .name-input {
  width: 180px;
}

.inline-filters .user-input {
  width: 160px;
}

.selected-panel {
  min-width: 0;
}

.merged-categories-section {
  flex: 0 0 calc(50% - 40px);
  min-height: 0;
  display: flex;
  flex-direction: column;
  margin-bottom: 12px;
}

.merging-categories-section {
  flex: 0 0 calc(50% - 40px);
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.merged-scroll {
  flex: 1;
  overflow-y: scroll;
  min-height: 0;
}

.merged-scroll :deep(.el-table) {
  height: auto;
  overflow: visible;
}

.merging-scroll {
  flex: 1;
  overflow-y: scroll;
  min-height: 0;
  max-height: 100%;
}

.merge-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
  flex-shrink: 0;
  padding-top: 8px;
  border-top: 1px solid #e4e7ed;
}

.action-buttons {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 16px;
  flex-shrink: 0;
  padding: 12px 0 8px 0;
  background: #fff;
  border-top: 1px solid #e4e7ed;
  z-index: 10;
  position: sticky;
  bottom: 0;
  width: 100%;
}

.view-merged-scroll,
.view-all-merged-scroll {
  height: 500px;
  overflow-y: scroll;
  overflow-x: hidden;
}

.rename-dialog-body :deep(.el-select),
.rename-dialog-body :deep(.el-input) {
  width: 200px;
}

.rename-dialog-body :deep(.el-form-item) {
  margin-bottom: 12px;
}

.subset-tags-table :deep(.el-table__row) {
  cursor: pointer;
}

.subset-table :deep(.el-table__row.is-current-parent) {
  background-color: #e6f7ff !important;
  font-weight: 600;
}

.task-table :deep(.is-disabled .el-table__cell) {
  color: #c0c4cc;
}

.task-table :deep(.is-disabled .el-checkbox__inner) {
  background-color: #f5f7fa;
  border-color: #e4e7ed;
  opacity: 0.5;
}

.subset-tags-table :deep(.is-disabled .el-table__cell) {
  color: #c0c4cc;
}

.subset-tags-table :deep(.is-disabled .el-checkbox__inner) {
  background-color: #f5f7fa;
  border-color: #e4e7ed;
  opacity: 0.5;
}

::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 4px;
}

::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 4px;
}

::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

.new-template-dialog :deep(.el-dialog) {
  height: 600px;
  display: flex;
  flex-direction: column;
}

.new-template-dialog :deep(.el-dialog__header) {
  flex-shrink: 0;
}

.new-template-dialog :deep(.el-dialog__body) {
  padding: 20px;
  flex: 1;
  overflow: hidden;
  box-sizing: border-box;
}

.new-template-dialog :deep(.el-dialog__footer) {
  flex-shrink: 0;
}

.new-template-dialog-body {
  padding: 0 8px;
  height: 100%;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  overflow: hidden;
}

.new-template-dialog-body .el-form {
  flex-shrink: 0;
}

.type-list-container {
  margin-top: 16px;
  padding: 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  height: 200px;
  overflow-y: auto;
  overflow-x: hidden;
  flex-shrink: 0;
  box-sizing: border-box;
}

.type-item {
  margin-bottom: 8px;
}

.type-item:last-child {
  margin-bottom: 0;
}

.empty-type-hint {
  text-align: center;
  color: #909399;
  padding: 40px 0;
  font-size: 14px;
}
</style>