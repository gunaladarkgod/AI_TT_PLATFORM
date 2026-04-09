<template>
  <div class="content-div">
    <!-- 搜索区域 -->
    <div class="search-div flex-between">
      <div class="flex-start">
        <el-select v-model="searchType" placeholder="数据集类型" size="small" @change="fetchList" style="width: 150px;">
          <el-option label="全部类型" :value="null" />
          <el-option label="自然" :value="0" />
          <el-option label="遥感可见光" :value="1" />
          <el-option label="遥感SAR" :value="2" />
          <el-option label="遥感红外" :value="3" />
        </el-select>
        <el-input v-model="searchName" placeholder="名称查询" size="small" style="width: 150px;" @keyup.enter="fetchList" />
        <el-button @click="fetchList" size="small">
          <el-icon><i class="iconfont icon-sousuo"></i></el-icon>
        </el-button>
      </div>
    </div>

    <!-- 主体：左右布局 -->
    <div class="split-layout">
      <!-- 左侧：任务数据集列表 -->
      <div class="left-panel">
        <el-table
        class="my-table"
        :data="taskDatasetList"
        stripe
        size="small"
        @row-click="handleRowClick"
        :row-class-name="getRowClassName"
        v-el-height-adaptive-table="{ bottomOffset: 70, isUse: true }"
      >
        <el-table-column type="index" label="序号" width="60" align="center" />
        
        <!-- 数据集名称 -->
        <el-table-column prop="name" label="数据集名称" align="center" />

        <!-- 传感器类型 -->
        <el-table-column prop="sensorType" label="传感器类型" align="center" />

        <!-- 目标类型 -->
        <el-table-column prop="targetType" label="目标类型" align="center" />

        <!-- 类别数 -->
        <el-table-column prop="coreClassNum" label="类别数" align="center" />

        <!-- 图片数 -->
        <el-table-column label="图片数" align="center">
          <template #default="{ row }">
            {{ (row.coreImgNum || 0) + (row.supImgNum || 0) }}
          </template>
        </el-table-column>

        <!-- 标注数 -->
        <el-table-column label="标注数" align="center">
          <template #default="{ row }">
            {{ (row.coreAnnoNum || 0) + (row.supAnnoNum || 0) }}
          </template>
        </el-table-column>

        <!-- 创建用户 -->
        <el-table-column prop="username" label="创建用户" align="center" />

        <!-- 选择 -->
        <el-table-column label="选择" align="center" width="60">
          <template #default="{ row }">
            <el-button
              circle
              size="small"
              :type="selectedRowKey === row.id ? 'primary' : ''"
              @click="handleRowSelect(row)"
            >
              <el-icon v-if="selectedRowKey === row.id">
                <CircleCheck />
              </el-icon>
              <el-icon v-else>
                <Circle />
              </el-icon>
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

      <!-- 右侧：训测划分配置 -->
      <div class="right-panel" v-if="selectedDataset">
        <h4>训测划分配置：{{ selectedDataset.name }}</h4>

        <!-- 目标子集 -->
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

        <!-- 预训练子集 -->
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

        <!-- 统一保存按钮 -->
        <el-button type="primary" @click="saveTrainTestSplit" class="mt-16" style="width: 100%">
          保存训测划分
        </el-button>
      </div>

      <!-- 右侧：无选择时提示 -->
      <div class="right-panel empty" v-else>
        <el-empty description="请选择左侧的任务数据集进行配置" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { TaskDatasetService } from '@/api/api'
import { useRouter } from 'vue-router'

const router = useRouter();

// 筛选状态
const searchName = ref('')
const searchType = ref(null)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const taskDatasetList = ref([])

// 右侧配置
const selectedDataset = ref(null)
const selectedRowKey = ref(null)
const targetSubsets = ref([])
const pretrainDatasets = ref([]); 
const selectedTestIds = ref([])
const selectedOriginalIds = ref([])
const trainRatio = ref(null)

// 划分参数
const splitLimit = ref(1); // 划分上限
const splitOptions = ref([]); // 生成的方案列表

// 获取列表
const fetchList = async () => {
  const params = {
    current: currentPage.value,
    size: pageSize.value,
    name: searchName.value || undefined,
    type: searchType.value !== null ? searchType.value : undefined
  };
  const res = await TaskDatasetService.queryList(params);
  if (res.code === 0) {
    taskDatasetList.value = res.data?.records || [];
    total.value = res.data?.total || 0;
  } else {  
    ElMessage.warning(res.msg || '加载失败');
    taskDatasetList.value = [];
    total.value = 0;
  }
};



// 分页控制
const handleSizeChange = (val) => {
  pageSize.value = val
  fetchList()
}
const handleCurrentChange = (val) => {
  currentPage.value = val
  fetchList()
}

