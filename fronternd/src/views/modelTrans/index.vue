<template>
  <div class="content-div">
    <div class="search-div flex-between">
      <div class="flex-start">
        <el-select v-model="searchStatus" placeholder="选择状态" size="small" @change="handleCurrentChange"
          style="width: 150px;">
          <el-option label="全部状态" value=""></el-option>
          <el-option label="准备阶段" :value="0"></el-option>
          <el-option label="转换中" :value="3"></el-option>
          <el-option label="已完成" :value="4"></el-option>
        </el-select>
        <el-select v-model="searchType" placeholder="转换类别" size="small" @change="handleCurrentChange"
          style="width: 150px;">
          <el-option label="全部类别" value=""></el-option>
          <el-option v-for="(item, id) in algList" :key="id" :value="item.name"></el-option>
        </el-select>
        <el-input v-model="searchName" placeholder="名称查询" size="small" style="width: 150px;">
        </el-input>
        <el-button @click="handleCurrentChange" size="small"><el-icon><i
              class="iconfont icon-sousuo"></i></el-icon></el-button>
      </div>
      <div>
        <el-space>
          <logview type="trans"></logview>
          <el-button type="primary" @click="showBaseVal" size="small"><el-text size="small"
              class="text-white">基础测试集</el-text>
          </el-button>
          <el-button type="primary" @click="showAddModel" size="small"><el-text size="small"
              class="text-white">创建转换任务</el-text>
          </el-button>
        </el-space>
      </div>
    </div>
    <div class="table-div">
      <el-table class="my-table" :data="tableData" stripe style="width: 100%" size="small"
        v-el-height-adaptive-table="{ bottomOffset: 70, isUse: true }">
        <el-table-column label="id" align="center">
          <template #default="{ row }">
            <el-text size="small">#{{ row.id }}</el-text>
          </template>
        </el-table-column>
        <el-table-column prop="type" label="转换算法" align="center">
        </el-table-column>
        <el-table-column prop="name" label="任务名称" align="center" />
        <el-table-column prop="weights" label="源模型" align="center" />
        <el-table-column prop="status" label="状态" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.status == 4" size="small" type="success">
              已完成</el-tag>
            <el-tag v-else-if="scope.row.status == 3" size="small" type="danger">
              <el-icon class="iconfont icon-yunxingzhong is-loading"></el-icon>转换中</el-tag>
            <el-tag v-else size="small" type="info">准备阶段</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createtime" label="创建时间" align="center" width="100">
          <template #default="{ row }">
            <el-text size="small">{{ row.createtime ? showDateTime(row.createtime) : '' }}</el-text>
          </template>
        </el-table-column>
        <el-table-column prop="starttime" label="开始时间" align="center" width="100">
          <template #default="{ row }">
            <el-text size="small">{{ row.starttime ? showDateTime(row.starttime) : '' }}</el-text>
          </template>
        </el-table-column>
        <el-table-column prop="endtime" label="结束时间" align="center" width="100">
          <template #default="{ row }">
            <el-text size="small">{{ row.status == 3 ? '' : (row.endtime ? showDateTime(row.endtime) : '') }}</el-text>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" align="center">
          <template #default="{ row }">
            <el-tooltip v-if="row.remark" :content="row.remark" placement="top">
              <el-text size="small">{{ showRemark(row.remark,20) }}</el-text>
            </el-tooltip>
            <el-text v-else></el-text>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" fixed="right" width="250px">
          <template #default="scope">
            <el-space wrap>
              <el-button link size="small" @click="showEditModal(scope.row)" v-show="scope.row.status != 3">
                <el-tag size="small" type="primary" class="iconfont icon-bianji fontSpan">编辑</el-tag>
              </el-button>
              <el-button link size="small" @click="showDataset(scope.row)">
                <el-tag size="small" type="success" class="iconfont icon-chakan fontSpan">查看</el-tag>
              </el-button>
              <el-button size="small" link @click="startTask(scope.row)">
                <el-tag size="small" class="iconfont icon-qidongruanjian fontSpan"
                  v-show="scope.row.status != 3">启动</el-tag>
              </el-button>
              <el-button @click="delRecord(scope.row)" link size="small" v-show="scope.row.status != 3">
                <el-tag size="small" type="danger" class="iconfont icon-shanchu fontSpan">删除</el-tag></el-button>
            </el-space>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <div class="flex-end">
      <el-pagination background size="small" v-model:current-page="currentPage" v-model:page-size="currentSize"
        :page-sizes="[5, 10, 20, 30, 40, 50]" layout="total, sizes, prev, pager, next, jumper" :total="total"
        @size-change="handleCurrentChange" @current-change="handleCurrentChange" />
    </div>
  </div>
  <el-dialog top="1vh" v-model="addVisible" width="60%" :title="isAdd ? '创建转换任务' : ('编辑任务#' + addForm.id)" draggable
    :close-on-click-modal="false">
    <el-form label-position="right" label-width="auto">
      <el-row :span="22">
        <el-col :span="12">
          <el-form-item label="任务名称" required>
            <el-input v-model="addForm.name"></el-input>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :span="22">
        <el-col :span="12">
          <el-form-item label="源模型(weights)" required>
            <el-space>
              <el-upload action="#" :auto-upload="false" :on-change="(f, l) => uploadChange(f, l, 'pt')" accept=".pt"
                ref="refPtAdd">
                <template #trigger>
                  <el-button size="small">+</el-button>
                </template>
              </el-upload>
              <el-text type="info">{{ addForm.weights }}</el-text>
            </el-space>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="模型配置(data)" required>
            <el-space>
              <el-upload action="#" :auto-upload="false" :on-change="(f, l) => uploadChange(f, l, 'yaml')" accept=".yaml"
                ref="refYamlAdd">
                <template #trigger>
                  <el-button size="small">+</el-button>
                </template>
              </el-upload>
              <el-text type="info">{{ addForm.params.data }}</el-text>
            </el-space>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :span="22">
        <el-col :span="12">
          <el-form-item label="转换算法" required>
            <el-select v-model="addForm.type" style="width:100%;" :disabled="!isAdd">
              <el-option v-for="(item, id) in algList" :key="id" :value="item.name"></el-option>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
            <!-- 转换模型算法模板 -->
          <el-form-item label="算法模板">
            <el-select v-model="templateAlgorithm"  style="width:calc(100% - 30px);"   @change="changeTemplate"  value-key="id">
              <el-option v-for="item in templateAlgorithmList" :key="item.id" :value="item" :label="item.name"></el-option>
            </el-select>
            <el-tooltip content="选择算法模板将'一键式'填写【其他参数】" placement="top">
              <el-icon class="iconfont icon-bangzhu" style="margin-left: 10px;"></el-icon>
            </el-tooltip>
          </el-form-item>
        </el-col>
      </el-row>
      <el-divider><el-text size="small">其他参数</el-text></el-divider>
      <div v-if="addForm.type == 'pt2onnx'">
        <el-form-item label="图像尺寸(imgsz)" required>
          <el-input-number v-model="addForm.params.imgsz" style="width: 120px;" :controls="false"
            :min="1"></el-input-number>
        </el-form-item>
      </div>
      <div v-else>
        <el-row :gutter="22">
          <el-col :span="12">
            <el-form-item label="模型类型(type)" required>
              <el-select v-model="addForm.params.type" style="width: 100%">
                <el-option value="5s"></el-option>
                <el-option value="5m"></el-option>
                <el-option value="5l"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="图片类型(chn)" required>
              <el-select v-model="addForm.params.chn" style="width: 100%">
                <el-option value="vis"></el-option>
                <el-option value="inf"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="22">
          <el-col :span="12">
            <el-form-item label="模型宽(model_w)" required>
              <el-input-number v-model="addForm.params.model_w" style="width: 100%;" :controls="false"
                :min="1"></el-input-number>
            </el-form-item>
         
          </el-col>
          <el-col :span="12">
            <el-form-item label="模型高(model_h)" required>
              <el-input-number v-model="addForm.params.model_h" style="width: 100%;" :controls="false"
                :min="1"></el-input-number>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="22">
          <el-col :span="12">
            <el-form-item label="转换日期(date)" required>
              <el-date-picker v-model="addForm.params.date" value-format="YYYYMMDD"
              style="width: 100%;"></el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="名称量化(quantise)" required>
              <el-switch v-model="addForm.params.quantise" :active-value="1" :inactive-value="0" 
                active-text="开" inactive-text="关" inline-prompt></el-switch>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="22">
          <el-col :span="12">
            <el-form-item label="基础校验集(check)" required>
              <div class="spaceBox">
                <el-select v-model="addForm.params.base_check" style="width: 100%;">
                  <el-option v-for="(item, id) in checkList" :key="id" :value="item"></el-option>
                </el-select>
                <el-button size="small" @click="queryCalibrate('check')" class="iconfont icon-gengxinchajian" title="刷新"
                  circle></el-button>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="&nbsp;&nbsp;补充校验集">
              <el-space>
                <el-upload action="#" :auto-upload="false" :on-change="(f, l) => uploadChange(f, l, 'check')" accept=".zip"
                  ref="refCheckAdd">
                  <template #trigger>
                    <el-button size="small">+</el-button>
                  </template>
                </el-upload>
                <el-space v-show="!isAdd && addForm.params.ext_check">
                  <el-text type="info">{{ addForm.params.ext_check }}</el-text><el-button size="small"
                    @click="clearExtCheck">清空</el-button>
                </el-space>
              </el-space>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="22">
          <el-col :span="12">
            <el-form-item label="基础验证集(val)" required>
              <div class="spaceBox">
                <el-select v-model="addForm.params.base_val" style="width: 100%;">
                  <el-option v-for="(item, id) in valList" :key="id" :value="item"></el-option>
                </el-select>
                <el-button size="small" @click="queryCalibrate('val')" class="iconfont icon-gengxinchajian" title="刷新"
                  circle></el-button>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="&nbsp;&nbsp;补充验证集">
              <el-space>
                <el-upload action="#" :auto-upload="false" :on-change="(f, l) => uploadChange(f, l, 'val')" accept=".zip"
                  ref="refValAdd">
                  <template #trigger>
                    <el-button size="small">+</el-button>
                  </template>
                </el-upload>
                <el-space v-show="!isAdd && addForm.params.ext_val">
                  <el-text type="info">{{ addForm.params.ext_val }}</el-text><el-button size="small"
                    @click="cleartExtVal">清空</el-button>
                </el-space>

              </el-space>
            </el-form-item>
          </el-col>
        </el-row>
      </div>
      <el-form-item label="&nbsp;&nbsp;备注说明">
        <el-input v-model="addForm.remark" type="textarea"></el-input>
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="addVisible = false">关闭</el-button>
        <el-button @click="saveRecord" type="primary">确定</el-button>
      </span>
    </template>
  </el-dialog>
  <el-dialog top="1vh" v-model="dataVisible" width="70%" title="查看" draggable :close-on-click-modal="false">
    <div class="fileViewDialog">
      <fileview :base_type="base_type" :base_uri="base_uri" :base_path="base_path" :key="base_uri" v-if="base_path">
      </fileview>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="dataVisible = false">关闭</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref ,watch } from "@vue/reactivity";
