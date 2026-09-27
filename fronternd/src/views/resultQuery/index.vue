

<template>
  <div class="content app-list-page">
    <PermissionNotice />
    <div class="search-div app-list-toolbar flex-between">
      <div class="flex-start gap-8">
        <el-button size="small" @click="clearTableColumnFilters">清除列筛选</el-button>
        <el-button size="small" @click="clearTableSort">清除列排序</el-button>
      </div>
      <div class="flex-start">
        <el-input
          v-model="searchName"
          placeholder="按任务名称/指标搜索"
          size="small"
          clearable
          class="result-search"
        />
        <el-button v-permission size="small" type="primary" class="result-manage-button" @click="toggleManageMode">
          {{ manageMode ? '完成管理' : '管理' }}
        </el-button>
        <el-button v-permission
          v-if="manageMode"
          size="small"
          type="danger"
          :disabled="!selectedResults.length || batchDeleting"
          :loading="batchDeleting"
          @click="deleteSelectedResults"
        >批量删除{{ selectedResults.length ? ` (${selectedResults.length})` : '' }}</el-button>
      </div>
    </div>

    <div class="table-div app-list-table">
      <el-table ref="tableRef" :row-key="resultRowKey" class="my-table" :data="pageData" stripe style="width: 100%" size="small"
        v-loading="loading" @filter-change="onTableFilterChange" @sort-change="onTableSortChange" @row-click="openResultDetail"
        @selection-change="handleSelectionChange"
        v-el-height-adaptive-table="{ bottomOffset: 110, isUse: true }">

        <el-table-column
          v-if="manageMode"
          type="selection"
          width="48"
          fixed="left"
          :selectable="canSelectResult"
          :reserve-selection="true"
        />

        <el-table-column prop="resultName" label="项目名称" align="center" fixed="left" width="260"
          column-key="resultName" sortable="custom" :filters="resultNameFilterOptions"
          :filter-method="tableColumnFilterPassAll">
          <template #default="{ row }">{{ row.resultName || row.taskName }}</template>
        </el-table-column>

        <el-table-column label="标签" align="center" width="180">
          <template #default="{ row }">
            <div class="result-tags-cell">
              <el-tag v-for="tag in (row.tags || [])" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
              <el-text v-if="!(row.tags || []).length" size="small" type="info">-</el-text>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="taskName" label="任务名称" align="center" fixed="left" width="260"
          column-key="taskName" sortable="custom" :filters="taskNameFilterOptions"
          :filter-method="tableColumnFilterPassAll" />


        <el-table-column prop="userName" label="创建人" align="center" column-key="userName"
          sortable="custom" :filters="userFilterOptions" :filter-method="tableColumnFilterPassAll" />

        <el-table-column prop="modelType" label="模型类别" align="center" width="120" column-key="modelType"
          sortable="custom" :filters="modelTypeFilterOptions" :filter-method="tableColumnFilterPassAll" />

        <el-table-column prop="dataset" label="数据集" align="center" width="120" column-key="dataset"
          sortable="custom" :filters="datasetFilterOptions" :filter-method="tableColumnFilterPassAll" />

        <el-table-column prop="networkName" label="网络名称" align="center" width="130" column-key="networkName"
          sortable="custom" :filters="networkFilterOptions" :filter-method="tableColumnFilterPassAll" />

        <el-table-column prop="time" label="完成时间" align="center" width="170" sortable="custom">
          <template #default="{ row }">
            <el-text size="small">{{ row.training ? '训练中' : showDateTime(row.time) }}</el-text>
          </template>
        </el-table-column>

        <el-table-column label="耗时" align="center" width="100">
          <template #default="{ row }">
            <el-text size="small">{{ row.training ? formatElapsed(row.time) : formatDuration(row.durationSeconds) }}</el-text>
          </template>
        </el-table-column>

        <el-table-column prop="map" label="mAP" align="center" width="80" sortable="custom"/>

        <el-table-column prop="ap50" label="AP50" align="center" width="80" sortable="custom"/>

        <el-table-column prop="ap75" label="AP75" align="center" width="80" sortable="custom"/>

        <el-table-column prop="aps" label="APs" align="center" width="80" sortable="custom"/>

        <el-table-column prop="apm" label="APm" align="center" width="80" sortable="custom"/>

        <el-table-column prop="apl" label="APl" align="center" width="80" sortable="custom"/>

        <el-table-column label="操作" align="center" width="185" fixed="right">
          <template #default="{ row }">
            <div class="result-action-group" @click.stop>
              <el-dropdown trigger="click" @command="command => handleResultCommand(command, row)">
                <el-button type="primary" size="small" class="result-operation-button">
                  其他操作<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item :disabled="logLoadingTaskId === row.taskId" command="log">
                      <el-icon><View /></el-icon>查看日志
                    </el-dropdown-item>
                    <el-dropdown-item :disabled="configLoadingTaskId === row.taskId" command="config">
                      <el-icon><Document /></el-icon>查看配置
                    </el-dropdown-item>
                    <el-dropdown-item :disabled="openPathLoadingId === row.id" command="path">
                      <el-icon><FolderOpened /></el-icon>打开路径
                    </el-dropdown-item>
                    <el-dropdown-item :disabled="row.training || inferenceLoadingId === row.id" command="infer">
                      <el-icon><Aim /></el-icon>模型推理
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-button v-permission v-if="!row.training" type="danger" size="small" @click="deleteResult(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>

      </el-table>
    </div>

    <div class="flex-end app-list-footer">
      <el-pagination background size="small" v-model:current-page="currentPage" v-model:page-size="currentSize"
        :page-sizes="[5, 10, 20, 30, 40, 50]" layout="total, sizes, prev, pager, next, jumper" :total="total"
        @size-change="handlePageSizeChange" />
    </div>

    <el-dialog v-model="detailVisible" width="min(760px, 92vw)" top="8vh" :title="`结果详情 - ${detailForm.resultName || detailForm.taskName || ''}`">
      <div v-loading="detailLoading" class="result-detail-dialog">
        <el-form label-width="92px" class="result-detail-form">
          <el-form-item label="项目名称">
            <el-input v-model="detailForm.resultName" :disabled="detailForm.training" maxlength="120" show-word-limit />
          </el-form-item>
          <el-form-item label="标签">
            <el-select v-model="detailForm.tags" multiple filterable allow-create default-first-option
              :disabled="detailForm.training" placeholder="输入后按 Enter 添加标签" style="width: 100%" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="detailForm.remark" type="textarea" :rows="3" :disabled="detailForm.training"
              maxlength="500" show-word-limit placeholder="填写该次训练结果的备注" />
          </el-form-item>
        </el-form>
        <el-descriptions :column="2" border size="small" class="result-detail-info">
          <el-descriptions-item label="训练任务">{{ detailForm.taskName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="模型类别">{{ detailForm.modelType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ showDateTime(detailForm.time) || '-' }}</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ formatDuration(detailForm.durationSeconds) }}</el-descriptions-item>
          <el-descriptions-item label="配置快照" :span="2">{{ detailConfigStatus || detailForm.configPath || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-permission v-if="!detailForm.training" type="primary" :loading="detailSaving" @click="saveResultDetail">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="textVisible" width="min(960px, 90vw)" top="6vh" :title="textTitle" draggable>
      <pre class="result-text-viewer">{{ textContent }}</pre>
      <template #footer>
        <el-button @click="textVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="inferenceVisible" width="min(920px, 92vw)" top="6vh"
      :title="`模型推理 - ${inferenceRow?.resultName || inferenceRow?.taskName || ''}`" @closed="resetInferenceDialog">
      <div class="inference-dialog">
        <el-alert type="info" :closable="false" show-icon
          title="上传一张本地图片，系统会使用本次训练结果的权重进行推理并保存带标注的输出。" />
        <el-upload v-model:file-list="inferenceFileList" action="#" :auto-upload="false" :limit="1"
          accept="image/jpeg,image/png,image/bmp,image/gif" :on-change="handleInferenceFileChange"
          :on-exceed="handleInferenceFileExceed" class="inference-upload">
          <el-button type="primary" plain>选择图片</el-button>
          <template #tip><div class="el-upload__tip">支持 JPG、PNG、BMP、GIF，单张不超过 10 MB。</div></template>
        </el-upload>
        <el-image v-if="inferencePreviewUrl" :src="inferencePreviewUrl" fit="contain" class="inference-image-preview" />
        <el-empty v-else description="请选择待推理图片" :image-size="88" />
        <div v-if="inferenceResult" class="inference-output">
          <el-divider content-position="left">推理结果（{{ inferenceResult.detections?.length || 0 }} 个目标）</el-divider>
          <el-image :src="inferenceResult.imageUrl" fit="contain" class="inference-image-preview" preview-teleported />
          <el-table v-if="inferenceResult.detections?.length" :data="inferenceResult.detections" border size="small" max-height="220">
            <el-table-column prop="label" label="类别" min-width="150" />
            <el-table-column prop="score" label="置信度" width="110">
              <template #default="{ row }">{{ Number(row.score).toFixed(4) }}</template>
            </el-table-column>
            <el-table-column prop="bbox" label="检测框 (x1, y1, x2, y2)" min-width="260">
              <template #default="{ row }">{{ row.bbox?.join(', ') }}</template>
            </el-table-column>
          </el-table>
          <el-text v-else type="info">未检测到符合当前模型阈值的目标。</el-text>
        </div>
      </div>
      <template #footer>
        <el-button @click="inferenceVisible = false">关闭</el-button>
        <el-button type="primary" :disabled="!inferenceFile" :loading="inferenceLoadingId !== null" @click="runInference">开始推理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import PermissionNotice from '@/components/PermissionNotice.vue'
    import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
    import { ElMessage, ElMessageBox } from 'element-plus';
    import { Aim, ArrowDown, Document, FolderOpened, View } from '@element-plus/icons-vue';
    import dayjs   from 'dayjs'
    import { ResultQueryService, TrainTaskService } from "../../api/api";

    const searchName = ref("");

    const currentPage = ref(1);
    const currentSize = ref(10);
    const tableRef = ref(null);
    const loading = ref(false);
    const manageMode = ref(false);
    const selectedResults = ref([]);
    const batchDeleting = ref(false);
    const textVisible = ref(false);
    const textTitle = ref('查看');
    const textContent = ref('');
    const logLoadingTaskId = ref(null);
    const configLoadingTaskId = ref(null);
    const openPathLoadingId = ref(null);
    const inferenceLoadingId = ref(null);
    const inferenceVisible = ref(false);
    const inferenceRow = ref(null);
    const inferenceFileList = ref([]);
    const inferenceFile = ref(null);
    const inferencePreviewUrl = ref('');
    const inferenceResult = ref(null);
    const detailVisible = ref(false);
    const detailLoading = ref(false);
    const detailSaving = ref(false);
    const detailConfigStatus = ref('');
    const detailForm = ref({
      id: null, resultName: '', taskName: '', remark: '', tags: [], training: false,
      modelType: '', time: null, durationSeconds: null, configPath: '',
    });


    const showDateTime = (time) => {
      return time ? dayjs(time).format('YYYY/MM/DD HH:mm:ss') : '';
    }
    const nowTick = ref(Date.now());
    const formatElapsed = (startedAt) => {
      const started = new Date(startedAt).getTime();
      const seconds = Number.isFinite(started)
        ? Math.max(0, Math.floor((nowTick.value - started) / 1000))
        : 0;
      const hours = Math.floor(seconds / 3600);
      const minutes = Math.floor((seconds % 3600) / 60);
      const rest = seconds % 60;
      return [hours, minutes, rest].map(value => String(value).padStart(2, '0')).join(':');
    };
    const formatDuration = (durationSeconds) => {
      if (durationSeconds === null || durationSeconds === undefined) return '—';
      const seconds = Math.max(0, Number(durationSeconds) || 0);
      const hours = Math.floor(seconds / 3600);
      const minutes = Math.floor((seconds % 3600) / 60);
      const rest = Math.floor(seconds % 60);
      return [hours, minutes, rest].map(value => String(value).padStart(2, '0')).join(':');
    };

    const tableData = ref([])
    const deletingId = ref(null)
    const columnFilters = ref({
      resultName: [],
      taskName: [],
      userName: [],
      modelType: [],
      dataset: [],
      networkName: [],
    });
    const columnSort = ref(null);

    const makeFilterOptions = (prop) => computed(() =>
      [...new Set(tableData.value.map(row => row[prop]).filter(value => value !== null && value !== undefined && value !== ''))]
        .sort((a, b) => String(a).localeCompare(String(b), 'zh-CN'))
        .map(value => ({ text: String(value), value }))
    );
    const userFilterOptions = makeFilterOptions('userName');
    const resultNameFilterOptions = makeFilterOptions('resultName');
    const taskNameFilterOptions = makeFilterOptions('taskName');
    const modelTypeFilterOptions = makeFilterOptions('modelType');
    const datasetFilterOptions = makeFilterOptions('dataset');
    const networkFilterOptions = makeFilterOptions('networkName');

    const filteredData = computed(() => {
      const q = searchName.value.trim().toLowerCase();
      return tableData.value.filter(row => {
        const filterPass = Object.entries(columnFilters.value).every(([prop, values]) =>
          !values.length || values.includes(row[prop])
        );
        if (!filterPass) return false;
        if (!q) return true;
        return [
          row.id, row.taskId, row.resultName, row.taskName, row.remark, (row.tags || []).join(' '), row.userName, row.modelType,
          row.dataset, row.networkName, row.time, row.map, row.ap50,
          row.ap75, row.aps, row.apm, row.apl, row.durationSeconds,
        ].join(' ').toLowerCase().includes(q);
      });
    });

    const sortedData = computed(() => {
      const rows = [...filteredData.value];
      const runningRows = rows.filter(row => row.training)
        .sort((a, b) => new Date(b.time || 0) - new Date(a.time || 0));
      const finishedRows = rows.filter(row => !row.training);
      const sort = columnSort.value;
      if (!sort?.prop || !sort?.order) {
        finishedRows.sort((a, b) => new Date(b.time || 0) - new Date(a.time || 0));
        return [...runningRows, ...finishedRows];
      }
      const factor = sort.order === 'descending' ? -1 : 1;
      finishedRows.sort((a, b) => compareValues(a[sort.prop], b[sort.prop], sort.prop) * factor);
      return [...runningRows, ...finishedRows];
    });

    const total = computed(() => filteredData.value.length);
    const pageData = computed(() => {
      const start = (currentPage.value - 1) * currentSize.value;
      return sortedData.value.slice(start, start + currentSize.value);
    });

    const refreshing = ref(false);
    const loadResults = async (silent = false) => {
      if (refreshing.value) return;
      refreshing.value = true;
      if (!silent) loading.value = true;
      try {
        const res = await ResultQueryService.queryList({
          current: 1,
          size: 100000,
        });
        if (res.code === 0) {
            tableData.value = res.data.records || [];
        } else {
          ElMessage.warning(res.msg);
        }
      } finally {
        if (!silent) loading.value = false;
        refreshing.value = false;
      }
    };

    const resultRowKey = (row) => row.training ? `training-${row.taskId}` : `result-${row.id}`;

    const compareValues = (a, b, prop) => {
      if (prop === 'time') return new Date(a || 0) - new Date(b || 0);
      if (['id', 'taskId', 'map', 'ap50', 'ap75', 'aps', 'apm', 'apl'].includes(prop)) {
        return Number(a ?? Number.NEGATIVE_INFINITY) - Number(b ?? Number.NEGATIVE_INFINITY);
      }
      return String(a ?? '').localeCompare(String(b ?? ''), 'zh-CN');
    };

    const tableColumnFilterPassAll = () => true;
    const onTableFilterChange = (filters = {}) => {
      Object.keys(columnFilters.value).forEach(key => {
        if (Object.prototype.hasOwnProperty.call(filters, key)) {
          columnFilters.value[key] = filters[key] || [];
        }
      });
      currentPage.value = 1;
    };
    const onTableSortChange = ({ prop, order }) => {
      columnSort.value = order ? { prop, order } : null;
      currentPage.value = 1;
    };
    const clearTableColumnFilters = () => {
      Object.keys(columnFilters.value).forEach(key => { columnFilters.value[key] = []; });
      currentPage.value = 1;
      nextTick(() => tableRef.value?.clearFilter?.());
    };
    const clearTableSort = () => {
      columnSort.value = null;
      currentPage.value = 1;
      nextTick(() => tableRef.value?.clearSort?.());
    };
    const handlePageSizeChange = () => { currentPage.value = 1; };

    const canSelectResult = (row) => !row.training && !!row.id;
    const handleSelectionChange = (rows) => {
      selectedResults.value = Array.isArray(rows) ? rows.filter(canSelectResult) : [];
    };
    const toggleManageMode = () => {
      manageMode.value = !manageMode.value;
      selectedResults.value = [];
      nextTick(() => tableRef.value?.clearSelection?.());
    };

    watch(searchName, () => { currentPage.value = 1; });

    const deleteResult = async (row) => {
      if (deletingId.value !== null) return;
      try {
        await ElMessageBox.confirm(
          `确定删除训练结果“${row.taskName}”吗？对应的本地训练文件也会一并删除，此操作不可恢复。`,
          '删除结果',
          {
            confirmButtonText: '确定删除',
            cancelButtonText: '取消',
            type: 'warning',
          }
        );
        deletingId.value = row.id;
        const res = await ResultQueryService.deleteResult(row.id);
        if (res.code !== 0) {
          ElMessage.warning(res.msg || '删除失败');
          return;
        }
        ElMessage.success(res.data?.fileDeleted ? '记录和本地文件已删除' : '记录已删除（本地文件不存在）');
        if (pageData.value.length === 1 && currentPage.value > 1) {
          currentPage.value -= 1;
        }
        await loadResults();
      } catch (error) {
        if (error !== 'cancel' && error !== 'close') {
          ElMessage.error(error?.msg || '删除失败');
        }
      } finally {
        deletingId.value = null;
      }
    };

    const deleteSelectedResults = async () => {
      const rows = selectedResults.value.filter(canSelectResult);
      if (!rows.length || batchDeleting.value) return;
      try {
        await ElMessageBox.confirm(
          `确定删除已选中的 ${rows.length} 条训练结果吗？对应的本地训练文件也会一并删除，此操作不可恢复。`,
          '批量删除结果',
          { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
        );
        batchDeleting.value = true;
        const res = await ResultQueryService.deleteResults(rows.map(row => row.id));
        if (res.code !== 0) {
          ElMessage.error(res.msg || '批量删除失败');
          return;
        }
        const data = res.data || {};
        const deletedCount = Number(data.deletedCount || 0);
        const errors = Array.isArray(data.errors) ? data.errors : [];
        if (errors.length) {
          ElMessage.warning(`已删除 ${deletedCount} 条，${errors.length} 条删除失败：${errors[0]}`);
        } else {
          ElMessage.success(`已删除 ${deletedCount} 条训练结果及对应本地文件`);
        }
        selectedResults.value = [];
        tableRef.value?.clearSelection?.();
        if (pageData.value.length === rows.length && currentPage.value > 1) currentPage.value -= 1;
        await loadResults();
      } catch (error) {
        if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.msg || '批量删除失败');
      } finally {
        batchDeleting.value = false;
      }
    };

    const applyResultDetail = (record = {}, extra = {}) => {
      detailForm.value = {
        id: record.id ?? null,
        resultName: record.resultName || record.taskName || '',
        taskName: record.taskName || '',
        remark: record.remark || '',
        tags: Array.isArray(record.tags) ? [...record.tags] : [],
        training: !!record.training,
        modelType: record.modelType || '',
        time: record.time || null,
        durationSeconds: record.durationSeconds ?? null,
        configPath: extra.configPath || record.configPath || '',
      };
      detailConfigStatus.value = extra.configStatus || '';
    };

    const openResultDetail = async (row) => {
      if (!row) return;
      detailVisible.value = true;
      detailLoading.value = true;
      applyResultDetail(row);
      if (row.training || !row.id) {
        detailConfigStatus.value = '训练中的项目尚未生成结果配置快照';
        detailLoading.value = false;
        return;
      }
      try {
        const res = await ResultQueryService.getResultDetail(row.id);
        if (res.code !== 0) {
          ElMessage.warning(res.msg || '读取结果详情失败');
          return;
        }
        const data = res.data || {};
        applyResultDetail(data.result || row, data);
      } catch (error) {
        ElMessage.error(error?.msg || error?.message || '读取结果详情失败');
      } finally {
        detailLoading.value = false;
      }
    };

    const saveResultDetail = async () => {
      if (!detailForm.value.id || detailSaving.value) return;
      detailSaving.value = true;
      try {
        const res = await ResultQueryService.updateResultMetadata({
          id: detailForm.value.id,
          resultName: detailForm.value.resultName,
          remark: detailForm.value.remark,
          tags: detailForm.value.tags,
        });
        if (res.code !== 0) {
          ElMessage.warning(res.msg || '保存结果详情失败');
          return;
        }
        const data = res.data || {};
        applyResultDetail(data.result || detailForm.value, data);
        const index = tableData.value.findIndex(item => item.id === detailForm.value.id);
        if (index >= 0) tableData.value[index] = { ...tableData.value[index], ...detailForm.value };
        ElMessage.success('结果详情已保存');
      } catch (error) {
        ElMessage.error(error?.msg || error?.message || '保存结果详情失败');
      } finally {
        detailSaving.value = false;
      }
    };

    const viewResultLog = async (row) => {
      if (!row.taskId) {
        ElMessage.warning('该结果没有关联训练任务，无法读取日志');
        return;
      }
      logLoadingTaskId.value = row.taskId;
      try {
        const res = await TrainTaskService.latestTrainLog({ id: row.taskId, lines: 2000 });
        if (res.code !== 0 || !res.data) {
          ElMessage.warning(res.msg || '该任务还没有训练日志');
          return;
        }
        const log = res.data;
        textTitle.value = `最新训练日志 - ${row.taskName}`;
        textContent.value = [
          `日志文件：${log.log_path || '-'}`,
          `更新时间：${log.modified_time || '-'}`,
          `显示行数：${log.returned_lines || 0} / ${log.total_lines || 0}`,
          '',
          log.content || '(日志为空)'
        ].join('\n');
        textVisible.value = true;
      } catch (error) {
        ElMessage.error('读取训练日志失败');
      } finally {
        logLoadingTaskId.value = null;
      }
    };
    const viewResultConfigSnapshot = async (row) => {
      if (!row?.id || row.training) {
        ElMessage.warning('训练中的项目尚未生成配置快照');
        return;
      }
      configLoadingTaskId.value = row.taskId || row.id;
      try {
        const res = await ResultQueryService.readResultConfig(row.id);
        if (res.code !== 0 || !res.data) {
          ElMessage.warning(res.msg || '该结果没有可查看的配置快照');
          return;
        }
        const config = res.data;
        textTitle.value = `结果配置快照 - ${row.resultName || row.taskName}`;
        textContent.value = [
          `配置副本：${config.config_path || '-'}`,
          '',
          config.text || '(配置文件为空)'
        ].join('\n');
        textVisible.value = true;
      } catch (error) {
        ElMessage.error(error?.msg || error?.message || '读取结果配置快照失败');
      } finally {
        configLoadingTaskId.value = null;
      }
    };

    const viewResultConfig = async (row) => {
      if (!row.taskId) {
        ElMessage.warning('该结果没有关联训练任务，无法读取配置');
        return;
      }
      configLoadingTaskId.value = row.taskId;
      try {
        const res = await TrainTaskService.readConfig({ id: row.taskId, includeText: true });
        if (res.code !== 0 || !res.data) {
          ElMessage.warning(res.msg || '该任务还没有生成配置文件');
          return;
        }
        const config = res.data;
        textTitle.value = `训练配置 - ${row.taskName}`;
        textContent.value = [
          `配置文件：${config.config_path || '-'}`,
          '',
          config.text || '(配置文件为空)'
        ].join('\n');
        textVisible.value = true;
      } catch (error) {
        ElMessage.error('读取训练配置失败');
      } finally {
        configLoadingTaskId.value = null;
      }
    };
    const openResultPath = async (row) => {
      if (!row.id || row.training) {
        ElMessage.warning('训练中的任务暂不支持打开结果目录');
        return;
      }
      openPathLoadingId.value = row.id;
      try {
        const res = await ResultQueryService.openResultPath(row.id);
        if (res.code !== 0) {
          ElMessage.warning(res.msg || '打开路径失败');
          return;
        }
        ElMessage.success(`已打开：${res.data?.path || '结果目录'}`);
      } catch (error) {
        ElMessage.error(error?.msg || '打开路径失败');
      } finally {
        openPathLoadingId.value = null;
      }
    };
    const clearInferencePreview = () => {
      if (inferencePreviewUrl.value) URL.revokeObjectURL(inferencePreviewUrl.value);
      inferencePreviewUrl.value = '';
    };
    const resetInferenceDialog = () => {
      clearInferencePreview();
      inferenceRow.value = null;
      inferenceFile.value = null;
      inferenceFileList.value = [];
      inferenceResult.value = null;
    };
    const openInferenceDialog = (row) => {
      if (!row?.id || row.training) {
        ElMessage.warning('训练中的任务暂不支持模型推理');
        return;
      }
      resetInferenceDialog();
      inferenceRow.value = row;
      inferenceVisible.value = true;
    };
    const handleInferenceFileChange = (file) => {
      const raw = file?.raw;
      const supported = ['image/jpeg', 'image/png', 'image/bmp', 'image/gif'];
      if (!raw || !supported.includes(raw.type) || raw.size > 10 * 1024 * 1024) {
        ElMessage.warning('请选择不超过 10 MB 的 JPG、PNG、BMP 或 GIF 图片');
        inferenceFile.value = null;
        inferenceFileList.value = [];
        clearInferencePreview();
        return;
      }
      clearInferencePreview();
      inferenceFile.value = raw;
      inferencePreviewUrl.value = URL.createObjectURL(raw);
      inferenceResult.value = null;
    };
    const handleInferenceFileExceed = () => ElMessage.warning('一次只能选择一张推理图片');
    const runInference = async () => {
      if (!inferenceRow.value?.id || !inferenceFile.value || inferenceLoadingId.value !== null) return;
      inferenceLoadingId.value = inferenceRow.value.id;
      inferenceResult.value = null;
      try {
        const formData = new FormData();
        formData.append('id', String(inferenceRow.value.id));
        formData.append('file', inferenceFile.value);
        const res = await ResultQueryService.inferResult(formData);
        if (res.code !== 0 || !res.data?.image_base64) {
          ElMessage.warning(res.msg || '模型推理失败');
          return;
        }
        const data = res.data;
        inferenceResult.value = {
          imageUrl: `data:${data.image_mime || 'image/png'};base64,${data.image_base64}`,
          detections: Array.isArray(data.detections) ? data.detections : [],
        };
        ElMessage.success('模型推理完成');
      } catch (error) {
        ElMessage.error(error?.msg || error?.message || '模型推理失败');
      } finally {
        inferenceLoadingId.value = null;
      }
    };
    const handleResultCommand = (command, row) => {
      if (command === 'log') return viewResultLog(row);
      if (command === 'config') return viewResultConfigSnapshot(row);
      if (command === 'path') return openResultPath(row);
      if (command === 'infer') return openInferenceDialog(row);
    };

const refreshTimer = window.setInterval(() => loadResults(true), 2000);
const elapsedTimer = window.setInterval(() => { nowTick.value = Date.now(); }, 1000);
onBeforeUnmount(() => {
  window.clearInterval(refreshTimer);
  window.clearInterval(elapsedTimer);
  clearInferencePreview();
});
loadResults();
   

</script>

<style scoped>

    .content {
    padding: 0;
    background: transparent;
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

    .gap-8 {
    gap: 8px;
    }

    .result-search {
    width: 220px;
    }

    .result-manage-button {
    margin-left: 8px;
    }

    .result-operation-button {
    min-width: 86px;
    }

    .result-action-group {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    }

    .result-tags-cell {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 4px;
    }

    .result-detail-dialog {
    padding: 2px 4px;
    }

    .result-detail-form {
    margin-bottom: 12px;
    }

    .result-detail-info :deep(.el-descriptions__label) {
    width: 100px;
    }

    .table-div {
    padding-top: 8px;
    padding-bottom: 8px;
    }

    .text-white {
    color: white;
    }

    .my-table.el-table--small {
    border-radius: 4px;
    }

    .result-text-viewer {
    min-height: 360px;
    max-height: 68vh;
    margin: 0;
    padding: 16px;
    overflow: auto;
    white-space: pre-wrap;
    word-break: break-word;
    line-height: 1.55;
    color: #303133;
    background: #f7f9fc;
    border: 1px solid #e4e7ed;
    border-radius: 8px;
    }

    .inference-dialog {
      display: flex;
      flex-direction: column;
      gap: 14px;
    }

    .inference-upload {
      align-self: flex-start;
    }

    .inference-image-preview {
      width: 100%;
      max-height: 420px;
      background: #f7f9fc;
      border: 1px solid #e4e7ed;
      border-radius: 8px;
    }

    .inference-output {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }



    .button-container {
    display: flex;
    justify-content: center; /* 水平居中 */
    margin-top: 16px; 
    }

    .button-container .mt-4 {
    width: auto; /* 取消100%宽度 */
    min-width: 120px; /* 设置最小宽度 */
    }

</style>
