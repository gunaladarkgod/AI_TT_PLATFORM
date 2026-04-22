<!--
  与 taskDatasetManageDev/index.vue 中「任务管理」区块（含 section-block、标题、描述、工具栏、卡片/表格）一致。
  统合页内嵌时去掉创建/映射/侧栏；映射相关操作跳转完整 dev 页。
-->
<template>
  <div class="unified-task-panel">
    <div class="content content--embed">
      <el-card class="original-dataset-panel original-dataset-panel--embed" shadow="never">
        <div class="original-dataset-panel__main original-dataset-panel__main--embed">
          <div class="original-dataset-toolbar-row flex-between">
            <div class="flex-start gap-8">
              <el-button size="small" @click="clearTaskTableFilters">清除列筛选</el-button>
              <el-button size="small" @click="clearTaskTableSort">清除列排序</el-button>
              <el-select
                v-if="viewAsTable"
                v-model="colFilterStatusPair"
                multiple
                clearable
                collapse-tags
                collapse-tags-tooltip
                placeholder="导出/映射状态"
                size="small"
                class="task-toolbar-status-filter"
                @change="onTaskStatusToolbarFilterChange"
              >
                <el-option
                  v-for="o in taskStatusPairFilterOptions"
                  :key="o.value"
                  :label="o.text"
                  :value="o.value"
                />
              </el-select>
            </div>
            <div class="flex-start gap-8 original-dataset-toolbar-row__right">
              <el-input
                v-model="taskToolbarSearch"
                size="small"
                clearable
                placeholder="Type to search"
                class="original-dataset-toolbar-search"
              />
              <el-button type="primary" size="small" @click="createTaskDialogVisible = true">
                创建任务
              </el-button>
            </div>
          </div>

          <el-empty v-if="!tasks.length" description="暂无任务，请先创建" />
          <div
            v-else-if="!viewAsTable"
            class="task-card-grid original-dataset-panel__scroll"
          >
            <div
                v-for="task in tasksPageData"
                :key="task.name"
                class="task-card"
                @click="handleTaskCardClick(task, $event)"
              >
                <el-descriptions
                  class="task-card-descriptions"
                  :column="taskCardDescrColumn"
                  size="small"
                  border
                >
                  <template #title>
                    <span class="task-card-title-with-dot">
                      <span class="task-card-title-text">{{ task.name }}</span>
                      <el-tooltip placement="top" :show-after="200">
                        <template #content>
                          <div
                            v-if="taskTitleDotState(task) === 'error'"
                            class="task-export-tooltip task-export-tooltip--plain"
                          >
                            {{ mappingStatusTooltip(task) }}
                          </div>
                          <div v-else-if="taskTitleDotState(task) === 'pending'" class="task-export-tooltip">
                            <div>
                              <strong>{{ taskExportDotPendingSummary(task) }}</strong>
                            </div>
                            <div v-if="String(task.last_export_time || '').trim()">
                              最近导出：{{ task.last_export_by || '-' }}
                              {{ formatTaskDateTime(task.last_export_time) }}
                            </div>
                            <div v-else>最近导出：尚未导出到中间数据集</div>
                            <div v-if="task.updated_by || task.updated_time">
                              任务定义更新：{{ task.updated_by || '-' }}
                              {{ formatTaskDateTime(task.updated_time) }}
                            </div>
                            <div v-else>任务定义更新：暂无记录</div>
                            <div class="task-export-dot-tooltip-hint">请点击工具栏或卡片上的「导出/更新」同步到 instance_dataset_mid。</div>
                          </div>
                          <div v-else class="task-export-tooltip">
                            <div v-if="String(task.last_export_time || '').trim()">
                              最近导出：{{ task.last_export_by || '-' }}
                              {{ formatTaskDateTime(task.last_export_time) }}
                            </div>
                            <div v-else>最近导出：尚未导出到中间数据集</div>
                            <div v-if="task.updated_by || task.updated_time">
                              任务定义更新：{{ task.updated_by || '-' }}
                              {{ formatTaskDateTime(task.updated_time) }}
                            </div>
                            <div v-else>任务定义更新：暂无记录</div>
                            <div>中间实例数据已与当前任务/映射导出版本一致（已就绪）。</div>
                          </div>
                        </template>
                        <span
                          class="task-export-dot-wrap"
                          :class="{
                            'task-export-dot-wrap--error': taskTitleDotState(task) === 'error',
                            'task-export-dot-wrap--pending': taskTitleDotState(task) === 'pending'
                          }"
                          @click.stop
                        >
                          <span
                            class="task-export-dot"
                            :class="{
                              'task-export-dot--error': taskTitleDotState(task) === 'error',
                              'task-export-dot--pending': taskTitleDotState(task) === 'pending'
                            }"
                          />
                        </span>
                      </el-tooltip>
                    </span>
                  </template>
                  <template #extra>
                    <div class="task-card-descriptions-extra" @click.stop>
                      <el-tooltip
                        :content="exportButtonTooltip(task)"
                        placement="top"
                        :show-after="200"
                      >
                        <span class="card-header-action-wrap">
                          <el-button
                            type="primary"
                            size="small"
                            :loading="exportLoadingTaskName === task.name"
                            :disabled="task.mapping_status_code !== 'ok'"
                            @click.stop="exportTask(task)"
                          >
                            {{ exportActionText(task) }}
                          </el-button>
                        </span>
                      </el-tooltip>
                    <el-tooltip
                      content="仅清除本地已导出的中间数据集：删除数据库中 father_name 为该任务名的中间表记录，并删除 instance_dataset_mid 下对应目录。不会删除任务定义，列表中的任务卡片仍会保留；清除后可再次点击「导出/更新」重新生成。"
                      placement="top"
                      :show-after="200"
                    >
                        <span class="card-header-action-wrap">
                          <el-button type="warning" plain size="small" @click.stop="clearTask(task)">
                            清除
                          </el-button>
                        </span>
                      </el-tooltip>
                    </div>
                  </template>

                  <el-descriptions-item label="任务描述" :span="taskCardDescrColumn">
                    <span class="descr-value-text">{{ task.desc || '-' }}</span>
                  </el-descriptions-item>
                  <el-descriptions-item label="目标类别" :span="taskCardDescrColumn">
                    <div class="info-tags info-tags--card-plain" v-if="task.target_schema?.length">
                      <el-tag v-for="cls in task.target_schema" :key="`${task.name}-schema-${cls}`" size="small">
                        {{ cls }}：{{ targetMappedSampleCountForTask(task, cls) }}
                      </el-tag>
                    </div>
                    <span v-else>-</span>
                  </el-descriptions-item>
                  <el-descriptions-item label="关联数据集" :span="taskCardDescrColumn">
                    <div class="info-tags info-tags--card-plain" v-if="task.test_datasets?.length">
                      <el-tag
                        v-for="ds in task.test_datasets"
                        :key="`${task.name}-dataset-${ds}`"
                        size="small"
                      >
                        {{ ds }}
                      </el-tag>
                    </div>
                    <span v-else>-</span>
                  </el-descriptions-item>
                  <el-descriptions-item label="数据集状态" :span="taskCardDescrColumn">
                    <div class="task-status-badges">
                      <el-tooltip :content="exportStatusTooltip(task.status_code)" placement="top">
                        <el-tag :type="statusTagType(task.status_code)">
                          {{ task.status_text || '未导出' }}
                        </el-tag>
                      </el-tooltip>
                      <el-tooltip :content="mappingStatusTooltip(task)" placement="top" :show-after="150">
                        <el-tag
                          :type="mappingTagType(task.mapping_status_code)"
                          :class="[
                            'mapping-status-tag',
                            { 'mapping-status-tag--error': task.mapping_status_code !== 'ok' }
                          ]"
                          @click.stop="handleMappingTagClick(task)"
                        >
                          {{ task.mapping_status_text || '映射错误' }}
                        </el-tag>
                      </el-tooltip>
                    </div>
                  </el-descriptions-item>
                </el-descriptions>

                <div class="task-card-footer" @click.stop>
                  <div class="task-card-footer-left">
                    <el-button plain @click="jumpToTaskMappingEditor(task)">查看和编辑映射关系</el-button>
                  </div>
                  <div class="task-card-footer-right">
                    <el-button type="primary" plain @click="editTask(task)">编辑</el-button>
                    <el-tooltip
                      content="从列表中删除该任务定义（tasks.json）。确认时可选择是否同时清理本地已导出的中间数据；默认推荐一并清理。"
                      placement="top"
                      :show-after="200"
                    >
                      <span class="card-header-action-wrap">
                        <el-button type="danger" plain @click="deleteTask(task)">删除</el-button>
                      </span>
                    </el-tooltip>
                  </div>
                </div>
              </div>
            </div>

            <div
              v-else
              class="table-div table-div--embed-scroll original-dataset-panel__scroll"
            >
            <el-table
              ref="taskListTableRef"
              :data="tasksPageData"
              row-key="name"
              border
              stripe
              size="small"
              class="task-list-table my-table"
              table-layout="fixed"
              style="width: 100%"
              :height="'100%'"
              v-el-height-adaptive-table="{ bottomOffset: 120, isUse: false }"
              @sort-change="onTaskTableSortChange"
              @filter-change="onTaskTableFilterChange"
              @row-click="handleTaskTableRowClick"
            >
              <el-table-column label="序号" width="48" align="center" fixed="left">
                <template #default="scope">
                  <el-text size="small">
                    {{ (tableCurrentPage - 1) * tablePageSize + scope.$index + 1 }}
                  </el-text>
                </template>
              </el-table-column>
              <el-table-column
                label="任务名称"
                prop="name"
                column-key="name"
                min-width="260"
                fixed="left"
                sortable="custom"
              >
                <template #default="{ row }">
                  <div class="table-task-name-with-status">
                    <div class="table-task-name-left">
                      <span class="table-task-name" :title="row.name">{{ row.name }}</span>
                      <el-tooltip placement="top" :show-after="200">
                        <template #content>
                          <div
                            v-if="taskTitleDotState(row) === 'error'"
                            class="task-export-tooltip task-export-tooltip--plain"
                          >
                            {{ mappingStatusTooltip(row) }}
                          </div>
                          <div v-else-if="taskTitleDotState(row) === 'pending'" class="task-export-tooltip">
                            <div>
                              <strong>{{ taskExportDotPendingSummary(row) }}</strong>
                            </div>
                            <div v-if="String(row.last_export_time || '').trim()">
                              最近导出：{{ row.last_export_by || '-' }}
                              {{ formatTaskDateTime(row.last_export_time) }}
                            </div>
                            <div v-else>最近导出：尚未导出到中间数据集</div>
                            <div v-if="row.updated_by || row.updated_time">
                              任务定义更新：{{ row.updated_by || '-' }}
                              {{ formatTaskDateTime(row.updated_time) }}
                            </div>
                            <div v-else>任务定义更新：暂无记录</div>
                            <div class="task-export-dot-tooltip-hint">请点击「导出/更新」同步到 instance_dataset_mid。</div>
                          </div>
                          <div v-else class="task-export-tooltip">
                            <div v-if="String(row.last_export_time || '').trim()">
                              最近导出：{{ row.last_export_by || '-' }}
                              {{ formatTaskDateTime(row.last_export_time) }}
                            </div>
                            <div v-else>最近导出：尚未导出到中间数据集</div>
                            <div v-if="row.updated_by || row.updated_time">
                              任务定义更新：{{ row.updated_by || '-' }}
                              {{ formatTaskDateTime(row.updated_time) }}
                            </div>
                            <div v-else>任务定义更新：暂无记录</div>
                            <div>中间实例数据已与当前任务/映射导出版本一致（已就绪）。</div>
                          </div>
                        </template>
                        <span
                          class="task-export-dot-wrap"
                          :class="{
                            'task-export-dot-wrap--error': taskTitleDotState(row) === 'error',
                            'task-export-dot-wrap--pending': taskTitleDotState(row) === 'pending'
                          }"
                          @click.stop
                        >
                          <span
                            class="task-export-dot"
                            :class="{
                              'task-export-dot--error': taskTitleDotState(row) === 'error',
                              'task-export-dot--pending': taskTitleDotState(row) === 'pending'
                            }"
                          />
                        </span>
                      </el-tooltip>
                    </div>
                    <div class="table-task-name-status" @click.stop>
                      <el-tooltip :content="exportStatusTooltip(row.status_code)" placement="top">
                        <el-tag :type="statusTagType(row.status_code)" size="small">
                          {{ row.status_text || '未导出' }}
                        </el-tag>
                      </el-tooltip>
                      <el-tooltip :content="mappingStatusTooltip(row)" placement="top" :show-after="150">
                        <el-tag
                          size="small"
                          :type="mappingTagType(row.mapping_status_code)"
                          :class="[
                            'mapping-status-tag',
                            { 'mapping-status-tag--error': row.mapping_status_code !== 'ok' }
                          ]"
                          @click.stop="handleMappingTagClick(row)"
                        >
                          {{ row.mapping_status_text || '映射错误' }}
                        </el-tag>
                      </el-tooltip>
                    </div>
                  </div>
                </template>
              </el-table-column>
              <el-table-column
                label=" "
                column-key="statusPair"
                prop="statusPair"
                width="40"
                align="center"
                fixed="left"
                class-name="task-col-status-sort"
                sortable="custom"
              >
                <template #default><span /></template>
              </el-table-column>
              <el-table-column
                label="任务描述"
                prop="desc"
                column-key="desc"
                min-width="140"
                sortable="custom"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ row.desc || '-' }}
                </template>
              </el-table-column>
              <el-table-column
                label="目标类别"
                prop="targetSchema"
                column-key="targetSchema"
                min-width="200"
                sortable="custom"
                :filters="taskTargetClassFilterOptions"
                :filter-method="tableColumnFilterPassAll"
                filter-placement="bottom-end"
              >
                <template #default="{ row }">
                  <div class="info-tags info-tags--table-varied" v-if="row.target_schema?.length">
                    <el-tag
                      v-for="cls in row.target_schema"
                      :key="`${row.name}-t-${cls}`"
                      size="small"
                      :type="listInfoTagType(`${row.name}\t${cls}`)"
                    >
                      {{ cls }}：{{ targetMappedSampleCountForTask(row, cls) }}
                    </el-tag>
                  </div>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column
                label="关联数据集"
                prop="testDatasets"
                column-key="testDatasets"
                min-width="180"
                sortable="custom"
                :filters="taskTestDatasetFilterOptions"
                :filter-method="tableColumnFilterPassAll"
                filter-placement="bottom-end"
              >
                <template #default="{ row }">
                  <div class="info-tags info-tags--table-varied" v-if="row.test_datasets?.length">
                    <el-tag
                      v-for="ds in row.test_datasets"
                      :key="`${row.name}-ds-${ds}`"
                      size="small"
                      :type="listInfoTagType(`${row.name}\t${ds}`)"
                    >
                      {{ ds }}
                    </el-tag>
                  </div>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column
                label="最近修改"
                prop="updated_time"
                column-key="updated_time"
                width="150"
                align="center"
                sortable="custom"
              >
                <template #default="{ row }">
                  <el-text size="small">{{ formatTaskDateTime(row.updated_time) || '-' }}</el-text>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="168" fixed="right" align="right">
                <template #default="{ row }">
                  <div class="task-table-actions" @click.stop>
                    <el-dropdown
                      trigger="click"
                      @command="cmd => handleTaskTableMoreCommand(cmd, row)"
                    >
                      <el-button
                        type="primary"
                        size="small"
                        :loading="exportLoadingTaskName === row.name"
                      >
                        其他操作
                        <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                      </el-button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item
                            command="export"
                            :disabled="row.mapping_status_code !== 'ok'"
                          >
                            {{ exportActionText(row) }}
                          </el-dropdown-item>
                          <el-dropdown-item command="clear" divided>
                            清除导出
                          </el-dropdown-item>
                          <el-dropdown-item command="mapping">
                            查看和编辑映射
                          </el-dropdown-item>
                          <el-dropdown-item command="edit">
                            编辑任务
                          </el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                    <el-tooltip
                      content="从列表中删除该任务定义（tasks.json）。确认时可选择是否同时清理本地已导出的中间数据；默认推荐一并清理。"
                      placement="top"
                      :show-after="200"
                    >
                      <el-button type="danger" plain size="small" @click="deleteTask(row)">
                        删除
                      </el-button>
                    </el-tooltip>
                  </div>
                </template>
              </el-table-column>
            </el-table>
            </div>
        </div>
        <template #footer>
          <div class="original-dataset-panel__footer">
            <el-pagination
              v-if="viewAsTable"
              background
              size="small"
              v-model:current-page="tableCurrentPage"
              v-model:page-size="tablePageSize"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              :total="taskListTotal"
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
              :total="taskListTotal"
              @size-change="onCardPageSizeChange"
            />
          </div>
        </template>
      </el-card>
    </div>

    <el-dialog
      v-model="createTaskDialogVisible"
      title="创建任务"
      width="640px"
      destroy-on-close
      :close-on-click-modal="false"
      @open="onCreateTaskDialogOpen"
    >
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
              :key="`create-${tag}`"
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
            title="注意：创建后请在「任务映射」弹窗中继续配置具体类别映射。"
            type="warning"
            :closable="false"
            show-icon
          />
        </el-form-item>
        <el-form-item class="action-form-item">
          <el-button @click="createTaskDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="createLoading" @click="onCreateTaskSubmit">创建任务基础结构</el-button>
        </el-form-item>
      </el-form>
    </el-dialog>

    <el-dialog
      v-model="mappingEditorDialogVisible"
      title="任务映射"
      width="min(1100px, 96vw)"
      align-center
      class="task-mapping-editor-dialog"
      :close-on-click-modal="false"
      @closed="onMappingDialogClosed"
    >
      <p class="mapping-dialog-desc">将各数据集原始类别映射到当前任务的目标类别。</p>
      <el-empty v-if="!tasks.length" description="暂无任务，请先创建" />
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
                filterable
              >
                <el-option
                  v-for="task in tasksBaseSorted"
                  :key="task.name"
                  :label="task.name"
                  :value="task.name"
                />
              </el-select>
            </div>
          </div>
          <div v-if="selectedTask" class="mapping-summary-row">
            <div class="mapping-summary-label">当前目标类别</div>
            <div class="mapping-summary-value">
              <el-tag
                v-for="cls in selectedTask.target_schema || []"
                :key="cls"
                :class="[
                  'schema-tag',
                  { 'schema-tag-problem': isTargetIssue(cls) },
                  { 'schema-tag-active': activeTargetTag === cls }
                ]"
                @click="handleTargetTagClick(cls)"
              >
                {{ cls }}：{{ targetMappedSampleCountInEditor(cls) }}
              </el-tag>
            </div>
          </div>
          <div v-if="selectedTask" class="mapping-summary-row">
            <div class="mapping-summary-label">图例</div>
            <div class="mapping-summary-value target-legend">
              <span class="legend-item">
                <span class="legend-dot legend-dot-active"></span>
                蓝色：当前筛选
              </span>
              <span class="legend-item">
                <span class="legend-dot legend-dot-problem"></span>
                浅红：未覆盖
              </span>
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
          <div class="auto-map-bar">
            <el-button type="primary" plain @click="applyAutoMapping">自动映射</el-button>
            <el-checkbox v-model="autoMapCaseSensitive">区分大小写</el-checkbox>
            <el-checkbox v-model="autoMapStrict">严格匹配</el-checkbox>
            <el-checkbox v-model="autoMapOverwrite">覆盖已有映射</el-checkbox>
            <span class="auto-map-hint">
              按原始标签与目标类别名称匹配。关闭「严格匹配」时支持分段一致（如 large_ship → ship）或名称互相包含。
            </span>
          </div>
          <div
            v-for="datasetName in selectedTask.test_datasets || []"
            :key="datasetName"
            class="dataset-panel"
          >
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
              <div
                v-if="!(datasetSelectedClasses[datasetName] || []).length"
                class="mapping-empty"
              >
                请先为“{{ datasetName }}”选择需要使用的标签，再进行映射配置。
              </div>
              <div v-else class="mapping-grid">
                <div
                  v-for="cls in datasetSelectedClasses[datasetName]"
                  :key="`${datasetName}-${cls}`"
                  :class="[
                    'mapping-item',
                    { 'mapping-item-missing': isIssuePair(datasetName, cls) },
                    { 'mapping-item-active-target': isActiveTargetMapping(datasetName, cls) }
                  ]"
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
    </el-dialog>

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
        <el-form-item v-if="editDialogTask" label="状态">
          <div class="edit-dialog-status-tags">
            <el-tooltip :content="exportStatusTooltip(editDialogTask.status_code)" placement="top">
              <el-tag :type="statusTagType(editDialogTask.status_code)" size="small">
                {{ editDialogTask.status_text || '未导出' }}
              </el-tag>
            </el-tooltip>
            <el-tooltip :content="mappingStatusTooltip(editDialogTask)" placement="top">
              <el-tag
                :type="mappingTagType(editDialogTask.mapping_status_code)"
                size="small"
                :class="{ 'mapping-status-tag--error': editDialogTask.mapping_status_code !== 'ok' }"
              >
                {{ editDialogTask.mapping_status_text || '映射错误' }}
              </el-tag>
            </el-tooltip>
          </div>
          <div
            v-if="String(editDialogTask.last_export_time || '').trim()"
            class="edit-dialog-status-export-time"
          >
            最近导出：{{ formatTaskDateTime(editDialogTask.last_export_time) }}
          </div>
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
        <el-form-item label="最近编辑">
          <span class="edit-meta-readonly">
            <template v-if="editForm.updatedBy || editForm.updatedTime">
              {{ editForm.updatedBy || '-' }} {{ formatTaskDateTime(editForm.updatedTime) }}
            </template>
            <template v-else>暂无记录</template>
          </span>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="edit-dialog-footer">
          <div class="edit-dialog-footer-left">
            <el-button plain @click="jumpToMappingFromEditDialog">查看和编辑映射关系</el-button>
          </div>
          <div class="edit-dialog-footer-right">
            <el-button @click="editDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="editLoading" @click="submitEditTask">
              保存修改
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import { OriginalDatasetService, TaskDatasetDevService } from '@/api/api'

