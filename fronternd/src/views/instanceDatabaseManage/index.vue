<!-- instanceDatabase/list/index.vue -->
<template>
  <div class="content-div">
    <div class="search-div">
      <!-- 创建按钮单独一行 -->
      <!--
      <div class="create-button-row">
        <el-button type="primary" @click="$router.push('/instanceDatabase/create')" size="small">
          <el-text size="small" class="text-white">创建实例数据集</el-text>
        </el-button>
      </div>
      -->
      <!-- 搜索条件区域 -->
      <div class="search-conditions flex-start">
        <el-input v-model="searchConditions.fatherName" placeholder="所属任务名" size="small" style="width: 200px;"></el-input>
        <el-input v-model="searchConditions.name" placeholder="数据集名称" size="small" style="width: 200px;"></el-input>
        <el-select v-model="searchConditions.sensorType" placeholder="传感器类型" size="small" clearable style="width: 150px;">
          <el-option v-for="type in sensorTypeOptions" :key="type" :label="type" :value="type"></el-option>
        </el-select>
        <el-select v-model="searchConditions.targetType" placeholder="目标类型" size="small" clearable style="width: 150px;">
          <el-option v-for="type in targetTypeOptions" :key="type" :label="type" :value="type"></el-option>
        </el-select>
        <el-select v-model="searchConditions.username" placeholder="创建用户" size="small" clearable style="width: 150px;">
          <el-option v-for="user in createUserList" :key="user" :label="user" :value="user"></el-option>
        </el-select>
        <el-button type="primary" @click="applyMainFilters" size="small">搜索</el-button>
        <el-button @click="resetMainFilters" size="small">重置</el-button>
      </div>
    </div>
    <div class="table-container">
      <el-table 
        class="my-table" 
        :data="paginatedTableData" 
        stripe 
        size="small" 
        v-loading="tableLoading"
        style="width: 100%">
        <el-table-column type="index" label="序号" align="center" width="60" />
        <el-table-column prop="fatherName" label="所属任务名" align="center" width="90" />
        <el-table-column prop="name" label="数据集名称" align="center" width="90" />
        <el-table-column prop="sensorType" label="传感器类型" align="center" width="90" />
        <el-table-column prop="targetType" label="目标类型" align="center" width="90" />
        <el-table-column prop="classNum" label="类别数" align="center" width="70" />
        <el-table-column prop="classList" label="类别名称" align="center" width="200">
          <template #default="{ row }">
            <div class="category-tags-container">
              <el-tag
                v-for="(item, index) in getCategoryList(row.classList)"
                :key="`${row.id}-${index}`"
                size="small"
                :type="getTagType(index)"
                style="margin: 2px; white-space: nowrap;"
              >
                {{ item.name }}: {{ item.count }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="imgNum" label="图片数" align="center" width="70" />
        <el-table-column prop="annoNum" label="样本数" align="center" width="70" />
        <el-table-column prop="configList" label="预处理描述" align="center" width="400" show-overflow-tooltip>
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
        <el-table-column prop="username" label="创建用户" align="center" width="120" />
        <!-- 示例按钮 -->
        <el-table-column label="示例" align="center" width="80">
          <template #default="{ row }">
            <el-button @click="openPreview(row)" link size="small">
              <el-tag size="small" type="info" class="fontSpan">查看</el-tag>
            </el-button>
          </template>
        </el-table-column>

       <!-- 删除操作列 -->
       <!-- 删除操作列 -->
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
    <div class="pagination-container flex-end">
      <el-pagination 
        background 
        size="small" 
        v-model:current-page="currentPage" 
        v-model:page-size="currentSize"
        :page-sizes="[5, 10, 20, 30, 40, 50]" 
        layout="total, sizes, prev, pager, next, jumper" 
        :total="filteredTableData.length"
        @size-change="handlePageChange" 
        @current-change="handlePageChange" />
    </div>

    <!-- 实例数据集“示例”预览弹窗（带 DOTA 蓝框） -->
    <el-dialog
      v-model="previewDialogVisible"
      :title="previewRow ? `示例预览 - ${previewRow.name}` : '示例预览'"
      width="60%"
      top="5vh"
    >
      <!-- 加载中 -->
      <div v-if="previewLoading" class="image-preview-dialog">
        <div style="padding: 24px; text-align: center; color: #909399;">
          图片加载中…
        </div>
      </div>
      <!-- 无数据 -->
      <div v-else-if="!previewGroups.length" class="image-preview-dialog">
        <div style="padding: 24px; text-align: center; color: #909399;">
          暂无可展示的示例图片
        </div>
      </div>
      <!-- 有数据时显示 -->
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
              <!-- 已有标注：用 SVG 叠加多边形 -->
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
              <!-- 暂无标注：先出图，再异步拉取 -->
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
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { InstanceDatasetService } from '@/api/api.js'
import { request } from '@/api/axios'

// ========== 响应式数据（完全保留原有，仅删除创建页相关）==========
const tableData = ref([])
const rawTableData = ref([])
const tableLoading = ref(false)
const currentPage = ref(1)
const currentSize = ref(10)
const total = ref(0)

// ========== 主列表搜索条件 ==========
const searchConditions = reactive({
  name: '',
  fatherName: '',
  sensorType: '',
  targetType: '',
  username: ''
})

const sensorTypeOptions = ref([])
const targetTypeOptions = ref([])
const createUserList = ref([])

// ====== 预览相关 ======
const previewDialogVisible = ref(false)
const previewRow = ref(null)
const previewGroups = ref([])
const previewLoading = ref(false)
const objectsMeta = ref({})

// ========== 计算属性 ==========
const filteredTableData = computed(() => tableData.value)
const paginatedTableData = computed(() => {
  const start = (currentPage.value - 1) * currentSize.value
  const end = start + currentSize.value
  return filteredTableData.value.slice(start, end)
})

// ========== 方法 ==========
const getTagType = (index) => {
  const types = ['primary', 'success', 'warning', 'danger', 'info']
  return types[index % types.length]
}

const getCategoryList = (categoryList) => {
  if (!categoryList) return []
  try {
    let parsed = categoryList
    if (typeof categoryList === 'string') {
      parsed = JSON.parse(categoryList)
    }
    if (typeof parsed !== 'object' || Array.isArray(parsed)) {
      return []
    }
    return Object.entries(parsed).map(([name, count]) => ({
      name,
      count: parseInt(count, 10)
    }))
  } catch (e) {
    console.error('解析 classList 失败:', categoryList, e)
    return []
  }
}

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
}

