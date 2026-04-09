<template>
  <div class="content-div">
    <div class="search-div flex-between">
      <div class="flex-start">
        <el-select v-model="searchDel" size="small" @change="handleCurrentChange" placeholder="标记">
          <el-option :value="-1" label="全部"></el-option>
          <el-option :value="1" label="CVAT已删除"></el-option>
          <el-option :value="0" label="CVAT未删除"></el-option>
        </el-select>
        <el-select v-model="searchProject" placeholder="选择项目" size="small" clearable multiple collapse-tags
          collapse-tags-tooltip @change="handleCurrentChange">
          <!-- <el-option label="全部" value="" ></el-option> -->
          <el-option v-for="item in project_list" :key="item.project_id" :label="'#' + item.project_id + ':' + item.a_n"
            :value="item.project_id"></el-option>
        </el-select>
        <el-select v-model="searchStatus" placeholder="选择状态"  clearable    size="small" @change="handleCurrentChange">
          <el-option label="标注阶段" value="annotation"></el-option>
          <el-option label="验证阶段" value="validation"></el-option>
          <el-option label="已完成" value="completed"></el-option>
        </el-select>
        <el-input v-model="searchName" placeholder="名称查询" size="small">
        </el-input>
        <el-input class="width-300" v-model="searchId" placeholder="任务id" size="small">
        </el-input>

        <el-button @click="handleCurrentChange" size="small"><el-icon><i
              class="iconfont icon-sousuo"></i></el-icon></el-button>
        <el-switch active-text="拓展信息开" inactive-text="拓展信息关" v-model="showExt" inline-prompt></el-switch>
      </div>
      <div>
        <el-button type="primary" @click="clearDelTask" size="small" :loading="clearLoading" v-if="!encode_src"><el-text
            size="small" class="text-white">清理CVAT已删除项</el-text>
        </el-button>
        <el-button type="primary" @click="addNewTask" size="small" :loading="addLoading"><el-text size="small"
            class="text-white">导入新任务</el-text>
        </el-button>
      </div>
    </div>
    <el-space wrap :size="0">
      <el-select v-model="searchS" size="small" style="width: 100px;" @change="handleCurrentChange" placeholder="厂家"   clearable  multiple  collapse-tags  collapse-tags-tooltip >
        <!-- <el-option key="all" value="" label="全部"></el-option> -->
        <el-option v-for="(item, id) in a_s_list" :key="id" :value="item.a_s" :label="item.a_s"></el-option>
      </el-select>
      <el-select v-model="searchR" size="small" style="width: 100px;" @change="handleCurrentChange" placeholder="分辨率" clearable  multiple  collapse-tags  collapse-tags-tooltip>
        <!-- <el-option key="all" value="" label="全部"></el-option> -->
        <el-option v-for="(item, id) in a_r_list" :key="id" :value="item.a_r" :label="item.a_r"></el-option>
      </el-select>
      <el-select v-model="searchV" size="small" style="width: 100px;" @change="handleCurrentChange" placeholder="场地" clearable  multiple  collapse-tags  collapse-tags-tooltip>
        <!-- <el-option key="all" value="" label="全部"></el-option> -->
        <el-option v-for="(item, id) in a_v_list" :key="id" :value="item.a_v" :label="item.a_v"></el-option>
      </el-select>
      <el-select v-model="searchP" size="small" style="width: 100px;" @change="handleCurrentChange" placeholder="视角" clearable  multiple  collapse-tags  collapse-tags-tooltip>
        <!-- <el-option key="all" value="" label="全部"></el-option> -->
        <el-option v-for="(item, id) in a_p_list" :key="id" :value="item.a_p"
          :label="perspectiveMap.get(item.a_p) || item.a_p"></el-option>
      </el-select>
      <el-select v-model="searchA" size="small" style="width: 100px;" @change="handleCurrentChange" placeholder="精度" clearable  multiple  collapse-tags  collapse-tags-tooltip>
        <!-- <el-option key="all" value="" label="全部"></el-option> -->
        <el-option v-for="(item, id) in a_a_list" :key="id" :value="item.a_a" :label="item.a_a"></el-option>
      </el-select>
      <el-select v-model="searchE" size="small" style="width: 100px;" @change="handleCurrentChange" placeholder="场景" clearable  multiple  collapse-tags  collapse-tags-tooltip>
        <!-- <el-option key="all" value="" label="全部"></el-option> -->
        <el-option v-for="(item, id) in a_e_list" :key="id" :value="item.a_e" :label="item.a_e"></el-option>
      </el-select>
      <el-date-picker size="small" v-model="searchObtainRange" type="daterange" start-placeholder="获得时间起:"
        value-format="YYYYMMDD" format="YYYY-MM-DD" style="max-width: 200px;" end-placeholder="止:" :editable="false"
        @change="handleCurrentChange"></el-date-picker>
    </el-space>
    <div class="table-div">
      <el-table class="my-table" :data="tableData" stripe style="width: 100%" size="small"
        v-el-height-adaptive-table="{ bottomOffset: 70, isUse: true }">
        <el-table-column label="项目" align="center">
          <template #default="{ row }">
            <el-tooltip :content="project_map.get(row.project_id)?.labels" placement="top">
              <el-text size="small">{{ "#" + row.project_id + ":" + project_map.get(row.project_id)?.a_n }}</el-text>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="任务id" align="center">
          <template #default="scope">
            <el-text size="small">#{{ scope.row.id }}</el-text>
            <el-text class="iconfont icon-yishanchu" type="danger" v-if="scope.row.del_flg"></el-text>
          </template>
        </el-table-column>
        <el-table-column prop="a_n" label="任务名称" align="center" />
        <el-table-column prop="status" label="状态" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.status == 'completed'" size="small" type="success">已完成</el-tag>
            <el-tag v-else-if="scope.row.status == 'annotation'" size="small" type="info">标注阶段</el-tag>
            <el-tag v-else-if="scope.row.status == 'validation'" size="small" type="warning">验证阶段</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="预览图" align="center" v-if="!encode_src">
          <template #default="{ row }">
            <authimg v-if="row.first_img" preview-teleported style="height: 40px;" :bm="!isSys && row.a_se == 'p'"
              :url="basePath_TASK + row.project_id + '/' + row.id + '/images/' + row.first_img + '?' + row.export_time">
            </authimg>
          </template>
        </el-table-column>
        <el-table-column prop="updated_date" label="更新时间" align="center">
          <template #default="scope">
            <el-text size="small">{{ showDateTime(scope.row.updated_date) }}</el-text>
          </template>
        </el-table-column>
        <el-table-column prop="export_status" label="导入时间" align="center">
          <template #default="scope">
            <el-text size="small"> {{ showDateTime(scope.row.export_time) }}</el-text>
          </template>
        </el-table-column>
        <el-table-column prop="size" label="样本数" align="center" />
        <el-table-column prop="a_s" label="厂家" align="center" v-if="showExt" />
        <el-table-column prop="a_r" label="分辨率" align="center" v-if="showExt" />
        <el-table-column prop="a_t" label="时间" align="center" v-if="showExt" />
        <el-table-column prop="a_v" label="场地" align="center" v-if="showExt" />
        <el-table-column prop="a_p" label="视角" align="center" v-if="showExt">
          <template #default="{ row }">
            {{ perspectiveMap.get(row.a_p) || row.a_p }}
          </template>
        </el-table-column>
        <el-table-column prop="a_a" label="精度" align="center" v-if="showExt" />
        <el-table-column prop="a_e" label="场景" align="center" v-if="showExt" />

        <el-table-column label="操作" align="center" fixed="right" width="250px">
          <template #default="{ row }">
            <el-space wrap>
              <el-button link size="small" @click="syncOnetask(row)" type="primary" :loading="row.loading_sync">
                <el-tag size="small" type="primary" class="iconfont icon-yingshe fontSpan">同步</el-tag>
              </el-button>
              <el-button v-if="isSys" link size="small" @click="showEditModal(row)">
                <el-tag size="small" type="primary" class="iconfont icon-bianji fontSpan">编辑</el-tag>
              </el-button>
              <el-button v-if="isSys" @click="delRecord(row)" link size="small">
                <el-tag size="small" type="danger" class="iconfont icon-shanchu fontSpan">删除</el-tag></el-button>

              <el-button @click="showDataset(row)" link v-if="row.first_img && !encode_src"
                :disabled="!isSys && row.a_se == 'p'" size="small">
                <el-tag size="small" :type="!isSys && row.a_se == 'p' ? 'info' : 'success'"
                  class="iconfont icon-chakan fontSpan">查看</el-tag>
              </el-button>
              <!-- 成功 -->
              <el-button v-if="row.export_status == 1" size="small" link @click="exportDataset(row)"><el-tag
                  size="small" type="success" class="iconfont icon-jinru fontSpan">{{ row.first_img ?
                    '重新导入' : '导入' }}</el-tag>
              </el-button>
              <!-- 排队中 -->
              <el-space v-else-if="row.export_status == 2" :size="1">
                <el-button size="small" link :loading="true"><el-tag size="small" type="danger"
                    class="iconfont icon-jinru fontSpan">排队中</el-tag>
                </el-button>
                <el-button size="small" link @click="topExport(row)"><el-tag size="small" type="success">置顶</el-tag>
                </el-button>
                <el-button size="small" link @click="cancelExport(row)"><el-tag size="small" type="success">取消</el-tag>
                </el-button>
              </el-space>
              <!-- 执行中 -->
              <el-button v-else-if="row.export_status == 3" size="small" link :loading="true"><el-tag size="small"
                  type="danger" class="iconfont icon-jinru fontSpan">正在导入</el-tag>
              </el-button>
              <!-- 失败 -->
              <el-button v-else-if="row.export_status == 4" size="small" link @click="exportDataset(row)"
                title="最近一次导入失败"><el-tag size="small" type="danger" class="iconfont icon-yichang fontSpan">{{
                  row.first_img ?
                    '重新导入' : '导入' }}</el-tag>
              </el-button>
              <!-- 其他 -->
              <el-button v-else size="small" link @click="exportDataset(row)"><el-tag size="small" type="info"
                  class="iconfont icon-jinru fontSpan">{{ row.first_img ?
                    '重新导入' : '导入' }}</el-tag>
              </el-button>
              <el-button v-if="row.first_img" size="small" link @click="showDataTrans(row)" :loading="row.data_trans"><el-tag size="small"
                  type="info" class="iconfont icon-modelTrans fontSpan">数据集转换</el-tag>
              </el-button>
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
  <el-dialog v-model="editVisible" width="60%" title="编辑" draggable :close-on-click-modal="false">
    <el-form label-position="left" label-width="80">
      <el-form-item label="id">
        <el-input v-model="editForm.id" disabled></el-input>
      </el-form-item>
      <el-form-item label="名称">
        <el-input v-model="editForm.a_n"></el-input>
      </el-form-item>
      <el-form-item label="厂家">
        <el-input v-model="editForm.a_s"></el-input>
      </el-form-item>
      <el-form-item label="分辨率">
        <el-input v-model="editForm.a_r"></el-input>
      </el-form-item>
      <el-form-item label="时间">
        <el-input v-model="editForm.a_t"></el-input>
      </el-form-item>
      <el-form-item label="场地">
        <el-input v-model="editForm.a_v"></el-input>
      </el-form-item>
      <el-form-item label="视角">
        <el-input v-model="editForm.a_p"></el-input>
      </el-form-item>
      <el-form-item label="精度">
        <el-input v-model="editForm.a_a"></el-input>
      </el-form-item>
      <el-form-item label="场景">
        <el-input v-model="editForm.a_e"></el-input>
      </el-form-item>

    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="editVisible = false">关闭</el-button>
        <el-button @click="editRecord" type="primary">确定</el-button>
      </span>
    </template>
  </el-dialog>
  <!-- 数据集转换 -->
  <el-dialog v-model="transVisible" width="60%" title="数据集转换" draggable :close-on-click-modal="false">
    <el-form label-position="left" label-width="80">
      <el-form-item label="转换算法">
        <el-select v-model="dataForm.alg" @change="onAlgChange">
          <el-option v-for="item in algList" :key="item.id" :value="item.id" :label="item.name"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="转换参数" prop="cfg">
        <div style="height:200px;width:100%">
          <aceEdit ref='aceEditRef' v-model="dataForm.cfg" :height="200" />
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="transVisible = false">关闭</el-button>
        <el-button @click="startTrans" type="primary">确定</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref } from "@vue/reactivity";