const props = defineProps({
  /** 与统合父页同步（列表/卡片） */
  taskViewAsTable: { type: Boolean, default: true }
})

const viewAsTable = computed(() => props.taskViewAsTable)

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
const autoMapCaseSensitive = ref(false)
const autoMapStrict = ref(true)
const autoMapOverwrite = ref(false)
const editDialogVisible = ref(false)
const createTaskDialogVisible = ref(false)
const mappingEditorDialogVisible = ref(false)
const exportLoadingTaskName = ref('')
const activeTargetTag = ref('')
const issueHighlightTaskName = ref('')
const issueHighlightPairMap = ref({})
const issueHighlightTargetMap = ref({})

/** 任务卡片内 Descriptions 列数：随窗口变化，避免窄屏下表格过挤 */
const taskCardDescrColumn = ref(3)

function updateTaskCardDescrColumn() {
  if (typeof window === 'undefined') return
  const w = window.innerWidth
  if (w < 600) taskCardDescrColumn.value = 1
  else if (w < 1000) taskCardDescrColumn.value = 2
  else taskCardDescrColumn.value = 3
}

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
  testDatasets: [],
  updatedBy: '',
  updatedTime: ''
})

/** 编辑弹窗打开时，从任务列表取当前任务以展示导出/映射状态（随 tasks 刷新） */
const editDialogTask = computed(() => {
  if (!editDialogVisible.value) return null
  const name = String(editForm.value.originalName || '').trim()
  if (!name) return null
  return tasks.value.find(t => t.name === name) || null
})

