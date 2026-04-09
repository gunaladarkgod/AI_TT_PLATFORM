<template>
  <div class="content-div">
    <div class="search-div flex-between">
      <div class="flex-start">
        <el-select v-model="searchType" placeholder="选择配置类型" size="small" class="width-200"
          @change="handleCurrentChange">
          <el-option label="全部" value=""></el-option>
          <el-option :label="fileTypeMap.get('weights')" value="weights"></el-option>
          <el-option :label="fileTypeMap.get('cfg')" value="cfg"></el-option>
          <el-option :label="fileTypeMap.get('hyp')" value="hyp"></el-option>
        </el-select>
        <el-input v-model="searchId" placeholder="id查询" size="small" class="width-150">
        </el-input>
        <el-input v-model="searchName" placeholder="名称查询" size="small">
        </el-input>
        <el-button @click="handleCurrentChange" size="small"><el-icon><i
              class="iconfont icon-sousuo"></i></el-icon></el-button>
      </div>
      <div>
        <el-button type="primary" :loading="addLoading" @click="showAddModal" size="small" v-show="isSys"><el-text
            size="small" class="text-white">添加配置文件</el-text>
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
        <el-table-column prop="type" label="类型" align="center">
          <template #default="{ row }">
            {{ fileTypeMap.get(row.type) }}
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" align="center" />
        <el-table-column prop="created_date" label="创建时间" align="center" width="100">
          <template #default="scope">
            <el-text size="small">{{ showDateTime(scope.row.created_date) }}</el-text>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" align="center" />

        <el-table-column label="操作" align="center" fixed="right" width="250">
          <template #default="scope">
            <el-space v-show="isSys">
              <el-button link size="small" @click="viewFile(scope.row)">
                <el-tag size="small" type="primary" class="iconfont icon-chakan fontSpan">查看</el-tag>
              </el-button>
              <el-button link size="small" @click="showEditModal(scope.row)">
                <el-tag size="small" type="primary" class="iconfont icon-bianji fontSpan">编辑</el-tag>
              </el-button>
              <el-button @click="delRecord(scope.row)" link size="small">
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
  <el-dialog v-model="addVisible" width="60%" title="创建" draggable :close-on-click-modal="false">
    <el-form label-width="120" label-position="left">
      <el-form-item label="文件类型" required>
        <el-select v-model="addForm.type" placeholder="选择配置类型" class="width-200">
          <el-option :label="fileTypeMap.get('weights')" value="weights"></el-option>
          <el-option :label="fileTypeMap.get('cfg')" value="cfg"></el-option>
          <el-option :label="fileTypeMap.get('hyp')" value="hyp"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="配置名称" required>
        <el-input v-model="addForm.name"></el-input>
      </el-form-item>
      <el-form-item label="上传文件" required>
        <el-upload action="#" :auto-upload="false" :on-change="uploadChange" ref="uploadRef"
          :accept="addForm.type == 'weights' ? '.pt' : '.yaml'">
          <template #trigger>
            <el-button >+</el-button>
          </template>
        </el-upload>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="addForm.remark" type="textarea"></el-input>
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="addVisible = false">关闭</el-button>
        <el-button @click="addRecord"  type="primary">确定</el-button>
      </span>
    </template>
  </el-dialog>
  <el-dialog v-model="editVisible" width="60%" title="编辑" draggable :close-on-click-modal="false">
    <el-form label-width="120">
      <el-form-item label="配置名称" required>
        <el-input v-model="editForm.name"></el-input>
      </el-form-item>
      <el-form-item label="文件类型" required>
        <el-select v-model="editForm.type" placeholder="选择配置类型" class="width-200" disabled>
          <el-option :label="fileTypeMap.get('weights')" value="weights"></el-option>
          <el-option :label="fileTypeMap.get('cfg')" value="cfg"></el-option>
          <el-option :label="fileTypeMap.get('hyp')" value="hyp"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="editForm.remark" type="textarea"></el-input>
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="editVisible = false">关闭</el-button>
        <el-button @click="editRecord" :loading="addLoading"  type="primary">确定</el-button>
      </span>
    </template>
  </el-dialog>
  <el-dialog v-model="txtVisible" width="60%" top="5vh" title="查看" draggable :close-on-click-modal="true"
    :destroy-on-close="true">
    <pre  class="preBox">{{ cur_text }}</pre>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="txtVisible = false">关闭</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref } from "@vue/reactivity";