import { useRoute } from "vue-router";
import { useTitleStore, useUserStore } from "../../stores/index";
import { ApiService, EngineProjectService, EngineTaskService, TrainScriptService } from "../../api/api";
import { ElMessage, dayjs, ElMessageBox } from "element-plus";
import { nextTick, onMounted, onUnmounted } from "@vue/runtime-core";
import { basePath_SSE_EXPORT, basePath_TASK } from "../../api/axios";
import fileview from "../../components/fileview.vue";
import authimg from "../../components/authimg.vue"
import { ExportStatusMap, perspectiveMap } from "../../utils/selfmaps";
import AceEdit from '@/components/AceEdit/index.vue'
// const { meta } = useRoute();
// useTitleStore().$patch((state) => {
//   state.title = meta.title;
// });
//获取配置信息,关闭图片预览功能
const encode_src = ref(false);
ApiService.getSysInfo().then(res => {
  if (res.code === 0) {
    encode_src.value = res.data.encode_src;
  }
})

const props = defineProps({
  msg: Number,
});
const isSys = useUserStore().user.type == 1;
const searchId = ref();
const searchName = ref("");
const searchProject = ref([]);
const project_list = ref([]);
const project_map = ref(new Map);
const searchStatus = ref("")
const searchDel = ref(-1);
const searchS = ref([])
const searchR = ref([])
const searchV = ref([])
const searchP = ref([])
const searchA = ref([])
const searchE = ref([])

