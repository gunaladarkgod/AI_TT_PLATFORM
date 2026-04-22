<!-- instanceDatabase/create/index.vue -->
<template>
  <div class="content-div" :class="{ 'content-div--embed': embedMode }">
    <div class="create-interface">
      <div class="create-header">
      <!--
        <el-button @click="$router.push('/instanceDatabase/list')" size="small" type="primary" text>
          <el-icon><ArrowLeft /></el-icon>返回
        </el-button>
        -->
        <h2 v-if="!embedHideCreateTitle" class="create-title">创建实例数据集</h2>
      </div>
      <div class="create-form">
        <!-- 预处理脚本选择区域 - 左右并排 -->
        <div class="augmentation-section full-width">
          <div class="scripts-container">
            <!-- 左侧：增强脚本 -->
            <div class="script-column">
              <div class="script-selector">
                <span class="script-label">选择增强脚本：</span>
                <el-select
                  v-model="selectedEnhancementScript"
                  placeholder="请选择增强脚本（可选）"
                  size="large"
                  style="width: 300px;"
                  clearable
                >
                  <el-option
                    v-for="script in enhancementScripts"
                    :key="script.id"
                    :label="script.name"
                    :value="script.id"
                  />
                </el-select>
              </div>
              <!-- 增强参数 -->
              <div v-if="selectedEnhancementScript" class="script-params">
                <!-- 修改后的代码 -->
<div
  v-for="param in selectedEnhancementScriptObj?.paramSchema || []"
  :key="param.name"
  class="param-item"
>
  <label>
    {{ param.label }}
    <span
      v-if="param.min !== undefined || param.max !== undefined"
      class="range-hint"
    >
      ({{ param.min ?? '∞' }} ~ {{ param.max ?? '∞' }})
    </span>
  </label>

  <!-- 根据参数类型动态渲染组件 -->
  <template v-if="param.type === 'boolean'">
    <el-select
      v-model="enhancementParamValues[param.name]"
      class="width-200"
      :placeholder="param.defaultValue !== undefined ? `默认: ${param.defaultValue}` : '请选择'"
    >
      <el-option label="true" value="true" />
      <el-option label="false" value="false" />
    </el-select>
  </template>

  <template v-else-if="param.type === 'number'">
    <el-input
      v-model="enhancementParamValues[param.name]"
      class="width-200"
      :min="param.min"
      :max="param.max"
      :step="1"
      controls-position="right"
      :placeholder="param.defaultValue !== undefined ? `默认: ${param.defaultValue}` : '请输入'"
    />
  </template>

  <template v-else>
    <!-- string 类型或其他类型 -->
    <el-input
      v-model="enhancementParamValues[param.name]"
      class="width-200"
      :placeholder="param.defaultValue !== undefined ? `默认: ${param.defaultValue}` : '请输入'"
    />
  </template>
</div>
              </div>
            </div>
            <!-- 右侧：增广脚本 -->
            <div class="script-column">
              <div class="script-selector">
                <span class="script-label">选择增广脚本：</span>
                <el-select
                  v-model="selectedAugmentationScript"
                  placeholder="请选择增广脚本（可选）"
                  size="large"
                  style="width: 300px;"
                  clearable
                >
                  <el-option
                    v-for="script in augmentationScripts"
                    :key="script.id"
                    :label="script.name"
                    :value="script.id"
                  />
                </el-select>
              </div>
              <!-- 增广参数 -->
             <!-- 增广参数 -->
<div v-if="selectedAugmentationScript" class="script-params">
  <div
    v-for="param in selectedAugmentationScriptObj?.paramSchema || []"
    :key="param.name"
    class="param-item"
  >
    <label>
      {{ param.label }}
      <!-- 修改后的 range-hint -->
    <span
      v-if="(param.min !== undefined && param.min !== null) || (param.max !== undefined && param.max !== null)"
      class="range-hint"
    >
      ({{ param.min != null ? param.min : '∞' }} ~ {{ param.max != null ? param.max : '∞' }})
    </span>
    </label>

    <!-- 根据参数类型动态渲染组件 -->
    <template v-if="param.type === 'boolean'">
      <el-select
        v-model="augmentationParamValues[param.name]"
        class="width-200"
        :placeholder="param.defaultValue !== undefined ? `默认: ${param.defaultValue}` : '请选择'"
      >
        <el-option label="true" value="true" />
        <el-option label="false" value="false" />
      </el-select>
    </template>

    <template v-else-if="param.type === 'number'">
      <el-input
        v-model="augmentationParamValues[param.name]"
        class="width-200"
        :min="param.min"
        :max="param.max"
        :step="1"
        controls-position="right"
        :placeholder="param.defaultValue !== undefined ? `默认: ${param.defaultValue}` : '请输入'"
      />
    </template>

    <template v-else>
      <!-- string 类型或其他类型 -->
      <el-input
        v-model="augmentationParamValues[param.name]"
        class="width-200"
        :placeholder="param.defaultValue !== undefined ? `默认: ${param.defaultValue}` : '请输入'"
      />
    </template>
  </div>
