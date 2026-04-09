<template>
  <div class="content-div">
    <div class="search-div flex-between">
      <div class="flex-start">
        <el-select v-model="searchType" size="small" @change="handleCurrentChange">
          <el-option v-for="(item, id) in ScriptTypeList" :key="id" :value="item[0]" :label="item[1]"></el-option>
        </el-select>
        <el-input v-model="searchName" placeholder="名称查询" size="small">
        </el-input>
        <el-button @click="handleCurrentChange" size="small"><el-icon><i
              class="iconfont icon-sousuo"></i></el-icon></el-button>
      </div>
      <div>
        <el-button type="primary" @click="addSeeEditDel('新增')" size="small" v-show="isSys"><el-text size="small"
            class="text-white">添加算法</el-text>
        </el-button>
      </div>
    </div>
    <div class="table-div">
      <el-table class="my-table" :data="tableData" stripe style="width: 100%" size="small"
        v-el-height-adaptive-table="{ bottomOffset: 70, isUse: true }">
        <el-table-column prop="id" label="id" align="center">
          <template #default="scope">
            <el-text size="small">#{{ scope.row.id }}</el-text>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" align="center" />
        <el-table-column prop="env" label="运行环境" align="center" />
        <el-table-column prop="cmd" label="运行指令" align="center" />
        <el-table-column prop="main" :label="searchType == 'train' ? '训练函数' : '转换函数'" align="center" />
        <el-table-column prop="val" label="验证函数" align="center" v-if="searchType == 'train'" />
        <el-table-column prop="detect" label="预测函数" align="center" v-if="searchType == 'train'" />
        <el-table-column prop="uptime" label="更新时间" align="center" width="100">
          <template #default="scope">
            <el-text size="small">{{ showDateTime(scope.row.uptime) }}</el-text>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" align="center" />
        <el-table-column label="操作" align="center" fixed="right" width="250">
          <template #default="scope">
            <el-space v-show="isSys">
              <el-button link size="small" @click="addSeeEditDel('查看', scope.row)">
                <el-tag size="small" type="primary" class="iconfont icon-chakan fontSpan">查看</el-tag>
              </el-button>
              <el-button link size="small" @click="addSeeEditDel('编辑', scope.row)">
                <el-tag size="small" type="primary" class="iconfont icon-bianji fontSpan">编辑</el-tag>
              </el-button>
              <el-button link size="small" @click="addSeeEditDel('删除', scope.row)">
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

  <!-- 算法弹框 -->
  <customElDialog :title="algorithmDialogTitle" draggable width='60%' destroyOnClose :ZIndex="1003"
    ref="algorithmDialogRef" :draggable="true" :before-close="() => { algorithmDialogRef.close() }">
    <template #content>
      <el-form label-position="left" label-width="auto" ref="algorithmFormRef" :model="algorithmForm"
        :rules="algorithmRules">
        <el-form-item label="算法类型" prop="type" v-if="algorithmDialogTitle.includes('新增')">
          <el-select v-model="algorithmForm.type" @change="changeType">
            <el-option v-for="(item, id) in ScriptTypeList" :key="id" :value="item[0]" :label="item[1]"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="算法名称" prop="name" v-if="algorithmForm.type != 'trans'">
          <el-input v-model="algorithmForm.name"></el-input>
        </el-form-item>
        <el-form-item label="算法名称" prop="name" :rules="transRulesName" v-else>
          <template v-if="algorithmDialogTitle.includes('新增')">
            <el-autocomplete v-model="algorithmForm.name" :fetch-suggestions="querySearch" :trigger-on-focus="false"
              clearable placeholder="算法名称" />
          </template>
          <template v-else>
            {{ algorithmForm.name }}
          </template>

        </el-form-item>

        <el-form-item label="运行环境" prop="env">
          <el-input v-model="algorithmForm.env">
            <template #append>
              <el-tooltip content="算法运行的conda环境名称" placement="top">
                <el-icon class="iconfont icon-bangzhu"></el-icon>
              </el-tooltip>
            </template>
          </el-input>
        </el-form-item>
        <div v-if="algorithmForm.type == 'train'">
          <el-form-item label="运行指令" prop="cmd">
            <el-space>
              <el-radio-group v-model="algorithmForm.cmd">
                <el-radio value="python">python[yolo5适用]</el-radio>
                <el-radio value="yolo">yolo[yolov8,10,11适用]</el-radio>
                <el-radio value="mmdet">mmdet[mmdetection适用]</el-radio>
              </el-radio-group>
            </el-space>
          </el-form-item>
          <el-form-item label="训练函数" prop="main">
            <el-input v-model="algorithmForm.main">
              <template #append>
                <el-tooltip placement="top">
                  <template #content>
                    <p>yolov5输入训练脚本文件的绝对路径;yolov8及以上输入train</p>
                  </template>
                  <el-icon class="iconfont icon-bangzhu"></el-icon>
                </el-tooltip>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item label="验证函数" prop="val">
            <el-input v-model="algorithmForm.val">
              <template #append>
                <el-tooltip placement="top">
                  <template #content>
                    <p>yolov5输入验证脚本文件的绝对路径;yolov8及以上输入val</p>
                  </template>
                  <el-icon class="iconfont icon-bangzhu"></el-icon>
                </el-tooltip>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item label="预测函数" prop="detect">
            <el-input v-model="algorithmForm.detect">
              <template #append>
                <el-tooltip placement="top">
                  <template #content>
                    <p>yolov5输入预测脚本文件的绝对路径;yolov8及以上输入predict</p>
                  </template>
                  <el-icon class="iconfont icon-bangzhu"></el-icon>
                </el-tooltip>
              </template>
            </el-input>
          </el-form-item>
        </div>
        <div v-else-if="algorithmForm.type == 'data'">
          <el-form-item label="转换函数" prop="main">
            <el-input v-model="algorithmForm.main">
            </el-input>
          </el-form-item>
          <el-form-item label="参数(json格式)" prop="cfg">
            <div style="height:200px;width:100%">
              <aceEdit ref='aceEditRef' v-model="algorithmForm.cfg" :height="200" />
            </div>
          </el-form-item>
        </div>
        <div v-else>
          <el-form-item label="转换函数" prop="main">
            <el-input v-model="algorithmForm.main">
            </el-input>
          </el-form-item>
        </div>

        <el-form-item label="备注" prop="remark">
          <el-input v-model="algorithmForm.remark" type="textarea"></el-input>
        </el-form-item>
      </el-form>
    </template>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="() => { algorithmDialogRef.close() }">取消</el-button>
        <el-button type="primary" @click="menuBinding(algorithmFormRef)">确定</el-button>
      </span>
    </template>
  </customElDialog>

  <!-- 查看 -->
  <el-dialog v-model="txtVisible" width="60%" top="5vh" title="查看" draggable :close-on-click-modal="true"
    :destroy-on-close="true">
    <pre class="preBox">{{ cur_text }}</pre>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="txtVisible = false">关闭</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup>
