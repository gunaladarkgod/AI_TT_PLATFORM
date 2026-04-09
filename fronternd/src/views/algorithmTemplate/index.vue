<template>
    <div  class="algorithmTemplate content-div">
        <el-tabs v-model="activeName">
            <template v-for="(value,key) in tabsData" :key="key">
                <el-tab-pane :label="value" :name="key"></el-tab-pane>
            </template>
        </el-tabs>
        <div class="content-box">
            <div class="topBox">
                <div class="searchBox">
                    <el-input v-model="name" placeholder="模板名称" size="small" />
                    <el-button @click="searchTable" size="small"><el-icon><i class="iconfont icon-sousuo"></i></el-icon></el-button>
                </div>
                <el-button type="primary" @click="addEditDelTrainTrans('新增')" size="small"><el-text size="small" class="text-white">添加模板</el-text>
                </el-button>
            </div>
            <el-table :data="trainTransTableData" size="small"  v-el-height-adaptive-table="{ bottomOffset: 70, isUse: true }">
                <template v-if="activeName=='train'">
                    <el-table-column type="index" label="序号" align="center" />
                    <el-table-column prop="name" label="名称" align="center" />
                    <el-table-column prop="alg_id" label="算法类别" align="center">
                        <template #default="{row}">
                            {{ algList.find(val=>val.id==row.alg_id)?.name||row.alg_id }}
                        </template>
                    </el-table-column>
                    <el-table-column prop="weights" label="初始权重" align="center" />
                    <el-table-column prop="hyp" label="超参配置" align="center" />
                    <el-table-column prop="cfg" label="模型配置" align="center" />
                    <el-table-column prop="batch_size" label="批大小" align="center" />
                    <el-table-column prop="device" label="硬件" align="center" />
                    <el-table-column prop="img_size" label="图尺寸" align="center" >
                        <template #default="{row}">
                            {{ algList.find(val=>val.id==row.alg_id)?.cmd=='mmdet'? `${row.img_w},${row.img_h}`:row.img_size }}
                        </template>
                    </el-table-column>
                    <el-table-column prop="epoch" label="训练轮次" align="center" />
                    <el-table-column prop="period" label="保存策略" align="center" />
                    <!-- <el-table-column prop="val_ratio" label="自训自测比例" align="center" /> -->
                    <el-table-column prop="f_max" label="过滤,长边" align="center" />
                    <el-table-column prop="f_min" label="过滤,短边" align="center" />
                    <el-table-column prop="f_area" label="过滤,面积" align="center" />
                    <el-table-column prop="remark" label="备注" align="center" />
                </template>
                <template v-if="activeName=='trans'">
                    <el-table-column type="index" label="序号" align="center" />
                    <el-table-column prop="name" label="名称" align="center" />
                    <el-table-column prop="type" label="转换算法类型" align="center" />
                    <el-table-column prop="params" label="参数" align="center" />
                    <el-table-column prop="remark" label="备注" align="center" />
                </template>
                
                <el-table-column label="操作" align="center" width="150" fixed="right">
                    <template #default="scope">
                        <el-space>
                            <el-button link size="small" @click="addEditDelTrainTrans('编辑',scope.row)">
                                <el-tag class="iconfont icon-xiugai fontSpan">编辑</el-tag>
                            </el-button>
                            <el-button link size="small" @click="addEditDelTrainTrans('删除',scope.row)">
                                <el-tag class="iconfont icon-shanchu fontSpan">删除</el-tag>
                            </el-button>
                        </el-space>
                    </template>
                </el-table-column>
            </el-table>

            <div class="flex-end  paginationBox">
                <el-pagination background size="small" :current-page="paginationConfig.currentPage" :page-size="paginationConfig.pageSize" :total="paginationConfig.total"
                    :page-sizes="[5, 10, 20, 30, 40, 50]" layout="total, sizes, prev, pager, next, jumper" 
                    @size-change="handleSizeChange" @current-change="handleCurrentChange" />
            </div>
        </div>

        <customElDialog    :destroyOnClose="true" :title="trainTransDialogTitle"   width="60%"  :ZIndex="1000" ref="trainTransDialogRef"  :draggable="true" :before-close="()=>{trainTransDialogRef.close()}">
            <template #content>
                <el-form  ref="trainTransFormRef"  :model="trainTransForm"    :rules="trainTransRules"  label-width="auto"
                    status-icon label-position="right"
                >
                    <template v-if="activeName=='train'">
                        <el-row :gutter="22">
                            <el-col :span="12">
                                <el-form-item label="名称" prop="name">
                                    <el-input v-model="trainTransForm.name" />
                                </el-form-item>
                            </el-col>
                            <el-col :span="12">
                                <el-form-item label="所属算法" prop="alg_id">
                                    <el-select v-model="trainTransForm.alg_id" clearable  filterable  placeholder="选择算法"  style="width: 100%"  >
                                        <el-option  v-for="item in algList"
                                            :key="item.id"
                                            :label="item.name"
                                            :value="item.id+''"
                                        />
                                        </el-select>
                                </el-form-item>
                            </el-col>   
                        </el-row>
                       
                       
                        <el-divider content-position="center">网络参数配置</el-divider>
                        <el-row  :gutter="22">
                            <el-col :span="12">
                                <el-form-item label="初始权重(weights)"  prop="weights">
                                    <vxe-table-select style="width: 100%;"
                                        v-model="trainTransForm.weights"
                                        :columns="columnList"  :popup-config="popupConfig"
                                        :options="weightsTableData"
                                        :option-props="{value: 'id', label: 'name'}"
                                        :grid-config="weightsGridConfig"
                                        @form-submit="(params)=>{formSubmitEvent('weights',params)}">
                                    </vxe-table-select>
                                </el-form-item>
                            </el-col>
                        </el-row>
                        <el-row :gutter="22">
                            <el-col :span="12">
                                <el-form-item label="模型配置(cfg)" prop="cfg"   v-if="(algList.find(item=>item.id==trainTransForm.alg_id)?.cmd) == 'python'">
                                    <vxe-table-select style="width: 100%;"
                                        v-model="trainTransForm.cfg"
                                        :columns="columnList"   :popup-config="popupConfig"
                                        :options="cfgTableData"
                                        :option-props="{value: 'id', label: 'name'}"
                                        :grid-config="cfgGridConfig"
                                        @form-submit="(params)=>{formSubmitEvent('cfg',params)}">
                                    </vxe-table-select> 
                                </el-form-item>
                            </el-col>
                            <el-col :span="12">
                                <el-form-item label="超参配置(hyp)" prop="hyp"  v-if="(algList.find(item=>item.id==trainTransForm.alg_id)?.cmd) == 'python'">
                                    <vxe-table-select
                                        v-model="trainTransForm.hyp"  style="width: 100%;"
                                        :columns="columnList"  :popup-config="popupConfig"
                                        :options="hypTableData"
                                        :option-props="{value: 'id', label: 'name'}"
                                        :grid-config="hypGridConfig"
                                        @form-submit="(params)=>{formSubmitEvent('hpy',params)}">
                                    </vxe-table-select> 
                                </el-form-item>
                            </el-col>
                        </el-row>
                        
                        <el-divider content-position="center">训练参数配置</el-divider>
                        <el-form-item label="小标签过滤参数(px)">
                            <el-row :gutter="22">
                                <el-col :span="8">
                                    <el-form-item label="" prop="f_max">
                                        <el-input v-model="trainTransForm.f_max"  type="number" >
                                            <template #prepend>长边></template>
                                        </el-input>
                                    </el-form-item>
                                </el-col>
                                <el-col :span="8">
                                    <el-form-item label="" prop="f_min">
                                        <el-input v-model="trainTransForm.f_min" type="number" >
                                            <template #prepend>短边></template>
                                        </el-input>
                                    </el-form-item>
                            
                                </el-col>
                                <el-col :span="8">
                                    <el-form-item label="" prop="f_area">
                                        <el-input v-model="trainTransForm.f_area"  type="number">
                                            <template #prepend>面积></template>
                                        </el-input>
                                    </el-form-item>
                                </el-col>
                            </el-row>
                            
                          
                       
                        </el-form-item>
                        <el-row :gutter="22">
                            <el-col :span="12">
                                <el-form-item label="批大小(batch_size)" prop="batch_size">
                                    <el-input-number  class="inputNumber" style="width: 100%;"  v-model="trainTransForm.batch_size"  :controls="false"/>
                                </el-form-item>
                            </el-col>
                            <el-col :span="12" v-if="algList.find(item=>item.id==trainTransForm.alg_id)?.cmd!='mmdet'">
                                <el-form-item label="图尺寸(img_size)" prop="img_size">
                                    <el-input-number  class="inputNumber" style="width: 100%;"  v-model="trainTransForm.img_size"  :controls="false" />
                                </el-form-item>
                            </el-col>
                        </el-row>
                        <el-row :gutter="22" v-if="algList.find(item=>item.id==trainTransForm.alg_id)?.cmd=='mmdet'">
                            <el-col :span="12">
                                <el-form-item label="图尺寸宽(img_w)" prop="img_w">
                                    <el-input-number  class="inputNumber" style="width: 100%;"  v-model="trainTransForm.img_w"  :controls="false" />
                                </el-form-item>
                            </el-col>
                            <el-col :span="12">
                                <el-form-item label="图尺寸高(img_h)" prop="img_h">
                                    <el-input-number  class="inputNumber" style="width: 100%;"  v-model="trainTransForm.img_h"  :controls="false" />
                                </el-form-item>
                            </el-col>
                        </el-row>
                        <el-row :gutter="22">
                            <el-col :span="12">
                                <el-form-item label="训练轮次(epoch)" prop="epoch">
                                    <el-input-number   class="inputNumber" style="width: 100%;"   v-model="trainTransForm.epoch"  :controls="false" />
                                </el-form-item>
                            </el-col>
                            <el-col :span="12">
                                <el-form-item label="保存策略(period)" prop="period">
                                    <el-input-number   class="inputNumber" style="width: 100%;"  v-model="trainTransForm.period"  :controls="false" />
                                </el-form-item>
                            </el-col>
                        </el-row>
                       
                       
                      
                       

                     
                        <el-form-item label="硬件(device)" prop="device">
                            <el-space spacer="|">
                                <el-switch inactive-text="使用CPU" active-text="使用GPU" v-model="trainTransForm.device" inline-prompt   
                                    active-value="gpu"
                                    inactive-value="cpu"   style="--el-switch-on-color: #409eff; --el-switch-off-color: #e6a23c"></el-switch>
                                <template v-if="trainTransForm.device!='cpu'">
                                    <el-checkbox-group v-model="trainTransForm.deviceList"   @change="handleCheckedCitiesChange">
                                        <el-checkbox v-for="gpu in gpu_list" :key="gpu" :label="gpu" :value="gpu" />
                                    </el-checkbox-group>
                                    <el-checkbox v-model="checkAll_yolo"  :indeterminate="isIndeterminate_yolo"
                                        @change="handleCheckAllChange_yolo">
                                        全选
                                    </el-checkbox>
                                </template>
                                <template v-else>{{trainTransForm.device}}</template>
                            </el-space>
                        </el-form-item>
                        
                      

                        <el-form-item label="备注" prop="remark">
                            <el-input v-model="trainTransForm.remark" :rows="2"   type="textarea" placeholder="备注说明" />
                        </el-form-item>
                    </template>
                    <template v-if="activeName=='trans'">
                        <el-row :gutter="22">
                            <el-col :span="12">
                                <el-form-item label="名称" prop="name">
                                    <el-input v-model="trainTransForm.name" />
                                </el-form-item>
                            </el-col>
                            <el-col :span="12">
                                <el-form-item label="所属算法" prop="type">
                                    <el-select v-model="trainTransForm.type" clearable  filterable  placeholder="选择算法"  style="width: 100%"  >
                                        <el-option v-for="(item, id) in algList" :key="id" :value="item.name"></el-option>
                                        </el-select>
                                </el-form-item>
                            </el-col>     
                        </el-row>
                        <el-divider content-position="center">其他参数</el-divider>
                        <template  v-if="trainTransForm.type?.includes('pt2onnx')">
                            <el-form-item label="图像尺寸(imgsz)" prop="params.imgsz">
                                <el-input-number v-model="trainTransForm.params.imgsz" style="width: 120px;"  :controls="false"  class="inputNumber"
                                    :min="1"></el-input-number>
                            </el-form-item>
                        </template>
                        <template v-else>
                            <el-row :gutter="22">
                                <el-col :span="12">
                                    <el-form-item label="模型类型(type)" prop="params.type">
                                        <el-select v-model="trainTransForm.params.type" style="width: 100%;">
                                        <el-option value="5s"></el-option>
                                        <el-option value="5m"></el-option>
                                        <el-option value="5l"></el-option>
                                        </el-select>
                                    </el-form-item>
                                </el-col>
                                <el-col :span="12">
                                    <el-form-item label="图片类型(chn)" prop="params.chn">
                                        <el-select v-model="trainTransForm.params.chn" style="width: 100%;">
                                            <el-option value="vis"></el-option>
                                            <el-option value="inf"></el-option>
                                        </el-select>
                                    </el-form-item>
                                </el-col>
                               
                            </el-row>
                            <el-row :gutter="24">
                                <el-col :span="12">
                                    <el-form-item label="模型宽(model_w)" prop="params.model_w">
                                        <el-input-number v-model="trainTransForm.params.model_w"  class="inputNumber" style="width: 100%" :controls="false"
                                        :min="1"></el-input-number>
                                    </el-form-item>
                                </el-col>
                                <el-col :span="12">
                                    <el-form-item label="模型高(model_h)" prop="params.model_h">
                                        <el-input-number v-model="trainTransForm.params.model_h"  class="inputNumber"  style="width: 100%;" :controls="false"
                                        :min="1"></el-input-number>
                                    </el-form-item>
                                </el-col>
                             
                            </el-row>
                            <el-row :gutter="22">
                                <el-col :span="12">
                                    <el-form-item label="转换日期(date)" prop="params.date">
                                        <el-date-picker v-model="trainTransForm.params.date" value-format="YYYYMMDD"
                                        style="width: 100%;"></el-date-picker>
                                    </el-form-item>
                                </el-col>
                                <el-col :span="12">
                                    <el-form-item label="名称量化(quantise)" prop="params.quantise">
                                        <el-switch v-model="trainTransForm.params.quantise" :active-value="1" :inactive-value="0" style="width: 120px;"
                                        active-text="开" inactive-text="关" inline-prompt></el-switch>
                                    </el-form-item>
                                </el-col>
                            </el-row>
                            <el-row :gutter="22">
                                <el-col :span="12">
                                    <el-form-item label="基础校验集(check)" prop="params.base_check">
                                        <div class="spaceBox">
                                            <el-select v-model="trainTransForm.params.base_check" style="width: 100%;">
                                                <el-option v-for="(item, id) in checkList" :key="id" :value="item"></el-option>
                                            </el-select>
                                            <el-button size="small" @click="refreshAction('check')" class="iconfont icon-gengxinchajian" title="刷新"
                                            circle></el-button>
                                        </div>
                                    </el-form-item>
                                </el-col>
                              
                            </el-row>
                            
                            <el-row :gutter="22">
                                <el-col :span="12">
                                    <el-form-item label="基础验证集(val)" prop="params.base_val">
                                        <div class="spaceBox">
                                            <el-select v-model="trainTransForm.params.base_val" style="width: 100%;">
                                                <el-option v-for="(item, id) in valList" :key="id" :value="item"></el-option>
                                                </el-select>
                                            <el-button size="small" @click="refreshAction('val')" class="iconfont icon-gengxinchajian" title="刷新"
                                            circle></el-button>
                                        </div>  
                                    </el-form-item>
                                </el-col>
                            </el-row>
                        </template>

                        <el-form-item label="备注说明" prop="remark">
                            <el-input v-model="trainTransForm.remark" type="textarea"></el-input>
                        </el-form-item>
                    </template>
                 
                </el-form>
            </template>
        
            <template #footer>
                <span class="dialog-footer">
                    <el-button @click="()=>{trainTransDialogRef.close()}">取消</el-button>
                    <el-button type="primary" @click="trainTransBinding(trainTransFormRef)" >确定</el-button>
                </span>
            </template>
        </customElDialog>

        <customElDialog  title="查看" width="60%"  :ZIndex="1100" ref="txtDialogRef"  :draggable="true" :before-close="()=>{txtDialogRef.close()}">
            <template #content>
                <pre class="preBox">{{ cur_text }}</pre>
            </template>
            <template #footer>
            <span class="dialog-footer">
                <el-button @click="()=>{txtDialogRef.close()}">关闭</el-button>
            </span>
            </template>
        </customElDialog>
    </div>