function sortTasksForDisplay(arr, mode) {
  const copy = Array.isArray(arr) ? [...arr] : []
  if (mode === 'name') {
    copy.sort((a, b) => String(a?.name || '').localeCompare(String(b?.name || ''), 'zh-CN'))
    return copy
  }
  copy.sort((a, b) => {
    const ta = Date.parse(String(a?.updated_time || '').trim()) || 0
    const tb = Date.parse(String(b?.updated_time || '').trim()) || 0
    if (tb !== ta) return tb - ta
    return String(a?.name || '').localeCompare(String(b?.name || ''), 'zh-CN')
  })
  return copy
}

/** 默认按最近修改时间（与原先「最近修改优先」一致）；表头再叠自定义排序 */
const tasksBaseSorted = computed(() => sortTasksForDisplay(tasks.value, 'updated'))

/** 表头列筛选（在基序列表上生效，与原始数据集一致） */
const colFilterTestDataset = ref([])
const colFilterTargetClass = ref([])
const colFilterStatusPair = ref([])
/** 表头自定义排序 */
const tableColumnSort = ref(null)

const taskTestDatasetFilterOptions = computed(() => {
  const set = new Set()
  for (const t of tasks.value) {
    for (const ds of t.test_datasets || []) set.add(ds)
  }
  return [...set].sort((a, b) => String(a).localeCompare(String(b), 'zh-CN')).map((ds) => ({ text: ds, value: ds }))
})

