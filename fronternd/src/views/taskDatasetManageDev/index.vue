<template>
  <div class="content">
    <div class="page-header">
      <div>
        <div class="page-title">任务数据集管理（dev）</div>
        <div class="page-subtitle">定义目标类别、选择测试数据集，并维护类别映射规则。</div>
      </div>
    </div>

    <el-card shadow="never" class="panel">
      <template #header>
        <div class="panel-header">创建新任务（支持多数据集组合）</div>
      </template>

      <el-form label-width="120px" class="task-form">
        <el-form-item label="任务名称">
          <el-input v-model="createForm.name" placeholder="例如：Task_Mixed_Port_Safety" clearable />
        </el-form-item>
        <el-form-item label="任务描述">
          <el-input
            v-model="createForm.desc"
            type="textarea"
            :rows="3"
            placeholder="描述该任务的评测目标..."
          />
        </el-form-item>
        <el-form-item label="目标类别列表">
          <div class="target-schema-editor">
            <el-tag
              v-for="tag in createForm.targetSchema"
              :key="tag"
              closable
              class="schema-editor-tag"
              @close="removeTargetTag(tag)"
            >
              {{ tag }}
            </el-tag>
            <el-input
              v-if="targetInputVisible"
              ref="targetInputRef"
              v-model="targetInputValue"
              class="tag-input"
              size="small"
              @keyup.enter="confirmAddTargetTag"
              @blur="confirmAddTargetTag"
            />
            <el-button v-else size="small" @click="showTargetInput">
              + 添加类别
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="测试数据集">
          <el-select
            v-model="createForm.testDatasets"
            multiple
            filterable
            clearable
            placeholder="选择一个或多个用于测试的数据集"
            style="width: 100%"
          >
            <el-option
              v-for="opt in datasetOptions"
              :key="opt.name"
              :label="opt.name"
              :value="opt.name"
            >
              <div class="dataset-option">
                <span>{{ opt.name }}</span>
                <span class="dataset-option-meta">{{ opt.source }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-alert
            title="注意：创建后请在下方“任务映射编辑器”里继续配置具体类别映射。"
            type="warning"
            :closable="false"
            show-icon
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="createLoading" @click="createTask">
            创建任务基础结构
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="panel">
      <template #header>
        <div class="panel-header">已定义任务管理</div>
      </template>

      <el-empty v-if="!tasks.length" description="暂无任务，请先创建" />
      <div v-else class="task-card-grid">
        <el-card
          v-for="task in tasks"
          :key="task.name"
          shadow="hover"
          class="task-card"
        >
          <template #header>
            <div class="card-header">
              <span>{{ task.name }}</span>
            </div>
          </template>

          <div class="task-info">
            <p><strong>描述：</strong>{{ task.desc || '-' }}</p>
            <div class="task-info-row">
              <strong>目标类别：</strong>
              <div class="info-tags" v-if="task.target_schema?.length">
                <el-tag v-for="cls in task.target_schema" :key="`${task.name}-schema-${cls}`" size="small">
                  {{ cls }}
                </el-tag>
              </div>
              <span v-else>-</span>
            </div>
            <div class="task-info-row">
              <strong>关联数据集：</strong>
              <div class="info-tags" v-if="task.test_datasets?.length">
                <el-tag
                  v-for="ds in task.test_datasets"
                  :key="`${task.name}-dataset-${ds}`"
                  size="small"
                  type="success"
                >
                  {{ ds }}
                </el-tag>
              </div>
              <span v-else>-</span>
            </div>
            <p v-if="task.updated_by || task.updated_time">
              <strong>最近更新：</strong>{{ task.updated_by || '-' }} {{ task.updated_time || '' }}
            </p>
          </div>

          <template #footer>
            <div class="task-card-footer">
              <el-button type="primary" plain @click="editTask(task)">编辑</el-button>
              <el-button type="danger" plain @click="deleteTask(task)">删除</el-button>
            </div>
          </template>
        </el-card>
      </div>
    </el-card>

    <el-card shadow="never" class="panel mapping-editor-anchor">
      <template #header>
        <div class="panel-header">任务映射编辑器</div>
      </template>

      <el-form label-width="120px">
        <el-form-item label="选择任务">
          <el-select
            v-model="selectedTaskName"
            placeholder="请选择要配置映射的任务"
            style="width: 420px"
            clearable
          >
            <el-option
              v-for="task in tasks"
              :key="task.name"
              :label="task.name"
              :value="task.name"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <el-empty v-if="!selectedTask" description="请选择一个任务进行映射配置" />

      <template v-else>
        <div class="schema-line">
          <span class="schema-label">当前目标类别：</span>
          <el-tag
            v-for="cls in selectedTask.target_schema || []"
            :key="cls"
            class="schema-tag"
          >
            {{ cls }}
          </el-tag>
        </div>

        <el-alert
          v-if="missingDatasets.length"
          type="warning"
          :closable="false"
          show-icon
          class="missing-alert"
          :title="`以下数据集当前未在可用列表中找到：${missingDatasets.join('、')}`"
        />

        <div v-for="datasetName in selectedTask.test_datasets || []" :key="datasetName" class="dataset-panel">
          <div class="dataset-panel-title">数据集源：{{ datasetName }}</div>

          <el-alert
            v-if="!datasetClassMap[datasetName] || !datasetClassMap[datasetName].length"
            title="无法提取该数据集类别，请确认数据集信息完整且 class_list 可解析。"
            type="warning"
            :closable="false"
            show-icon
          />

          <div v-else class="mapping-grid">
            <div
              v-for="cls in datasetClassMap[datasetName]"
              :key="`${datasetName}-${cls}`"
              class="mapping-item"
            >
              <div class="mapping-from">原：{{ cls }}</div>
              <el-select
                v-model="mappingEditor[datasetName][cls]"
                placeholder="请选择目标类别"
                class="mapping-select"
              >
                <el-option label="(忽略/不使用)" value="" />
                <el-option
                  v-for="target in selectedTask.target_schema || []"
                  :key="`${datasetName}-${cls}-${target}`"
                  :label="target"
                  :value="target"
                />
              </el-select>
            </div>
          </div>
        </div>

        <div class="save-bar">
          <el-button type="primary" :loading="saveLoading" @click="saveMappingRules">
            更新任务映射规则
          </el-button>
        </div>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { OriginalDatasetService, TaskDatasetDevService } from '@/api/api'

const datasetOptions = ref([])
const tasks = ref([])
const selectedTaskName = ref('')
const mappingEditor = ref({})
const targetInputRef = ref()
const targetInputVisible = ref(false)
const targetInputValue = ref('')

const createLoading = ref(false)
const saveLoading = ref(false)

const createForm = ref({
  name: '',
  desc: '',
  targetSchema: ['Large_Vehicle', 'Person', 'Ship'],
  testDatasets: []
})

const selectedTask = computed(() => {
  return tasks.value.find(item => item.name === selectedTaskName.value) || null
})

const datasetClassMap = computed(() => {
  const out = {}
  datasetOptions.value.forEach(item => {
    out[item.name] = Array.isArray(item.classes) ? item.classes : []
  })
  return out
})

const missingDatasets = computed(() => {
  if (!selectedTask.value) return []
  return (selectedTask.value.test_datasets || []).filter(name => !datasetClassMap.value[name])
})

function safeParse(objOrStr) {
  if (!objOrStr) return {}
  if (typeof objOrStr === 'object') return objOrStr
  try {
    return JSON.parse(objOrStr)
  } catch {
    return {}
  }
}

function extractClasses(classListRaw) {
  const obj = safeParse(classListRaw)
  return Object.keys(obj || {})
}

async function loadDatasets() {
  const [raw, rawExternal] = await Promise.all([
    OriginalDatasetService.list({ page: 1, size: 1000, sortBy: 'created_time', order: 'desc' }),
    OriginalDatasetService.listExternal()
  ])

  const obj = typeof raw === 'string' ? JSON.parse(raw) : raw
  const extObj = typeof rawExternal === 'string' ? JSON.parse(rawExternal) : rawExternal
  const dataNode = obj?.data ?? obj
  const extNode = extObj?.data ?? extObj

  const items = Array.isArray(dataNode?.items) ? dataNode.items : Array.isArray(dataNode) ? dataNode : []
  const extItems = Array.isArray(extNode) ? extNode : []

  const merged = [
    ...items.map(item => ({
      name: item.name,
      source: 'CVAT',
      classes: extractClasses(item.class_list ?? item.classList)
    })),
    ...extItems
      .filter(item => !item.error)
      .map(item => ({
        name: item.name,
        source: '外部导入',
        classes: extractClasses(item.class_list ?? item.classList)
      }))
  ]

  datasetOptions.value = merged.sort((a, b) => a.name.localeCompare(b.name))
}

async function loadTasks() {
  const res = await TaskDatasetDevService.listTasks()
  if (res?.code !== 0) {
    throw new Error(res?.msg || '读取任务列表失败')
  }
  tasks.value = Array.isArray(res?.data) ? res.data : []
  if (selectedTaskName.value && !tasks.value.some(item => item.name === selectedTaskName.value)) {
    selectedTaskName.value = ''
  }
  if (!selectedTaskName.value && tasks.value.length) {
    selectedTaskName.value = tasks.value[0].name
  }
}

function initMappingEditor(task) {
  if (!task) {
    mappingEditor.value = {}
    return
  }
  const next = {}
  const saved = task.mapping_rules || {}
  ;(task.test_datasets || []).forEach(datasetName => {
    const classes = datasetClassMap.value[datasetName] || []
    const savedOne = saved?.[datasetName] || {}
    next[datasetName] = {}
    classes.forEach(cls => {
      if (savedOne[cls]) {
        next[datasetName][cls] = savedOne[cls]
      } else if ((task.target_schema || []).includes(cls)) {
        next[datasetName][cls] = cls
      } else {
        next[datasetName][cls] = ''
      }
    })
  })
  mappingEditor.value = next
}

watch([selectedTask, datasetClassMap], ([task]) => {
  initMappingEditor(task)
}, { immediate: true })

function showTargetInput() {
  targetInputVisible.value = true
  setTimeout(() => {
    targetInputRef.value?.focus?.()
  }, 0)
}

function confirmAddTargetTag() {
  const value = String(targetInputValue.value || '').trim()
  if (value && !createForm.value.targetSchema.includes(value)) {
    createForm.value.targetSchema.push(value)
  }
  targetInputVisible.value = false
  targetInputValue.value = ''
}

function removeTargetTag(tag) {
  createForm.value.targetSchema = createForm.value.targetSchema.filter(item => item !== tag)
}

async function createTask() {
  const name = String(createForm.value.name || '').trim()
  const targetSchema = (createForm.value.targetSchema || []).map(item => String(item || '').trim()).filter(Boolean)

  if (!name) {
    ElMessage.error('请输入任务名称')
    return
  }
  if (!targetSchema.length) {
    ElMessage.error('请填写至少一个目标类别')
    return
  }
  if (!createForm.value.testDatasets.length) {
    ElMessage.error('请至少选择一个数据集作为测试源')
    return
  }

  createLoading.value = true
  try {
    const res = await TaskDatasetDevService.createTask({
      name,
      desc: String(createForm.value.desc || '').trim(),
      targetSchema,
      testDatasets: createForm.value.testDatasets
    })
    if (res?.code !== 0) {
      ElMessage.error(res?.msg || '创建失败')
      return
    }
    ElMessage.success('任务创建成功')
    tasks.value = Array.isArray(res?.data) ? res.data : []
    selectedTaskName.value = name
    createForm.value = {
      name: '',
      desc: '',
      targetSchema: ['Large_Vehicle', 'Person', 'Ship'],
      testDatasets: []
    }
  } catch (e) {
    ElMessage.error(`创建失败：${e?.message || e}`)
  } finally {
    createLoading.value = false
  }
}

async function deleteTask(task) {
  try {
    await ElMessageBox.confirm(`确定删除任务 "${task.name}" 吗？`, '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
    const res = await TaskDatasetDevService.deleteTask({ name: task.name })
    if (res?.code !== 0) {
      ElMessage.error(res?.msg || '删除失败')
      return
    }
    ElMessage.success('删除成功')
    tasks.value = Array.isArray(res?.data) ? res.data : []
    if (selectedTaskName.value === task.name) {
      selectedTaskName.value = ''
    }
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(`删除失败：${e?.message || e}`)
    }
  }
}

function editTask(task) {
  if (!task?.name) return
  selectedTaskName.value = task.name
  const editor = document.querySelector('.mapping-editor-anchor')
  if (editor) {
    editor.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

async function saveMappingRules() {
  if (!selectedTask.value) {
    ElMessage.warning('请先选择一个任务')
    return
  }

  const normalized = {}
  Object.keys(mappingEditor.value || {}).forEach(datasetName => {
    const inner = mappingEditor.value[datasetName] || {}
    const saved = {}
    Object.keys(inner).forEach(cls => {
      if (inner[cls]) {
        saved[cls] = inner[cls]
      }
    })
    normalized[datasetName] = saved
  })

  saveLoading.value = true
  try {
    const res = await TaskDatasetDevService.updateMapping({
      name: selectedTask.value.name,
      mappingRules: normalized
    })
    if (res?.code !== 0) {
      ElMessage.error(res?.msg || '保存失败')
      return
    }
    ElMessage.success('映射规则已保存')
    tasks.value = Array.isArray(res?.data) ? res.data : []
  } catch (e) {
    ElMessage.error(`保存失败：${e?.message || e}`)
  } finally {
    saveLoading.value = false
  }
}

onMounted(async () => {
  const [datasetRes, taskRes] = await Promise.allSettled([loadDatasets(), loadTasks()])
  if (taskRes.status === 'rejected') {
    ElMessage.error(`任务定义加载失败：${taskRes.reason?.message || taskRes.reason || '请确认后端已重启并加载新接口'}`)
  }
  if (datasetRes.status === 'rejected') {
    ElMessage.warning(`数据集列表加载失败：${datasetRes.reason?.message || datasetRes.reason}`)
  }
})
</script>

<style scoped>
.content {
  padding: 10px;
  background-color: #f5f7fa;
}

.page-header {
  display: flex;
  justify-content: flex-start;
  align-items: center;
  margin-bottom: 12px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.page-subtitle {
  margin-top: 4px;
  color: #606266;
  font-size: 13px;
}

.panel {
  margin-bottom: 12px;
  border-radius: 8px;
}

.panel-header {
  font-weight: 600;
  color: #303133;
}

.task-form {
  max-width: 920px;
}

.target-schema-editor {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  min-height: 32px;
}

.schema-editor-tag {
  margin-right: 0;
}

.tag-input {
  width: 140px;
}

.dataset-option {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.dataset-option-meta {
  color: #909399;
  font-size: 12px;
}

.task-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 16px;
}

.task-card {
  transition: box-shadow 0.25s ease, transform 0.25s ease;
}

.task-card:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  transform: translateY(-2px);
}

.card-header {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.task-info p {
  margin: 0 0 8px;
  color: #303133;
}

.task-info-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 0 0 8px;
  color: #303133;
}

.info-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.task-card-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.schema-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.schema-label {
  color: #606266;
}

.schema-tag {
  margin-right: 0;
}

.missing-alert {
  margin-bottom: 12px;
}

.dataset-panel {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 12px;
  background: #fff;
}

.dataset-panel-title {
  font-weight: 600;
  margin-bottom: 12px;
  color: #303133;
}

.mapping-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.mapping-item {
  border: 1px solid #f0f2f5;
  border-radius: 6px;
  padding: 10px;
  background: #fafafa;
}

.mapping-from {
  margin-bottom: 8px;
  color: #303133;
  font-weight: 500;
}

.mapping-select {
  width: 100%;
}

.save-bar {
  margin-top: 16px;
}

.content :deep(.el-card) {
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.08);
}
</style>