const searchObtainRange = ref()
const currentPage = ref(1);
const currentSize = ref(10);
const total = ref(0);
const tableData = ref([]);
const showExt = ref(false)
/**页码变化 */
const handleCurrentChange = () => {
  let range = searchObtainRange.value
  let start_time = null
  let end_time = null
  if (range && range.length == 2) {
    start_time = range[0]
    end_time = range[1]
  }
  let tid = parseInt(searchId.value) || null;
  EngineTaskService.queryList({
    current: currentPage.value,
    size: currentSize.value,
    a_n: searchName.value,
    project_ids: searchProject.value?.join() || null,
    id: tid,
    status: searchStatus.value,
    a_s: searchS.value?.join() || null,
    a_r: searchR.value?.join() || null,
    a_v: searchV.value?.join() || null,
    a_p: searchP.value?.join() || null,
    a_a: searchA.value?.join() || null,
    a_e: searchE.value?.join() || null,
    start_time: start_time,
    end_time: end_time,
    del_flg: searchDel.value
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

const dataVisible = ref(false);
const base_type = ref('task');
const base_uri = ref();
const base_path = ref();
const showDataset = (row) => {
  base_path.value = row.project_id + '/' + row.id;//文件路径相对根目录
  base_uri.value = basePath_TASK + '/' + base_path.value;//url的完整根路径
  dataVisible.value = true
}
const exportDataset = (row) => {
  ElMessageBox.confirm(
    // "导入内容是否包含图片",
    "请选择导入内容",
    '提示',
    {
      distinguishCancelAndClose: true,
      // confirmButtonText: '包含图片',
      // cancelButtonText: '不含图片',
      confirmButtonText: '导入标签、图片',
      cancelButtonText: '导入标签',
      type: 'info',
    }
  ).then(() => {
    execExport(row, true)
  }).catch((action) => {
    if (action === 'cancel') {
      execExport(row, false)
    }
  })
}
const execExport = (row, withImg) => {
  EngineTaskService.exportDataset({
    id: row.id,
    format: 'CVAT+for+images+1.1',
    save_image: withImg  //true 包含图片  false,只导标签
  }).then(res => {
    if (res.code === 0) {
    } else {
      ElMessage.warning(res.msg)
    }
  })
}
const syncOnetask = (row) => {
  row.loading_sync = true;
  EngineTaskService.syncOneTask({ id: row.id }).then(res => {
    if (res.code === 0) {
      ElMessage.success(res.msg);
      handleCurrentChange();
    } else {
      ElMessage.warning(res.msg)
    }
    row.loading_sync = false;
  }).catch(() => {
    row.loading_sync = false;
  })
}
/**置顶 */
const topExport = (row) => {
  EngineTaskService.exportTop({ id: row.id }).then(res => {
    if (res.code === 0) {
      ElMessage.success('请求成功')
      handleCurrentChange()
    } else {
      ElMessage.warning(res.msg)
    }
  })
}
/**取消 */
const cancelExport = (row) => {
  EngineTaskService.exportCancel({ id: row.id }).then(res => {
    if (res.code === 0) {
      ElMessage.success('请求成功')
      handleCurrentChange()
    } else {
      ElMessage.warning(res.msg)
    }
  })
}
const addLoading = ref(false)
const clearLoading = ref(false)
const addNewTask = () => {
  ElMessageBox.prompt('请输入任务id', '导入新任务', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
  })
    .then(({ value }) => {
      if (isNaN(value)) {
        ElMessage.warning('请输入正确的id')
        return
      }
      addLoading.value = true
      EngineTaskService.add({ id: value }).then(res => {
        if (res.code === 0) {
          ElMessage.success(res.msg)
          handleCurrentChange()
        } else {
          ElMessage.warning(res.msg)
        }
        addLoading.value = false
      }).catch((e) => {
        addLoading.value = false
      })
    })
    .catch(() => {

    })
}
const clearDelTask = () => {
  ElMessageBox.confirm(
    "删除要系统中全部标记为已删除的任务?",
    '',
    {
      distinguishCancelAndClose: true,
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info',
    }
  ).then(() => {
    clearLoading.value = true;
    EngineTaskService.cleartDelFlg().then(res => {
      if (res.code == 0) {
        ElMessage.success(res.msg + ":" + res.data)
        handleCurrentChange()
      } else {
        ElMessage.warning(res.msg)
      }
      clearLoading.value = false
    }).catch(() => {
      clearLoading.value = false
    })
  }).catch(() => {
  })
}
const delRecord = (row) => {
  ElMessageBox.confirm(
    `删除任务及任务相关的数据集,确定要删除[${row.name}]?`,
    '',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  )
    .then(() => {
      EngineTaskService.delete({ id: row.id }).then(res => {
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
/**数据集转换 */
const transVisible = ref(false)
const dataForm = ref({})
const algList = ref([])
const aceEditRef = ref()
const queryAlgList = () => {
  TrainScriptService.queryAll({ type: 'data' }).then(res => {
    if (res.code === 0) {
      algList.value = res.data;
      console.log(res.data)
    }
  })
}
const getItemById = (id, list) => {
  for (let i = 0; i < list.length; i++) {
    if (list[i].id == id) {
      return list[i]
    }
  }
  return null;
}
const onAlgChange = (val) => {
  let item = getItemById(val, algList.value);
  if (item) {
    dataForm.value.cfg = item.cfg;
  }
}
const showDataTrans = (row) => {
  dataForm.value = { id: row.id };
  transVisible.value = true;
}
const startTrans = () => {
  let form = dataForm.value;
  if (!form.alg) {
    ElMessage.warning('请选择转换算法')
    return
  }
  try {
    let json = JSON.parse(form.cfg)
    EngineTaskService.startDataTrans({
      id: form.id,
      alg_id: form.alg,
      params: json,
    }).then(res => {
      if (res.code === 0) {
        handleCurrentChange()
        transVisible.value = false
      } else {
        ElMessage.warning(res.msg)
      }
    })

  } catch (error) {
    ElMessage.warning('请输入正确的参数,json格式')
    return;
  }
}

const editVisible = ref(false)
const editForm = reactive({})
const showEditModal = (row) => {
  editForm.id = row.id
  editForm.a_n = row.a_n
  editForm.a_s = row.a_s
  editForm.a_r = row.a_r
  editForm.a_t = row.a_t
  editForm.a_v = row.a_v
  editForm.a_p = row.a_p
  editForm.a_a = row.a_a
  editForm.a_e = row.a_e

  editVisible.value = true
}
const editRecord = () => {
  EngineTaskService.update({
    id: editForm.id,
    a_n: editForm.a_n,
    a_s: editForm.a_s,
    a_r: editForm.a_r,
    a_t: editForm.a_t,
    a_v: editForm.a_v,
    a_p: editForm.a_p,
    a_a: editForm.a_a,
    a_e: editForm.a_e,
  }).then(res => {
    if (res.code === 0) {
      ElMessage.success(res.msg)
      handleCurrentChange()
      editVisible.value = false
    } else {
      ElMessage.warning(res.msg)
    }
  })
}
const queryAllproject = () => {
  EngineProjectService.queryAll().then(res => {
    if (res.code === 0) {
      project_list.value = res.data
      let map = new Map()
      res.data.forEach(item => {
        map.set(item.project_id, item)
      });
      project_map.value = map
    }
  })
}

const a_s_list = ref([]);
const a_r_list = ref([]);
const a_v_list = ref([]);
const a_p_list = ref([]);
const a_a_list = ref([]);
const a_e_list = ref([]);

EngineTaskService.queryDistinct({ field: 'a_s' }).then(res => {
  if (res.code === 0) {
    a_s_list.value = res.data;
  }
})
EngineTaskService.queryDistinct({ field: 'a_r' }).then(res => {
  if (res.code === 0) {
    a_r_list.value = res.data;
  }
})
EngineTaskService.queryDistinct({ field: 'a_v' }).then(res => {
  if (res.code === 0) {
    a_v_list.value = res.data;
  }
})
EngineTaskService.queryDistinct({ field: 'a_p' }).then(res => {
  if (res.code === 0) {
    a_p_list.value = res.data;
  }
})
EngineTaskService.queryDistinct({ field: 'a_a' }).then(res => {
  if (res.code === 0) {
    a_a_list.value = res.data;
  }
})
EngineTaskService.queryDistinct({ field: 'a_e' }).then(res => {
  if (res.code === 0) {
    a_e_list.value = res.data;
  }
})
queryAllproject();
queryAlgList();
handleCurrentChange();

const updateTable = (item) => {
  let arr = tableData.value;
  for (let i = 0; i < arr.length; i++) {
    const task = arr[i];
    if (task.id === item.id) {//匹配
      if (task.export_status !== undefined) {
        task.export_status = item.export_status;
        if (item.export_status == 1) {
          task.first_img = item.first_img;
          task.export_time = item.export_time;
          ElMessage.success(`任务[${task.id}]导入成功`)
        } else if (item.export_status == 4) {
          ElMessage.error(`任务[${task.id}]导入失败`)
        }
      } else if (item.data_trans !== undefined) {
        task.data_trans = item.data_trans
      }
      break;
    }
  }
}
/**sse 连接 */
let eventSource;
onMounted(() => {
  eventSource = new EventSource(basePath_SSE_EXPORT);

  eventSource.onmessage = (event) => {
    updateTable(JSON.parse(event.data))
  };

  eventSource.onerror = (error) => {
    console.error('Error:', error);
  };
})
onUnmounted(() => {
  if (eventSource) {
    eventSource.close();
  }
})


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