// 点击配置
const handleRowClick = (row) => {
  
  console.log('选中的任务数据集 name:', row.name)

  selectedRowKey.value = row.id;
  selectedDataset.value = row;
  selectedTestIds.value = [];
  selectedOriginalIds.value = [];
  trainRatio.value = null;

  const promises = [];

  //直接拆分 core_* 字段（目标子集）
  promises.push(
    TaskDatasetService.getTargetSubsets(row.id)
      .then(res => {
        if (res?.code === 0) {
          // 直接赋值，不再拆分
          targetSubsets.value = res.data || [];
        } else {
          targetSubsets.value = [];
        }
      })
      .catch(err => {
        console.error('加载目标子集失败:', err);
        ElMessage.error('加载目标子集失败');
        targetSubsets.value = [];
      })
  );

  //直接拆分 sup_* 字段（预训练子集）
  const supIds = (row.supId || "").split("_").filter(id => id.trim());
  const supNames = (row.supName || "").split("_").filter(name => name.trim());
  pretrainDatasets.value = supIds.map((id, i) => ({
    id: id.trim(),
    name: supNames[i] ? supNames[i].trim() : id,
    imgNum: row.supImgNum || 0
  }));

  drawerVisible.value = true;
};

// 行高亮
const getRowClassName = ({ row }) => {
  return selectedRowKey.value === row.id ? 'selected-row' : ''
}



// 判断小数据集是否可选（用于 disabled）
const isTestSelectionDisabled = (purpose) => {
  // 优先：如果用户输入了训测比，则全部不可手动勾选
  if (trainRatio.value != null) {
    return true;
  }
};

// 1. 生成所有 C(n, k) 组合
const getCombinations = (arr, k) => {
  if (k === 0) return [[]];
  if (arr.length === 0) return [];
  const [first, ...rest] = arr;
  const withFirst = getCombinations(rest, k - 1).map(c => [first, ...c]);
  const withoutFirst = getCombinations(rest, k);
  return [...withFirst, ...withoutFirst];
};

// 2. Jaccard 距离（越大越不同）
const jaccardDistance = (a, b) => {
  const A = new Set(a), B = new Set(b);
  const inter = new Set([...A].filter(x => B.has(x)));
  const union = new Set([...A, ...B]);
  return 1 - (inter.size / union.size);
};

// 3. 贪心选择多样性最大的方案
const selectDiversePlans = (allPlans, limit) => {
  if (allPlans.length <= limit) return allPlans;
  const selected = [allPlans[0]];
  const remaining = allPlans.slice(1);
  while (selected.length < limit && remaining.length > 0) {
    let bestIdx = 0, maxMinDist = -1;
    for (let i = 0; i < remaining.length; i++) {
      const cand = remaining[i];
      let minDist = Infinity;
      for (const sel of selected) {
        minDist = Math.min(minDist, jaccardDistance(cand, sel));
      }
      if (minDist > maxMinDist) {
        maxMinDist = minDist;
        bestIdx = i;
      }
    }
    selected.push(remaining[bestIdx]);
    remaining.splice(bestIdx, 1);
  }
  return selected;
};




const saveTrainTestSplit = async () => {
  let allTestPlans = [];
  let trainOriginalIds = [...selectedOriginalIds.value]; // 预训练子集总是存在的

  // 直接使用 targetSubsets（已从前端拆分好）
  if (trainRatio.value != null && splitLimit.value != null) {
    const testRatio = 1 - trainRatio.value;
    const eligible = targetSubsets.value; // 所有目标子集
    const k = Math.max(1, Math.min(eligible.length - 1, Math.floor(eligible.length * testRatio)));

    if (k > 0 && eligible.length >= k) {
      const items = eligible.map(item => item.id);
      const allCombs = getCombinations(items, k);
      allTestPlans = selectDiversePlans(allCombs, splitLimit.value);
    } else {
      // 无法生成有效方案，退化到空
      allTestPlans = [[]];
    }
  } else {
    // 手动模式
    allTestPlans = selectedTestIds.value.length > 0 
      ? [[...selectedTestIds.value]] 
      : [[]];
  }

  const payload = {
    taskId: selectedDataset.value.id,     // 用 taskId
    testPlans: allTestPlans,
    trainOriginalIds: trainOriginalIds
  };

   console.log('【前端发送的 payload】', JSON.stringify(payload, null, 2));

  try {
    const res = await TaskDatasetService.saveTrainTestSplit(payload);
    if (res?.code === 0) {
      ElMessage.success(`成功保存 ${allTestPlans.length} 个划分方案`);
      router.push('/preprocess');
    } else {
      ElMessage.warning(res?.msg || '保存失败');
    }
  } catch (err) {
    ElMessage.error('提交失败');
  }
};

// 初始加载
fetchList()
</script>

<style scoped lang="scss">
.content-div {
  padding: 10px;
}
.table-div {
  padding: 8px 0;
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
.right-panel.empty {
  display: flex;
  align-items: center;
  justify-content: center;
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
</style>