import { useRoute } from "vue-router";
import { useTitleStore, useUserStore } from "../../stores/index";
import { ModelTransService, TrainScriptService ,transService} from "../../api/api";
import { ElMessage, dayjs, ElMessageBox } from "element-plus";
import { nextTick, onBeforeUnmount, onMounted } from "@vue/runtime-core";
import { basePath_MODEL_TRANS, basePath_SRC, basePath_WS_TRANS ,apiRequest} from "../../api/axios";
import fileview from "../../components/fileview.vue";
import logview from '../../components/logger.vue'
import { uuid } from "vue-uuid";
import { showRemark } from "../../utils/str";
const { meta, query } = useRoute();
// useTitleStore().$patch((state) => {
//   state.title = meta.title;
// });


const templateAlgorithm=ref()
const templateAlgorithmList=ref([])

const props = defineProps({
  msg: Number,
});
const curUser = useUserStore().user || {};
const isSys = curUser.type == 1;
const searchName = ref(query.name);
const searchStatus = ref('')
const searchType = ref('')

const currentPage = ref(1);
const currentSize = ref(10);
const total = ref(0);
const tableData = ref([]);
/**页码变化 */
const handleCurrentChange = () => {
  ModelTransService.queryList({
    current: currentPage.value,
    size: currentSize.value,
    status: searchStatus.value,
    name: searchName.value,
    type: searchType.value
  }).then(function (res) {
    if (res.code === 0) {
      nextTick(() => {
        tableData.value = res.data.records;
        total.value = res.data.total;
      });
    } else {
      ElMessage.warning(res.msg);
    }
  });
};
/**时间展示 */
const showDateTime = (time) => {
  return time ? dayjs(time).format('YYYY/MM/DD HH:mm:ss') : '';
}