const taskTargetClassFilterOptions = computed(() => {
  const set = new Set()
  for (const t of tasks.value) {
    for (const c of t.target_schema || []) set.add(c)
  }
  return [...set].sort((a, b) => String(a).localeCompare(String(b), 'zh-CN')).map((c) => ({ text: c, value: c }))
})

const taskStatusPairFilterOptions = computed(() => {
  const map = new Map()
  for (const t of tasks.value) {
    const v = `${t.status_code ?? ''}\t${t.mapping_status_code ?? ''}`
    if (map.has(v)) continue
    const text = `${t.status_text || '未导出'} / ${t.mapping_status_text || '映射'}`
    map.set(v, { text, value: v })
  }
  return [...map.values()]
})

function tableColumnFilterPassAll() {
  return true
}

function onTaskTableFilterChange(filters) {
  const f = filters || {}
  if (f.testDatasets !== undefined) colFilterTestDataset.value = f.testDatasets
  if (f.targetSchema !== undefined) colFilterTargetClass.value = f.targetSchema
  if (f.statusPair !== undefined) colFilterStatusPair.value = f.statusPair
  tableCurrentPage.value = 1
  cardCurrentPage.value = 1
}

function onTaskStatusToolbarFilterChange() {
  tableCurrentPage.value = 1
  cardCurrentPage.value = 1
}

function compareTaskRowsForSort(a, b, prop) {
  if (prop === 'name') return String(a?.name || '').localeCompare(String(b?.name || ''), 'zh-CN')
  if (prop === 'desc') return String(a?.desc || '').localeCompare(String(b?.desc || ''), 'zh-CN')
  if (prop === 'updated_time') {
    const ta = Date.parse(String(a?.updated_time || '').trim()) || 0
    const tb = Date.parse(String(b?.updated_time || '').trim()) || 0
    return ta - tb
  }
  if (prop === 'targetSchema') {
    const sa = (a?.target_schema || []).join(',')
    const sb = (b?.target_schema || []).join(',')
    return String(sa).localeCompare(String(sb), 'zh-CN')
  }
  if (prop === 'testDatasets') {
    const sa = (a?.test_datasets || []).join(',')
    const sb = (b?.test_datasets || []).join(',')
    return String(sa).localeCompare(String(sb), 'zh-CN')
  }
  if (prop === 'statusPair') {
    const c1 = String(a?.status_code ?? '')
    const c2 = String(b?.status_code ?? '')
    if (c1 !== c2) return c1.localeCompare(c2)
    return String(a?.mapping_status_code ?? '').localeCompare(String(b?.mapping_status_code ?? ''), 'zh-CN')
  }
  if (prop === 'updated_time') {
    const ta = Date.parse(String(a?.updated_time || '').trim()) || 0
    const tb = Date.parse(String(b?.updated_time || '').trim()) || 0
    return ta - tb
  }
  return 0
}

function onTaskTableSortChange({ prop, order }) {
  if (!order) {
    tableColumnSort.value = null
  } else {
    tableColumnSort.value = { prop, order }
  }
  tableCurrentPage.value = 1
  cardCurrentPage.value = 1
}

const tasksAfterColFilters = computed(() => {
  return tasksBaseSorted.value.filter((row) => {
    const tds = row.test_datasets || []
    const okDs =
      !colFilterTestDataset.value.length || tds.some((ds) => colFilterTestDataset.value.includes(ds))
    const ts = row.target_schema || []
    const okT =
      !colFilterTargetClass.value.length || ts.some((c) => colFilterTargetClass.value.includes(c))
    const pair = `${row.status_code ?? ''}\t${row.mapping_status_code ?? ''}`
    const okP = !colFilterStatusPair.value.length || colFilterStatusPair.value.includes(pair)
    return okDs && okT && okP
  })
})

const taskToolbarSearch = ref('')
const tableCurrentPage = ref(1)
const tablePageSize = ref(10)
const cardCurrentPage = ref(1)
const cardPageSize = ref(6)
const taskListTableRef = ref(null)

const tasksAfterSearch = computed(() => {
  const list = tasksAfterColFilters.value
  const q = (taskToolbarSearch.value || '').trim().toLowerCase()
  if (!q) return list
  return list.filter((task) => {
    const n = (task.name || '').toLowerCase()
    const d = (task.desc || '').toLowerCase()
    if (n.includes(q) || d.includes(q)) return true
    const st = (task.status_text || '').toLowerCase()
    const mt = (task.mapping_status_text || '').toLowerCase()
    if (st.includes(q) || mt.includes(q)) return true
    const schema = (task.target_schema || []).join(' ').toLowerCase()
    const tds = (task.test_datasets || []).join(' ').toLowerCase()
    return schema.includes(q) || tds.includes(q)
  })
})

const tasksOrderedForDisplay = computed(() => {
  const list = [...tasksAfterSearch.value]
  const ts = tableColumnSort.value
  if (!ts?.prop || !ts?.order) return list
  const mul = ts.order === 'descending' ? -1 : 1
  list.sort((a, b) => compareTaskRowsForSort(a, b, ts.prop) * mul)
  return list
})

const taskListTotal = computed(() => tasksOrderedForDisplay.value.length)

const tasksPageData = computed(() => {
  const list = tasksOrderedForDisplay.value
  if (viewAsTable.value) {
    const start = (tableCurrentPage.value - 1) * tablePageSize.value
    return list.slice(start, start + tablePageSize.value)
  }
  const start = (cardCurrentPage.value - 1) * cardPageSize.value
  return list.slice(start, start + cardPageSize.value)
})