import customElDialog from '@/components/customElDialog/index.vue'
import { reactive, ref, watch } from "@vue/reactivity";
import { useRoute } from "vue-router";
import { useTitleStore, useUserStore } from "../../stores/index";
import { FileService, TrainScriptService } from "../../api/api";
import { ElMessage, dayjs, ElMessageBox } from "element-plus";
import { nextTick } from "@vue/runtime-core";
import { basePath_SCRIPT } from "../../api/axios";
import { isNum } from "../../utils/regex";
import { ScriptTypeMap, ScriptTypeList } from "../../utils/selfmaps";
import AceEdit from '@/components/AceEdit/index.vue'

const transNameValidate = (rule, value, callback) => {
  if (value === '') {
    callback(new Error('算法名称不能为空'))
  } else {
    const originalList = ['pt2onnx', 'pt2rknn_3588', 'pt2rknn_3576', 'pt2rknn_3566']
    const findItem = originalList.find(item => item == value)
    if (findItem) {
      callback()
      return
    }
    const pattern = /^pt2rknn_/;
    if (!pattern.test(value)) {
      callback(new Error('若自定义输入算法名称，则必须以 pt2rknn_ 开头'));
    } else {
      callback();
    }
  }
}


const modelAlgorithmList = ref([
  { value: 'pt2onnx', label: 'pt2onnx' },
  { value: 'pt2rknn_3588', label: 'pt2rknn_3588' },
  { value: 'pt2rknn_3576', label: 'pt2rknn_3576' },
  { value: 'pt2rknn_3566', label: 'pt2rknn_3566' },
])
const querySearch = (queryString, cb) => {
  const results = queryString
    ? modelAlgorithmList.value.filter(createFilter(queryString))
    : modelAlgorithmList.value
  cb(results)
}
const createFilter = (queryString) => {
  return (modelAlgorithmList) => {
    return (
      modelAlgorithmList.value.toLowerCase().indexOf(queryString.toLowerCase()) === 0
    )
  }
}