</div>
            </div>
            <!-- 新增：上传按钮 -->
            <div class="upload-actions">
              <el-button type="primary" @click="openUploadDialog" size="large">
                <el-icon><Upload /></el-icon>
                上传脚本
              </el-button>
            </div>
          </div>
        </div>
        <!-- ========== 选择数据（中间实例数据集） =========== -->
        <div class="data-selection">
          <h3 class="section-title">选择数据（中间实例数据集）</h3>
          <!-- 筛选条件 -->
          <div class="filter-section full-width">
            <div class="filter-row-single">
              <el-select v-model="filterConditions.sensorType" placeholder="传感器类型" size="small" clearable style="width: 120px;" @change="applyFilters">
                <el-option v-for="type in sensorTypeOptions" :key="type" :label="type" :value="type"></el-option>
              </el-select>
              <el-select v-model="filterConditions.targetType" placeholder="目标类型" size="small" clearable style="width: 120px;" @change="applyFilters">
                <el-option v-for="type in targetTypeOptions" :key="type" :label="type" :value="type"></el-option>
              </el-select>
              <el-select v-model="filterConditions.username" placeholder="创建用户" size="small" clearable style="width: 120px;" @change="applyFilters">
                <el-option v-for="user in createUserList" :key="user" :label="user" :value="user"></el-option>
              </el-select>
              <el-button @click="resetFilters" size="small">重置筛选</el-button>
            </div>
          </div>
          <!-- 数据表格（表格区滚动，分页条固定在下方） -->
          <div class="selection-table-container">
            <div class="selection-table-scroll">
            <el-table 
              :data="paginatedSelectionData" 
              stripe 
              size="small"
              @selection-change="handleSelectionChange"
              v-loading="datasetsLoading"
              ref="selectionTableRef"
              :row-key="row => row.id"
              class="selection-table"
            >
              <el-table-column type="selection" width="55" :reserve-selection="true"></el-table-column>
              <el-table-column type="index" label="序号" width="60" />
              <el-table-column prop="name" label="数据集名称" width="150"></el-table-column>
              <el-table-column prop="sensorType" label="传感器类型" width="120"></el-table-column>
              <el-table-column prop="targetType" label="目标类型" width="120"></el-table-column>
              <el-table-column prop="classNum" label="类别数" width="80"></el-table-column>
              <el-table-column prop="classList" label="类别名称" width="200">
                <template #default="{ row }">
                  <div class="category-tags-container">
                    <el-tag
                      v-for="(item, index) in getCategoryList(row.classList)"
                      :key="index"
                      size="small"
                      :type="getTagType(index)"
                      style="margin: 2px; white-space: nowrap;"
                    >
                      {{ item.name }}: {{ item.count }}
                    </el-tag>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="imgNum" label="图片数" width="80"></el-table-column>
              <el-table-column prop="annoNum" label="样本数" width="80"></el-table-column>
              <el-table-column prop="username" label="创建用户" width="120"></el-table-column>
            </el-table>
            </div>
            <div class="selection-table-footer">
            <div class="pagination-container flex-end" style="margin-top: 16px;">
              <el-pagination 
                background 
                size="small" 
                v-model:current-page="selectionCurrentPage" 
                v-model:page-size="selectionCurrentSize"
                :page-sizes="[5, 10, 20]" 
                layout="total, sizes, prev, pager, next, jumper" 
                :total="filteredSelectionData.length"
                @size-change="handleSelectionPageChange" 
                @current-change="handleSelectionPageChange" />
            </div>
            </div>
          </div>
        </div>
        <div class="form-actions">
          <el-button @click="$router.push('/instanceDatabase/list')" size="small">取消</el-button>
          <el-button type="primary" @click="showCreateDialog" size="small" :loading="createLoading">创建</el-button>
        </div>
      </div>
    </div>

    <!-- 创建实例数据集对话框 -->
    <el-dialog
      v-model="showCreateInstanceDialog"
      title="创建实例数据集"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-width="120px"
      >
        <el-form-item label="中间实例数据集" class="selection-info">
          <div class="selected-info">
            <el-alert
              v-if="selectedTaskDatasets.length === 0"
              title="未选择任务数据集"
              type="warning"
              :closable="false"
              show-icon
              style="width: 300px;"
            />
            <div v-else>
            <!--
              <el-alert
                v-for="(dataset, index) in selectedTaskDatasets"
                :key="index"
                :title="`${dataset.name} (${getDataTypeLabel(dataset.type)})`"
                type="success"
                :closable="false"
                show-icon
                style="width: 300px; margin-bottom: 4px;"
              />
              -->
              <el-alert
                v-for="(dataset, index) in selectedTaskDatasets"
                :key="index"
                :title="`${dataset.name} `"
                type="success"
                :closable="false"
                show-icon
                style="width: 300px; margin-bottom: 4px;"
              />
            </div>
          </div>
        </el-form-item>
        <el-form-item label="增强脚本" class="selection-info">
          <div class="selected-info">
            <el-alert
              v-if="selectedEnhancementScript"
              :title="`已选择: ${getScriptName(selectedEnhancementScript, enhancementScripts)} (ID: ${selectedEnhancementScript})`"
              type="info"
              :closable="false"
              show-icon
              style="width: 300px;"
            />
            <el-alert
              v-else
              title="未选择增强脚本"
              type="info"
              :closable="false"
              show-icon
              style="width: 300px;"
            />
          </div>
        </el-form-item>
        <el-form-item label="增广脚本" class="selection-info">
          <div class="selected-info">
            <el-alert
              v-if="selectedAugmentationScript"
              :title="`已选择: ${getScriptName(selectedAugmentationScript, augmentationScripts)} (ID: ${selectedAugmentationScript})`"
              type="info"
              :closable="false"
              show-icon
              style="width: 300px;"
            />
            <el-alert
              v-else
              title="未选择增广脚本"
              type="info"
              :closable="false"
              show-icon
              style="width: 300px;"
            />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateInstanceDialog = false" size="small">取消</el-button>
        <el-button type="primary" @click="confirmCreateInstanceDataset" size="small" :loading="createLoading">确定创建</el-button>
      </template>
    </el-dialog>

   <!-- 上传脚本弹窗 -->