watch(
  [taskToolbarSearch, () => props.taskViewAsTable, tasksBaseSorted, tasksAfterColFilters],
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

function clearTaskTableFilters() {
  taskToolbarSearch.value = ''
  colFilterTestDataset.value = []
  colFilterTargetClass.value = []
  colFilterStatusPair.value = []
  tableCurrentPage.value = 1
  cardCurrentPage.value = 1
  nextTick(() => taskListTableRef.value?.clearFilter?.())
}

function clearTaskTableSort() {
  tableColumnSort.value = null
  tableCurrentPage.value = 1
  cardCurrentPage.value = 1
  nextTick(() => taskListTableRef.value?.clearSort?.())
}

function ensureSelectedTaskNameAfterListUpdate() {
  if (selectedTaskName.value && !tasks.value.some((item) => item.name === selectedTaskName.value)) {
    selectedTaskName.value = ''
  }
  if (!selectedTaskName.value && tasks.value.length) {
    const ordered = sortTasksForDisplay(tasks.value, 'updated')
    selectedTaskName.value = ordered[0]?.name || ''
  }
}

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

/** 各数据集原始标签的样本数（来自 class_list） */
const datasetClassCounts = computed(() => {
  const m = {}
  for (const item of datasetOptions.value) {
    m[item.name] = item.classCounts && typeof item.classCounts === 'object' ? item.classCounts : {}
  }
  return m
})

/** 任务映射页：按当前勾选与 mappingEditor 汇总到各目标类的样本数 */
function targetMappedSampleCountInEditor(targetName) {
  const task = selectedTask.value
  if (!task || targetName == null || targetName === '') return 0
  let sum = 0
  const t = String(targetName)
  for (const dn of task.test_datasets || []) {
    const counts = datasetClassCounts.value[dn] || {}
    const sel = new Set(datasetSelectedClasses.value[dn] || [])
    const map = mappingEditor.value[dn] || {}
    for (const orig of sel) {
      const to = map[orig]
      if (to === t || String(to || '').trim() === t) {
        sum += Number(counts[orig] || 0)
      }
    }
  }
  return sum
}

/** 任务卡片/表格：按已保存的 mapping_rules 汇总 */
function targetMappedSampleCountForTask(task, targetName) {
  if (!task || targetName == null || targetName === '') return 0
  let sum = 0
  const t = String(targetName)
  for (const dn of task.test_datasets || []) {
    const counts = datasetClassCounts.value[dn] || {}
    const rules = task.mapping_rules?.[dn] || {}
    for (const [orig, mapped] of Object.entries(rules)) {
      if (!String(mapped || '').trim()) continue
      if (mapped === t || String(mapped).trim() === t) {
        sum += Number(counts[orig] || 0)
      }
    }
  }
  return sum
}

const missingDatasets = computed(() => {
  if (!selectedTask.value) return []
  return (selectedTask.value.test_datasets || []).filter(name => !datasetClassMap.value[name])
})

const selectedTaskIssueSummary = computed(() => {
  if (!selectedTask.value) {
    return {
      invalidPairs: [],
      uncoveredTargets: [],
      detail: ''
    }
  }
  return analyzeTaskMappingIssues(selectedTask.value, (datasetName, cls) => {
    const editorValue = mappingEditor.value?.[datasetName]?.[cls]
    if (typeof editorValue === 'string') {
      return editorValue
    }
    return selectedTask.value?.mapping_rules?.[datasetName]?.[cls] || ''
  })
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
    ...items.map(item => {
      const obj = safeParse(item.class_list ?? item.classList)
      const classes = extractClasses(item.class_list ?? item.classList)
      const classCounts = {}
      for (const k of classes) {
        const v = obj[k]
        classCounts[k] = typeof v === 'number' ? v : parseInt(String(v ?? 0), 10) || 0
      }
      return { name: item.name, source: 'CVAT', classes, classCounts }
    }),
    ...extItems
      .filter(item => !item.error)
      .map(item => {
        const obj = safeParse(item.class_list ?? item.classList)
        const classes = extractClasses(item.class_list ?? item.classList)
        const classCounts = {}
        for (const k of classes) {
          const v = obj[k]
          classCounts[k] = typeof v === 'number' ? v : parseInt(String(v ?? 0), 10) || 0
        }
        return { name: item.name, source: '外部导入', classes, classCounts }
      })
  ]

  datasetOptions.value = merged.sort((a, b) => a.name.localeCompare(b.name))
}

async function loadTasks() {
  const res = await TaskDatasetDevService.listTasks()
  if (res?.code !== 0) {
    throw new Error(res?.msg || '读取任务列表失败')
  }
  tasks.value = Array.isArray(res?.data) ? res.data : []
  ensureSelectedTaskNameAfterListUpdate()
}

function normalizeLabelForMatch(s, caseSensitive) {
  const t = String(s ?? '').trim()
  if (!t) return ''
  return caseSensitive ? t : t.toLowerCase()
}

/** 与 target_schema 中某一项大小写不敏感相等时返回该条目的规范字符串，否则 null */
function findTargetSchemaIdentityMatch(cls, targetSchema) {
  const arr = Array.isArray(targetSchema) ? targetSchema : []
  const clsNorm = normalizeLabelForMatch(cls, false)
  if (!clsNorm) return null
  for (const t of arr) {
    if (normalizeLabelForMatch(t, false) === clsNorm) {
      return String(t ?? '').trim() || null
    }
  }
  return null
}

/**
 * 在目标类别中为原始标签找一个匹配项。
 * 严格：仅规范化后全等；非严格：全等 → 分段全等（按 _ - 空格等切分）→ 双向包含（名称长度均 ≥2）。
 */
function findBestTargetMatch(origLabel, targetSchema, caseSensitive, strict) {
  const targets = Array.isArray(targetSchema)
    ? [...new Set(targetSchema.map(x => String(x || '').trim()).filter(Boolean))]
    : []
  if (!targets.length) return null
  const rawOrig = String(origLabel ?? '').trim()
  if (!rawOrig) return null

  const nOrig = normalizeLabelForMatch(rawOrig, caseSensitive)

  for (const t of targets) {
    if (nOrig === normalizeLabelForMatch(t, caseSensitive)) return t
  }
  if (strict) return null

  let best = null
  let bestLen = 0
  const parts = rawOrig.split(/[_\-\s.,;:|/\\]+/).map(p => normalizeLabelForMatch(p, caseSensitive)).filter(Boolean)
  for (const t of targets) {
    const nt = normalizeLabelForMatch(t, caseSensitive)
    if (nt.length < 2) continue
    if (parts.some(p => p === nt) && nt.length >= bestLen) {
      best = t
      bestLen = nt.length
    }
  }
  if (best) return best

  best = null
  bestLen = 0
  for (const t of targets) {
    const nt = normalizeLabelForMatch(t, caseSensitive)
    if (nt.length < 2 || nOrig.length < 2) continue
    if (nOrig.includes(nt) || nt.includes(nOrig)) {
      if (nt.length > bestLen) {
        best = t
        bestLen = nt.length
      }
    }
  }
  return best
}

function applyAutoMapping() {
  const task = selectedTask.value
  if (!task) {
    ElMessage.warning('请先选择一个任务')
    return
  }
  const targetSchema = task.target_schema || []
  if (!targetSchema.length) {
    ElMessage.warning('请先配置目标类别')
    return
  }

  const caseSensitive = autoMapCaseSensitive.value
  const strict = autoMapStrict.value
  const overwrite = autoMapOverwrite.value

  const nextMap = JSON.parse(JSON.stringify(mappingEditor.value || {}))
  const nextSel = { ...(datasetSelectedClasses.value || {}) }
  let filled = 0

  for (const datasetName of task.test_datasets || []) {
    const origClasses = datasetClassMap.value[datasetName] || []
    if (!nextMap[datasetName]) nextMap[datasetName] = {}
    const savedOne = task.mapping_rules?.[datasetName] || {}
    const selSet = new Set(nextSel[datasetName] || [])

    for (const cls of origClasses) {
      const cur = nextMap[datasetName][cls]
      const hasVal = cur != null && String(cur).trim() !== ''
      const inSaved = Object.prototype.hasOwnProperty.call(savedOne, cls)
      const idMatch = findTargetSchemaIdentityMatch(cls, targetSchema)
      const isImplicitIdentity =
        !inSaved && hasVal && idMatch != null && String(cur).trim() === idMatch
      if (hasVal && !overwrite && !isImplicitIdentity) continue

      const matched = findBestTargetMatch(cls, targetSchema, caseSensitive, strict)
      if (matched) {
        const before = nextMap[datasetName][cls]
        nextMap[datasetName][cls] = matched
        selSet.add(cls)
        if (String(before ?? '').trim() !== String(matched).trim()) {
          filled++
        }
      }
    }
    nextSel[datasetName] = Array.from(selSet)
  }

  mappingEditor.value = nextMap
  datasetSelectedClasses.value = nextSel

  if (filled) {
    ElMessage.success(`已自动映射 ${filled} 个标签，请确认后点击「更新任务映射规则」保存`)
  } else {
    ElMessage.info('没有新增匹配（检查目标类别与原始标签命名，或勾选「覆盖已有映射」）')
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
      } else {
        const idMatch = findTargetSchemaIdentityMatch(cls, task.target_schema || [])
        if (idMatch) {
          next[datasetName][cls] = idMatch
          selectedOne.push(cls)
        } else {
          next[datasetName][cls] = ''
        }
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

watch(selectedTaskName, () => {
  activeTargetTag.value = ''
})

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

function onCreateTaskDialogOpen() {
  createForm.value = {
    name: '',
    desc: '',
    targetSchema: ['Large_Vehicle', 'Person', 'Ship'],
    testDatasets: []
  }
  targetInputVisible.value = false
  targetInputValue.value = ''
}

async function createTask() {
  const name = String(createForm.value.name || '').trim()
  const targetSchema = (createForm.value.targetSchema || []).map(item => String(item || '').trim()).filter(Boolean)

  if (!name) {
    ElMessage.error('请输入任务名称')
    return false
  }
  if (!targetSchema.length) {
    ElMessage.error('请填写至少一个目标类别')
    return false
  }
  if (!createForm.value.testDatasets.length) {
    ElMessage.error('请至少选择一个数据集作为测试源')
    return false
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
      return false
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
    return true
  } catch (e) {
    ElMessage.error(`创建失败：${e?.message || e}`)
    return false
  } finally {
    createLoading.value = false
  }
}

async function onCreateTaskSubmit() {
  const ok = await createTask()
  if (!ok) return
  createTaskDialogVisible.value = false
  mappingEditorDialogVisible.value = true
}

function onMappingDialogClosed() {
  clearIssueHighlight()
}

async function deleteTask(task) {
  let alsoDeleteLocal = true
  try {
    await ElMessageBox.confirm(
      `即将从列表中删除任务「${task.name}」的记录（本地 tasks.json 中的任务定义）。\n\n是否同时删除本地已导出的中间数据集（instance_dataset_mid 目录及数据库中 father_name 对应记录）？\n建议选择「删除记录并清理本地」，以免磁盘残留。`,
      '删除确认',
      {
        type: 'warning',
        distinguishCancelAndClose: true,
        confirmButtonText: '删除记录并清理本地',
        cancelButtonText: '仅删除记录（保留本地）'
      }
    )
  } catch (action) {
    if (action === 'cancel') {
      alsoDeleteLocal = false
    } else {
      return
    }
  }

  try {
    const res = await TaskDatasetDevService.deleteTask({
      name: task.name,
      alsoDeleteLocal
    })
    if (res?.code !== 0) {
      ElMessage.error(res?.msg || '删除失败')
      return
    }
    ElMessage.success(
      alsoDeleteLocal ? '已删除任务记录，并已清理本地导出数据' : '已删除任务记录（本地导出目录未删除）'
    )
    tasks.value = Array.isArray(res?.data) ? res.data : []
    if (selectedTaskName.value === task.name) {
      selectedTaskName.value = ''
    }
    ensureSelectedTaskNameAfterListUpdate()
  } catch (e) {
    ElMessage.error(`删除失败：${e?.message || e}`)
  }
}

async function clearTask(task) {
  try {
    await ElMessageBox.confirm(
      `确定清除任务「${task.name}」的本地已导出中间数据吗？\n将删除中间表 father_name 对应记录及 instance_dataset_mid 下相关目录；任务定义会保留，列表中的卡片不会消失。`,
      '清除确认',
      {
        type: 'warning',
        confirmButtonText: '确认清除',
        cancelButtonText: '取消'
      }
    )
  } catch (e) {
    return
  }

  try {
    const res = await TaskDatasetDevService.clearTask({ name: task.name })
    if (res?.code !== 0) {
      const msg = res?.msg || '清除失败'
      if (String(msg).includes('无需清除')) {
        ElMessage.info(msg)
      } else {
        ElMessage.error(msg)
      }
      return
    }
    ElMessage.success('已清除本地导出数据，任务记录仍保留')
    tasks.value = Array.isArray(res?.data) ? res.data : []
  } catch (e) {
    ElMessage.error(`清除失败：${e?.message || e}`)
  }
}

function editTask(task) {
  if (!task?.name) return
  editForm.value = {
    originalName: task.name,
    name: task.name,
    desc: task.desc || '',
    targetSchema: [...(task.target_schema || [])],
    testDatasets: [...(task.test_datasets || [])],
    updatedBy: task.updated_by || '',
    updatedTime: task.updated_time || ''
  }
  editTargetInputVisible.value = false
  editTargetInputValue.value = ''
  editDialogVisible.value = true
}

function handleTaskCardClick(task, e) {
  if (!task?.name || !e?.target) return
  if (e.target.closest('.task-card-footer')) return
  if (e.target.closest('button, .el-button')) return
  if (e.target.closest('.el-tag')) return
  editTask(task)
}

function formatTaskDateTime(raw) {
  if (raw == null || raw === '') return ''
  const s = String(raw).trim()
  if (!s) return ''
  let out = s.replace('T', ' ')
  out = out.replace(/(\d{2}:\d{2}:\d{2})\.\d+/, '$1')
  out = out.replace(/Z$/i, '').trim()
  out = out.replace(/[+-]\d{2}:\d{2}$/, '').trim()
  return out
}

function handleTaskTableRowClick(row, _column, event) {
  if (!row?.name || !event?.target) return
  if (event.target.closest('button, .el-button, .el-tag, .task-export-dot-wrap, a, .el-dropdown')) return
  if (event.target.closest('.el-table__column-filter-trigger')) return
  if (event.target.closest('.el-popper')) return
  editTask(row)
}

function handleTaskTableMoreCommand(cmd, row) {
  if (!row?.name) return
  switch (cmd) {
    case 'export':
      exportTask(row)
      break
    case 'clear':
      clearTask(row)
      break
    case 'mapping':
      jumpToTaskMappingEditor(row)
      break
    case 'edit':
      editTask(row)
      break
    default:
      break
  }
}

function jumpToMappingFromEditDialog() {
  const name = editForm.value.originalName || editForm.value.name
  if (!name) return
  editDialogVisible.value = false
  nextTick(() => {
    clearIssueHighlight()
    selectedTaskName.value = name
    mappingEditorDialogVisible.value = true
  })
}

function jumpToTaskMappingEditor(task) {
  if (!task?.name) return
  clearIssueHighlight()
  selectedTaskName.value = task.name
  mappingEditorDialogVisible.value = true
}

function statusTagType(statusCode) {
  if (statusCode === 'ready') return 'success'
  if (statusCode === 'stale') return 'warning'
  return 'warning'
}

function mappingTagType(statusCode) {
  return statusCode === 'ok' ? 'success' : 'danger'
}

/** 列表视图目标类别 / 关联数据集：按内容哈希分配颜色，同一标签颜色稳定 */
const LIST_INFO_TAG_TYPES = ['primary', 'success', 'warning', 'danger', 'info']

function hashSeed(s) {
  let h = 2166136261 >>> 0
  const str = String(s)
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i)
    h = Math.imul(h, 16777619) >>> 0
  }
  return h
}

function listInfoTagType(seedKey) {
  return LIST_INFO_TAG_TYPES[hashSeed(seedKey) % LIST_INFO_TAG_TYPES.length]
}

/** 与后端 resolveExportStatusCode 一致：无 last_export_time 视为未导出，按钮显示「导出」 */
function exportActionText(task) {
  const code = task?.status_code
  const hasExportTime = String(task?.last_export_time || '').trim().length > 0
  if (!hasExportTime || !code || code === 'never_exported') return '导出'
  return '更新'
}

function exportButtonTooltip(task) {
  if (task?.mapping_status_code !== 'ok') {
    return mappingStatusTooltip(task)
  }
  const isFirst =
    !String(task?.last_export_time || '').trim() ||
    !task?.status_code ||
    task.status_code === 'never_exported'
  const lead = isFirst
    ? '首次导出：将当前任务定义与类别映射合并写入中间实例数据集（instance_dataset_mid），固定使用任务名称。'
    : '更新：在映射仍正确的前提下重新导出，覆盖同名任务在数据库与磁盘上的中间数据。'
  return `${lead}${exportStatusTooltip(task.status_code)}`
}

function exportStatusTooltip(statusCode) {
  if (statusCode === 'ready') {
    return '已就绪：当前任务定义已经导出到 instance_dataset_mid，且导出版本与当前任务一致。'
  }
  if (statusCode === 'stale') {
    return '未更新：任务定义或映射已修改，但尚未重新导出到 instance_dataset_mid。'
  }
  return '未导出：当前任务尚未导出到 instance_dataset_mid。'
}

/** 标题旁小圆点：红=映射未通过；黄=映射通过但中间层未导出或与任务已不同步；绿=已导出且一致 */
function taskTitleDotState(task) {
  if (!task || task.mapping_status_code !== 'ok') return 'error'
  if (task.status_code === 'ready') return 'synced'
  return 'pending'
}

function taskExportDotPendingSummary(task) {
  return exportStatusTooltip(task?.status_code)
}

function mappingStatusTooltip(task) {
  const fromBackend = String(task?.mapping_status_detail || '').trim()
  if (fromBackend) return fromBackend
  const analyzed = analyzeTaskMappingIssues(task, (datasetName, cls) => task?.mapping_rules?.[datasetName]?.[cls] || '')
  return analyzed.detail || '请先完成每个测试数据集类别到目标类别的映射。'
}

function analyzeTaskMappingIssues(task, mappingResolver) {
  const targetSchema = Array.isArray(task?.target_schema) ? task.target_schema : []
  const targetSet = new Set(targetSchema)
  const testDatasets = Array.isArray(task?.test_datasets) ? task.test_datasets : []
  const savedRules = task?.mapping_rules || {}
  const incomingTargets = new Set()
  const invalidPairs = []

  for (const datasetName of testDatasets) {
    const classes = datasetClassMap.value[datasetName]?.length
      ? datasetClassMap.value[datasetName]
      : Object.keys(savedRules[datasetName] || {})
    for (const cls of classes) {
      const mapped = String(mappingResolver(datasetName, cls) || '').trim()
      if (!mapped) continue
      if (!targetSet.has(mapped)) {
        invalidPairs.push({ datasetName, cls, mapped })
        continue
      }
      incomingTargets.add(mapped)
    }
  }

  const uncoveredTargets = targetSchema.filter(target => !incomingTargets.has(target))
  const detailParts = []
  if (invalidPairs.length) {
    const top = invalidPairs.slice(0, 6).map(one => `${one.datasetName}:${one.cls}->${one.mapped}`)
    detailParts.push(`非法映射 ${invalidPairs.length} 项（${top.join('，')}）`)
  }
  if (uncoveredTargets.length) {
    detailParts.push(`未被任何来源映射到的目标类别：${uncoveredTargets.join('，')}`)
  }

  return {
    invalidPairs,
    uncoveredTargets,
    detail: detailParts.join('；')
  }
}

function issuePairKey(datasetName, cls) {
  return `${datasetName}@@${cls}`
}

function clearIssueHighlight() {
  issueHighlightTaskName.value = ''
  issueHighlightPairMap.value = {}
  issueHighlightTargetMap.value = {}
}

function applyIssueHighlight(taskName, issueSummary) {
  const nextPairMap = {}
  const nextTargetMap = {}
  for (const one of issueSummary.invalidPairs || []) {
    nextPairMap[issuePairKey(one.datasetName, one.cls)] = true
  }
  for (const target of issueSummary.uncoveredTargets || []) {
    nextTargetMap[target] = true
  }
  issueHighlightTaskName.value = taskName
  issueHighlightPairMap.value = nextPairMap
  issueHighlightTargetMap.value = nextTargetMap
}

function isIssuePair(datasetName, cls) {
  if (!selectedTaskName.value || issueHighlightTaskName.value !== selectedTaskName.value) return false
  return !!issueHighlightPairMap.value[issuePairKey(datasetName, cls)]
}

function isTargetIssue(targetLabel) {
  if (selectedTaskIssueSummary.value.uncoveredTargets.includes(targetLabel)) return true
  if (!selectedTaskName.value || issueHighlightTaskName.value !== selectedTaskName.value) return false
  return !!issueHighlightTargetMap.value[targetLabel]
}

function mappedTargetForClass(datasetName, cls) {
  const editorValue = mappingEditor.value?.[datasetName]?.[cls]
  if (typeof editorValue === 'string') return editorValue
  return selectedTask.value?.mapping_rules?.[datasetName]?.[cls] || ''
}

function isActiveTargetMapping(datasetName, cls) {
  if (!activeTargetTag.value) return false
  return mappedTargetForClass(datasetName, cls) === activeTargetTag.value
}

function handleTargetTagClick(tag) {
  if (!tag) return
  activeTargetTag.value = activeTargetTag.value === tag ? '' : tag
}

function jumpToTaskMappingIssues(task) {
  if (!task?.name) return
  const analyzed = analyzeTaskMappingIssues(task, (datasetName, cls) => task?.mapping_rules?.[datasetName]?.[cls] || '')
  selectedTaskName.value = task.name
  applyIssueHighlight(task.name, analyzed)
  mappingEditorDialogVisible.value = true
}

function handleMappingTagClick(task) {
  if (!task?.name) return
  if (task.mapping_status_code === 'ok') return
  jumpToTaskMappingIssues(task)
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
    if (selectedTaskName.value === task.name) {
      clearIssueHighlight()
    }
  } catch (e) {
    ElMessage.error(`导出失败：${e?.message || e}`)
  } finally {
    exportLoadingTaskName.value = ''
  }
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
    const name = String(selectedTaskName.value || '').trim()
    const t = name ? tasks.value.find((x) => x.name === name) : null
    if (t?.mapping_status_code === 'ok' && t?.name) {
      try {
        await ElMessageBox.confirm(
          '映射规则已保存。是否将当前任务导出/更新到中间实例数据集（instance_dataset_mid）？',
          '导出到中间数据',
          {
            type: 'info',
            confirmButtonText: '立即导出/更新',
            cancelButtonText: '稍后',
            closeOnClickModal: false
          }
        )
        await exportTask(t)
      } catch {
        /* 用户取消或关闭 */
      }
    }
  } catch (e) {
    ElMessage.error(`保存失败：${e?.message || e}`)
  } finally {
    saveLoading.value = false
  }
}