</template>

<script setup>
import {ref, watch ,reactive,nextTick, onBeforeMount } from 'vue'
import {trainService,transService,TrainScriptService,TrainYoloService,ModelTransService,FileService} from '@/api/api' 
import customElDialog from '@/components/customElDialog/index.vue'
import {apiRequest,basePath_YOLO} from '@/api/axios'
import { ElMessage, dayjs, ElMessageBox } from "element-plus";


let tabsData={
    "train":"模型训练",
    "trans":"模型转换"
}

let apiObj={
    "train":trainService,
    "trans":transService
}

let validateDevice=(rule, value, callback)=>{
    if (value === '') {
    callback(new Error('硬件不能为空'))
  } else {
    if(value=='gpu'){
        if(!trainTransForm.value?.deviceList || trainTransForm.value?.deviceList?.length<=0){
            callback(new Error('硬件不能为空'))
            return 
        }
        callback()
    }
    callback()
  }
}

let isShowFormItem=ref(true)

let txtDialogRef=ref()
let cur_text=ref("")

let apiFun
//弹框
const trainTransDialogTitle=ref()
const trainTransDialogRef=ref()
const trainTransFormRef=ref()
const trainTransForm=ref()
let trainTransRules=reactive({
    name: [  { required: true, message: '模板名称不能为空', trigger: 'blur', } ],
    //模型训练模板
    alg_id: [  { required: true, message: '所属算法不能为空', trigger: 'change', } ],
    weights: [  { required: true, message: '初始权重不能为空', trigger: 'change', } ],
    cfg: [  { required: true, message: '模型配置不能为空', trigger: 'change', } ],
    hyp: [  { required: true, message: '超参配置不能为空', trigger: 'change', } ],
    f_max: [  { required: true, message: '长边>不能为空', trigger: 'change', } ],
    f_min: [  { required: true, message: '短边>不能为空', trigger: 'change', } ],
    f_area: [  { required: true, message: '面积>不能为空', trigger: 'change', } ],
    device:[{ validator: validateDevice, trigger: ['blur','change'] }],
    batch_size: [  { required: true, message: '批大小不能为空', trigger: 'change', } ],
    img_size: [  { required: true, message: '图尺寸不能为空', trigger: 'change', } ],
    img_w:[ { required: true, message: '图尺寸宽不能为空', trigger: 'change', }],
    img_h:[ { required: true, message: '图尺寸高不能为空', trigger: 'change', }],
    epoch: [  { required: true, message: '训练轮次不能为空', trigger: 'change', } ],
    period: [  { required: true, message: '保存策略不能为空', trigger: 'change', } ],
    
    //模型转换模板
    type:[ { required: true, message: '所属算法不能为空', trigger: 'change', }],
    'params.imgsz':[{ required: true, message: '图像尺寸不能为空', trigger: 'blur', } ],
    "params.type":[{ required: true, message: '模型类型不能为空', trigger: 'change', }],
    "params.chn":[{ required: true, message: '图片类型不能为空', trigger: 'change', }],
    "params.model_w":[{ required: true, message: '模型宽不能为空', trigger: 'change', }],
    "params.model_h":[{ required: true, message: '模型高不能为空', trigger: 'change', }],
    "params.date":[{ required: true, message: '转换日期不能为空', trigger: 'change', }],
    "params.quantise":[{ required: true, message: '量化名称不能为空', trigger: 'change', }],
    "params.base_check":[{ required: true, message: '基础校验集不能为空', trigger: 'change', }],
    "params.base_val":[{ required: true, message: '基础验证集不能为空', trigger: 'change', }],

})

