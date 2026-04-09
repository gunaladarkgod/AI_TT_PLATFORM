<template>
  <div class="content-div">
    <div class="search-div flex-between">
      <div class="flex-start">
        <el-select v-model="searchMerge" placeholder="标签类别" size="small" @change="handleCurrentChange">
          <el-option label="全部" :value="-1"></el-option>
          <el-option label="合并标签" :value="1"></el-option>
          <el-option label="原始标签" :value="0"></el-option>
        </el-select>
        <el-input v-model="searchName" placeholder="名称检索" size="small">
        </el-input>
        <!-- <el-input v-model="searchNick" placeholder="别名检索" size="small">
        </el-input> -->
        <el-button @click="handleCurrentChange" size="small"><el-icon><i
              class="iconfont icon-sousuo"></i></el-icon></el-button>
      </div>
      <div>
        <el-button type="primary" :loading="syncAllLoading" @click="syncRecord" size="small"><el-text size="small"
            v-if="isSys" class="text-white">同步原始标签</el-text>
        </el-button>
        <el-button type="primary" @click="showAddModal" size="small"><el-text size="small"
            class="text-white">添加合并标签</el-text>
        </el-button>
      </div>
    </div>
    <div class="table-div">
      <el-table class="my-table" :data="filterData" stripe style="width: 100%" size="small"
        v-el-height-adaptive-table="{ bottomOffset: 70, isUse: true }">
        <el-table-column type="index" label="序号" align="center">
        </el-table-column>
        <el-table-column prop="name" label="名称" align="center">
          <template #default="scope">
            <el-tag size="small" :type="scope.row.merge ? 'warning' : 'info'">{{ scope.row.name }}</el-tag>
          </template>
        </el-table-column>
        <!-- <el-table-column prop="nick" label="别名" align="center" /> -->
        <el-table-column prop="status" label="标签子集" align="center">
          <template #default="scope">
            <el-space v-if="scope.row.merge">
              <el-tag size="small" v-for="(item, id) in scope.row.children.split(',')" :key="id">{{ item }}</el-tag>
            </el-space>
          </template>
        </el-table-column>

        <el-table-column label="操作" align="center">
          <template #default="scope">
            <el-space>
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
        @size-change="" @current-change="" />

    </div>
  </div>
  <el-dialog v-model="addVisible" width="50%" title="添加合并标签" draggable :close-on-click-modal="false">
    <el-form label-position="left" label-width="100">
      <el-form-item label="新标签名" required>
        <el-input v-model="addForm.name"></el-input>
      </el-form-item>
      <el-form-item label="标签别名">
        <el-input v-model="addForm.nick"></el-input>
      </el-form-item>
      <el-form-item label="标签内容" required>
        <el-select v-model="addForm.children" multiple clearable collapse-tags-tooltip placeholder="选择要合并的标签"
          filterable>
          <el-option v-for="item in tableData" :key="item.id" :label="item.name" :value="item.name" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="addVisible = false">关闭</el-button>
        <el-button @click="addRecord" type="primary">确定</el-button>
      </span>
    </template>
  </el-dialog>
  <el-dialog v-model="editVisible" width="50%" title="编辑标签" draggable :close-on-click-modal="false">
    <el-form>
      <el-form-item label="新标签名">
        <el-input v-model="editForm.name" disabled></el-input>
      </el-form-item>
      <el-form-item label="标签别名">
        <el-input v-model="editForm.nick"></el-input>
      </el-form-item>
      <el-form-item label="标签内容" v-if="editForm.merge">
        <el-select v-model="editForm.children" multiple clearable collapse-tags-tooltip placeholder="选择要合并的标签"
          filterable>
          <el-option v-for="item in tableData" :key="item.id" :label="item.name" :value="item.name" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="editVisible = false">关闭</el-button>
        <el-button @click="editRecord" type="primary">确定</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from "@vue/reactivity";
import { useRoute } from "vue-router";
import { useTitleStore, useUserStore } from "../../stores/index";
import { TrainLabelService } from "../../api/api";
import { ElMessage, ElMessageBox } from "element-plus";
import { nextTick } from "@vue/runtime-core";
// const { meta } = useRoute();
// useTitleStore().$patch((state) => {
//   state.title = meta.title;
// });
const props = defineProps({
  msg: Number,
});
const isSys = useUserStore().user.type == 1;

const searchName = ref("");
const searchNick = ref("");
const searchMerge = ref(-1)
const currentPage = ref(1);
const currentSize = ref(20);
const total = ref(0);
const tableData = ref([]);
const filterData = computed(() => {
  return tableData.value.slice((currentPage.value - 1) * currentSize.value, currentPage.value * currentSize.value)
})

const handleCurrentChange = () => {
  TrainLabelService.queryAll({
    name: searchName.value,
    nick: searchNick.value,
    merge: searchMerge.value
  }).then(function (res) {
    if (res.code === 0) {
      nextTick(() => {
        tableData.value = res.data;
        total.value = res.data.length;
      });
    } else {
      ElMessage.warning(res.msg);
    }
  });
};
const syncAllLoading = ref(false)
const syncRecord = () => {
  syncAllLoading.value = true
  TrainLabelService.syncAll({}).then(res => {
    if (res.code === 0) {
      ElMessage.success(res.msg)
      handleCurrentChange()
    } else {
      ElMessage.warning(res.msg)
    }
    syncAllLoading.value = false
  }).catch(() => {
    syncAllLoading.value = false
  })
}

const addForm = reactive({})
const addVisible = ref(false)

const showAddModal = () => {
  addForm.name = null
  addForm.children = null
  addVisible.value = true
}
const addRecord = () => {
  let name = addForm.name?.trim()
  let children = addForm.children
  if (!name) {
    ElMessage.warning('请输入名称')
    return;
  }
  if (!children || children.length == 0) {
    ElMessage.warning('请选择需要合并的标签')
    return
  }
  TrainLabelService.add({
    name: name,
    children: children.join(','),
    nick: addForm.nick || '',
    merge: 1
  }).then(res => {
    if (res.code === 0) {
      ElMessage.success(res.msg)
      handleCurrentChange()
      addVisible.value = false
    } else {
      ElMessage.warning(res.msg)
    }
  })
}

const editForm = reactive({})
const editVisible = ref(false)
const showEditModal = (row) => {
  editForm.id = row.id
  editForm.name = row.name
  editForm.nick = row.nick
  editForm.merge = row.merge
  editForm.children = row.children?.split(',')
  editVisible.value = true
}

const editRecord = () => {
  if (editForm.merge) {
    let children = editForm.children
    if (!children || children.length == 0) {
      ElMessage.warning('请选择需要合并的标签')
      return
    }
  }
  TrainLabelService.update({
    id: editForm.id,
    nick: editForm.nick,
    children: editForm.merge ? editForm.children.join(',') : null
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
      TrainLabelService.delete({ id: row.id }).then(res => {
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