const dataVisible = ref(false);
const base_type = ref('model_trans');
const base_uri = ref();
const base_path = ref();
//
const showDataset = (row) => {
  base_type.value = 'model_trans';
  base_path.value = row.id;//文件路径相对根目录
  base_uri.value = basePath_MODEL_TRANS + '/' + base_path.value;//url的完整根路径
  dataVisible.value = true
}

//基础校验集
const showBaseVal = () => {
  base_type.value = 'calibrate';
  base_path.value = "/";
  base_uri.value = basePath_SRC + base_type.value;
  dataVisible.value = true
}

/**创建任务 */
const addLoading = ref(false)
const addVisible = ref(false)
const isAdd = ref(true); // 创建模式 or  编辑模式
const addForm = ref({ params: {} })
const yamlList = ref()
const ptList = ref()
const checkZipList = ref()
const valZipList = ref()
const refYamlAdd = ref()
const refPtAdd = ref()
const refCheckAdd = ref()
const refValAdd = ref()
/**监听 文件列表变化 */
const uploadChange = (file, list, type) => {
  if (list.length > 1 && file.status !== "fail") {
    list.splice(0, 1);
  } else if (file.status === "fail") {
    ElMessage.warning("上传失败，请重新上传！");
    list.splice(0, 1);
  }
  if (type == 'yaml') {
    yamlList.value = list
  } else if (type == 'pt') {
    ptList.value = list
  } else if (type == 'check') {
    checkZipList.value = list
  } else if (type == 'val') {
    valZipList.value = list
  }
};
const clearExtCheck = () => {
  addForm.value.params.ext_check = ''
}
const cleartExtVal = () => {
  addForm.value.params.ext_val = ''
}
/**清理文件上传的列表 */
const clearFileList = () => {
  yamlList.value = []
  ptList.value = []
  checkZipList.value = []
  valZipList.value = []
  refYamlAdd.value?.clearFiles();
  refPtAdd.value?.clearFiles();
  refCheckAdd.value?.clearFiles();
  refValAdd.value?.clearFiles();
}