const checkList = ref([]);
const valList = ref([])

const gpu_list = [0, 1, 2, 3, 4, 5, 6, 7]
const checkAll_yolo = ref(false)
const isIndeterminate_yolo = ref(false)
const handleCheckAllChange_yolo = (val) => {
  if (val) {
    trainTransForm.value.deviceList = [...gpu_list]
 
  } else {
    trainTransForm.value.deviceList = []
  }
  isIndeterminate_yolo.value = false
}
const handleCheckedCitiesChange=(val)=>{
    const checkedCount = val.length
    checkAll_yolo.value = checkedCount === gpu_list.length
    isIndeterminate_yolo.value = checkedCount > 0 && checkedCount < gpu_list.length
}

//下拉表格虚拟滚动
const popupConfig = reactive({
  width: 500,
  height: 350
})
//下拉表格配置
const columnList = ref([
    { field: 'id', title: 'id' ,width:'50px'},
    { field: 'name', title: '名称' },
    { field: 'created_date', title: '创建时间',formatter:({row})=>{
        return  showDate(row.created_date) 
    } },
    { field: 'remark', title: '备注' },
    {
      title: '操作',width:'80px',
      cellRender: {
        name: 'VxeButtonGroup',
        props: {  mode: 'text' },
        options: [ { content: '查看', name: 'view' }, ],
        events: {
          click ({row}) {
            let url = basePath_YOLO + row.type + "_" + row.id + row.path
            if (row.type === 'weights') {
                downFile(url)
            } else {
                readTxt(url)
            }
          }
        }
      }
    }
])
//下拉表格搜索栏
const weightsGridConfig = reactive({
  formConfig: {
    size:'mini',
    data: {id:"",name:""},
    items: [
      {span: 8, field: 'id', title: 'id', itemRender: { name: 'VxeInput' } },
      { span: 8,field: 'name', title: '名称', itemRender: { name: 'VxeInput' } },
      { span: 8,itemRender: {  name: 'VxeButtonGroup', options: [   { type: 'submit', content: '搜索', status: 'primary' }, ] } }
    ]
  }
})
const hypGridConfig = reactive({
  formConfig: {
    size:'mini',
    data: {id:"",name:""},
    items: [
      {span: 8,field: 'id', title: 'id',  itemRender: { name: 'VxeInput' } },
      {span: 8, field: 'name', title: '名称', itemRender: { name: 'VxeInput' } },
      {span: 8, itemRender: {  name: 'VxeButtonGroup', options: [   { type: 'submit', content: '搜索', status: 'primary' }, ] } }
    ]
  }
})
const cfgGridConfig = reactive({
  formConfig: {
    size:'mini',
    data: {id:"",name:""},
    items: [
      {span: 8, field: 'id', title: 'id', itemRender: { name: 'VxeInput' } },
      { span: 8,field: 'name', title: '名称', itemRender: { name: 'VxeInput' } },
      {span: 8, itemRender: {  name: 'VxeButtonGroup', options: [   { type: 'submit', content: '搜索', status: 'primary' }, ] } }
    ]
  }
})
const weightsTableData=ref([])   //初始权重表格数据
const cfgTableData=ref([])       //模型配置
const hypTableData=ref([])       //超参配置