<el-dialog
  v-model="showUploadDialog"
  title="上传预处理脚本"
  width="720px"
  @close="resetUploadForm"
>


  <!-- ========== 上传表单 ========== -->
  <el-form :model="uploadForm" :rules="uploadRules" ref="uploadFormRef" label-width="100px">
    <el-form-item label="脚本名称" prop="name">
      <el-input v-model="uploadForm.name" placeholder="例如：自适应增强" />
    </el-form-item>

    <el-form-item label="脚本类型" prop="type">
      <el-select v-model="uploadForm.type" placeholder="请选择">
        <el-option :value="0" label="增强脚本" />
        <el-option :value="1" label="增广脚本" />
      </el-select>
    </el-form-item>

    <el-form-item label="脚本文件" prop="file" required>
      <el-upload
        drag
        :auto-upload="false"
        :on-change="handleFileChange"
        :file-list="uploadFileList"
        accept=".py"
        :limit="1"
        :on-exceed="() => ElMessage.warning('只能上传一个脚本')"
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">
          将 .py 文件拖到此处，或<em>点击上传</em>
        </div>
      </el-upload>
    </el-form-item>

    <el-form-item label="上传人" prop="uploader">
      <el-input v-model="uploadForm.uploader" placeholder="请输入您的用户名" />
    </el-form-item>

    <!-- 参数定义 -->
    <div class="param-section">
      <div class="param-header">
        <span>参数定义（决定 sys.argv[5+] 顺序！）</span>
        <el-button size="small" @click="addParam">+ 添加参数</el-button>
      </div>
      <el-collapse v-model="uploadActiveNames">
        <el-collapse-item
          v-for="(param, index) in uploadForm.paramSchema"
          :key="index"
          :name="index.toString()"
        >
          <template #title>
            {{ param.name || `参数 ${index + 1}` }}
            <el-button size="small" type="danger" @click.stop="removeParam(index)">删除</el-button>
          </template>

          <el-form-item label="参数名 (英文)" required>
            <el-input v-model="param.name" placeholder="如：control_param" />
          </el-form-item>
          <el-form-item label="显示名称">
            <el-input v-model="param.label" placeholder="如：增强强度" />
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="param.type">
              <el-option value="number" label="数值" />
              <el-option value="string" label="字符串" />
              <el-option value="boolean" label="布尔值" />
            </el-select>
          </el-form-item>
          <el-form-item label="必填">
            <el-switch v-model="param.required" />
          </el-form-item>
          <el-form-item label="默认值">
            <!-- 数值类型用普通 input，但 type="number" -->

            <el-input
              v-if="param.type === 'number'"
              v-model="param.defaultValue"
              type="number"
              :min="param.min"
              :max="param.max"
              placeholder="例如：3.0"
              @input="val => param.defaultValue = val === '' ? null : Number(val)"
            />
            <template v-else-if="param.type === 'boolean'">
              <el-select
                v-model="param.defaultValue"
                placeholder="请选择默认值"
              >
                <el-option label="true" value="true" />
                <el-option label="false" value="false" />
              </el-select>
            </template>
            <template v-else>
              <el-input
                v-model="param.defaultValue"
                placeholder="例如：auto"
              />
            </template>
            <!-- 字符串/布尔类型 -->
            <!-- <el-input
              v-else
              v-model="param.defaultValue"
              :placeholder="param.type === 'boolean' ? 'true 或 false' : '例如：auto'"
            /> -->
          </el-form-item>
          <template v-if="param.type === 'number'">
            <el-form-item label="最小值">
              <el-input
                v-model="param.min"
                type="number"
                placeholder="可为空"
                @input="val => param.min = val === '' ? null : Number(val)"
              />
            </el-form-item>
            <el-form-item label="最大值">
              <el-input
                v-model="param.max"
                type="number"
                placeholder="可为空"
                @input="val => param.max = val === '' ? null : Number(val)"
              />
            </el-form-item>
          </template>
        </el-collapse-item>
      </el-collapse>
    </div>

    <!-- <el-alert
      title="⚠️ 重要：参数顺序 = sys.argv[5,6,7...] 位置！"
      type="warning"
      :closable="false"
      style="margin-top: 16px"
    >
      <p>系统将按您添加参数的顺序传参，脚本中必须按顺序读取！</p>
    </el-alert> -->
  </el-form>

    <!-- ========== 脚本规范（前置） ========== -->
    <div class="script-guide">
    <!-- �� 顶部红色警告（最重要！） -->
    <el-alert
      title="❗ 参数顺序决定一切！"
      type="error"
      :closable="false"
      style="margin-bottom: 16px;"
    >
      <p><strong>系统将严格按照你添加参数的顺序传参！</strong></p>
      <p>例如：你先添加 <code>threshold</code>，再添加 <code>flip_prob</code>，则脚本中必须用：</p>
      <div class="code-example">
        threshold = extra[0]  # 第一个参数<br>
        flip_prob = extra[1]  # 第二个参数
      </div>
    </el-alert>

    <div class="guide-section">
      <h4>1. 参数接收规范（必须按顺序！）</h4>
      <p>系统将调用：</p>
      <div class="code-example">
        python 脚本.py img_in lbl_in img_out lbl_out [参数1] [参数2] ...
      </div>
      <p>你的脚本必须这样读取参数：</p>
      <div class="code-example">
        import sys<br>
        img_in = sys.argv[1]   # 输入图像目录<br>
        lbl_in = sys.argv[2]   # 输入标注目录<br>
        img_out = sys.argv[3]  # 输出图像目录<br>
        lbl_out = sys.argv[4]  # 输出标注目录<br>
        extra = sys.argv[5:]   # 自定义参数（<strong>按你添加的顺序</strong>）
      </div>
    </div>

