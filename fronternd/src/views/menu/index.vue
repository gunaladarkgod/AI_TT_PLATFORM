<template>
    <div class="menuPage content-div">
        <div class="search-div flex-between">
        <div class="flex-start">
            <!-- <el-input v-model="searchInput" placeholder="输入用户名查询" size="small">
            </el-input>
            <el-button @click="handleCurrentChange" size="small"><el-icon><i
                class="iconfont icon-sousuo"></i></el-icon></el-button> -->
        </div>
        <div>
            <el-button type="primary" @click="addEditDelRole('新增')" size="small"><el-text size="small" class="text-white">添加菜单</el-text>
            </el-button>

            <template v-for="item in roleTypeData" :key="item[0]">
                <el-button type="primary" @click="addEditDelRole('权限',item)" size="small">{{ item[1] }}</el-button>
            </template>

        </div>
        </div>

        <div class="table-div">
            <el-table :data="menuData" size="small"  v-el-height-adaptive-table="{ bottomOffset: 70, isUse: true }">
                <el-table-column type="index" label="序号" align="center" />
                <el-table-column prop="name" label="菜单名称" align="center" />
                <el-table-column prop="url" label="菜单路径" align="center" />
                <el-table-column prop="is_hidden" label="显示/隐藏"  align="center">
                    <template #default="{row}">
                        <el-tag  :type="row.is_hidden?'danger':'primary'">{{ row.is_hidden?'隐藏':'显示' }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="parent_id" label="是否列入其他"  align="center">
                    <template #default="{row}">
                        <template v-if="row.url!='other'">
                            <el-tag  type="primary">{{ row.parent_id?'是':'否' }}</el-tag>
                        </template>
                        <span v-else></span>
                    </template>
                </el-table-column>
                <el-table-column prop="order_num" label="顺序" align="center" />
                
                <el-table-column label="操作" align="center" width="150" fixed="right">
                    <template #default="scope">
                        <el-space>
                            <el-button link size="small" @click="addEditDelRole('编辑',scope.row)">
                                <el-tag class="iconfont icon-xiugai fontSpan">编辑</el-tag>
                            </el-button>
                            <el-button link size="small" @click="addEditDelRole('删除',scope.row)">
                                <el-tag class="iconfont icon-shanchu fontSpan">删除</el-tag>
                            </el-button>
                        </el-space>
                    </template>
                </el-table-column>
            </el-table>
        </div>
        <div class="flex-end">
            <el-pagination background size="small" :current-page="paginationConfig.currentPage" :page-size="paginationConfig.pageSize" :total="paginationConfig.total"
                :page-sizes="[5, 10, 20, 30, 40, 50]" layout="total, sizes, prev, pager, next, jumper" 
                @size-change="handleSizeChange" @current-change="handleCurrentChange" />
        </div>
        


        <customElDialog  :title="menuDialogTitle"   :width="menuDialogTitle!='权限'?'45%':'25%'"    destroyOnClose      :ZIndex="1100" ref="menuDialogRef"  :draggable="true" :before-close="()=>{menuDialogRef.close()}">
            <template #content>
                <el-form label-position="left"  :label-width="menuDialogTitle!='权限'?'100':'50'"
                    ref="menuFormRef"
                    :model="menuForm"
                    :rules="menuRules"
                >
                    <template v-if="menuDialogTitle!='权限'">
                        <el-form-item label="菜单名称" prop="name">
                            <el-input v-model="menuForm.name" />
                        </el-form-item>
                        <el-form-item label="菜单路径" prop="url">
                            <el-input v-model="menuForm.url" />
                        </el-form-item>
                        <el-form-item label="父级菜单"  prop="parent_id">
                            <el-select  v-model="menuForm.parent_id"      placeholder="父级菜单" style="width: 100%"  :disabled="menuForm.id==13">
                                <el-option  v-for="item in menuOptions" :key="item.id"  :label="item.name" :value="item.id"/>
                            </el-select>
                        </el-form-item>
                        <el-form-item label="顺序" prop="order_num">
                            <el-input-number class="inputNumber" v-model="menuForm.order_num" :min="0"  :controls="false" />
                        </el-form-item>
                        <el-form-item label="显示/隐藏" prop="is_hidden">
                            <el-radio-group v-model="menuForm.is_hidden">
                                <el-radio :value="0">显示</el-radio>
                                <el-radio :value="1">隐藏</el-radio>
                            </el-radio-group>
                        </el-form-item>
                    </template>
                    <template v-else>
                        <el-form-item  prop="menuIds">
                            <el-tree
                                :data="menuListData"  default-expand-all
                                show-checkbox  
                                ref="menuRef"
                                node-key="id"
                                :default-checked-keys="menuForm.menuIds"
                                :props="defaultProps"
                            />
                        </el-form-item>


                    </template>
                </el-form>
            </template>
        
            <template #footer>
                <span class="dialog-footer">
                    <el-button @click="()=>{menuDialogRef.close()}">取消</el-button>
                    <el-button type="primary" @click="menuBinding(menuFormRef)" >确定</el-button>
                </span>
            </template>
        </customElDialog>


    </div>
</template>

<script setup>

import { ref ,nextTick,reactive, onBeforeMount} from 'vue';
import {userTypeList} from '@/utils/selfmaps'
import {buildMenuTree} from '@/utils/routeUtils.js'
import customElDialog from '@/components/customElDialog/index.vue'
import {MenuService} from '@/api/api' 
import {apiRequest } from '@/api/axios'
import { ElMessage, ElMessageBox } from "element-plus";
const paginationConfig=reactive({
    currentPage:1,
    pageSize:10,
    total:0
})
const defaultProps = {
  children: 'children',
  label: 'name',
}
const menuData=ref([])
const menuListData=ref([])
const menuOptions=ref([])
const roleTypeData=ref([])
roleTypeData.value=userTypeList.filter(item=>item[0]!=1)
const menuRef=ref()

const currentRoleMenu=ref([])
const menuDialogRef=ref()
const menuDialogTitle=ref()
const menuFormRef=ref()
const menuForm=ref({
    name:null,
    url:null,
    order_num:1,
    is_hidden:0,
    parent_id:0
})
let apiFun

const menuRules=reactive({
    name:[ { required: true, message: "请输入菜单名称", trigger: ["blur"] },],
    url:[ { required: true, message: "请输入菜单地址", trigger: ["blur"] },],
    order_num:[ { required: true, message: "请输入菜单顺序", trigger: ["blur"] },],
    is_hidden:[ { required: true, message: "请选择菜单显示/隐藏", trigger: ["blur"] },],
    menuIds:[ { required: true, message: "菜单不能为空", trigger: ["blur"] },]
})



const addEditDelRole=async(type,row)=>{
    menuDialogTitle.value=type
    if(type=="删除"){
        ElMessageBox.confirm('此操作将永久删除该菜单, 是否继续?','提示',
            {
                confirmButtonText: '确定',
                cancelButtonText: '取消',
                type: 'warning'
            }
        ).then(async ()=>{
            const id=row.id
            try {
                let res=await MenuService.delete({id})
                if(res&&res.code===0){
                    ElMessage({type: 'success',message: '删除成功'})
                    getTableList()
                }else{
                    ElMessage({type: 'error',message:res.msg})
                }
            } catch (error) {
                console.log(error)
            }
        }).catch(()=>{
            ElMessage({type: 'info',message: '取消删除'})
        })
        return 
    }else if(type=='权限'){
        menuListData.value=buildMenuTree(await apiRequest(MenuService.queryList,{})).filter(item=>!(item.url=='other'&&item.children&&item.children.length==0)) //过滤掉其他一级菜单为空
        // menuListData.value=(await apiRequest(MenuService.queryList,{})).filter(item=>item.url!="menu")
        //回显时过滤掉‘其他’，防止其选中后其子级全部选中
        currentRoleMenu.value=(await apiRequest(MenuService.getRoleMenu,{role_id:row[0]})).filter(item=>item.url!='other').map(item=>item.id)
        console.log( currentRoleMenu.value,' currentRoleMenu.value')
        menuForm.value={menuIds:[...currentRoleMenu.value],  role_id:row[0] }
        apiFun=MenuService.roleMenu
    }else if(type=="编辑"){
        menuForm.value={...row}
        apiFun=MenuService.update
    }else{
        menuForm.value={
            name:null,
            url:null,
            order_num:1,
            is_hidden:0,
            parent_id:0
        }
        apiFun=MenuService.add
    }
    menuOptions.value=[{id:0,name:'根目录'},...(await apiRequest(MenuService.queryList,{})).filter(item=>item.url=='other')]
    menuDialogRef.value.open()
    nextTick(()=>{
        menuFormRef.value&&menuFormRef.value.clearValidate();
    })
}


const menuBinding=async(formEl)=>{
    if(!formEl) return 
      //   如果是权限设置，则先更新 menuForm.menuIds
    if (menuDialogTitle.value === '权限') {
        const checkedKeys = menuRef.value.getCheckedNodes(false,true).map(item=>item.id)
        menuForm.value.menuIds = [...checkedKeys];
    }
    await formEl.validate(async (valid)=>{
        if(valid){
            let params=JSON.parse(JSON.stringify(menuForm.value))
            if(menuForm.value.hasOwnProperty('menuIds')){
                let menu_ids=menuForm.value.menuIds.join()||""
                delete params.menuIds
                params={...params,menu_ids}
            }
            try {
                let res=await apiFun({...params})
                if(res&&res.code==0){
                    ElMessage.success(res.msg);
                    menuDialogRef.value.close()
                    getTableList()
                }
            } catch (error) {
                console.log(error)
            }
        }
    })
}

const handleSizeChange=(val)=>{
    paginationConfig.currentPage=1
    paginationConfig.pageSize=val
    getTableList()
}
const handleCurrentChange=(val)=>{
    paginationConfig.currentPage=val
    getTableList()
}


const getTableList=async ()=>{
    try {
        let {pageSize:size,currentPage:current}=paginationConfig
        let res=await MenuService.getMenuPage({size,current})
        if(res&&res.code==0){
            menuData.value=res.data.records
            paginationConfig.currentPage=res.data.current
            paginationConfig.total=res.data.total
            paginationConfig.pageSize=res.data.size
        }
    } catch (error) {
        console.log(error)
    }
}







onBeforeMount(async()=>{
   await getTableList()
})

</script>

<style scoped>

.table-div{
    padding: 8px 0;
    box-sizing: border-box;
}

.inputNumber{
    width: 100%;
}
::v-deep(.inputNumber .el-input__inner){
    text-align: left;
}
</style>