let algList=ref([])
let activeName=ref("train")
let apiService=apiObj[activeName.value]
let trainTransTableData=ref([])
let name=ref("")
const paginationConfig=reactive({
    currentPage:1,
    pageSize:10,
    total:0
})
const searchTable=()=>{
    paginationConfig.currentPage=1
    getTableData()
}

const getTableData=async ()=>{
    try {
        let {pageSize:size,currentPage:current}=paginationConfig
        let res=await apiService.getTablePage({size,current,name:name.value})
        if(res&&res.code==0){
            trainTransTableData.value=res.data.records
            paginationConfig.currentPage=res.data.current
            paginationConfig.total=res.data.total
            paginationConfig.pageSize=res.data.size
            
        }
    } catch (error) {
        console.log(error)
    }
}
watch(()=>activeName.value,(newV)=>{
    paginationConfig.currentPage=1
    paginationConfig.pageSize=10
    paginationConfig.total=0
    apiService=apiObj[newV]
    getTableData()
},{immediate:true})





const addEditDelTrainTrans=async (type,row)=>{
    trainTransDialogTitle.value=`${type}【${tabsData[activeName.value]}】模板`
    if(type=="删除"){
        apiFun="delete"
        ElMessageBox.confirm(`此操作将永久删除【${row.name}】模板, 是否继续?`,'提示',
            {
                confirmButtonText: '确定',
                cancelButtonText: '取消',
                type: 'warning'
            }
        ).then(async ()=>{
            const id=row.id
            try {
                let res=await apiService[apiFun]({id})
                if(res&&res.code===0){
                    ElMessage({type: 'success',message: '删除成功'})
                    getTableData()
                }else{
                    ElMessage({type: 'error',message:res.message})
                }
            } catch (error) {
                console.log(error)
            }
        }).catch(()=>{
            ElMessage({type: 'info',message: '取消删除'})
        })
        return 
    }else if(type=="编辑"){
      
        if(activeName.value=='train'){
            trainTransForm.value={...row,alg_id:row.alg_id+""}
            if(row.device!='cpu'){
                trainTransForm.value={...trainTransForm.value,device:'gpu',deviceList:row.device.split(",").map(item=>item*1)}
            }
        }
        if(activeName.value=="trans"){
            trainTransForm.value={...row}
            if(parseJSON(row.params).success){
                trainTransForm.value={...row,params:parseJSON(row.params).data}
            }else{
                trainTransForm.value={...row,params:{ 
                    imgsz:null,
                    type:null,
                    chn:null,
                    model_w:null,
                    model_h:null,
                    date:null,
                    quantise:null,
                    base_check:null,
                    base_val:null,}}
            }
        }
        apiFun='update'
    }else {
        apiFun="add"
        if(activeName.value=="train"){ //模型训练
            trainTransForm.value={
                name:"",
                alg_id:null,
                weights:null,
                hyp:null,
                cfg:null,
                batch_size:null,
                device:"gpu",
                img_size:null,
                epoch:null,
                period:null,
                val_ratio:null,
                f_max:null,
                f_min:null,
                f_area:null,
                remark:null,
                img_h:null,
                img_w:null,
                deviceList:[]
            }
        }
        if(activeName.value=="trans"){  //模型转换
            trainTransForm.value={
                type:null,
                name:null,
                params:{
                    imgsz:null,
                    type:null,
                    chn:null,
                    model_w:null,
                    model_h:null,
                    date:null,
                    quantise:null,
                    base_check:null,
                    base_val:null,


                }
            }
        }
       

    }
    algList.value=(await apiRequest(TrainScriptService.queryAll,{type:activeName.value}))||[]
    if(activeName.value=="train"){
        const params={ current: 1,  size: 200 }
        weightsTableData.value=(await apiRequest(TrainYoloService.queryList,{...params,type:'weights',id:weightsGridConfig.formConfig.data.id,name:weightsGridConfig.formConfig.data.name}))?.records || []
        cfgTableData.value=(await apiRequest(TrainYoloService.queryList,{...params,type:'cfg',id:cfgGridConfig.formConfig.data.id,name:cfgGridConfig.formConfig.name}))?.records || []
        hypTableData.value=(await apiRequest(TrainYoloService.queryList,{...params,type:'hyp',id:hypGridConfig.formConfig.data.id,name:hypGridConfig.formConfig.name}))?.records || []
    }
    if(activeName.value=='trans'){
        checkList.value=(await apiRequest(ModelTransService.queryCalibrate,{type:'check'}))||[]
        valList.value=(await apiRequest(ModelTransService.queryCalibrate,{type:'val'}))||[]
    }
    trainTransDialogRef.value.open()
    nextTick(()=>{
        trainTransFormRef.value&&trainTransFormRef.value.clearValidate();
    })


}

