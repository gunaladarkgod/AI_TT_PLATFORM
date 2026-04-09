<template>
  <div class="content-div">
    <!-- 主体：左右布局 -->
    <div class="split-layout">
      <!-- 左侧：任务数据集列表 -->
      <div class="left-panel">
        <h4>任务数据集（请点选）</h4>
        <el-table
          class="my-table"
          :data="taskDatasetList"
          stripe
          size="small"
          @row-click="handleTaskRowClick"
          :row-class-name="getRowClassName"
          v-el-height-adaptive-table="{ bottomOffset: 70, isUse: true }"
        >
          <!-- 选择列：单选按钮 -->
          <el-table-column label="选择" width="60" align="center">
            <template #default="{ row }">
              <el-radio
                v-model="selectedTaskId"
                :label="row.id"
                @change="handleTaskSelect(row)"
                style="margin: 0;"
              />
            </template>
          </el-table-column>

          <el-table-column type="index" label="序号" width="60" align="center" />
          <el-table-column prop="name" label="数据集名称" align="center" />
          <el-table-column prop="sensorType" label="传感器类型" align="center" />
          <el-table-column prop="targetType" label="目标类型" align="center" />
          <el-table-column prop="coreClassNum" label="类别数" align="center" />
          <!-- 类别名称 → 彩色标签 -->
          <el-table-column label="类别名称" align="center">
            <template #default="{ row }">
              <div class="category-tags-container">
                <el-tag
                  v-for="(item, index) in getTaskCategoryList(row.coreClassList)"
                  :key="`${row.id}-${index}`"
                  size="small"
                  :type="getTagType(index)"
                  style="margin: 2px; white-space: nowrap;"
                >
                  {{ item.name }}{{ item.count != null ? `: ${item.count}` : '' }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="username" label="创建用户" align="center" />
          <el-table-column label="操作" align="center" width="120">
            <template #default="{ row }">
              <el-button size="small" @click.stop="openTrainTestDrawer(row)">
                训测划分
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <!-- 分页 -->
        <div class="flex-end mt-12">
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

      <!-- 右侧：实例数据集列表 -->
      <div class="right-panel">
        <h4>实例数据集（{{ selectedTask?.name || '未选择' }}）</h4>
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
    </div>

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
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { TaskDatasetService, InstanceDatasetService } from '@/api/api'
import { useRouter } from 'vue-router'
import { request } from '@/api/axios'

const router = useRouter()

// 左侧任务数据集
const taskDatasetList = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const selectedTask = ref(null)
// ✅ 修改：使用 selectedTaskId (number 类型)
const selectedTaskId = ref(null)

// 右侧实例数据集
const instanceDatasetList = ref([])
const instanceLoading = ref(false)

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

// 新增：用于左侧类别名称解析
const getTagType = (index) => {
  const types = ['primary', 'success', 'warning', 'danger', 'info']
  return types[index % types.length]
}

const getTaskCategoryList = (coreClassList) => {
  if (!coreClassList || coreClassList === '-' || coreClassList === '[]') return []

  // 情况1: JSON 对象字符串，如 '{"ship":120}'
  if (typeof coreClassList === 'string' && coreClassList.trim().startsWith('{')) {
    try {
      const parsed = JSON.parse(coreClassList)
      if (typeof parsed === 'object' && !Array.isArray(parsed)) {
        return Object.entries(parsed).map(([name, count]) => ({
          name,
          count: parseInt(count, 10)
        }))
      }
    } catch (e) {
      console.warn('解析 coreClassList 失败（对象格式）:', coreClassList, e)
    }
  }

  // 情况2: JSON 数组字符串，如 '["ship","plane"]'
  if (typeof coreClassList === 'string') {
    try {
      const parsed = JSON.parse(coreClassList)
      if (Array.isArray(parsed)) {
        return parsed.map(name => ({ name, count: null }))
      }
    } catch (e) {
      // 继续尝试逗号分隔
    }
  }

  // 情况3: 逗号分隔字符串，如 'ship,plane'
  if (typeof coreClassList === 'string') {
    const names = coreClassList.split(',').map(s => s.trim()).filter(Boolean)
    if (names.length > 0) {
      return names.map(name => ({ name, count: null }))
    }
  }

  // 情况4: 已是对象（非字符串）
  if (typeof coreClassList === 'object' && !Array.isArray(coreClassList)) {
    return Object.entries(coreClassList).map(([name, count]) => ({
      name,
      count: parseInt(count, 10)
    }))
  }

  // 默认：当作单个类别
  return [{ name: String(coreClassList), count: null }]
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

// ✅ 点击任务数据集行（支持选中/取消）
const handleTaskRowClick = (row) => {
  if (selectedTaskId.value === row.id) {
    selectedTask.value = null
    selectedTaskId.value = null
    instanceDatasetList.value = []
    return
  }
  handleTaskSelect(row)
}

// ✅ 处理单选按钮选择
const handleTaskSelect = (row) => {
  selectedTask.value = row
  selectedTaskId.value = row.id // number 类型
  fetchInstanceList(row.name)
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
})
</script>

<style scoped lang="scss">
.content-div {
  padding: 10px;
}
.split-layout {
  display: flex;
  gap: 20px;
  margin-top: 12px;
  height: calc(100vh - 180px);
}
.left-panel {
  flex: 1;
  overflow-y: auto;
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

/* 隐藏单选按钮的标签文本 */
::v-deep .el-radio__label {
  display: none !important;
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

/* ========== 类别标签样式 ========== */
.category-tags-container {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  max-width: 100%;
  overflow: hidden;
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