<!--
    <div class="guide-section">
      <h4>2. 必须处理的事项</h4>
      <ul class="guide-list">
        <li>✅ 遍历 <code>img_in</code> 所有图像（支持 .png/.jpg/.tif）</li>
        <li>✅ 将处理结果写入 <code>img_out</code>（建议输出 .png）</li>
        <li>✅ <strong>必须生成标注文件</strong>到 <code>lbl_out</code>（DOTA 格式 .txt）</li>
        <li>✅ 标注文件名必须与图像文件名一致</li>
      </ul>
    </div>

    <div class="guide-section">
      <h4>3. 禁止事项</h4>
      <ul class="guide-list">
        <li>❌ 硬编码路径（如 C:/data/...）</li>
        <li>❌ 弹窗或用户交互（如 input()）</li>
        <li>❌ 修改输入目录（img_in / lbl_in 必须只读）</li>
        <li>❌ 使用未安装的库</li>
      </ul>
    </div>
-->
   <div class="guide-section">
  <h4>4. 完整模板（推荐直接复制修改）</h4>
  <div class="code-example">
    import os, sys, shutil<br>
    import cv2, numpy as np<br>
    <br>
    if __name__ == '__main__':<br>
    &nbsp;&nbsp;if len(sys.argv) < 5:<br>
    &nbsp;&nbsp;&nbsp;&nbsp;print("Usage: script.py img_in lbl_in img_out lbl_out [params...]")<br>
    &nbsp;&nbsp;&nbsp;&nbsp;sys.exit(1)<br>
    &nbsp;&nbsp;<br>
    &nbsp;&nbsp;img_in, lbl_in, img_out, lbl_out = sys.argv[1:5]<br>
    &nbsp;&nbsp;extra = sys.argv[5:]<br>
    &nbsp;&nbsp;<br>
    &nbsp;&nbsp;# ========== 你的逻辑 ==========<br>
    &nbsp;&nbsp;os.makedirs(img_out, exist_ok=True)<br>
    &nbsp;&nbsp;os.makedirs(lbl_out, exist_ok=True)<br>
    &nbsp;&nbsp;<br>
    &nbsp;&nbsp;# ========== 参数顺序示例 ==========<br>
    &nbsp;&nbsp;# 假设你上传时定义了：<br>
    &nbsp;&nbsp;# 1. threshold (数值)<br>
    &nbsp;&nbsp;# 2. enable_flip (布尔)<br>
    &nbsp;&nbsp;<br>
    &nbsp;&nbsp;threshold = float(extra[0]) if len(extra) > 0 else 3.0<br>
    &nbsp;&nbsp;enable_flip = extra[1].lower() == 'true' if len(extra) > 1 else False<br>
    &nbsp;&nbsp;# =================================<br>
    &nbsp;&nbsp;<br>
    &nbsp;&nbsp;# 复制标注（如需处理，替换此处逻辑）<br>
    &nbsp;&nbsp;for f in os.listdir(lbl_in):<br>
    &nbsp;&nbsp;&nbsp;&nbsp;if f.endswith('.txt'):<br>
    &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;shutil.copy2(os.path.join(lbl_in, f), lbl_out)<br>
    &nbsp;&nbsp;<br>
    &nbsp;&nbsp;# 处理图像<br>
    &nbsp;&nbsp;for img_file in os.listdir(img_in):<br>
    &nbsp;&nbsp;&nbsp;&nbsp;if img_file.lower().endswith(('.png', '.jpg', '.tif')):<br>
    &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;# TODO: 使用 threshold 和 enable_flip 处理图像<br>
    &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;pass<br>
    &nbsp;&nbsp;# =============================<br>
    &nbsp;&nbsp;<br>
    &nbsp;&nbsp;print("✅ 脚本执行成功！")
  </div>
</div>
  </div>

  <!-- 确认勾选 -->
  <el-checkbox v-model="uploadForm.agreed" style="margin-top: 12px;">
    我已阅读并遵守脚本编写规范
  </el-checkbox>

  <template #footer>
    <el-button @click="showUploadDialog = false">取消</el-button>
    <el-button 
      type="primary" 
      @click="submitUpload" 
      :loading="uploadLoading"
      :disabled="!uploadForm.agreed"
    >
      确定上传
    </el-button>
  </template>
