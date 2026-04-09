<template>
  <div class="content">
    <div class="search-div flex-between">
      <div class="flex-start">
        <div class="filter-row mid-filters" style="margin-bottom: 8px;">
          <el-select
            v-model="midFilters.sensorType"
            placeholder="传感器类型"
            size="small"
            class="width-200"
            clearable
          >
            <el-option
              v-for="opt in sensorTypeOptions"
              :key="opt"
              :label="opt"
              :value="opt"
            />
          </el-select>

          <el-select
            v-model="midFilters.targetType"
            placeholder="目标类型"
            class="width-200"
            size="small"
            clearable
          >
            <el-option
              v-for="opt in targetTypeOptions"
              :key="opt"
              :label="opt"
              :value="opt"
            />
          </el-select>

          <el-input
            v-model="midFilters.name"
            placeholder="名称"
            class="width-200"
            size="small"
            clearable
          />

          <el-input
            v-model="midFilters.user"
            placeholder="创建用户"
            class="width-200"
            size="small"
            clearable
          />

          <el-button
            type="primary"
            style="margin-left: 10px;"
            targetType="primary"
            @click="handleFilter"
            size="small"
          >
            <el-text size="small" class="text-white">筛选</el-text>
          </el-button>

          <el-button
            type="primary"
            style="margin-left: 20px;"
            targetType="primary"
            @click="showDialog = true"
            size="small"
          >
            <el-text size="small" class="text-white">创建</el-text>
          </el-button>

          <!-- 创建数据集弹窗 -->
          <el-dialog
            v-model="showDialog"
            title="任务数据集名称"
            width="400px"
          >
            <el-form :model="form" :rules="formRules" ref="formRef" label-width="0">
              <el-form-item prop="name">
                <el-input 
                  v-model="form.name" 
                  placeholder="例如：航目标检测_2025Q4" 
                  show-word-limit
                  size="large"
                ></el-input>
              </el-form-item>
            </el-form>
            <template #footer>
              <span class="dialog-footer">
                <el-button @click="cancelCreate">取消</el-button>
                <el-button type="primary" @click="confirmCreate">创建</el-button>
              </span>
            </template>
          </el-dialog>
        </div>
      </div>
    </div>

    <div class="table-div">
      <el-table
        ref="tableRef"
        class="my-table"
        :data="filteredTableData"
        stripe
        style="width: 100%"
        size="small"
        v-el-height-adaptive-table="{ bottomOffset: 70, isUse: true }"
        :row-key="getRowKey"
        @row-click="handleRowClick"
      >
        <!-- 父级行列：保持原样 -->
        <el-table-column
          prop="id"
          label="序号"
          align="center"
          fixed="left"
          :width="columnWidths.id"
        />
        <el-table-column
          prop="name"
          label="数据集名称"
          align="center"
          fixed="left"
          :width="columnWidths.name"
        />
        <el-table-column
          prop="sensorType"
          label="传感器类型"
          align="center"
          fixed="left"
          :width="columnWidths.sensorType"
        />
        <el-table-column
          prop="targetType"
          label="目标类型"
          align="center"
          fixed="left"
          :width="columnWidths.targetType"
        />
        <el-table-column
          prop="classNum"
          label="类别数"
          align="center"
          fixed="left"
          :width="columnWidths.classNum"
        />
        <el-table-column
          label="类别名称"
          align="center"
          :min-width="columnWidths.labels"
          show-overflow-tooltip
        >
          <template #default="scope">
            <div class="tag-container">
              <el-tag
                v-for="item in getVisibleTags(scope.row.labels)"
                :key="item.name"
                style="opacity: 0.5;"
                size="small"
              >
                <span style="color: black; opacity: 1;">
                  {{ item.name }}:{{ item.count }}
                </span>
              </el-tag>

              <el-tag
                v-if="scope.row.labels?.length > 5"
                size="small"
                type="info"
                style="cursor: pointer;"
                @click.stop="showAllTags(scope.row)"
              >
                +{{ scope.row.labels.length - 5 }} 更多
              </el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column
          prop="imageNum"
          label="图片数"
          align="center"
          fixed="right"
          :width="columnWidths.imageNum"
        />
        <el-table-column
          prop="sampleNum"
          label="样本数"
          align="center"
          fixed="right"
          :width="columnWidths.sampleNum"
        />
        <el-table-column
          prop="createdBy"
          label="创建用户"
          align="center"
          fixed="right"
          :width="columnWidths.createdBy"
        />

        <!-- 父级行占位列（子集） -->
        <el-table-column
          label="子集"
          align="center"
          fixed="right"
          :width="columnWidths.operation"
        >
          <template #default>
            <!-- 父行无按钮 -->
          </template>
        </el-table-column>

        <!-- 新增的删除按钮列 - 只有父级行显示 -->
        <el-table-column label="操作" align="center" fixed="right" :width="columnWidths.del">
          <template #default="scope">
            <el-button 
              
              type="danger" 
              size="small"
               @click.stop="confirmDelete(scope.row)"
            >
              <el-text size="small" class="text-white">删除</el-text>
            </el-button>
          </template>
        </el-table-column>



        <!-- 展开列：隐藏图标，点击整行展开 -->
        <el-table-column type="expand" width="0">
          <template #default="props">
            <div v-loading="props.row.loading" class="child-table-wrapper">
              <el-table
                :data="props.row.children"
                class="child-table"
                :show-header="false"
                size="small"
                @row-click="handleChildRowClick"
                :cell-style="childCellStyle"
              >
                <!-- 子表列宽与父表对齐 -->
                <el-table-column
                  prop="id"
                  align="center"
                  fixed="left"
                  :width="columnWidths.id"
                />
                <el-table-column
                  prop="name"
                  align="center"
                  fixed="left"
                  :width="columnWidths.name"
                />
                <el-table-column
                  prop="sensorType"
                  align="center"
                  fixed="left"
                  :width="columnWidths.sensorType"
                />
                <el-table-column
                  prop="targetType"
                  align="center"
                  fixed="left"
                  :width="columnWidths.targetType"
                />
                <el-table-column
                  prop="classNum"
                  align="center"
                  fixed="left"
                  :width="columnWidths.classNum"
                />
                <el-table-column
                  align="center"
                  :min-width="columnWidths.labels"
                  show-overflow-tooltip
                >
                  <template #default="scope">
                    <div class="tag-container">
                      <el-tag
                        v-for="item in getVisibleTags(scope.row.labels)"
                        :key="item.name"
                        style="opacity: 0.5;"
                        size="small"
                      >
                        <span style="color: black; opacity: 1;">
                          {{ item.name }}:{{ item.count }}
                        </span>
                      </el-tag>

                      <el-tag
                        v-if="scope.row.labels?.length > 5"
                        size="small"
                        type="info"
                        style="cursor: pointer;"
                        @click.stop="showAllTags(scope.row)"
                      >
                        +{{ scope.row.labels.length - 5 }} 更多
                      </el-tag>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column
                  prop="imageNum"
                  align="center"
                  fixed="right"
                  :width="columnWidths.imageNum"
                />
                <el-table-column
                  prop="sampleNum"
                  align="center"
                  fixed="right"
                  :width="columnWidths.sampleNum"
                />
                <el-table-column
                  prop="createdBy"
                  align="center"
                  fixed="right"
                  :width="columnWidths.createdBy"
                />

                <!-- 子集“显示”按钮 -->
                <el-table-column
                  align="center"
                  fixed="right"
                  :width="columnWidths.operation"
                >
                  <template #default="scope">
                    <el-button
                      type="primary"
                      size="small"
                      @click.stop="handleShowDetail(scope.row)"
                    >
                      <el-text size="small" class="text-white">显示</el-text>
                    </el-button>
                  </template>
                </el-table-column>
                <el-table-column
                  
                  align="center"
                  fixed="right"
                  :width="columnWidths.del"
                />
              </el-table>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 标签详情弹窗 -->
      <el-dialog
        v-model="dialogVisible"
        :title="currentRow ? '所有标签 - ' + currentRow.name : '所有标签'"
        width="500px"
      >
        <div>
          <el-tag
            v-for="item in currentRow?.labels"
            :key="item.id"
            style="opacity: 0.5; margin-bottom: 8px;"
            size="small"
          >
            <span style="color: black; opacity: 1;">
              {{ item.name }}:{{ item.count }}
            </span>
          </el-tag>
        </div>
        <template #footer>
          <el-button @click="dialogVisible = false">关闭</el-button>
        </template>
      </el-dialog>

      <!-- 类别图片预览弹窗（带标注框） -->
      <el-dialog
        v-model="detailDialogVisible"
        :title="
          currentDetail
            ? `${currentDetail.name} - 类别图片预览`
            : '类别图片预览'
        "
        width="50%"
        top="5vh"
      >
        <!-- 加一个加载状态 -->
        <div v-if="previewLoading" class="image-preview-dialog">
          <div style="padding: 24px; text-align: center; color: #909399;">
            图片加载中…
          </div>
        </div>

        <!-- 图片真正渲染在这里 -->
        <div v-else-if="currentDetail" class="image-preview-dialog">
          <div
            class="category-section"
            v-for="category in currentDetail.labels"
            :key="category.id"
          >
            <div class="category-header">
              <h3 class="category-title">
                <el-tag style="margin-right: 8px; opacity: 0.7;">
                  <el-text style="color: black; opacity: 1;">
                    {{ category.name }}
                  </el-text>
                </el-tag>
                <span>类别图片 ({{ category.count }} 个样本)</span>
              </h3>
            </div>
            <div class="image-row">
              <div
                v-for="(image, index) in getCategoryImages(category.name)"
                :key="index"
                class="image-item"
              >
                <!-- 有标注：SVG 叠加多边形 -->
                <svg
                  v-if="
                    objectsMeta[image.url]?.width &&
                    objectsMeta[image.url]?.height
                  "
                  class="preview-image"
                  :viewBox="`0 0 ${objectsMeta[image.url].width} ${objectsMeta[image.url].height}`"
                  preserveAspectRatio="xMidYMid meet"
                >
                  <image
                    :href="image.url"
                    :width="objectsMeta[image.url].width"
                    :height="objectsMeta[image.url].height"
                  />
                  <g
                    v-for="(obj, idx2) in objectsMeta[image.url].objects || []"
                    :key="idx2"
                  >
                    <polygon
                      :points="pointsAttr(obj.points)"
                      class="anno-poly"
                    />
                  </g>
                </svg>

                <!-- 还没有标注数据：先出图，再异步拉取 -->
                <img
                  v-else
                  :src="image.url"
                  :alt="`${category.name} 图片 ${index + 1}`"
                  class="preview-image"
                  @load="ensureAnnoFor(image.url)"
                  @error="onImageError($event)"
                />

                <div class="image-info">
                  <span class="image-name">
                    {{ category.name }}_{{ index + 1 }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <template #footer>
          <el-button @click="detailDialogVisible = false">关闭</el-button>
        </template>
      </el-dialog>
    </div>

    <div class="flex-end">
      <el-pagination
        background
        size="small"
        v-model:current-page="currentPage"
        v-model:page-size="currentSize"
        :page-sizes="[5, 10, 20, 30, 40, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        @current-change="handleCurrentChange"
        @size-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from "@vue/reactivity";
import { useRouter } from 'vue-router';
import dayjs from "dayjs";
import { TaskDatabaseManageService } from "../../api/api";
import { request } from "../../api/axios";
import { ElMessage, ElMessageBox } from 'element-plus'

const now = new Date();

const currentPage = ref(1);
const currentSize = ref(10);
const total = ref(0);


const router = useRouter();



// 创建弹窗
const showDialog = ref(false);
const formRef = ref();
const taskName = ref('');


// 表单数据
const form = reactive({
  name: ''
});

// 表单验证规则
const formRules = reactive({
  name: [
    { required: true, message: '例如：航目标检测_2025Q4'},
    
  ]
});

// 方法定义
const cancelCreate = () => {
  showDialog.value = false;
  resetForm();
};

const confirmCreate = () => {
  formRef.value.validate((valid) => {
    if (valid) {
      // 将数据集名称赋值给taskName
      taskName.value = form.name;
      
      // 关闭弹窗
      showDialog.value = false;
      
      // 跳转到 engineProject 页面并使用路由参数传递数据集名称
      router.push({
        name: 'engineProject', // 使用路由名称
        query: {
          taskName: form.name
        }
      });
      // 重置表单
      resetForm();
      
    } else {
      ElMessage.error('请输入有效的数据集名称');
      return false;
    }
  });
};

const resetForm = () => {
  form.name = '';
};





// 列宽（原样）
const columnWidths = reactive({
  id: 100,
  name: 120,
  sensorType: 120,
  targetType: 120,
  classNum: 120,
  labels: 300,
  imageNum: 120,
  sampleNum: 120,
  createdBy: 120,
  operation: 150,
  del:100
});

const sensorTypeOptions = ref([
  "自然",
  "遥感可见光",
  "遥感SAR",
  "遥感红外",
  "遥感多光谱",
]);
const targetTypeOptions = ref(["舰船", "飞机", "车辆", "复合", "其他"]);
const midFilters = reactive({
  sensorType: "",
  targetType: "",
  name: "",
  user: "",
});

const showDate = (time) => (time ? dayjs(time).format("YYYY/MM/DD") : "");
const showDateTime = (time) =>
  time ? dayjs(time).format("YYYY/MM/DD HH:mm:ss") : "";

const tableRef = ref();

// 初始示例数据（会被接口数据覆盖）
const tableData = ref([
  {
    id: "1",
    name: "DZM",
    sensorType: "遥感SAR",
    targetType: "舰船",
    classNum: 5,
    labels: [
      { id: 1, name: "Ship", color: "green", count: 1000 },
      { id: 2, name: "plane", color: "gray", count: 700 },
      { id: 3, name: "bike", color: "purple", count: 700 },
      { id: 4, name: "bus", color: "red", count: 900 },
      { id: 5, name: "car", color: "yellow", count: 400 },
      { id: 6, name: "train", color: "blue", count: 500 },
      { id: 7, name: "subway", color: "pink", count: 600 },
    ],
    imageNum: 100,
    sampleNum: 100,
    createdBy: "admin",
    children: [
      {
        id: "1_1",
        name: "DZM_core",
        sensorType: "遥感SAR",
        targetType: "车辆",
        classNum: 2,
        labels: [
          { id: 1, name: "Ship", color: "green", count: 1000 },
          { id: 2, name: "plane", color: "gray", count: 700 },
        ],
        imageNum: 100,
        sampleNum: 100,
        createdBy: "admin",
      },
      {
        id: "1_2",
        name: "DZM_sup",
        sensorType: "遥感SAR",
        targetType: "飞机",
        classNum: 3,
        labels: [
          { id: 3, name: "bike", color: "purple", count: 700 },
          { id: 4, name: "bus", color: "red", count: 900 },
          { id: 5, name: "car", color: "yellow", count: 400 },
        ],
        imageNum: 200,
        sampleNum: 200,
        createdBy: "admin",
        children: [],
      },
    ],
  },
]);

// 子表格 cell 样式（原来模板里有引用，这里给一个默认实现）
const childCellStyle = () => {
  return {};
};

// 行 key
const getRowKey = (row) => row.id;

// 父行点击：切换展开
const handleRowClick = (row, column, event) => {
  if (column.property === "operation") return;
  toggleRowExpansion(row);
};

// 子行点击：阻止冒泡，避免影响父行展开
const handleChildRowClick = (row, column, event) => {
  event.stopPropagation();
};

const toggleRowExpansion = (row) => {
  if (!tableRef.value) return;
  tableRef.value.toggleRowExpansion(row);
};

// 筛选后的数据
const filteredTableData = computed(() => {
  return tableData.value.filter((item) => {
    if (midFilters.sensorType && item.sensorType !== midFilters.sensorType) {
      return false;
    }
    if (midFilters.targetType && item.targetType !== midFilters.targetType) {
      return false;
    }
    if (
      midFilters.name &&
      !item.name.toLowerCase().includes(midFilters.name.toLowerCase())
    ) {
      return false;
    }
    if (
      midFilters.user &&
      !item.createdBy.toLowerCase().includes(midFilters.user.toLowerCase())
    ) {
      return false;
    }
    return true;
  });
});

// 详情弹窗状态
const detailDialogVisible = ref(false);
const currentDetail = ref(null);

// 预览数据缓存：{ [rowId]: { labelName: [{url,name}, ...] } }
const previewCache = ref({});
const previewLoading = ref(false);

// 标注缓存：{ [imgUrl]: { width, height, objects:[...] } }
const objectsMeta = ref({});

// 把 subset-image URL 转成 subset-objects URL
const objectsUrlFromImageUrl = (imgUrl) => {
  // 例如：/taskDataset/37/subset-image?subset=core&img=1
  // ->    /taskDataset/37/subset-objects?subset=core&img=1
  return imgUrl.replace("/subset-image?", "/subset-objects?");
};

// 拉取某张图的标注信息（只在第一次需要）
const ensureAnnoFor = async (src) => {
  if (objectsMeta.value[src]) return;
  try {
    const url = objectsUrlFromImageUrl(src);
    const resp = await fetch(url);
    const res = await resp.json();
    const data = res?.data || res;
    if (data && (data.width || Array.isArray(data.objects))) {
      objectsMeta.value = {
        ...objectsMeta.value,
        [src]: data,
      };
    }
  } catch (e) {
    // 失败就当没标注，不影响图片显示
    console.warn("加载标注失败", e);
  }
};

const pointsAttr = (points) => {
  return Array.isArray(points)
    ? points.map((p) => p.join(",")).join(" ")
    : "";
};

const onImageError = (e) => {
  e.target.style.display = "none";
};

// 解析：子表行属于哪个任务数据集、core/sup 哪个子集
const parseSubsetInfo = (row) => {
  const idStr = String(row.id || "");
  const parts = idStr.split("_");
  const taskDatasetId = Number(parts[0]) || null;

  let subset = "";
  if (row.dataPath && row.dataPath.indexOf("/target_subset/") !== -1) {
    subset = "core";
  } else if (
    row.dataPath &&
    row.dataPath.indexOf("/pretrain_subset/") !== -1
  ) {
    subset = "sup";
  }
  return { taskDatasetId, subset };
};

// 点击“显示”：调用后端 /taskDataset/{id}/subset-preview
const handleShowDetail = async (row) => {
  currentDetail.value = row;
  detailDialogVisible.value = true;

  // 已经有缓存，直接用
  if (previewCache.value[row.id]) return;

  const { taskDatasetId, subset } = parseSubsetInfo(row);
  if (!taskDatasetId || !subset) {
    console.warn("无法解析任务数据集 ID 或子集类型", row);
    return;
  }

  previewLoading.value = true;
  try {
    const res = await request(
      `/taskDataset/${taskDatasetId}/subset-preview`,
      { subset, perLabel: 3 },
      "get",
      "application/json;charset=UTF-8"
    );

    const obj = typeof res === "string" ? JSON.parse(res) : res;
    const data = obj.data || obj;
    const items = Array.isArray(data.items) ? data.items : [];

    const map = {};
    items.forEach((it) => {
      const label = it.label || it.name || it.className;
      const imgs = Array.isArray(it.images)
        ? it.images
        : Array.isArray(it.urls)
        ? it.urls
        : [];
      if (!label || !imgs.length) return;

      map[label] = imgs.map((u, idx) => ({
        url: u,
        name: `${label}_${idx + 1}`,
      }));
    });

    // 写入缓存，触发界面重渲染
    previewCache.value = {
      ...previewCache.value,
      [row.id]: map,
    };

    // 预取一下标注（不阻塞 UI）
    setTimeout(() => {
      Object.values(map).forEach((imgs) => {
        imgs.forEach((img) => ensureAnnoFor(img.url));
      });
    }, 0);
  } catch (e) {
    console.error("加载子集预览失败", e);
  } finally {
    previewLoading.value = false;
  }
};

// 取某个类别的图片列表（从预览缓存里拿）
const getCategoryImages = (categoryName) => {
  const row = currentDetail.value;
  if (!row) return [];
  const cacheForRow = previewCache.value[row.id] || {};
  return cacheForRow[categoryName] || [];
};

// el-image 预览用的 src 列表（现在只是备用，如果以后要恢复 el-image）
const getPreviewSrcList = (categoryName) => {
  return getCategoryImages(categoryName).map((img) => img.url);
};

// 筛选按钮（逻辑不变）
const handleFilter = () => {
  console.log("筛选条件:", midFilters);
  console.log("筛选结果数量:", filteredTableData.value.length);
};

// 标签弹窗
const dialogVisible = ref(false);
const currentRow = ref(null);

const getVisibleTags = (labels) => {
  return labels?.slice(0, 5);
};

const showAllTags = (row) => {
  currentRow.value = row;
  dialogVisible.value = true;
};


const confirmDelete = (row) => {
  ElMessageBox.confirm(`确定要删除数据集 "${row.name}" 吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    handleDelete(row)
  }).catch(() => {
    ElMessage.info('已取消删除')
  })
}

const handleDelete = (row) => {
  TaskDatabaseManageService.del({ id: row.id}).then(res => {
    if (res.code === 0) {
      ElMessage.success(res.msg)
      handleCurrentChange()
    } else {
      ElMessage.warning(res.msg)
    }
  })
}





// 分页：保持原逻辑，从后端取任务数据集列表
const handleCurrentChange = () => {
  TaskDatabaseManageService.queryList({
    current: currentPage.value,
    size: currentSize.value,
    }).then(function (res) {
    if (res.code === 0) {
      tableData.value = res.data.records;
      total.value = res.data.total;
      console.log("获取任务数据集成功");
    } else {
      if (window.ElMessage) {
        window.ElMessage.warning(res.msg);
      }
    }
  });
};

handleCurrentChange();
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

.table-div {
  padding-top: 8px;
  padding-bottom: 8px;
}

.text-white {
  color: white;
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
  justify-content: center;
  margin-top: 16px;
}

.button-container .mt-4 {
  width: auto;
  min-width: 120px;
}

.panel-title {
  font-weight: 600;
  color: #333;
  margin-bottom: 8px;
  font-size: 18px;
}

.filter-row {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
  flex-shrink: 0;
}

.mid-filters {
  display: flex;
  gap: 8px;
  flex-wrap: nowrap;
}

.my-table {
  cursor: pointer;
}

/* 隐藏展开图标 */
:deep(.el-table__expand-icon) {
  display: none;
}

/* 展开行样式 */
:deep(.el-table__expanded-cell) {
  padding: 0 !important;
  background-color: #f8f9fa;
  border-bottom: 1px solid #e0e0e0;
}

/* 子表容器 */
.child-table-wrapper {
  padding: 0;
  margin: 0;
}

/* 子表 */
.child-table {
  margin: 0;
  background-color: #f8f9fa;
}

/* 子表行 */
:deep(.child-table .el-table__row) {
  background-color: #f8f9fa;
}

:deep(.child-table .el-table__row:hover) {
  background-color: #e9ecef;
}

/* 子表单元格 */
:deep(.child-table .el-table__cell) {
  padding: 16px 0;
  background-color: #f8f9fa;
  border-bottom: 1px solid #e8e8e8;
}

/* 父表单元格 */
:deep(.my-table .el-table__cell) {
  padding: 16px 0;
}

/* 子表 body 背景 */
:deep(.child-table .el-table__body) {
  background-color: #f8f9fa;
}

/* 子表首列缩进 + 小箭头 */
:deep(.child-table .el-table__row > .el-table__cell:first-child) {
  position: relative;
  padding-left: 20px;
}

:deep(.child-table .el-table__row > .el-table__cell:first-child::before) {
  content: "↳";
  position: absolute;
  left: 8px;
  color: #999;
  font-size: 12px;
}

/* 弹窗样式 */
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

/* 图片行：三列均分 */
.image-row {
  display: flex;
  gap: 16px;
  justify-content: space-between;
}

.image-item {
  flex: 1;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  overflow: hidden;
  transition: all 0.3s ease;
  background: white;
  max-width: calc(33.333% - 11px);
}

.image-item:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

/* 关键：正方形缩略图（SVG / IMG 都用这个类） */
.preview-image {
  width: 100%;
  aspect-ratio: 1 / 1;
  display: block;
  cursor: pointer;
}

.image-info {
  padding: 8px;
  text-align: center;
  background: #fafafa;
}

.image-name {
  font-size: 12px;
  color: #606266;
  word-break: break-all;
}

/* 滚动条 */
.image-preview-dialog::-webkit-scrollbar {
  width: 6px;
}

.image-preview-dialog::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

.image-preview-dialog::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
}

.image-preview-dialog::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

/* 标注 polygon 样式（和子集划分页保持一致） */
.anno-poly {
  fill: rgba(59, 130, 246, 0.12);
  stroke: #3b82f6;
  stroke-width: 2;
}

/* 响应式 */
@media (max-width: 768px) {
  .image-row {
    flex-direction: column;
    gap: 12px;
  }

  .image-item {
    max-width: 100%;
  }
}
</style>
