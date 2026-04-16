<template>
  <div class="page-shell">
    <div class="doc-layout">
      <aside class="doc-left">
        <div class="doc-left-placeholder"></div>
      </aside>

      <main class="doc-main">
        <section id="page-top" class="doc-section page-hero">
          <div class="page-title">任务数据集管理（dev）</div>
          <div class="page-subtitle">定义目标类别、选择测试数据集，并维护类别映射规则。</div>
        </section>

        <section id="create-task" class="doc-section section-block">
          <div class="section-heading">
            <h2 class="section-title">
              创建任务
              <a class="section-link" href="#create-task" @click.prevent="scrollToAnchor('#create-task')">#</a>
            </h2>
            <p class="section-desc">定义任务基础信息、目标类别和测试数据集组合。</p>
          </div>
          <div class="section-body">
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
                  title="注意：创建后请在下方“任务映射”里继续配置具体类别映射。"
                  type="warning"
                  :closable="false"
                  show-icon
                />
              </el-form-item>
              <el-form-item class="action-form-item">
                <el-button type="primary" :loading="createLoading" @click="createTask">
                  创建任务基础结构
                </el-button>
              </el-form-item>
            </el-form>
          </div>
        </section>

        <section id="task-list" class="doc-section section-block">
          <div class="section-heading">
            <h2 class="section-title">
              任务管理
              <a class="section-link" href="#task-list" @click.prevent="scrollToAnchor('#task-list')">#</a>
            </h2>
            <p class="section-desc">查看已有任务，并执行编辑或删除操作。</p>
          </div>
          <div class="section-body">
            <el-empty v-if="!tasks.length" description="暂无任务，请先创建" />
            <div v-else class="task-card-grid">
              <div
                v-for="task in tasks"
                :key="task.name"
                class="task-card"
              >
                <div class="card-header">
                  <span>{{ task.name }}</span>
                </div>

                <div class="task-card-body task-info">
                  <div class="task-info-row">
                    <div class="task-info-label">描述：</div>
                    <div class="task-info-value">{{ task.desc || '-' }}</div>
                  </div>
                  <div class="task-info-row">
                    <div class="task-info-label">目标类别：</div>
                    <div class="task-info-value">
                      <div class="info-tags" v-if="task.target_schema?.length">
                        <el-tag v-for="cls in task.target_schema" :key="`${task.name}-schema-${cls}`" size="small">
                          {{ cls }}
                        </el-tag>
                      </div>
                      <span v-else>-</span>
                    </div>
                  </div>
                  <div class="task-info-row">
                    <div class="task-info-label">关联数据集：</div>
                    <div class="task-info-value">
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
                  </div>
                  <div class="task-info-row" v-if="task.updated_by || task.updated_time">
                    <div class="task-info-label">最近更新：</div>
                    <div class="task-info-value">{{ task.updated_by || '-' }} {{ task.updated_time || '' }}</div>
                  </div>
                  <div class="task-info-row">
                    <div class="task-info-label">状态：</div>
                    <div class="task-info-value">
                      <el-tag :type="statusTagType(task.status_code)">
                        {{ task.status_text || '未导出' }}
                      </el-tag>
                    </div>
                  </div>
                  <div class="task-info-row" v-if="task.last_export_time">
                    <div class="task-info-label">最近导出：</div>
                    <div class="task-info-value">{{ task.last_export_by || '-' }} {{ task.last_export_time }}</div>
                  </div>
                </div>

                <div class="task-card-footer">
                  <div class="task-card-footer-left">
                    <el-button plain @click="openMappingView(task)">查看映射</el-button>
                  </div>
                  <div class="task-card-footer-right">
                    <el-button
                      type="primary"
                      :loading="exportLoadingTaskName === task.name"
                      @click="exportTask(task)"
                    >
                      {{ exportActionText(task.status_code) }}
                    </el-button>
                    <el-button type="primary" plain @click="editTask(task)">编辑</el-button>
                    <el-button type="danger" plain @click="deleteTask(task)">删除</el-button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section id="mapping-editor" class="doc-section section-block mapping-editor-anchor">
          <div class="section-heading">
            <h2 class="section-title">
              任务映射
              <a class="section-link" href="#mapping-editor" @click.prevent="scrollToAnchor('#mapping-editor')">#</a>
            </h2>
            <p class="section-desc">将各数据集原始类别映射到当前任务的目标类别。</p>
          </div>
          <div class="section-body">
            <el-empty v-if="!selectedTask && !tasks.length" description="暂无任务可供映射配置" />
            <template v-else>
              <div class="mapping-summary">
                <div class="mapping-summary-row">
                  <div class="mapping-summary-label">选择任务</div>
                  <div class="mapping-summary-value">
                    <el-select
                      v-model="selectedTaskName"
                      placeholder="请选择要配置映射的任务"
                      style="width: 100%; max-width: 640px"
                      clearable
                    >
                      <el-option
                        v-for="task in tasks"
                        :key="task.name"
                        :label="task.name"
                        :value="task.name"
                      />
                    </el-select>
                  </div>
                </div>

                <div class="mapping-summary-row" v-if="selectedTask">
                  <div class="mapping-summary-label">当前目标类别</div>
                  <div class="mapping-summary-value">
                    <el-tag
                      v-for="cls in selectedTask.target_schema || []"
                      :key="cls"
                      class="schema-tag"
                    >
                      {{ cls }}
                    </el-tag>
                  </div>
                </div>
              </div>

              <el-empty v-if="!selectedTask" description="请选择一个任务进行映射配置" />
              <template v-else>
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

                  <div v-else class="dataset-panel-body dataset-config-block">
                    <div class="dataset-config-row">
                      <div class="dataset-config-label">选择使用标签</div>
                      <div class="dataset-config-value">
                        <el-select
                          v-model="datasetSelectedClasses[datasetName]"
                          multiple
                          filterable
                          collapse-tags-tooltip
                          placeholder="请选择该数据集需要参与映射的标签"
                          style="width: 100%"
                        >
                          <el-option
                            v-for="cls in datasetClassMap[datasetName]"
                            :key="`${datasetName}-selected-${cls}`"
                            :label="cls"
                            :value="cls"
                          />
                        </el-select>
                      </div>
                    </div>

                    <div v-if="!(datasetSelectedClasses[datasetName] || []).length" class="mapping-empty">
                      请先为“{{ datasetName }}”选择需要使用的标签，再进行映射配置。
                    </div>

                    <div v-else class="mapping-grid">
                      <div
                        v-for="cls in datasetSelectedClasses[datasetName]"
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
                </div>

                <div class="save-bar">
                  <el-button type="primary" :loading="saveLoading" @click="saveMappingRules">
                    更新任务映射规则
                  </el-button>
                </div>
              </template>
            </template>
          </div>
        </section>
      </main>

      <aside class="doc-right">
        <div class="anchor-wrapper">
          <div class="anchor-title">Contents</div>
          <el-anchor :offset="90" class="page-anchor" @click="handleAnchorClick">
            <el-anchor-link href="#create-task" title="创建任务" />
            <el-anchor-link href="#task-list" title="任务管理" />
            <el-anchor-link href="#mapping-editor" title="任务映射" />
          </el-anchor>
        </div>
      </aside>
    </div>
    <el-backtop :right="36" :bottom="40" />

    <el-dialog
      v-model="editDialogVisible"
      title="编辑任务"
      width="680px"
      draggable
      :close-on-click-modal="false"
    >
      <el-form label-width="110px" class="task-form">
        <el-form-item label="任务名称">
          <el-input v-model="editForm.name" placeholder="请输入任务名称" clearable />
        </el-form-item>
        <el-form-item label="任务描述">
          <el-input
            v-model="editForm.desc"
            type="textarea"
            :rows="3"
            placeholder="描述该任务的评测目标..."
          />
        </el-form-item>
        <el-form-item label="目标类别列表">
          <div class="target-schema-editor">
            <el-tag
              v-for="tag in editForm.targetSchema"
              :key="`edit-${tag}`"
              closable
              class="schema-editor-tag"
              @close="removeEditTargetTag(tag)"
            >
              {{ tag }}
            </el-tag>
            <el-input
              v-if="editTargetInputVisible"
              ref="editTargetInputRef"
              v-model="editTargetInputValue"
              class="tag-input"
              size="small"
              @keyup.enter="confirmAddEditTargetTag"
              @blur="confirmAddEditTargetTag"
            />
            <el-button v-else size="small" @click="showEditTargetInput">
              + 添加类别
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="测试数据集">
          <el-select
            v-model="editForm.testDatasets"
            multiple
            filterable
            clearable
            placeholder="选择一个或多个用于测试的数据集"
            style="width: 100%"
          >
            <el-option
              v-for="opt in datasetOptions"
              :key="`edit-dataset-${opt.name}`"
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
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="editDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="editLoading" @click="submitEditTask">
            保存修改
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="mappingViewVisible"
      title="查看映射"
      width="760px"
      draggable
      :close-on-click-modal="false"
    >
      <div v-if="mappingViewTask" class="mapping-view">
        <div class="mapping-view-title">{{ mappingViewTask.name }}</div>
        <div v-if="!(mappingViewDatasetNames.length)" class="mapping-empty">
          当前任务还没有保存任何映射关系。
        </div>
        <div v-for="datasetName in mappingViewDatasetNames" :key="`view-${datasetName}`" class="dataset-panel">
          <div class="dataset-panel-title">数据集源：{{ datasetName }}</div>
          <div class="dataset-panel-body">
            <div class="mapping-grid">
              <div
                v-for="(target, source) in mappingViewTask.mapping_rules?.[datasetName] || {}"
                :key="`view-${datasetName}-${source}`"
                class="mapping-item"
              >
                <div class="mapping-pair">
                  <span class="mapping-pair-label">原标签</span>
                  <span class="mapping-pair-value">{{ source }}</span>
                </div>
                <div class="mapping-pair">
                  <span class="mapping-pair-label">映射到</span>
                  <span class="mapping-pair-value">{{ target }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="mappingViewVisible = false">关闭</el-button>
          <el-button type="primary" @click="jumpToMappingEditor">编辑</el-button>
        </div>
      </template>
    </el-dialog>
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
const datasetSelectedClasses = ref({})
const targetInputRef = ref()
const targetInputVisible = ref(false)
const targetInputValue = ref('')
const editTargetInputRef = ref()
const editTargetInputVisible = ref(false)
const editTargetInputValue = ref('')

const createLoading = ref(false)
const editLoading = ref(false)
const saveLoading = ref(false)
const editDialogVisible = ref(false)
const mappingViewVisible = ref(false)
const mappingViewTaskName = ref('')
const exportLoadingTaskName = ref('')

const createForm = ref({
  name: '',
  desc: '',
  targetSchema: ['Large_Vehicle', 'Person', 'Ship'],
  testDatasets: []
})

const editForm = ref({
  originalName: '',
  name: '',
  desc: '',
  targetSchema: [],
  testDatasets: []
})

const selectedTask = computed(() => {
  return tasks.value.find(item => item.name === selectedTaskName.value) || null
})

const mappingViewTask = computed(() => {
  return tasks.value.find(item => item.name === mappingViewTaskName.value) || null
})

const mappingViewDatasetNames = computed(() => {
  const rules = mappingViewTask.value?.mapping_rules || {}
  return Object.keys(rules)
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
    datasetSelectedClasses.value = {}
    return
  }
  const next = {}
  const selected = {}
  const saved = task.mapping_rules || {}
  ;(task.test_datasets || []).forEach(datasetName => {
    const classes = datasetClassMap.value[datasetName] || []
    const savedOne = saved?.[datasetName] || {}
    next[datasetName] = {}
    const selectedOne = []
    classes.forEach(cls => {
      if (savedOne[cls]) {
        next[datasetName][cls] = savedOne[cls]
        selectedOne.push(cls)
      } else if ((task.target_schema || []).includes(cls)) {
        next[datasetName][cls] = cls
      } else {
        next[datasetName][cls] = ''
      }
    })
    selected[datasetName] = selectedOne
  })
  mappingEditor.value = next
  datasetSelectedClasses.value = selected
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

function showEditTargetInput() {
  editTargetInputVisible.value = true
  setTimeout(() => {
    editTargetInputRef.value?.focus?.()
  }, 0)
}

function confirmAddEditTargetTag() {
  const value = String(editTargetInputValue.value || '').trim()
  if (value && !editForm.value.targetSchema.includes(value)) {
    editForm.value.targetSchema.push(value)
  }
  editTargetInputVisible.value = false
  editTargetInputValue.value = ''
}

function removeEditTargetTag(tag) {
  editForm.value.targetSchema = editForm.value.targetSchema.filter(item => item !== tag)
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
  editForm.value = {
    originalName: task.name,
    name: task.name,
    desc: task.desc || '',
    targetSchema: [...(task.target_schema || [])],
    testDatasets: [...(task.test_datasets || [])]
  }
  editTargetInputVisible.value = false
  editTargetInputValue.value = ''
  editDialogVisible.value = true
}

function openMappingView(task) {
  if (!task?.name) return
  mappingViewTaskName.value = task.name
  mappingViewVisible.value = true
}

function statusTagType(statusCode) {
  if (statusCode === 'ready') return 'success'
  if (statusCode === 'stale') return 'warning'
  return 'info'
}

function exportActionText(statusCode) {
  if (!statusCode || statusCode === 'never_exported') return '导出'
  return '更新'
}

async function exportTask(task) {
  if (!task?.name) return
  exportLoadingTaskName.value = task.name
  try {
    const res = await TaskDatasetDevService.exportTask({ name: task.name })
    if (res?.code !== 0) {
      ElMessage.error(res?.msg || '导出失败')
      return
    }
    ElMessage.success('已导出到中间实例数据集（instance_dataset_mid）')
    tasks.value = Array.isArray(res?.data) ? res.data : []
  } catch (e) {
    ElMessage.error(`导出失败：${e?.message || e}`)
  } finally {
    exportLoadingTaskName.value = ''
  }
}

function scrollToAnchor(href) {
  if (!href || typeof href !== 'string') return
  const target = document.querySelector(href)
  if (!target) return
  const top = target.getBoundingClientRect().top + window.scrollY - 84
  window.scrollTo({
    top: Math.max(top, 0),
    behavior: 'smooth'
  })
}

function handleAnchorClick(e, href) {
  e?.preventDefault?.()
  scrollToAnchor(href)
}

function jumpToMappingEditor() {
  if (!mappingViewTaskName.value) return
  selectedTaskName.value = mappingViewTaskName.value
  mappingViewVisible.value = false
  scrollToAnchor('#mapping-editor')
}

async function submitEditTask() {
  const originalName = String(editForm.value.originalName || '').trim()
  const name = String(editForm.value.name || '').trim()
  const targetSchema = (editForm.value.targetSchema || []).map(item => String(item || '').trim()).filter(Boolean)

  if (!originalName) {
    ElMessage.error('缺少原任务名称')
    return
  }
  if (!name) {
    ElMessage.error('请输入任务名称')
    return
  }
  if (!targetSchema.length) {
    ElMessage.error('请填写至少一个目标类别')
    return
  }
  if (!(editForm.value.testDatasets || []).length) {
    ElMessage.error('请至少选择一个数据集作为测试源')
    return
  }

  editLoading.value = true
  try {
    const res = await TaskDatasetDevService.updateTask({
      originalName,
      name,
      desc: String(editForm.value.desc || '').trim(),
      targetSchema,
      testDatasets: editForm.value.testDatasets
    })
    if (res?.code !== 0) {
      ElMessage.error(res?.msg || '更新失败')
      return
    }
    ElMessage.success('任务信息已更新')
    tasks.value = Array.isArray(res?.data) ? res.data : []
    if (selectedTaskName.value === originalName) {
      selectedTaskName.value = name
    }
    editDialogVisible.value = false
  } catch (e) {
    ElMessage.error(`更新失败：${e?.message || e}`)
  } finally {
    editLoading.value = false
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
.doc-layout {
  display: flex;
  gap: 24px;
  align-items: flex-start;
  background: #ffffff;
}

.doc-left {
  width: 220px;
  min-width: 220px;
}

.doc-left-placeholder {
  min-height: calc(100vh - 120px);
}

.doc-main {
  flex: 1;
  min-width: 0;
  max-width: 980px;
}

.doc-right {
  width: 220px;
  min-width: 220px;
}

.anchor-wrapper {
  position: fixed;
  top: 88px;
  right: 32px;
  width: 220px;
  padding-top: 8px;
  background: transparent;
  z-index: 10;
}

.anchor-title {
  margin-bottom: 10px;
  font-size: 18px;
  font-weight: 600;
  color: #606266;
}

.doc-section {
  margin-bottom: 28px;
}

.page-hero {
  padding: 60px 0 4px;
}

.page-title {
  font-size: 28px;
  font-weight: 600;
  color: #222;
}

.page-subtitle {
  margin-top: 8px;
  color: #666;
  font-size: 14px;
  line-height: 1.7;
}

.page-shell {
  padding: 0;
  background: #ffffff;
  color: #303133;
}

.section-block {
  background: #fff;
  border: 1px solid #e6e8ee;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.06);
}

.section-heading {
  padding: 22px 24px 14px;
  border-bottom: 1px solid #ededed;
  background: #ffffff;
}

.section-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #222;
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-link {
  color: #409eff;
  font-size: 16px;
  font-weight: 500;
  text-decoration: none;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.section-title:hover .section-link {
  opacity: 1;
}

.section-desc {
  margin: 8px 0 0;
  color: #777;
  font-size: 13px;
  line-height: 1.6;
}

.section-body {
  padding: 20px 24px 24px;
}

.action-form-item {
  margin-bottom: 0;
}

.action-form-item :deep(.el-form-item__content) {
  justify-content: flex-end;
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
  border: 1px solid #e6e8ee;
  border-radius: 12px;
  background: #ffffff;
  overflow: hidden;
  transition: box-shadow 0.25s ease, transform 0.25s ease, border-color 0.25s ease, background-color 0.25s ease;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.05);
}

.task-card:hover {
  box-shadow: 0 12px 28px rgba(37, 99, 235, 0.12);
  transform: translateY(-2px);
  border-color: #c7d2fe;
  background: #fff;
}

.card-header {
  font-size: 16px;
  font-weight: 600;
  color: #222;
  padding: 18px 18px 14px;
  border-bottom: 1px solid #ebeef5;
  background: #ffffff;
}

.task-card-body {
  padding: 16px 18px 18px;
}

.task-info p {
  margin: 0 0 8px;
  color: #444;
  line-height: 1.7;
}

.task-info-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin: 0 0 10px;
  color: #444;
}

.task-info-label {
  width: 96px;
  min-width: 96px;
  color: #303133;
  font-weight: 600;
  text-align: left;
  line-height: 1.8;
}

.task-info-value {
  flex: 1;
  min-width: 0;
  line-height: 1.8;
}

.info-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.task-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  padding: 14px 18px 18px;
  border-top: 1px solid #ebeef5;
  background: #fff;
}

.task-card-footer-left,
.task-card-footer-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.mapping-summary {
  margin-bottom: 12px;
}

.mapping-summary-row {
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 14px;
}

.mapping-summary-row:last-child {
  margin-bottom: 0;
}

.mapping-summary-label {
  width: 120px;
  min-width: 120px;
  color: #303133;
  font-size: 16px;
  font-weight: 600;
  line-height: 32px;
}

.mapping-summary-value {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.schema-tag {
  margin-right: 0;
}

.missing-alert {
  margin-bottom: 12px;
}

.dataset-panel {
  border: 1px solid #e6e8ee;
  border-radius: 10px;
  margin-bottom: 14px;
  background: #ffffff;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.04);
  overflow: hidden;
}

.dataset-panel-title {
  font-weight: 600;
  color: #333;
  padding: 14px 14px 12px;
  border-bottom: 1px solid #ebeef5;
}

.dataset-panel-body {
  padding: 14px;
}

.dataset-config-block {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.dataset-config-row {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.dataset-config-label {
  width: 96px;
  min-width: 96px;
  padding-top: 6px;
  color: #303133;
  font-weight: 600;
}

.dataset-config-value {
  flex: 1;
  min-width: 0;
}

.mapping-empty {
  padding: 14px 16px;
  border: 1px dashed #d6e4ff;
  border-radius: 8px;
  background: #f8fbff;
  color: #7a8699;
}

.mapping-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.mapping-item {
  border: 1px solid #e6e8ee;
  border-radius: 8px;
  padding: 12px;
  background: #ffffff;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.05);
}

.mapping-from {
  margin-bottom: 8px;
  color: #333;
  font-weight: 500;
}

.mapping-pair {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.mapping-pair + .mapping-pair {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #f0f2f5;
}

.mapping-pair-label {
  font-size: 12px;
  color: #909399;
}

.mapping-pair-value {
  font-size: 15px;
  color: #303133;
  font-weight: 500;
}

.mapping-view-title {
  margin-bottom: 14px;
  font-size: 18px;
  font-weight: 600;
  color: #222;
}

.mapping-select {
  width: 100%;
}

.save-bar {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.page-anchor {
  padding-left: 4px;
  background: transparent;
  font-size: 12px;
}

.page-anchor :deep(.el-anchor__marker) {
  background-color: #409eff;
}

.page-anchor :deep(.el-anchor__link) {
  color: #606266;
  background: transparent;
  font-size: 15px;
  line-height: 1.8;
}

.page-anchor :deep(.is-active > .el-anchor__link),
.page-anchor :deep(.el-anchor__link:hover) {
  color: #409eff;
}

.section-block :deep(.el-button--primary) {
  --el-button-bg-color: #409eff;
  --el-button-border-color: #409eff;
  --el-button-hover-bg-color: #66b1ff;
  --el-button-hover-border-color: #66b1ff;
  --el-button-active-bg-color: #337ecc;
  --el-button-active-border-color: #337ecc;
}

.page-shell :deep(.el-backtop) {
  background-color: #409eff;
  color: #ffffff;
}

.page-shell :deep(.el-backtop:hover) {
  background-color: #66b1ff;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.section-block :deep(.el-button--primary.is-plain) {
  --el-button-text-color: #409eff;
  --el-button-border-color: #b3d8ff;
  --el-button-bg-color: #ecf5ff;
  --el-button-hover-text-color: #ffffff;
  --el-button-hover-bg-color: #409eff;
  --el-button-hover-border-color: #409eff;
}

.section-block :deep(.el-tag) {
  --el-tag-bg-color: #ecf5ff;
  --el-tag-border-color: #b3d8ff;
  --el-tag-text-color: #409eff;
}

.section-block :deep(.el-alert--warning) {
  --el-alert-bg-color: #fdfdfd;
  --el-alert-border-color: #ebeef5;
  --el-alert-title-color: #606266;
}

.section-block :deep(.el-input__wrapper),
.section-block :deep(.el-select__wrapper),
.section-block :deep(.el-textarea__inner) {
  box-shadow: 0 0 0 1px #dcdfe6 inset;
  background: #ffffff;
}

.section-block :deep(.el-input__wrapper:hover),
.section-block :deep(.el-select__wrapper:hover) {
  box-shadow: 0 0 0 1px #a0cfff inset;
}

.section-block :deep(.el-input__wrapper.is-focus),
.section-block :deep(.el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 1px #409eff inset;
}

@media (max-width: 1400px) {
  .doc-left {
    display: none;
  }

  .anchor-wrapper {
    right: 20px;
  }
}

@media (max-width: 1100px) {
  .doc-layout {
    display: block;
  }

  .doc-main {
    max-width: none;
  }

  .doc-right {
    display: none;
  }
}

@media (max-width: 768px) {
  .task-info-row,
  .dataset-config-row,
  .mapping-summary-row {
    flex-direction: column;
    gap: 8px;
  }

  .task-info-label,
  .dataset-config-label,
  .mapping-summary-label {
    width: auto;
    min-width: 0;
    padding-top: 0;
    line-height: 1.6;
  }

  .task-card-footer {
    flex-direction: column;
    align-items: stretch;
  }

  .task-card-footer-left,
  .task-card-footer-right {
    justify-content: flex-end;
  }
}
</style>