const showAddModel = () => {
  isAdd.value = true;
  clearFileList()
  addForm.value = { params: {} }
  addVisible.value = true
}
const saveRecord = () => {
  if (isAdd.value) {
    addRecord();
  } else {
    updateRecord();
  }
}
//创建
const addRecord = () => {
  let form = addForm.value
  let type = form.type
  let name = form.name
  let params = form.params;
  if (!name) {
    ElMessage.warning('任务名称不能为空')
    return;
  }
  if (!type) {
    ElMessage.warning('请选择转换类型')
    return
  }
  let fd = new FormData();
  fd.append("type", type);
  fd.append("name", name);
  fd.append('createman', curUser.username || '')
  fd.append("remark", form.remark || '');
  if (!ptList.value || ptList.value.length == 0) {
    ElMessage.warning('请上传要转换的模型')
    return
  }
  fd.append("weight_file", ptList.value[0].raw);

  if (!yamlList.value || yamlList.value.length == 0) {
    ElMessage.warning('请上传模型配置文件');
    return
  }
  fd.append("data_file", yamlList.value[0].raw);
  //其他差异化参数
  if (type == 'pt2onnx') {
    let imgsz = params.imgsz
    if (!imgsz) {
      ElMessage.warning('请输入图像尺寸')
      return
    }
    fd.append("params", JSON.stringify(params));
  } else {
    if (!params.type || !params.chn || !params.model_w || !params.model_h
      || !params.date || !params.base_val || !params.base_check) {
      ElMessage.warning('参数填写不完整')
      return
    }
    params.quantise = params.quantise ? 1 : 0;
    if (checkZipList.value && checkZipList.value.length > 0) {
      fd.append("check_file", checkZipList.value[0].raw);
    }
    if (valZipList.value && valZipList.value.length > 0) {
      fd.append("val_file", valZipList.value[0].raw);
    }
    fd.append("params", JSON.stringify(params));
  }
  addLoading.value = true
  ModelTransService.add(fd).then(res => {
    if (res.code === 0) {
      ElMessage.success(res.msg)
      handleCurrentChange()
      addVisible.value = false;
    } else {
      ElMessage.warning(res.msg)
    }
    addLoading.value = false
  }).catch(() => {
    addLoading.value = false
  })
}
//修改
const updateRecord = () => {
  let form = addForm.value
  let id = form.id
  let type = form.type
  let name = form.name
  let params = form.params;
  if (!name) {
    ElMessage.warning('任务名称不能为空')
    return;
  }
  if (!type) {
    ElMessage.warning('请选择转换类型')
    return
  }
  let fd = new FormData();
  fd.append("id", id);
  fd.append("type", type);
  fd.append("name", name);
  fd.append('createman', curUser.username || '')
  fd.append("remark", form.remark || '');

  if (ptList.value && ptList.value.length > 0) {
    fd.append("weight_file", ptList.value[0].raw);
  }
  if (yamlList.value && yamlList.value.length > 0) {
    fd.append("data_file", yamlList.value[0].raw);
  }
  //其他参数
  if (type == 'pt2onnx') {
    let imgsz = params.imgsz
    if (!imgsz) {
      ElMessage.warning('请输入图像尺寸')
      return
    }
    fd.append("params", JSON.stringify(params));
  } else {
    //rknn
    if (!params.type || !params.chn || !params.model_w || !params.model_h
      || !params.date || !params.base_val || !params.base_check) {
      ElMessage.warning('参数填写不完整')
      return
    }
    params.quantise = params.quantise ? 1 : 0;
    if (checkZipList.value && checkZipList.value.length > 0) {
      fd.append("check_file", checkZipList.value[0].raw);
    }
    if (valZipList.value && valZipList.value.length > 0) {
      fd.append("val_file", valZipList.value[0].raw);
    }
    fd.append("params", JSON.stringify(params));
  }
  addLoading.value = true
  ModelTransService.update(fd).then(res => {
    if (res.code === 0) {
      ElMessage.success(res.msg)
      handleCurrentChange()
      addVisible.value = false;
    } else {
      ElMessage.warning(res.msg)
    }
    addLoading.value = false
  }).catch(() => {
    addLoading.value = false
  })
}

