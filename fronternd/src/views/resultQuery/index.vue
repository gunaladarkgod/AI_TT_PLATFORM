

<template>
  <div class="content">
    <div class="search-div flex-between">
      <div class="flex-start gap-8">
        <el-button size="small" @click="clearTableColumnFilters">清除列筛选</el-button>
        <el-button size="small" @click="clearTableSort">清除列排序</el-button>
      </div>
      <div class="flex-start">
        <el-input
          v-model="searchName"
          placeholder="Type to search"
          size="small"
          clearable
          class="result-search"
        />
      </div>
    </div>

    <div class="table-div">
      <el-table ref="tableRef" :row-key="resultRowKey" class="my-table" :data="pageData" stripe style="width: 100%" size="small"
        v-loading="loading" @filter-change="onTableFilterChange" @sort-change="onTableSortChange"
        v-el-height-adaptive-table="{ bottomOffset: 70, isUse: true }">


        <el-table-column prop="id" label="id" align="center" width="80" fixed="left" sortable="custom">
          <template #default="scope">
            <el-text size="small">{{ scope.row.training ? '—' : `#${scope.row.id}` }}</el-text>
          </template>
        </el-table-column>

        <el-table-column prop="taskId" label="任务id" align="center" fixed="left" sortable="custom" />

        <el-table-column prop="taskName" label="任务名称" align="center" fixed="left" sortable="custom" />


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

        <el-table-column label="操作" align="center" width="90" fixed="right">
          <template #default="{ row }">
            <el-tag v-if="row.training" type="warning" size="small">训练中</el-tag>
            <el-button v-else type="danger" link size="small" @click="deleteResult(row)">删除</el-button>
          </template>
        </el-table-column>

      </el-table>
    </div>

    <div class="flex-end">
      <el-pagination background size="small" v-model:current-page="currentPage" v-model:page-size="currentSize"
        :page-sizes="[5, 10, 20, 30, 40, 50]" layout="total, sizes, prev, pager, next, jumper" :total="total"
        @size-change="handlePageSizeChange" />
    </div>
  </div>
</template>

<script setup>
    import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
    import { ElMessage, ElMessageBox } from 'element-plus';
    import dayjs   from 'dayjs'
    import { ResultQueryService } from "../../api/api";

    const searchName = ref("");

    const currentPage = ref(1);
    const currentSize = ref(10);
    const tableRef = ref(null);
    const loading = ref(false);


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
          row.id, row.taskId, row.taskName, row.userName, row.modelType,
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

const refreshTimer = window.setInterval(() => loadResults(true), 2000);
const elapsedTimer = window.setInterval(() => { nowTick.value = Date.now(); }, 1000);
onBeforeUnmount(() => {
  window.clearInterval(refreshTimer);
  window.clearInterval(elapsedTimer);
});
loadResults();
   

</script>

<style scoped>

    .content {
    padding: 10px;
    background-color: #f5f7fa;
    
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

    .content ::v-deep(.el-table) {
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
    border-radius: 8px;
    overflow: hidden;
    }

    .content ::v-deep(.el-table__header) {
    background-color: #fafafa;
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