const applyMainFilters = () => {
  if (!rawTableData.value || rawTableData.value.length === 0) {
    tableData.value = []
    return
  }
  tableData.value = rawTableData.value.filter(item => {
    const matchesName = !searchConditions.name || item.name?.toLowerCase().includes(searchConditions.name.toLowerCase())
    const matchesFather = !searchConditions.fatherName || item.fatherName?.toLowerCase().includes(searchConditions.fatherName.toLowerCase())
    const matchesSensor = !searchConditions.sensorType || item.sensorType === searchConditions.sensorType
    const matchesTarget = !searchConditions.targetType || item.targetType === searchConditions.targetType
    const matchesUser = !searchConditions.username || item.username === searchConditions.username
    return matchesName && matchesFather && matchesSensor && matchesTarget && matchesUser
  })
  currentPage.value = 1
}
const resetMainFilters = () => {
  searchConditions.name = ''
  searchConditions.fatherName = ''
  searchConditions.sensorType = ''
  searchConditions.targetType = ''
  searchConditions.username = ''
  tableData.value = [...rawTableData.value]
  currentPage.value = 1
}

const loadInstanceDatasets = async () => {
  tableLoading.value = true
  try {
    const response = await InstanceDatasetService.queryList()
    let data = []
    if (Array.isArray(response)) {
      data = response
    } else if (response && Array.isArray(response.data)) {
      data = response.data
    } else {
      console.warn('响应格式异常:', response)
      ElMessage.warning('数据加载异常')
    }
    tableData.value = data
    rawTableData.value = data
    updateCreateUserList()
    const sensors = [...new Set(data.map(i => i.sensorType).filter(Boolean))]
    const targets = [...new Set(data.map(i => i.targetType).filter(Boolean))]
    sensorTypeOptions.value = sensors
    targetTypeOptions.value = targets
  } catch (error) {
    console.error('加载实例数据集失败:', error)
    ElMessage.error('数据加载失败: ' + error.message)
    tableData.value = []
  } finally {
    tableLoading.value = false
  }
}

// ========== 删除功能 ==========
const handleDelete = async (id) => {
  console.log('handleDelete called with id:', id);
  if (!id || id <= 0) {
    ElMessage.warning('无效的数据集ID');
    return;
  }
  try {
    const response = await InstanceDatasetService.deleteById(id);
    console.log('Delete response:', response);

    //  根据你后端的实际返回判断成功（code === 0）
    if (response && response.code === 0) {
      ElMessage.success(response.data || '删除成功');
      await loadInstanceDatasets(); // 刷新列表
    } else {
      ElMessage.error(response?.msg || '删除失败');
    }
  } catch (error) {
    console.error('Delete error:', error);
    ElMessage.error('删除失败：' + (error?.response?.data?.msg || error.message || '未知错误'));
  }
};

const updateCreateUserList = () => {
  const users = [...new Set(tableData.value.map(item => item.username).filter(Boolean))]
  createUserList.value = users
}

const openPreview = async (row) => {
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

const handlePageChange = () => {}

onMounted(() => {
  loadInstanceDatasets()
})
</script>

<style scoped>
/* 完全保留原始样式 */
.content-div {
  padding: 16px;
  height: 100%;
  display: flex;
  flex-direction: column;
  width: 100%;
  box-sizing: border-box;
}
.search-div {
  margin-bottom: 16px;
  width: 100%;
}
.create-button-row {
  margin-bottom: 16px;
  width: 100%;
}
.search-conditions {
  width: 100%;
}
.table-container {
  flex: 1;
  overflow: auto;
  margin-bottom: 16px;
  width: 100%;
  box-sizing: border-box;
}
.pagination-container {
  margin-top: 16px;
  width: 100%;
  box-sizing: border-box;
}
.flex-start {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
.flex-end {
  display: flex;
  justify-content: flex-end;
  width: 100%;
}
.my-table {
  height: 100%;
  width: 100% !important;
}
:deep(.el-table .el-table__cell) {
  padding: 4px 0;
}
:deep(.el-tag) {
  border: none;
}
.my-table :deep(.el-table__header) {
  background-color: #f5f7fa;
}
.my-table :deep(.el-table__header th) {
  background-color: #f5f7fa;
  color: #606266;
  font-weight: 600;
}
.my-table :deep(.el-table--striped .el-table__body tr.el-table__row--striped td) {
  background-color: #fafafa;
}
.my-table :deep(.el-table__body tr:hover > td) {
  background-color: #f5f7fa;
}
.my-table :deep(.el-table__cell) {
  padding: 8px 0;
  text-align: center;
}
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
</style>