import { useRoute, useRouter } from "vue-router";
import { useTitleStore, useUserStore } from "../../stores/index";
import { FileService, TrainYoloService } from "../../api/api";
import { ElMessage, dayjs, ElMessageBox } from "element-plus";
import { nextTick } from "@vue/runtime-core";
import { basePath_YOLO } from "../../api/axios";
import { isNum } from "../../utils/regex";
import { fileTypeMap } from "../../utils/selfmaps";
// const { meta } = useRoute();
// useTitleStore().$patch((state) => {
//   state.title = meta.title;
// });
const props = defineProps({
  msg: Number,
});
const isSys = useUserStore().user.type == 1;

const router = useRouter();
const searchName = ref("");
const searchId = ref('');
const searchType = ref('');
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
  TrainYoloService.queryList({
    current: currentPage.value,
    size: currentSize.value,
    name: searchName.value,
    id: id,
    type: searchType.value || null,
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

const addVisible = ref(false)
const addForm = reactive({})
const uploadRef = ref()
const fileList = ref([]);
const addLoading = ref(false)
const showAddModal = () => {
  addForm.type = searchType.value || 'weights';
  addForm.name = '';
  addVisible.value = true
  uploadRef.value?.clearFiles();
}

/**监听 文件列表变化 */
const uploadChange = (file, list) => {
  if (list.length > 1 && file.status !== "fail") {
    list.splice(0, 1);
  } else if (file.status === "fail") {
    ElMessage.warning("上传失败，请重新上传！");
    list.splice(0, 1);
  }
  fileList.value = list;
};

const addRecord = () => {
  let name = addForm.name?.trim()
  let type = addForm.type
  if (!name || !type) {
    ElMessage.warning('表单填写不完整')
    return
  }
  let fd = new FormData();
  fd.append("type", type);
  fd.append("name", name);
  fd.append("remark", addForm.remark || '');
  if (fileList.value.length > 0) {
    fd.append("file", fileList.value[0].raw);
  } else {
    ElMessage.warning('请上传配置文件')
    return;
  }
  addLoading.value = true;
  TrainYoloService.add(fd)
    .then((res) => {
      if (res.code === 0) {
        nextTick(() => {
          ElMessage.success(res.msg);
          addVisible.value = false;
          handleCurrentChange();
        });
      } else {
        ElMessage.warning(res.msg);
      }
      addLoading.value = false;
    })
    .catch((e) => {
      addLoading.value = false;
    });
}

const txtVisible = ref(false);
const cur_text = ref('')
const viewFile = (row) => {
  let url = basePath_YOLO + row.type + "_" + row.id + row.path
  if (row.type === 'weights') {
    downFile(url)
  } else {
    readTxt(url)
  }
}

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
const downFile = (url) => {
  if (!url) return;
  url = url.replace(/\\/g, '/');
  FileService.getFile(url, 'blob').then(blob => {
    const url2 = URL.createObjectURL(blob);
    // 自动触发下载
    const anchor = document.createElement('a');
    anchor.href = url2;
    anchor.download = url.substring(url.lastIndexOf('/') + 1);
    anchor.click();
    // 释放内存
    URL.revokeObjectURL(url2);
  })
}


const delRecord = (row) => {
  ElMessageBox.confirm(
    `确定要删除[${row.name}]?`,
    '',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  )
    .then(() => {
      TrainYoloService.delete({ id: row.id }).then(res => {
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

const editVisible = ref(false)
const editForm = reactive({})
const showEditModal = (row) => {
  editForm.id = row.id
  editForm.name = row.name
  editForm.type = row.type
  editVisible.value = true
}
const editRecord = () => {
  TrainYoloService.update({
    id: editForm.id,
    name: editForm.name,
    remark: editForm.remark,
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