onMounted(async () => {
  updateTaskCardDescrColumn()
  window.addEventListener('resize', updateTaskCardDescrColumn, { passive: true })

  const [datasetRes, taskRes] = await Promise.allSettled([loadDatasets(), loadTasks()])
  if (taskRes.status === 'rejected') {
    ElMessage.error(`任务定义加载失败：${taskRes.reason?.message || taskRes.reason || '请确认后端已重启并加载新接口'}`)
  }
  if (datasetRes.status === 'rejected') {
    ElMessage.warning(`数据集列表加载失败：${datasetRes.reason?.message || datasetRes.reason}`)
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', updateTaskCardDescrColumn)
})
</script>

<style scoped>
.doc-layout {
  display: flex;
  gap: 24px;
  align-items: flex-start;
  background: #ffffff;
}

.doc-layout--embed-tasklist {
  display: block;
  background: transparent;
}

.doc-layout--embed-tasklist .doc-main {
  max-width: none;
}

.task-list-section--embed {
  margin-bottom: 0;
}

.page-shell--embed-tasklist {
  background: transparent;
}

.section-heading--embed-toolbar {
  border-bottom: 1px solid #ededed;
  margin-bottom: 0;
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
  max-width: 1240px;
}

.doc-right {
  width: 220px;
  min-width: 220px;
}

.anchor-wrapper {
  position: fixed;
  top: 88px;
  right: 10px;
  width: 200px;
  padding-top: 8px;
  background: transparent;
  z-index: 10;
}

.anchor-title {
  margin-bottom: 8px;
  font-size: 15px;
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

/* 外层大卡片在统合页 #sec-task.section-block 上；本组件仅内层 el-card */
.unified-task-panel {
  min-width: 0;
  max-width: 100%;
}

.section-heading {
  padding: 22px 24px 14px;
  border-bottom: 1px solid #ededed;
  background: #ffffff;
}

.section-desc-row {
  margin-top: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px 16px;
  flex-wrap: wrap;
}

.section-task-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.section-view-toggle {
  flex-shrink: 0;
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

.section-desc.section-desc--inline {
  margin: 0;
  flex: 1;
  min-width: 200px;
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
  grid-template-columns: 1fr;
  gap: 16px;
}

@media (min-width: 1280px) {
  .task-card-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

.task-card {
  border: 1px solid #e6e8ee;
  border-radius: 12px;
  background: #ffffff;
  overflow: visible;
  display: flex;
  flex-direction: column;
  min-width: 0;
  cursor: pointer;
  transition: box-shadow 0.25s ease, transform 0.25s ease, border-color 0.25s ease, background-color 0.25s ease;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.05);
}

.task-card:hover {
  box-shadow: 0 12px 28px rgba(37, 99, 235, 0.12);
  transform: translateY(-2px);
  border-color: #c7d2fe;
  background: #fff;
}

.card-header-action-wrap {
  display: inline-flex;
  vertical-align: middle;
}

.task-card-descriptions {
  flex: 1;
  min-width: 0;
  margin: 0;
  --el-descriptions-label-bg-color: #fafafa;
}

.task-card-descriptions :deep(.el-descriptions__header) {
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 0;
  padding: 12px 10px 10px 16px;
  width: 100%;
  box-sizing: border-box;
}

.task-card-descriptions :deep(.el-descriptions__title) {
  font-size: 16px;
  font-weight: 600;
  color: #222;
  line-height: 32px;
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
}

.task-card-title-with-dot {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  max-width: 100%;
}

.task-card-title-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.task-export-dot-wrap {
  display: inline-flex;
  align-items: center;
  margin-left: 8px;
  flex-shrink: 0;
  cursor: help;
}

.task-export-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #67c23a;
  box-shadow: 0 0 0 1px rgba(103, 194, 58, 0.35);
  vertical-align: middle;
}

.task-export-dot--error {
  background: #f56c6c;
  box-shadow: 0 0 0 1px rgba(245, 108, 108, 0.45);
}

.task-export-dot--pending {
  background: #e6a23c;
  box-shadow: 0 0 0 1px rgba(230, 162, 60, 0.45);
}

.task-export-dot-wrap--error {
  cursor: help;
}

.task-export-dot-wrap--pending {
  cursor: help;
}

.task-export-dot-tooltip-hint {
  margin-top: 8px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.task-export-tooltip {
  max-width: 320px;
  line-height: 1.6;
}

.task-export-tooltip div + div {
  margin-top: 6px;
}

.task-export-tooltip--plain {
  max-width: 360px;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.55;
}

.table-task-name-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.table-task-name {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
}

.task-toolbar-status-filter {
  min-width: 200px;
  max-width: 300px;
}

.table-task-name-with-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  min-width: 0;
}

.table-task-name-left {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  flex: 1;
}

.table-task-name-with-status .table-task-name {
  max-width: min(200px, 45%);
}

.table-task-name-status {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  flex-shrink: 0;
  max-width: 52%;
}

.task-list-table :deep(.task-col-status-sort) {
  border-right: none;
}

.task-list-table :deep(.task-col-status-sort .cell) {
  padding: 0 2px;
}

.task-list-table {
  width: 100%;
}

.task-list-table :deep(.el-table__row) {
  cursor: pointer;
}

.task-list-table :deep(.el-table__cell .task-table-actions) {
  cursor: default;
}

.task-table-actions {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}


.edit-meta-readonly {
  color: #606266;
  font-size: 14px;
  line-height: 1.6;
}

.edit-dialog-status-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.edit-dialog-status-export-time {
  margin-top: 8px;
  color: #909399;
  font-size: 13px;
}

.task-status-badges .mapping-status-tag--error.el-tag--danger {
  --el-tag-bg-color: #fef0f0;
  --el-tag-border-color: #fbc4c4;
  --el-tag-text-color: #c45656;
}

.task-card-descriptions :deep(.el-descriptions__extra) {
  flex-shrink: 0;
  margin-left: auto;
}

.task-card-descriptions :deep(.el-descriptions__label.is-bordered-label) {
  width: 96px;
  min-width: 96px;
  max-width: 96px;
  text-align: justify;
  text-align-last: justify;
  text-justify: inter-ideograph;
  vertical-align: middle;
  line-height: 1.6;
}

.task-card-descriptions-extra .el-button {
  font-size: 12px;
  padding: 4px 10px;
}

.task-card-descriptions :deep(.el-descriptions__body .el-descriptions__table) {
  table-layout: fixed;
}

.task-card-descriptions :deep(.el-descriptions__cell) {
  word-break: break-word;
}

.task-card-descriptions-extra {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.descr-value-text {
  color: #303133;
  line-height: 1.7;
}

.task-status-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.info-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: flex-start;
  min-width: 0;
  max-width: 100%;
}

.task-card-descriptions :deep(.el-descriptions__body .el-descriptions__table) {
  table-layout: fixed;
  width: 100%;
}

.task-card-descriptions :deep(.el-descriptions__content) {
  min-width: 0;
  max-width: 100%;
  word-break: break-word;
}

.task-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  padding: 14px 18px 18px;
  border-top: 1px solid #ebeef5;
  background: #fff;
  cursor: default;
}

.task-card-footer-left,
.task-card-footer-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.task-mapping-editor-dialog :deep(.el-dialog__body) {
  max-height: calc(100vh - 140px);
  overflow-y: auto;
  padding-top: 8px;
}

.mapping-dialog-desc {
  margin: 0 0 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
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
  cursor: pointer;
}

.schema-tag-problem {
  --el-tag-bg-color: #fef0f0 !important;
  --el-tag-border-color: #fbc4c4 !important;
  --el-tag-text-color: #c45656 !important;
}

.schema-tag-active {
  --el-tag-bg-color: #d9ecff !important;
  --el-tag-border-color: #79bbff !important;
  --el-tag-text-color: #337ecc !important;
  box-shadow: 0 0 0 1px rgba(64, 158, 255, 0.2);
}

.target-legend {
  gap: 18px;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #606266;
  font-size: 13px;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
  border: 1px solid transparent;
}

.legend-dot-active {
  background: #d9ecff;
  border-color: #79bbff;
}

.legend-dot-problem {
  background: #fef0f0;
  border-color: #fbc4c4;
}

.missing-alert {
  margin-bottom: 12px;
}

.auto-map-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 16px;
  margin-bottom: 16px;
  padding: 12px 14px;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
}