/**编辑 */
const showEditModal = (row) => {
  let form = { params: {} };
  isAdd.value = false;
  clearFileList();
  form.id = row.id
  form.name = row.name
  form.type = row.type
  form.remark = row.remark
  form.weights = row.weights
  form.params = JSON.parse(row.params)
  addForm.value = form;
  addVisible.value = true
}

/**删除任务 */
const delRecord = (row) => {
  ElMessageBox.confirm(
    `删除任务及任务相关的文件,确定要删除[#${row.id}]:[${row.name}]?`,
    '',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  )
    .then(() => {
      ModelTransService.delete({ id: row.id }).then(res => {
        if (res.code === 0) {
          ElMessage.success(res.msg)
          handleCurrentChange()
        } else {
          ElMessage.warning(res.msg)
        }
      })
    })
    .catch(() => {

    })
}

/**启动任务 */
const startTask = (row) => {
  ModelTransService.start({ id: row.id }).then(res => {
    if (res.code === 0) {
      ElMessage.success(res.msg)
      handleCurrentChange();
    } else {
      ElMessage.warning(res.msg)
    }
  })
}
const algList = ref([]);
/**查询支持的模型转换算法 */
const queryAlgs = () => {
  TrainScriptService.queryAll({ type: 'trans' }).then(res => {
    if (res.code === 0) {
      algList.value = res.data
    }
  })
}
const checkList = ref([]);
const valList = ref([])
const queryCalibrate = (type) => {
  ModelTransService.queryCalibrate({ type: type }).then(res => {
    if (res.code === 0) {
      if (type == 'check') {
        checkList.value = res.data;
      } else if (type == 'val') {
        valList.value = res.data;
      }
    }
  })
}
queryCalibrate('check');
queryCalibrate('val');
queryAlgs();
handleCurrentChange();