</el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

defineProps({
  /** 嵌入「数据集管理（dev）」时收紧外边距 */
  embedMode: { type: Boolean, default: false },
  /** 统合页已提供大标题时隐藏内层「创建实例数据集」标题 */
  embedHideCreateTitle: { type: Boolean, default: false }
})
import { ArrowLeft } from '@element-plus/icons-vue'
import { InstanceDatasetService, PreprocessScriptService, SourceInstanceDatasetService } from '@/api/api.js'

// ========== 新增：上传脚本所需 ==========
import { Upload } from '@element-plus/icons-vue'
import { uploadScript } from '@/api/api.js' // 确保你已新增该函数

// ========== 状态（完全保留，仅移除非必要） ==========
const createLoading = ref(false)
const datasetsLoading = ref(false)
const selectionTableRef = ref()
const showCreateInstanceDialog = ref(false)
const createFormRef = ref()


// 默认不选择
// const selectedAugmentationScript = ref('')
// const selectedEnhancementScript = ref('')

// 默认选择不执行
const selectedAugmentationScript = ref(2)
const selectedEnhancementScript = ref(1)

const augmentationScripts = ref([])
const enhancementScripts = ref([])
const enhancementParamValues = ref({})
const augmentationParamValues = ref({})
const selectionData = ref([])
const filteredSelectionData = ref([])
const selectedTaskDatasets = ref([])
const selectionCurrentPage = ref(1)
const selectionCurrentSize = ref(10)

const sensorTypeOptions = ref([])
const targetTypeOptions = ref([])
const createUserList = ref([])

const filterConditions = reactive({
  sensorType: '',
  targetType: '',
  name: '',
  username: ''
})

const createForm = reactive({ name: '', description: '' })
const createRules = { name: [{ required: true, message: '请输入实例数据集名称', trigger: 'blur' }] }

const dataTypeList = ref([
  { label: '自然', value: 0 },
  { label: '可见光', value: 1 },
  { label: '遥感SAR', value: 2 },
  { label: '遥感红外', value: 3 }
])

//上传脚本
const showUploadDialog = ref(false)
const uploadLoading = ref(false)
const uploadFileList = ref([])
const uploadFormRef = ref()
const uploadActiveNames = ref([])

const uploadForm = reactive({
  name: '',
  type: null,
  file: null,
  uploader: '', // ← 新增
  paramSchema: [], // 参数列表
  agreed: false // ← 新增这行
})

