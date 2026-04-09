

<template>
  <div class="content">
    <div class="search-div flex-between">
      <div class="flex-start">
        
        <el-input v-model="searchName" placeholder="输入任务id或任务名称" size="small" class="width-300" style="max-width: 400px;">
        </el-input>
        
        <el-button  type="primary"  @click="" size="small" >
          <el-text size="small"
              class="text-white">搜索</el-text>
            </el-button>
        
      </div>
      
    </div>

    <div class="table-div">
      <el-table class="my-table" :data="tableData" stripe style="width: 100%" size="small"
        v-el-height-adaptive-table="{ bottomOffset: 70, isUse: true }">


        <el-table-column prop="id" label="id" align="center" width="80" fixed="left">
          <template #default="scope">
            <el-text size="small">#{{ scope.row.id }}</el-text>
          </template>
        </el-table-column>

        <el-table-column prop="taskId" label="任务id" align="center" fixed="left" />

        <el-table-column prop="taskName" label="任务名称" align="center" fixed="left" />


        <el-table-column prop="userName" label="创建人" align="center" />

        <el-table-column prop="modelType" label="模型类别" align="center" width="100" />

        <el-table-column prop="dataset" label="数据集" align="center" width="100" />

        <el-table-column prop="networkName" label="网络名称" align="center" width="100" />

        <el-table-column prop="time" label="完成时间" align="center" width="150">
          <template #default="{ row }">
            <el-text size="small">{{ showDateTime(row.time) }}</el-text>
          </template>
        </el-table-column>

        <el-table-column prop="map" label="mAP" align="center"  width="80"/>

        <el-table-column prop="ap50" label="AP50" align="center"  width="80"/>

        <el-table-column prop="ap75" label="AP75" align="center"  width="80"/>

        <el-table-column prop="aps" label="APs" align="center"  width="80"/>

        <el-table-column prop="apm" label="APm" align="center"  width="80"/>

        <el-table-column prop="apl" label="APl" align="center"  width="80"/>
        

        

        


  

      </el-table>
    </div>

    <div class="flex-end">
      <el-pagination background size="small" v-model:current-page="currentPage" v-model:page-size="currentSize"
        :page-sizes="[5, 10, 20, 30, 40, 50]" layout="total, sizes, prev, pager, next, jumper" :total="total"
        @current-change="handleCurrentChange" @size-change="handleCurrentChange" />
    </div>
  </div>
</template>

<script setup>
    import { computed, reactive, ref, toRaw, watch } from "@vue/reactivity";
    import dayjs   from 'dayjs'
    import { FileService, EngineProjectService, EngineTaskService, TrainLabelService, TrainTaskService, TrainScriptService, TrainYoloService, ApiService, ModelTransService, trainService, transService ,ResultQueryService } from "../../api/api";

    const now = new Date()
    const searchName = ref("");

    const currentPage = ref(1);
    const currentSize = ref(10);
    const total = ref(0);


    const showDate = (time) => {
      return time ? dayjs(time).format('YYYY/MM/DD') : '';
    }
    const showDateTime = (time) => {
      return time ? dayjs(time).format('YYYY/MM/DD HH:mm:ss') : '';
    }

    const tableData = ref([])
    const handleCurrentChange = () => {
      
      ResultQueryService.queryList({
        current: currentPage.value,
        size: currentSize.value,
      }).then(function (res) {
        if (res.code === 0) {
          
            tableData.value = res.data.records;
            total.value = res.data.total;
        } else {
          ElMessage.warning(res.msg);
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

    .my-table.el-table--small {
    border-radius: 4px;
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
    justify-content: center; /* 水平居中 */
    margin-top: 16px; 
    }

    .button-container .mt-4 {
    width: auto; /* 取消100%宽度 */
    min-width: 120px; /* 设置最小宽度 */
    }

</style>