const formSubmitEvent = async (type,params) => {
    const {id,name}=params.data
    const paramsSearch={current: 1,  size: 200,id,name}
    if(type=="weights"){
        weightsTableData.value=(await apiRequest(TrainYoloService.queryList,{...paramsSearch,type:'weights'})).records
    }
    if(type=="cfg"){
        cfgTableData.value=(await apiRequest(TrainYoloService.queryList,{...paramsSearch,type:'cfg'})).records
    }
    if(type=="hyp"){
        hypTableData.value=(await apiRequest(TrainYoloService.queryList,{...paramsSearch,type:'hyp'})).records
    }
}

const handleSizeChange=(val)=>{
    paginationConfig.currentPage=1
    paginationConfig.pageSize=val
    getTableData()
}
const handleCurrentChange=(val)=>{
    paginationConfig.currentPage=val
    getTableData()
}

const showDate = (time) => {
  return time ? dayjs(time).format('YYYY/MM/DD') : '';
}


//弹框确定
const trainTransBinding=async(formEl)=>{
    if(!formEl) return 
    await formEl.validate(async (valid)=>{
        if(valid){
            let newParams
            if(activeName.value=='train'){  //模型训练算法模板 数据处理
                newParams=JSON.parse(JSON.stringify(trainTransForm.value))
                let {device,deviceList}=newParams
                if(device!='cpu'){
                    newParams.device=deviceList.join()
                }
                delete newParams.deviceList
            }
            if(activeName.value=="trans"){
                newParams={...trainTransForm.value,params:JSON.stringify(trainTransForm.value.params)}
            }
            console.log(newParams,'newParams')
            try {
                let res=await apiService[apiFun](newParams)
                if(res&&res.code==0){
                    ElMessage.success(res.msg)
                    trainTransDialogRef.value.close()
                    getTableData()
                    return 
                }
                ElMessage.error(res.msg)
            } catch (error) {
                console.log(error)
            }
        }
    })
}
//刷新
const refreshAction=async(type)=>{
    if(type=='val'){
        valList.value=(await apiRequest(ModelTransService.queryCalibrate,{type:'val'}))||[]
    }
    if(type=='check'){
        checkList.value=(await apiRequest(ModelTransService.queryCalibrate,{type:'check'}))||[]
    }
}

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


const downFile = (url) => {
  if (!url) return;
  url = url.replace(/\\/g, '/');
  FileService.getFile(url, 'blob').then(blob => {
    const url2 = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url2;
    anchor.download = url.substring(url.lastIndexOf('/') + 1);
    anchor.click();
    // 释放内存
    URL.revokeObjectURL(url2);
  })
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
      txtDialogRef.value.open()
    })
    .catch((e) => {
      cur_text.value = '';
      console.log(e);
    });
};

onBeforeMount(async()=>{
    algList.value=(await apiRequest(TrainScriptService.queryAll,{type:activeName.value}))||[]
})

</script>

<style lang="scss" scoped>
.algorithmTemplate{
    .topBox{
        display: flex;
        justify-content: space-between;
        margin-bottom: 10px;
        .searchBox{
            display: flex;
        }
    }
    .spaceBox{
        display: flex;
        align-items: center;
        width: 100%;
        .el-select{
            margin-right: 10px;
        }
    }
    .inputNumber{
        ::v-deep(.el-input__inner){
            text-align: left !important;
        }
    }
    .paginationBox{
        padding: 8px 0 0;
        box-sizing: border-box;
    }
}
</style>