const uploadRules = {
  name: [{ required: true, message: '请输入脚本名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  uploader: [{ required: true, message: '请输入上传人', trigger: 'blur' }]
}
//上传脚本

// ========== 计算属性 ==========
const selectedEnhancementScriptObj = computed(() => enhancementScripts.value.find(s => s.id === selectedEnhancementScript.value) || null)
const selectedAugmentationScriptObj = computed(() => augmentationScripts.value.find(s => s.id === selectedAugmentationScript.value) || null)

const paginatedSelectionData = computed(() => {
  const start = (selectionCurrentPage.value - 1) * selectionCurrentSize.value
  const end = start + selectionCurrentSize.value
  return filteredSelectionData.value.slice(start, end)
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
const getDataTypeLabel = (type) => {
  const found = dataTypeList.value.find(item => item.value == type)
  return found ? found.label : type
}
const getScriptName = (scriptId, scriptList) => {
  if (!scriptId || !Array.isArray(scriptList)) return '未知脚本'
  const script = scriptList.find(s => s.id == scriptId)
  return script ? script.name : '未知脚本'
}

const loadPreprocessScripts = async () => {
  try {
    console.log('开始加载预处理脚本...')
    try {
      const augmentationResponse = await PreprocessScriptService.getAugmentationScripts()
      let parsedAugResponse = augmentationResponse
      if (typeof augmentationResponse === 'string') {
        try {
          parsedAugResponse = JSON.parse(augmentationResponse)
        } catch (parseError) {
          console.error('解析增广脚本响应失败:', parseError)
          parsedAugResponse = []
        }
      }
      if (Array.isArray(parsedAugResponse)) {
        augmentationScripts.value = parsedAugResponse
      } else if (parsedAugResponse && (parsedAugResponse.code === 200 || parsedAugResponse.code === 0)) {
        augmentationScripts.value = parsedAugResponse.data || []
      } else {
        augmentationScripts.value = []
      }
    } catch (augError) {
      console.error('加载增广脚本失败:', augError)
      augmentationScripts.value = []
    }
    try {
      const enhancementResponse = await PreprocessScriptService.getEnhancementScripts()
      let parsedEnhResponse = enhancementResponse
      if (typeof enhancementResponse === 'string') {
        try {
          parsedEnhResponse = JSON.parse(enhancementResponse)
        } catch (parseError) {
          console.error('解析增强脚本响应失败:', parseError)
          parsedEnhResponse = []
        }
      }
      if (Array.isArray(parsedEnhResponse)) {
        enhancementScripts.value = parsedEnhResponse
      } else if (parsedEnhResponse && (parsedEnhResponse.code === 200 || parsedEnhResponse.code === 0)) {
        enhancementScripts.value = parsedEnhResponse.data || []
      } else {
        enhancementScripts.value = []
      }
    } catch (enhError) {
      console.error('加载增强脚本失败:', enhError)
      enhancementScripts.value = []
    }
  } catch (error) {
    console.error('加载预处理脚本失败:', error)
    augmentationScripts.value = []
    enhancementScripts.value = []
  }
}

const loadSourceDatasetsForSelection = async () => {
  datasetsLoading.value = true
  try {
    const res = await SourceInstanceDatasetService.list({ presentOnDisk: true })
    let rawData = res
    if (typeof rawData === 'string') {
      try {
        rawData = JSON.parse(rawData)
      } catch (e) {
        console.error('响应是字符串但无法解析为JSON:', rawData)
        ElMessage.error('数据格式错误')
        selectionData.value = []
        filteredSelectionData.value = []
        return
      }
    }
    let data = []
    if (rawData && typeof rawData === 'object') {
      if (Array.isArray(rawData)) {
        data = rawData
      } else if (Array.isArray(rawData.data)) {
        data = rawData.data
      }
    }
    selectionData.value = data
    filteredSelectionData.value = [...data]
    if (!data.length) {
      ElMessage.info('未找到磁盘上路径完整且含训练样本的中间实例数据集，请先在任务数据集中生成实例数据')
    }
    const users = [...new Set(data.map(i => i.username).filter(Boolean))]
    const sensors = [...new Set(data.map(i => i.sensorType).filter(Boolean))]
    const targets = [...new Set(data.map(i => i.targetType).filter(Boolean))]
    createUserList.value = users
    sensorTypeOptions.value = sensors
    targetTypeOptions.value = targets
  } catch (e) {
    console.error('加载失败:', e)
    ElMessage.error('加载源数据集失败')
    selectionData.value = []
    filteredSelectionData.value = []
  } finally {
    datasetsLoading.value = false
  }
}

const handleSelectionChange = (selection) => {
  selectedTaskDatasets.value = selection
}
const applyFilters = () => {
  if (!selectionData.value || selectionData.value.length === 0) {
    filteredSelectionData.value = []
    return
  }
  filteredSelectionData.value = selectionData.value.filter(item => {
    const matchesSensor = !filterConditions.sensorType || item.sensorType === filterConditions.sensorType
    const matchesTarget = !filterConditions.targetType || item.targetType === filterConditions.targetType
    const matchesName = !filterConditions.name || (item.name?.toLowerCase().includes(filterConditions.name.toLowerCase()))
    const matchesUser = !filterConditions.username || item.username === filterConditions.username
    return matchesSensor && matchesTarget && matchesName && matchesUser
  })
  selectionCurrentPage.value = 1
}
const resetFilters = () => {
  filterConditions.sensorType = ''
  filterConditions.targetType = ''
  filterConditions.name = ''
  filterConditions.username = ''
  filteredSelectionData.value = [...selectionData.value]
  selectionCurrentPage.value = 1
}

const showCreateDialog = () => {
  if (selectedTaskDatasets.value.length === 0) {
    ElMessage.warning('请至少选择一个任务数据集')
    return
  }
  createForm.description = ''
  showCreateInstanceDialog.value = true
}

const confirmCreateInstanceDataset = async () => {
  if (!createFormRef.value) return;
  await createFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        createLoading.value = true;
        const selectedAugScript = augmentationScripts.value.find(s => s.id == selectedAugmentationScript.value);
        const selectedEnhScript = enhancementScripts.value.find(s => s.id == selectedEnhancementScript.value);
        if (selectedAugmentationScript.value && !selectedAugScript) {
          ElMessage.error('选择的增广脚本不存在，请重新选择');
          return;
        }
        if (selectedEnhancementScript.value && !selectedEnhScript) {
          ElMessage.error('选择的增强脚本不存在，请重新选择');
          return;
        }
        
        // let enhanceParams = {};
        // if (selectedEnhancementScript.value && selectedEnhancementScriptObj.value) {
        //   for (const param of selectedEnhancementScriptObj.value.paramSchema) {
        //     const userValue = enhancementParamValues.value[param.name];
        //     if (userValue === '' || userValue == null || userValue === undefined) {
        //       enhanceParams[param.name] = param.defaultValue !== undefined ? param.defaultValue : null;
        //     } else {
        //       const numValue = Number(userValue);
        //       if (isNaN(numValue)) {
        //         ElMessage.warning(`增强参数 "${param.label}" 输入无效，将使用默认值`);
        //         enhanceParams[param.name] = param.defaultValue !== undefined ? param.defaultValue : null;
        //       } else {
        //         enhanceParams[param.name] = numValue;
        //       }
        //     }
        //   }
        // }
        let enhanceParams = {};
if (selectedEnhancementScript.value && selectedEnhancementScriptObj.value) {
  for (const param of selectedEnhancementScriptObj.value.paramSchema) {
    const userValue = enhancementParamValues.value[param.name];
    // 新增：处理空值
    if (userValue === '' || userValue == null || userValue === undefined) {
      enhanceParams[param.name] = param.defaultValue !== undefined ? param.defaultValue : null;
    } else if (param.type === 'boolean') {
      // 布尔值：直接使用字符串值（"true" / "false"）
      enhanceParams[param.name] = userValue; // 注意：el-select 的 value 是字符串
    } else if (param.type === 'number') {
      // 数值：转换并校验
      const numValue = Number(userValue);
      if (isNaN(numValue)) {
        ElMessage.warning(`增强参数 "${param.label}" 输入无效，将使用默认值`);
        enhanceParams[param.name] = param.defaultValue !== undefined ? param.defaultValue : null;
      } else {
        enhanceParams[param.name] = numValue;
      }
    } else {
      // 字符串或其他类型
      enhanceParams[param.name] = userValue;
    }
  }
}
        // let augmentParams = {};
        // if (selectedAugmentationScript.value && selectedAugmentationScriptObj.value) {
        //   for (const param of selectedAugmentationScriptObj.value.paramSchema) {
        //     const userValue = augmentationParamValues.value[param.name];
        //     if (userValue === '' || userValue == null || userValue === undefined) {
        //       augmentParams[param.name] = param.defaultValue !== undefined ? param.defaultValue : null;
        //     } else {
        //       const numValue = Number(userValue);
        //       if (isNaN(numValue)) {
        //         ElMessage.warning(`增广参数 "${param.label}" 输入无效，将使用默认值`);
        //         augmentParams[param.name] = param.defaultValue !== undefined ? param.defaultValue : null;
        //       } else {
        //         augmentParams[param.name] = numValue;
        //       }
        //     }
        //   }
        // }
        let augmentParams = {};
if (selectedAugmentationScript.value && selectedAugmentationScriptObj.value) {
  for (const param of selectedAugmentationScriptObj.value.paramSchema) {
    const userValue = augmentationParamValues.value[param.name];
    if (userValue === '' || userValue == null || userValue === undefined) {
      augmentParams[param.name] = param.defaultValue !== undefined ? param.defaultValue : null;
    } else if (param.type === 'boolean') {
      augmentParams[param.name] = userValue;
    } else if (param.type === 'number') {
      const numValue = Number(userValue);
      if (isNaN(numValue)) {
        ElMessage.warning(`增广参数 "${param.label}" 输入无效，将使用默认值`);
        augmentParams[param.name] = param.defaultValue !== undefined ? param.defaultValue : null;
      } else {
        augmentParams[param.name] = numValue;
      }
    } else {
      augmentParams[param.name] = userValue;
    }
  }
}
        const requestBody = {
          sourceInstanceIds: selectedTaskDatasets.value.map(item => item.id),
          enhanceScriptId: selectedEnhancementScript.value || 0,
          enhanceParams: enhanceParams,
          augmentScriptId: selectedAugmentationScript.value || 0,
          augmentParams: augmentParams
        };
        console.log(' 发送预处理请求:', requestBody);
        const response = await fetch('/develop/api/preprocess/run', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json;charset=utf-8' },
          body: JSON.stringify(requestBody)
        });
        const result = await response.json();
        if (result && (result.code === 200 || result.code === 0)) {
          ElMessage.success('实例数据集创建成功！');
          showCreateInstanceDialog.value = false;
          setTimeout(() => {
            //原本
            // window.location.href = '/#/instanceDatabaseManage' 
            //修改
            window.location.href = '/#/intanceDatabase'
          }, 500);
        } else {
          ElMessage.error(result.msg || result.message || '创建失败');
        }
      } catch (error) {
        console.error(' 创建失败:', error);
        ElMessage.error('创建失败: ' + (error.message || '未知错误'));
      } finally {
        createLoading.value = false;
      }
    }
  });
};