/**任务通知机制 */
let ws;
let wscloseflg = false;
let reconnectInterval = 2000; // 重新连接的时间间隔，单位为毫秒
let heartInter = null;
const wsConnect = () => {
  ws = new WebSocket(basePath_WS_TRANS + uuid.v1());
  ws.onopen = function (e) {
    console.log("open");
    heartInter = setInterval(() => {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send("ping");
      }
    }, 5000);
  };
  ws.onmessage = function (e) {
    let data = JSON.parse(e.data);
    let id = data.id
    let status = data.status;
    for (let i = 0; i < tableData.value.length; i++) {
      let item = tableData.value[i]
      if (item.id === id) {
        item.status = status;
        if (status == 3) {
          item.endtime = null
          item.starttime = data.starttime;
        } else if (status == 4) {
          item.endtime = data.endtime
        }
        break;
      }
    }
  };
  ws.onclose = function (e) {
    console.log("ws onclose", e);
    clearInterval(heartInter);
    ws = null;
    if (!wscloseflg) {
      setTimeout(wsConnect, reconnectInterval);
    }
  };
  ws.onerror = function (e) {
    clearInterval(heartInter);
    console.log("ws onerror", e);
  };
};
onMounted(() => {
  wsConnect();
})
onBeforeUnmount(() => {
  clearInterval(heartInter);
  wscloseflg = true;
  if (ws) {
    try {
      ws.close();
    } catch (error) {
    }
  }
});




//监听转换算法，获取算法模版下拉数据
watch(()=>addForm.value.type,async(newV)=>{
  if(newV){
    templateAlgorithmList.value = (await apiRequest(transService.allTrain, { type: newV })) || []
    const findItem=templateAlgorithmList.value.find(val=>val.id==templateAlgorithm.value?.id)
    if(!findItem){
      templateAlgorithm.value = templateAlgorithmList.value[0]||null
    }
    templateAlgorithm.value&&changeTemplate({...templateAlgorithm.value})
  }
},{immediate:true})




//字符串转为json对象
const parseJSON=(jsonString)=> {
  try {
    const result = JSON.parse(jsonString)
    return { success: true, data: result }
  } catch (error) {
    console.log(error,'error')
    return { success: false, error: error.message };
  }
}
//算法模板的change事件
const changeTemplate=(param)=>{
  const {params,remark}=param
  addForm.value.remark=remark
  let parseParams=parseJSON(params)
  addForm.value.params=parseParams.success?parseParams.data:{}
}

</script>

<style scoped lang="scss">
.content-div {
  padding: 10px;
}

.table-div {
  padding-top: 8px;
  padding-bottom: 8px;
}

.span-1 {
  font-size: 18px;
  font-weight: 500;
}

.my-table.el-table--small {
  border-radius: 4px;
}
.spaceBox{
    display: flex;
    align-items: center;
    width: 100%;
    .el-select{
        margin-right: 10px;
    }
}
</style>