//算法弹框
const algorithmDialogTitle = ref()
const algorithmDialogRef = ref()
const algorithmFormRef = ref()
const aceEditRef = ref()
const algorithmForm = ref({
  type: null,
  name: null,
  env: null,
  cmd: null,
  main: null,
  remark: null,
  val: null,
  detect: null,
})
const algorithmRules = ref({
  type: [{ required: true, message: "算法类型不能为空", trigger: ["blur", "change"] },],
  name: [{ required: true, message: "算法名称不能为空", trigger: ["blur", "change"] },],
  env: [{ required: true, message: "运行环境不能为空", trigger: ["blur"] },],
  cmd: [{ required: true, message: "运行指令不能为空", trigger: ["blur", "change"] },],
  main: [{ required: true, message: "主函数不能为空", trigger: ["blur",] },]
})
const transRulesName = [
  { validator: transNameValidate, trigger: ['blur', 'change'] }
]

let apiFun = null
const addSeeEditDel = async (type, row) => {
  algorithmDialogTitle.value = `${type}算法`
  if (type == "删除") {
    ElMessageBox.confirm(`此操作将永久删除【${row.name}】算法, 是否继续?`, '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    ).then(async () => {
      try {
        let res = await TrainScriptService.delete({ id: row.id })
        if (res && res.code === 0) {
          ElMessage.success(res.msg)
          handleCurrentChange()
        } else {
          ElMessage({ type: 'error', message: res.msg })
        }
      } catch (error) {
        console.log(error)
      }
    }).catch(() => {
      ElMessage({ type: 'info', message: '取消删除' })
    })
    return
  } else if (type == "查看") {
    let url = basePath_SCRIPT + row.id + row.suff;
    readTxt(url)
    return
  } else if (type == "编辑") {
    algorithmForm.value = { ...row }
    apiFun = TrainScriptService.update

  } else {
    apiFun = TrainScriptService.add

    algorithmForm.value = {
      type: searchType.value,
      name: null,
      env: null,
      cmd: null,
      main: null,
      remark: null,
      val: null,
      detect: null,
    }
  }

  algorithmDialogRef.value.open()
  nextTick(() => {
    algorithmFormRef.value && algorithmFormRef.value.clearValidate();
  })
}

//弹框确定
const menuBinding = async (formEl) => {
  if (!formEl) return
  await formEl.validate(async (valid) => {
    if (valid) {
      let type = algorithmForm.value.type;
      let params = { ...algorithmForm.value }
      if (type == "trans") { //模型转换
        params.cmd = algorithmForm.value.name == 'pt2onnx' ? 'python' : '.';
        params.cfg = null;
      } else if (type == 'data') {//数据集转换
        params.cmd = 'python';
      } else {
        params.cfg = null;
      }
      try {
        let res = await apiFun(params)
        if (res && res.code == 0) {
          ElMessage.success(res.msg);
          algorithmDialogRef.value.close()
          handleCurrentChange();
        } else {
          ElMessage({ type: 'error', message: res.msg })
        }

      } catch (error) {
        console.log(error)
      }
    }
  })
}


const changeType = (params) => {
  if (params == 'trans') {
    algorithmForm.value.name = "pt2onnx"
  } else {
    algorithmForm.value.name = null
  }
}

const props = defineProps({
  msg: Number,
});
const isSys = useUserStore().user.type == 1;
const searchName = ref("");
const searchId = ref('');
const searchType = ref('train');
const currentPage = ref(1);
const currentSize = ref(10);
const total = ref(0);
const tableData = ref([]);
/**页码变化 */
const handleCurrentChange = () => {
  let id = searchId.value || null;
  if (isNum(id)) {
    id = parseInt(id)
  } else {
    id = null
  }
  TrainScriptService.queryList({
    current: currentPage.value,
    size: currentSize.value,
    name: searchName.value,
    id: id,
    type: searchType.value,
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

const showDateTime = (time) => {
  return time ? dayjs(time).format('YYYY/MM/DD HH:mm:ss') : '';
}
const txtVisible = ref(false);
const cur_text = ref('')
const readTxt = (url) => {
  if (!url) return;
  FileService.getFile(url)
    .then((res) => {
      const text = res.toString();
      if (text.length > 102400) {
        cur_text.value = text.substring(0, 102400) + "\n" + "........";
      } else {
        cur_text.value = text;
      }
      txtVisible.value = true
    })
    .catch((e) => {
      cur_text.value = '';
      console.log(e);
    });
};

handleCurrentChange();
</script>

<style scoped>
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
</style>