const handleSelectionPageChange = () => {}

watch(selectedEnhancementScriptObj, () => {
  enhancementParamValues.value = {}
})
watch(selectedAugmentationScriptObj, () => {
  augmentationParamValues.value = {}
})

// 页面加载时自动加载
loadPreprocessScripts()
loadSourceDatasetsForSelection()


//上传脚本
const handleFileChange = (file) => {
  uploadForm.file = file.raw
  uploadFileList.value = [file]
}

const addParam = () => {
  uploadForm.paramSchema.push({
    name: '',
    label: '',
    type: 'number',
    required: true,
    defaultValue: null,
    min: null,
    max: null
  })
  uploadActiveNames.value = [String(uploadForm.paramSchema.length - 1)]
}

const removeParam = (index) => {
  uploadForm.paramSchema.splice(index, 1)
}

const resetUploadForm = () => {
  uploadForm.name = ''
  uploadForm.type = null
  uploadForm.file = null
  uploadForm.paramSchema = []
  uploadFileList.value = []
  uploadFormRef.value?.clearValidate()
}

const submitUpload = async () => {
  console.log("【1】submitUpload 被触发");
  
  if (!uploadFormRef.value) {
    console.error("【ERROR】uploadFormRef is null!");
    return;
  }

  console.log("【2】开始表单验证", uploadForm);
  
  try {
    await uploadFormRef.value.validate(); // 不加 ?
    console.log("【3】表单验证通过");
    
    if (!uploadForm.file) {
      console.error("【ERROR】文件未选择");
      ElMessage.error('请选择脚本文件');
      return;
    }

    const formData = new FormData();
    formData.append('file', uploadForm.file);
    formData.append('name', uploadForm.name);
    formData.append('type', uploadForm.type);
    formData.append('uploader', uploadForm.uploader);
    formData.append('paramSchema', JSON.stringify(uploadForm.paramSchema));

    console.log("【4】准备发送请求", formData);

    uploadLoading.value = true;
    try {
      await uploadScript(formData);
      ElMessage.success('上传成功！');
      showUploadDialog.value = false;
      loadPreprocessScripts();
    } catch (err) {
      console.error("【5】上传失败", err);
      ElMessage.error(err.response?.data?.message || '上传失败: ' + (err.message || '未知错误'));
    } finally {
      uploadLoading.value = false;
    }
  } catch (err) {
    console.error("【6】表单验证失败", err);
    ElMessage.error('请检查表单输入');
  }
}