.auto-map-bar :deep(.el-checkbox) {
  margin-right: 0;
}

.auto-map-hint {
  flex: 1 1 220px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
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

.mapping-item-missing {
  border-color: #f3b3b3;
  background: #fff7f7;
  box-shadow: 0 4px 12px rgba(196, 86, 86, 0.12);
}

.mapping-item-active-target {
  border-color: #79bbff;
  box-shadow: 0 14px 26px rgba(64, 158, 255, 0.22);
  transform: translateY(-2px);
}

.mapping-status-tag {
  cursor: pointer;
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

.edit-dialog-footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

.edit-dialog-footer-left,
.edit-dialog-footer-right {
  display: flex;
  align-items: center;
  gap: 10px;
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
  font-size: 13px;
  line-height: 1.75;
}

.page-anchor :deep(.is-active > .el-anchor__link),
.page-anchor :deep(.el-anchor__link:hover) {
  color: #409eff;
}

.unified-task-panel :deep(.el-button--primary) {
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

.unified-task-panel :deep(.el-button--primary.is-plain) {
  --el-button-text-color: #409eff;
  --el-button-border-color: #b3d8ff;
  --el-button-bg-color: #ecf5ff;
  --el-button-hover-text-color: #ffffff;
  --el-button-hover-bg-color: #409eff;
  --el-button-hover-border-color: #409eff;
}

/* 仅统一「主色」类标签；勿覆盖 success/warning/danger/info，否则数据集状态等语义色会全部变蓝 */
.unified-task-panel
  :deep(
    .el-tag:not(.el-tag--success):not(.el-tag--warning):not(.el-tag--danger):not(.el-tag--info)
  ) {
  --el-tag-bg-color: #ecf5ff;
  --el-tag-border-color: #b3d8ff;
  --el-tag-text-color: #409eff;
}

/* 卡片内目标类别 / 关联数据集：白底（需压过上一段主色标签规则） */
.unified-task-panel .task-card .info-tags--card-plain :deep(.el-tag) {
  --el-tag-bg-color: #ffffff !important;
  --el-tag-border-color: #e4e7ed !important;
  --el-tag-text-color: #303133 !important;
  background-color: #ffffff !important;
  border-color: #e4e7ed !important;
  color: #303133 !important;
}

.unified-task-panel :deep(.el-alert--warning) {
  --el-alert-bg-color: #fdfdfd;
  --el-alert-border-color: #ebeef5;
  --el-alert-title-color: #606266;
}

.unified-task-panel :deep(.el-input__wrapper),
.unified-task-panel :deep(.el-select__wrapper),
.unified-task-panel :deep(.el-textarea__inner) {
  box-shadow: 0 0 0 1px #dcdfe6 inset;
  background: #ffffff;
}

.unified-task-panel :deep(.el-input__wrapper:hover),
.unified-task-panel :deep(.el-select__wrapper:hover) {
  box-shadow: 0 0 0 1px #a0cfff inset;
}

.unified-task-panel :deep(.el-input__wrapper.is-focus),
.unified-task-panel :deep(.el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 1px #409eff inset;
}

@media (max-width: 1400px) {
  .doc-left {
    display: none;
  }

  .anchor-wrapper {
    right: 8px;
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
  .dataset-config-row,
  .mapping-summary-row {
    flex-direction: column;
    gap: 8px;
  }

  .dataset-config-label,
  .mapping-summary-label {
    width: auto;
    min-width: 0;
    padding-top: 0;
    line-height: 1.6;
  }

  .task-card-descriptions :deep(.el-descriptions__title) {
    align-items: flex-start;
  }

  .task-card-title-text {
    white-space: normal;
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

/* 与 originalDatasetManage 内嵌 el-card 同构：工具行 + 可滚动主体 + footer 分页 */
.content.content--embed {
  padding: 0;
  background: transparent;
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

.original-dataset-panel {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  overflow: hidden;
}

.original-dataset-panel :deep(.el-card__body) {
  padding: 6px 16px 0;
}

.original-dataset-panel :deep(.el-card__footer) {
  padding: 12px 16px;
  border-top: 1px solid #ebeef5;
  background: #fafafa;
}

/* 与 originalDatasetManage 的 .original-dataset-panel__footer 一致，避免分页控件贴边、挤作一团 */
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

.original-dataset-panel.original-dataset-panel--embed {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  border: none;
  border-radius: 0;
}

.original-dataset-panel.original-dataset-panel--embed :deep(.el-card__body) {
  flex: 1 1 0;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding: 4px 0 0;
}

.original-dataset-panel.original-dataset-panel--embed :deep(.el-card__footer) {
  flex-shrink: 0;
  /* 与 content--embed 下「原始数据集」一致：不重复铺灰底，由统合区 section 已提供左右留白 */
  padding: 12px 0 0;
  background: transparent;
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

.table-div--embed-scroll {
  width: 100%;
  min-height: 0;
  flex: 1 1 0;
}
</style>
