<template>
  <div class="content-div">
    <div class="search-div flex-between">
      <div class="flex-start">
        <el-input v-model="searchInput" placeholder="输入用户名查询" size="small">
        </el-input>
        <el-button @click="handleCurrentChange" size="small"><el-icon><i
              class="iconfont icon-sousuo"></i></el-icon></el-button>
      </div>
      <div>
        <el-button type="primary" @click="addForm" size="small"><el-text size="small" class="text-white">添加用户</el-text>
        </el-button>
      </div>
    </div>
    <div class="table-div">
      <el-table class="my-table" :data="tableData" stripe style="width: 100%" size="small"
        v-el-height-adaptive-table="{ bottomOffset: 70, isUse: true }">
        <el-table-column type="index" label="序号" align="center" />
        <el-table-column prop="id" label="用户id" align="center" />
        <el-table-column prop="username" label="用户名" align="center" />
        <el-table-column prop="nickname" label="姓名" align="center" />
        <el-table-column label="类别" align="center">
          <template #default="scope">
            {{ userTypeMap.get(scope.row.type) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" align="center">
          <template #default="scope">
            {{ scope.row.status == 1 ? "正常" : "锁定" }}
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机" align="center" />
        <el-table-column prop="part" label="部门" align="center" />
        <el-table-column prop="addtime" label="创建时间" align="center" width="170" />
        <el-table-column label="操作" align="center" width="300" fixed="right">
          <template #default="scope">
            <el-space>
              <el-button link size="small" @click="showEditModal(scope.row)">
                <el-tag class="iconfont icon-xiugai fontSpan">修改</el-tag>
              </el-button>
              <el-button link size="small" @click="showResetModal(scope.row)">
                <el-tag class="iconfont icon-zhongzhi fontSpan">重置密码</el-tag>
              </el-button>
              <el-button link size="small" @click="showDelModal(scope.row)"
                :disabled="scope.row.username == userStore.user?.username">
                <el-tag class="iconfont icon-shanchu fontSpan">删除</el-tag>
              </el-button>
              <el-button link size="small" @click="projectAuthorization(scope.row)"  v-if="scope.row.type!=1">
                <el-tag class="iconfont icon-shouquan fontSpan">项目授权</el-tag>  
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
  <el-dialog v-model="createVisible" width="60%" title="添加用户" draggable :close-on-click-modal="false">
    <div>
      <el-form :model="form" ref="formRef" :rules="rules">
        <el-form-item label="用户名称" required prop="username">
          <el-input v-model="form.username"></el-input>
        </el-form-item>
        <el-form-item label="用户姓名" class="label-before" prop="nickname">
          <el-input v-model="form.nickname"></el-input>
        </el-form-item>
        <el-form-item label="用户部门" class="label-before" prop="part">
          <el-input v-model="form.part"></el-input>
        </el-form-item>
        <el-form-item label="用户手机" class="label-before" prop="phone">
          <el-input v-model="form.phone"></el-input>
        </el-form-item>
        <el-form-item label="用户类别" required prop="type">
          <el-radio-group v-model="form.type">
            <el-radio :value="item[0]" v-for="item in userTypeList" :key="item[0]">{{ item[1] }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="用户状态" required prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="1">正常</el-radio>
            <el-radio value="0">锁定</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注说明" class="label-before" prop="remark">
          <el-input v-model="form.remark" type="textarea"></el-input>
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="createRecord">确定</el-button>
      </span>
    </template>
  </el-dialog>
  <el-dialog v-model="editVisible" width="40%" title="修改" draggable :close-on-click-modal="false">
    <div>
      <el-form label-position="left" label-width="80">
        <el-form-item label="用户id" v-show="false">
          <el-input v-model="editForm.id" type="text" disabled hidden />
        </el-form-item>
        <el-form-item label="用户名称" required>
          <el-input v-model="editForm.username" type="text" disabled />
        </el-form-item>
        <el-form-item label="&ensp;用户姓名">
          <el-input v-model="editForm.nickname" type="text" />
        </el-form-item>
        <el-form-item label="&ensp;用户手机">
          <el-input v-model="editForm.phone" type="text" />
        </el-form-item>
        <el-form-item label="&ensp;用户部门">
          <el-input v-model="editForm.part" type="text" />
        </el-form-item>
        <el-form-item label="用户类别" required>
          <el-radio-group v-model="editForm.type">
            <el-radio :value="item[0]" v-for="item in userTypeList" :key="item[0]">{{ item[1] }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="用户状态" required>
          <el-radio-group v-model="editForm.status">
            <el-radio value="1">正常</el-radio>
            <el-radio value="0">锁定</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="&ensp;备注说明">
          <el-input v-model="editForm.remark" type="textarea" />
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="updateRecord">确定</el-button>
      </span>
    </template>
  </el-dialog>
  <el-dialog v-model="delVisible" width="30%" title="删除" draggable :close-on-click-modal="false">
    <div>
      <h3>
        确定要<el-text type="danger"> 删除 </el-text>
        <el-tag>{{ delForm.username }}</el-tag> ?
      </h3>
      <el-form>
        <el-form-item label="id" v-show="false">
          <el-input v-model="delForm.id" type="text" disabled />
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="delVisible = false">取消</el-button>
        <el-button type="primary" @click="delRecord">确定</el-button>
      </span>
    </template>
  </el-dialog>
  <el-dialog v-model="resetVisible" width="40%" title="重置密码" draggable :close-on-click-modal="false">
    <div>
      <h3>
        重置后密码将恢复到<el-tag>默认值</el-tag>，确定重置用户
        <el-tag> {{ resetForm.username }}</el-tag> 的密码?
      </h3>
      <el-form>
        <el-form-item label="id" v-show="false">
          <el-input v-model="resetForm.id" type="text" disabled />
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" @click="resetPwd">确定</el-button>
      </span>
    </template>
  </el-dialog>
  <el-dialog v-model="libVisible" width="40%" :title="'关联型号[' + curUser.username + ']'" draggable
    :close-on-click-modal="false">
    <div class="flex-center">
      <el-transfer :props="{
        key: 'id',
        label: 'id',
      }" filterable v-model="curList" :data="libList" :titles="['型号列表', '关联型号']" />
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="libVisible = false">取消</el-button>
        <el-button type="primary" @click="updateUserLib">确定</el-button>
      </span>
    </template>
  </el-dialog>




 
  <!-- 项目授权 -->

  <customElDialog title="项目授权"  :ZIndex="2032" ref="projectRef"  :draggable="true" :before-close="()=>{projectRef.close()}">
    <template #content>
      <vxe-table
        stripe
        border="inner"
        show-overflow
        ref="projectTableRef"  
        header-row-class-name="projectTableHeader"
        height="500px"
        align="center"
        size="mini"
        :scroll-y="{enabled: true, gt: 0}"
        :loading="loading">
        <vxe-column type="checkbox" width="55"></vxe-column>
        <vxe-column type="seq" title="project_id">
          <template #default="scope">
            <el-text size="small">#{{ scope.row.id }}</el-text>
            <el-text class="iconfont icon-yishanchu" type="danger" v-if="scope.row.del_flg"></el-text>
          </template>
        </vxe-column>
        <vxe-column field="a_n" title="名称"></vxe-column>
        <vxe-column field="a_l" title="光源类型" ></vxe-column>
        <vxe-column field="a_s" title="来源" ></vxe-column>
        <vxe-column field="a_g" title="用途" ></vxe-column>
      </vxe-table>
    </template>
 
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="()=>{projectRef.close()}">取消</el-button>
        <el-button type="primary" @click="projectBinding" >确定</el-button>
      </span>
    </template>
  </customElDialog>

</template>

<script setup>
import customElDialog from '../../components/customElDialog/index.vue'
import { reactive, ref } from "@vue/reactivity";

import { useRouter, useRoute } from 'vue-router'
import { useTitleStore, useUserStore ,useLoginStore} from "../../stores/index";
import { UserService ,EngineProjectService,UserProjectService} from "../../api/api";
import { ElMessage } from "element-plus";
import { nextTick } from "@vue/runtime-core";
import { userTypeMap, userTypeList } from "../../utils/selfmaps";
import { onBeforeUnmount } from 'vue';
// const { meta } = useRoute();
// useTitleStore().$patch((state) => {
//   state.title = meta.title;
// });
const props = defineProps({
  msg: Number,
});
let timer=null
const router = useRouter();
const loginStore = useLoginStore();
const userStore = useUserStore();
const searchInput = ref("");
const currentPage = ref(1);
const currentSize = ref(10);
const total = ref(0);
const tableData = ref([]);
const createVisible = ref(false);
const editVisible = ref(false);
const delVisible = ref(false);
const resetVisible = ref(false);
const libVisible = ref(false);
const libList = ref([]);
const curList = ref([]);
const curUser = ref({});
const form = reactive({
  username: "",
  nickname: "",
  part: "",
  phone: "",
  type: 3,
  status: "1",
  remark: "",
});
// 重置表单ref
const formRef = ref(null);
// 表单验证
const rules = {
  username: [
    { required: true, message: "请输入用户名", trigger: ["change", "blur"] },
  ],
  type: [
    { required: true, message: "请选择用户类别", trigger: ["change", "blur"] },
  ],
  status: [
    { required: true, message: "请选择用户状态", trigger: ["change", "blur"] },
  ],
};
const editForm = reactive({});
const delForm = reactive({});
const resetForm = reactive({});
const unitList = ref([]);
/**页码变化 */
const handleCurrentChange = () => {
  UserService.queryList({
    current: currentPage.value,
    size: currentSize.value,
    username: searchInput.value,
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
// 打开添加软件dialog
const addForm = () => {
  createVisible.value = true;
  nextTick(() => {
    formRef.value.resetFields();
  });
};
/**添加用户 */
const createRecord = () => {
  formRef.value.validate((valid) => {
    if (valid) {
      let username = form.username;
      let nickname = form.nickname;
      let part = form.part;
      let phone = form.phone;
      let type = form.type;
      let status = form.status;
      let remark = form.remark;

      UserService.add({
        username: username,
        nickname: nickname,
        part: part,
        phone: phone,
        type: type,
        status: parseInt(status),
        remark: remark,
      }).then((res) => {
        if (res.code === 0) {
          ElMessage.success("添加成功");
          createVisible.value = false;
          handleCurrentChange();
        } else {
          ElMessage.warning(res.msg);
        }
      });
    } else {
      return;
    }
  });
};
/**修改 */
const showEditModal = (row) => {
  editForm.id = row.id;
  editForm.username = row.username;
  editForm.nickname = row.nickname;
  editForm.phone = row.phone;
  editForm.type = row.type;
  editForm.status = row.status + "";
  editForm.remark = row.remark;
  editForm.part = row.part;

  editVisible.value = true;
};
const updateRecord = () => {
  let timer=null
  clearTimeout(timer)
  UserService.update({
    id: parseInt(editForm.id),
    name: editForm.name,
    phone: editForm.phone,
    part: editForm.part,
    status: parseInt(editForm.status),
    type: editForm.type,
    nickname: editForm.nickname,
    remark: editForm.remark,
  }).then((res) => {
    if (res.code === 0) {
     
      editVisible.value = false;
      if(userStore.user.id==editForm.id&&editForm.type!=userStore.user.type){  //编辑的当前用户,并且改了 角色
          ElMessage.success('修改成功,由于您修改了当前登录用户类别，请重新登录')
          timer= setTimeout(async () => {
              router.replace({ path: '/login' });
            }, 2000);
          return 
      }
      ElMessage.success("修改成功");
      userStore.setUser({...editForm})
      handleCurrentChange();
    } else {
      ElMessage.warning(res.msg);
    }
  });
};
/**删除 */
const showDelModal = (row) => {
  delForm.id = row.id;
  delForm.username = row.username;
  delVisible.value = true;
};
const delRecord = () => {
  UserService.delete({
    id: parseInt(delForm.id),
  }).then((res) => {
    if (res.code === 0) {
      ElMessage.success("删除成功");
      delVisible.value = false;
      handleCurrentChange();
    } else {
      ElMessage.warning(res.msg);
    }
  });
};

/**重置 */
const showResetModal = (row) => {
  resetForm.id = row.id;
  resetForm.username = row.username;
  resetVisible.value = true;
};
const resetPwd = () => {
  UserService.resetPwd({
    id: parseInt(resetForm.id),
  }).then((res) => {
    if (res.code === 0) {
      if(resetForm.id==userStore.user.id){  //重置的用户为当前登录用户，需要重新登录 
        resetVisible.value = false;
        clearTimeout(timer)
        ElMessage.success('密码重置成功！系统将自动退出，请重新登录')
        loginStore.$patch((state) => {
          state.isLogin = false;
          state.uuidLogin = "";
          state.token = "";
        });
        timer= setTimeout(async () => {
          router.replace({ path: "/" });
        }, 2000);
        return 
      }
      ElMessage.success("密码重置成功");
      resetVisible.value = false;
    } else {
      ElMessage.warning(res.msg);
    }
  });
};

//项目授权
let currentUser=ref({})
let currentUserProject=ref([])
let projectTable=ref([])
const projectRef=ref()
const projectTableRef=ref()
const loading = ref(false)
const projectAuthorization=async (row)=>{
  currentUser.value=row
  //初始化 
  currentUserProject.value=[]
  projectTable.value=await apiRequest(EngineProjectService.getProjectAll,{})
  currentUserProject.value=await apiRequest(UserProjectService.getUserProject,{user_id:row.id})
  projectRef.value.open()
  nextTick(()=>{
    const $table = projectTableRef.value
    // console.log(projectTable.value,'$table', $table)
    if($table){
      loading.value=true
      $table.reloadData(projectTable.value).then(()=>{
        currentUserProject.value.forEach(item=>{
          const findItem=projectTable.value.find(val=>val.id===item.project_id)
          if(findItem){
            projectTableRef.value.setCheckboxRow(findItem,true)
          }
        })
        loading.value = false
      })
    }
  })
}
const projectBinding=async ()=>{
  const selectedTable=projectTableRef.value.getCheckboxRecords(false).map(item=>item.id).join(",")
  try {
    let res=await UserProjectService.savaUserProject({user_id:currentUser.value.id,project_ids:selectedTable})
    if(res&&res.code===0){
      ElMessage.success("项目授权成功");
      projectRef.value.close()
    }
  } catch (error) {
    console.log(error)
  }
}
//API
const apiRequest=async (api,params)=>{
  try {
    let res=await api(params)
    if(res&&res.code===0){
      return res.data
    }
    return []
  } catch (error) {
    console.log(error)
    return []
  }
}

handleCurrentChange();
onBeforeUnmount(()=>{
  clearTimeout(timer)
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
<style>
.projectTableHeader{
  background:#dbdada;
}

</style>