const openUploadDialog = () => {
  showUploadDialog.value = true
}
</script>

<style scoped>
/* 完全保留原始样式（仅保留 create 相关） */
.content-div {
  padding: 16px;
  height: 100%;
  display: flex;
  flex-direction: column;
  width: 100%;
  box-sizing: border-box;
}
.create-interface {
  height: 100%;
  display: flex;
  flex-direction: column;
  width: 100%;
  box-sizing: border-box;
}
.create-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
  width: 100%;
}
.create-title {
  margin: 0;
  color: #333;
}
.create-form {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
  width: 100%;
  box-sizing: border-box;
  min-height: 0;
  overflow: hidden;
}
.augmentation-section {
  background: #f8f9fa;
  padding: 16px;
  border-radius: 4px;
  width: 100%;
  box-sizing: border-box;
}
.scripts-container {
  display: flex;
  gap: 32px;
  width: 100%;
}
.script-column {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}
.script-selector {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.script-selector .script-label {
  white-space: nowrap;
}
.script-params {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 8px;
}
.param-item {
  display: flex;
  align-items: center;
  gap: 12px;
}
.param-item label {
  width: 100px;
  text-align: right;
  white-space: nowrap;
  font-size: 14px;
}
.range-hint {
  font-size: 12px;
  color: #999;
  margin-left: 4px;
}
.width-200 {
  width: 200px !important;
}
.data-selection {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
  box-sizing: border-box;
  background: #f8f9fa;
  padding: 16px;
  border-radius: 4px;
  min-height: 0;
  overflow: hidden;
}
.section-title {
  margin: 0 0 12px 0;
  color: #333;
  font-size: 16px;
}
.filter-section {
  background:transparent;
  padding: 16px 0;
  border-radius: 4px;
  width: 100%;
  box-sizing: border-box;
}
.filter-row-single {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: nowrap;
  overflow-x: auto;
  padding-bottom: 8px;
  width: 100%;
  box-sizing: border-box;
}
.selection-table-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  width: 100%;
  box-sizing: border-box;
}

.selection-table-scroll {
  flex: 1 1 0;
  min-height: 0;
  overflow: auto;
}

.selection-table-footer {
  flex-shrink: 0;
}
.selection-table {
  width: 100% !important;
  min-width: 100%;
}
.form-actions {
  display: flex;
  justify-content: center;
  gap: 20px;
  padding-top: 16px;
  border-top: 1px solid #e8e8e8;
  width: 100%;
  box-sizing: border-box;
  flex-shrink: 0;
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
:deep(.el-table__cell) {
  padding: 8px 0;
  text-align: center;
}
.selection-table :deep(.el-table__header) {
  background-color: #f5f7fa;
}
.selection-table :deep(.el-table__header th) {
  background-color: #f5f7fa;
  color: #606266;
  font-weight: 600;
}
.selection-table :deep(.el-table--striped .el-table__body tr.el-table__row--striped td) {
  background-color: #fafafa;
}
.selection-table :deep(.el-table__body tr:hover > td) {
  background-color: #f5f7fa;
}
.selected-info {
  display: flex;
  align-items: center;
}
.selection-info :deep(.el-form-item__content) {
  display: flex;
  align-items: center;
}

/* 上传按钮对齐 */
.scripts-container {
  display: flex;
  gap: 24px;
  width: 100%;
  align-items: flex-end; /* 底部对齐 */
}

.upload-actions {
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding-bottom: 8px;
}

/* 参数定义区域 */
.param-section {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px dashed var(--el-border-color);
}
.param-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-weight: bold;
}
/* 红色强调文字 */
.script-guide .el-alert--error strong {
  color: #e64545;
  font-weight: bold;
}

/* 代码块中的关键注释 */
.code-example strong {
  color: #e64545;
  font-weight: bold;
}

/* 脚本规范样式 */
.script-guide {
  margin-bottom: 20px;
  max-height: 400px;
  overflow-y: auto;
  padding-right: 8px;
}

.guide-section {
  margin-bottom: 16px;
}

.guide-section h4 {
  margin: 12px 0 8px;
  color: #333;
  font-size: 15px;
}

.code-example {
  background: #f8f9fa;
  padding: 12px;
  border-radius: 4px;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}

.guide-list {
  padding-left: 20px;
  margin: 8px 0;
}

.guide-list li {
  margin: 4px 0;
  line-height: 1.5;
}

.guide-list code {
  background: #f1f3f5;
  padding: 2px 4px;
  border-radius: 3px;
  font-size: 13px;
}

/* 参数定义区域 */
.param-section {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px dashed var(--el-border-color);
}
.param-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-weight: bold;
}

.content-div--embed {
  padding: 0;
  background: transparent;
}

.content-div--embed .create-interface {
  padding-top: 8px;
  flex: 1 1 0;
  min-height: 0;
  overflow: hidden;
}
</style>