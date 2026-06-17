<template>
  <div class="content-div">
    <div class="search-div flex-between">
      <div class="flex-start">
        <el-select v-model="searchStatus" placeholder="任务状态" size="small" @change="handleCurrentChange"
          style="max-width: 100px;">
          <el-option v-for="(item, id) in taskStatusList" :key="id" :value="item[0]" :label="item[1]"></el-option>
        </el-select>
        <el-select v-model="searchUser" placeholder="选择用户" size="small" @change="handleCurrentChange"
          style="max-width: 100px;">
          <el-option value='' label="全部用户" key="-1"></el-option>
          <el-option v-for="item in userList" :key="item" :value="item" :label="item"></el-option>
        </el-select>
        <el-input v-model="searchName" placeholder="名称检索" size="small" style="max-width: 200px;">
        </el-input>
        <el-date-picker size="small" v-model="searchCreateRange" type="datetimerange" start-placeholder="最早创建时间"
          style="max-width: 400px;" end-placeholder="最晚创建时间" :editable="false"
          value-format="YYYY-MM-DDTHH:mm:ss"></el-date-picker>
        <el-button @click="handleCurrentChange" size="small"><el-icon><i
              class="iconfont icon-sousuo"></i></el-icon></el-button>
        <el-switch active-text="样本信息开" inactive-text="样本信息关" v-model="showExt" inline-prompt></el-switch>
      </div>
      <div>

        <el-space>
          <logview type="train"></logview>
          <el-button type="primary" @click="showAddModal" size="small"><el-text size="small"
              class="text-white">创建训练任务</el-text>
          </el-button>
        </el-space>

      </div>
    </div>

    <!-- 表格 -->
    <div class="table-div">
      <el-table class="my-table" :data="tableData" stripe style="width: 100%" size="small"
        v-el-height-adaptive-table="{ bottomOffset: 110, isUse: true }">


        <el-table-column prop="id" label="id" align="center" width="80" fixed="left">
          <template #default="scope">
            <el-text size="small">#{{ scope.row.id }}</el-text>
          </template>
        </el-table-column>

        <el-table-column prop="name" label="名称" align="center" fixed="left" />


        <el-table-column prop="username" label="创建人" align="center" />


        <el-table-column label="模型类别" align="center">
          <template #default="{ row }">
            <el-text>{{ algMap.get(row.type)?.name || row.type }}</el-text>
          </template>
        </el-table-column>

        <el-table-column prop="updated_date" label="更新时间" align="center" width="100">
          <template #default="{ row }">
            <el-text size="small">{{ showDateTime(row.updated_date) }}</el-text>
          </template>
        </el-table-column>


        <el-table-column prop="started_date" label="开始时间" align="center" width="100">
          <template #default="{ row }">
            <el-text size="small">{{ showDateTime(row.started_date) }}</el-text>
          </template>
        </el-table-column>


        <el-table-column prop="finish_date" label="结束时间" align="center" width="100">
          <template #default="{ row }">
            <el-text size="small">{{ showDateTime(row.finish_date) }}</el-text>
          </template>
        </el-table-column>


        <el-table-column label="耗时" align="center">
          <template #default="{ row }">
            <el-text v-if="row.finish_date" size="small">{{ getTimeDif(row.started_date, row.finish_date) }}</el-text>
          </template>
        </el-table-column>


        <el-table-column prop="status" label="任务状态" align="center" width="160">
          <template #default="{ row }">
            <el-button v-if="row.status == 0" loading size="small" type="info" link> {{
              taskStatusMap.get(row.status) }}
            </el-button>
            <el-button v-else-if="row.status == 1" size="small" type="success" link>{{
              taskStatusMap.get(row.status) }}
            </el-button>
            <el-button v-else-if="row.status == 2" size="small" type="warning" link>{{
              taskStatusMap.get(row.status) }}
            </el-button>
            <el-space v-else-if="row.status == 3">
              <el-button loading size="small" type="danger" link>{{ showStatus(row) }}
              </el-button>
              <el-button size="small" type="danger" link @click="openLog(row)" class="iconfont icon-genzong">
              </el-button>
            </el-space>
            <el-space v-else-if="row.status == 4">
              <el-button size="small" type="info" link>{{ showStatus(row) }}
                <el-button size="small" type="info" link @click="openLog(row)" class="iconfont icon-genzong">
                </el-button>
              </el-button>
            </el-space>
            <el-space v-else-if="row.status == 5">
              <el-button size="small" type="warning" link>{{ showStatus(row) }}
              </el-button>
            </el-space>
          </template>
        </el-table-column>


        <el-table-column label="备注" align="center">
          <template #default="{ row }">
            <el-tooltip v-if="row.remark" :content="row.remark" placement="top">
              <el-text size="small">{{ showRemark(row.remark, 20) }}</el-text>
            </el-tooltip>
            <el-text v-else></el-text>
          </template>
        </el-table-column>
        <el-table-column prop="prj_num" label="project数" align="center" v-if="showExt" />
        <el-table-column prop="task_num" label="task数" align="center" v-if="showExt" />
        <el-table-column prop="img_num" label="图片数" align="center" v-if="showExt" />
        <el-table-column prop="cls_num" label="标签数" align="center" v-if="showExt" />
        <el-table-column prop="obj_num" label="标注数" align="center" v-if="showExt" />
        <el-table-column label="操作" align="center" width="270" fixed="right">


          <!-- 操作按钮 -->
          <template #default="{ row }">
            <div class="flex-start  flex-wrap">
              <el-space :wrap="true">
                <el-button link size="small" @click="showEditModal(row)" v-if="isSys || curUser == row.username">
                  <el-tag size="small" type="primary" class="iconfont icon-bianji fontSpan">{{ row.run_name ? '查看任务' :
                    '编辑任务' }}</el-tag>
                </el-button>
                <el-button link size="small" @click="showCloneModal(row)">
                  <el-tag size="small" type="primary" class="iconfont icon-guanlianxinghao fontSpan">克隆任务</el-tag>
                </el-button>
                <el-button @click="viewResult(row)" link size="small" v-show="isSys || curUser == row.username">
                  <el-tag size="small" class="iconfont icon-chakan fontSpan">浏览文件</el-tag></el-button>

                
                <template v-if="isSys || curUser == row.username">
                  <!-- 执行成功了 -->
                  <template v-if="row.run_name">
                    <el-button @click="showTransModal(row)" link size="small"><el-tag size="small" type="warning"
                        class="iconfont icon-yingshe fontSpan">模型转换</el-tag></el-button>
                    <el-button @click="showValModal(row, true)" link size="small" :loading="row.val_state == 1"><el-tag
                        size="small" type="warning"
                        :class="row.val_state ? '' : 'iconfont icon-trainScript fontSpan'">模型验证</el-tag></el-button>
                    <el-button @click="showValModal(row, false)" link size="small"
                      :loading="row.predict_state == 1"><el-tag size="small" type="warning"
                        :class="row.predict_state ? '' : 'iconfont icon-brain-o fontSpan'">模型预测</el-tag></el-button>
                    <el-button @click="delRecord(row)" link size="small">
                      <el-tag size="small" type="danger"
                        class="iconfont icon-shanchu fontSpan">删除任务</el-tag></el-button>
                  </template>
                  <template v-else-if="row.status == 1 || row.status == 4">
                    <el-button link size="small" @click="enqueueTask(row)" v-if="!row.run_name">
                      <el-tag size="small" type="success" class="iconfont icon-qidongruanjian fontSpan">发布任务</el-tag>
                    </el-button>
                    <el-button @click="delRecord(row)" link size="small">
                      <el-tag size="small" type="danger"
                        class="iconfont icon-shanchu fontSpan">删除任务</el-tag></el-button>
                  </template>
                  <template v-else-if="row.status == 5">
                    <el-button @click="delRecord(row)" link size="small">
                      <el-tag size="small" type="danger"
                        class="iconfont icon-shanchu fontSpan">删除任务</el-tag></el-button>
                  </template>
                  <template v-else-if="row.status == 2">
                    <el-button link size="small" @click="topTask(row)">
                      <el-tag size="small" type="primary" class="iconfont icon-qidongruanjian fontSpan">置顶任务</el-tag>
                    </el-button>
                    <el-button link size="small" @click="cancelTask(row)">
                      <el-tag size="small" type="info" class="iconfont icon-tingzhiruanjian fontSpan">取消任务</el-tag>
                    </el-button>
                  </template>
                  <template v-else-if="row.status == 3">
                    <el-button link size="small" @click="stopTask(row)" :disabled="row.status != 3">
                      <el-tag size="small" type="danger" class="iconfont icon-tingzhiruanjian fontSpan">停止任务</el-tag>
                    </el-button>
                  </template>
                </template>
              </el-space>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>



    <div class="flex-end">
      <el-pagination background size="small" v-model:current-page="currentPage" v-model:page-size="currentSize"
        :page-sizes="[5, 10, 20, 30, 40, 50]" layout="total, sizes, prev, pager, next, jumper" :total="total"
        @current-change="handleCurrentChange" @size-change="handleCurrentChange" />
    </div>

    <div class="runner-status-footer">
      <el-alert
        :type="runnerHealthOk === true ? 'success' : (runnerHealthOk === false ? 'error' : 'info')"
        :closable="false"
        show-icon
      >
        <template #title>
          <span class="runner-status-title">MMDet 训练服务（Runner）</span>
        </template>
        <div class="runner-status-row">
          <el-text size="small" class="runner-status-text">{{ runnerHealthDetail }}</el-text>
          <el-button type="primary" link size="small" @click="refreshRunnerHealth">刷新</el-button>
        </div>
      </el-alert>
    </div>

  </div>


  <!-- 创建训练任务 -->
  <el-dialog top="1vh" v-model="addVisible" width="98%" :close-on-press-escape="false"
    :title="is_create ? (is_add ? '创建任务(追加模式)' : '创建任务') : '编辑任务_#' + cur_task_id" draggable
    :close-on-click-modal="false">
    <el-form size="small" label-width="130" label-position="left">
      <el-space wrap>
        <el-form-item label="任务名称" required>
          <el-input v-model="addForm.name" class="width-300" :disabled="isSee"></el-input>
        </el-form-item>
        <el-form-item label="模型类别" required>
          <el-select v-model="addForm.type" class="width-150" :disabled="is_add || isSee" @change="handleTypeChange">
            <el-option v-for="item in algList" :key="item.id" :value="item.id + ''" :label="item.name"></el-option>
          </el-select>
        </el-form-item>

        <!-- 算法模板（一键填写网络参数及算法参数） -->
        <el-form-item label="算法模板">
          <el-select v-model="addForm.temp" class="width-150" :disabled="!isMMDetSelected||isSee"
             @change="handleTempChange">
            <el-option v-for="item in templateList" :key="item" :value="item"
              :label="item"></el-option>
          </el-select>
          
        </el-form-item>
      </el-space>
      <el-row v-if="!isMMDetSelected">
        <el-col :span="6">
          <el-form-item label="模型标签" required>
            <el-select-v2 v-model="addForm.labels" :options="label_list" :reserve-keyword="false" filterable
              :disabled="is_add || isSee" @change="onChangeLabel" clearable multiple collapse-tags collapse-tags-tooltip
              placeholder="选择训练模型标签" popper-class="my-select-prop" :max-collapse-tags="1" :props="selectV2Props">
              <template #header>
                <el-checkbox v-model="selectV2CheckAll" :indeterminate="selectV2Indeterminate"
                  :disabled="is_add || isSee" @change="selectV2HandleCheckAll">
                  全选
                </el-checkbox>
              </template>
              <template #default="{ item }">
                <el-tag :type="item.merge ? 'warning' : 'primary'">{{ item.name }}</el-tag>
                <el-space v-if="item.merge" class="my-space">
                  <el-tag v-for="child in item.children" :key="child">{{ child }}</el-tag>
                </el-space>
              </template>
            </el-select-v2>
          </el-form-item>
        </el-col>
        <el-col :span="18" v-if="addForm.labels && addForm.labels.length > 0">

          <VueDraggable ref="el" v-model="addForm.labels" :disabled="is_add || isSee">
            <template v-for="(element, i) in addForm.labels" :key="element">
              <el-tag class="tagItem" :closable="!(is_add || isSee)" :disable-transitions="false"
                @close="labelsHandleClose(element)">
                {{ i }}.{{label_list.find(val => val.id == element)?.name || element}}
              </el-tag>
            </template>
          </VueDraggable>
        </el-col>
      </el-row>

      <el-tabs v-if="!isMMDetSelected" v-model="activeTab" class="tabs-div">
        <el-tab-pane label="训练样本配置" name="data-tab">
          <div class="pane-div">
            <el-container>
              <el-aside class="aside-container not-select">
                <div style="padding-bottom: 8px;">
                  <el-space :size="1">
                    <el-select v-model="filter_l" size="small" style="width:65px;" placeholder="光源"    clearable  multiple  collapse-tags  collapse-tags-tooltip>
                      <el-option v-for="(item, id) in a_l_list" :key="id" :value="item"></el-option>
                    </el-select>
                    <el-select v-model="filter_s" size="small" style="width: 65px;" placeholder="来源"  clearable  multiple  collapse-tags  collapse-tags-tooltip>
                      <el-option v-for="(item, id) in a_s_list" :key="id" :value="item"></el-option>
                    </el-select>
                    <el-select v-model="filter_g" size="small" style="width: 65px;" placeholder="用途"  clearable  multiple  collapse-tags  collapse-tags-tooltip> 
                      <el-option v-for="(item, id) in a_g_list" :key="id" :value="item"></el-option>
                    </el-select>
                    <el-input placeholder="名称" v-model="filter_n"></el-input>
                  </el-space>
                </div>
                <div v-for="(item) in filter_project" :key="item.id" v-show="item.show" style="padding-bottom: 10px;">
                  <el-badge :value="item.badge" :type="item.badge_type">
                    <el-link @click="showTask(item)" underline="never">
                      <el-space :size="1">
                        <el-tag style="width: 65px;height: 25px;" type="info">
                          <el-text truncated>{{ item.a_l }}</el-text>
                        </el-tag>
                        <el-tag style="width: 65px;height: 25px;" type="info">
                          <el-text truncated>{{ item.a_s }}</el-text>
                        </el-tag>
                        <el-tag style="width: 65px;height: 25px;" type="info">
                          <el-text truncated>{{ item.a_g }}</el-text>
                        </el-tag>
                        <el-tag style="width: 102px;height: 25px;"
                          :type="cur_project.project_id == item.project_id ? 'danger' : 'info'">
                          <el-text truncated style="width: 92px;"> {{ item.a_n }}</el-text>
                        </el-tag>
                      </el-space>

                    </el-link>
                  </el-badge>
                </div>
              </el-aside>
              <el-container>
                <el-header class="header-container">
                  <el-space wrap :size="0">
                    <el-select v-model="searchS" size="small" style="width: 100px;" placeholder="厂家"  clearable  multiple  collapse-tags  collapse-tags-tooltip >
                      <el-option v-for="(item, id) in cur_f_list.s" :key="id" :value="item"></el-option>
                    </el-select>
                    <el-select v-model="searchR" size="small" style="width: 100px;" placeholder="分辨率" clearable  multiple  collapse-tags  collapse-tags-tooltip>
                      <el-option v-for="(item, id) in cur_f_list.r" :key="id" :value="item"></el-option>
                    </el-select>
                    <el-select v-model="searchV" size="small" style="width: 100px;" placeholder="场地" clearable  multiple  collapse-tags  collapse-tags-tooltip>
                      <el-option v-for="(item, id) in cur_f_list.v" :key="id" :value="item"></el-option>
                    </el-select>
                    <el-select v-model="searchP" size="small" style="width: 100px;" placeholder="视角" clearable  multiple  collapse-tags  collapse-tags-tooltip>
                      <el-option v-for="(item, id) in cur_f_list.p" :key="id" :value="item"
                        :label="perspectiveMap.get(item) || item"></el-option>
                    </el-select>
                    <el-select v-model="searchA" size="small" style="width: 100px;" placeholder="精度" clearable  multiple  collapse-tags  collapse-tags-tooltip>
                      <el-option v-for="(item, id) in cur_f_list.a" :key="id" :value="item"></el-option>
                    </el-select>
                    <el-select v-model="searchE" size="small" style="width: 100px;" placeholder="场景" clearable  multiple  collapse-tags  collapse-tags-tooltip>
                      <el-option v-for="(item, id) in cur_f_list.e" :key="id" :value="item"></el-option>
                    </el-select>

                    <el-input v-model="searchN" placeholder="名称" size="small" style="max-width: 100px;">
                    </el-input>
                    <el-date-picker size="small" v-model="searchObtainRange" type="daterange" start-placeholder="获得时间起:"
                      value-format="YYYYMMDD" format="YYYY-MM-DD" style="max-width: 180px;" end-placeholder="止:"
                      :editable="false"></el-date-picker>
                    <el-button size="small"><el-icon class="iconfont icon-sousuo"></el-icon></el-button>
                    <el-checkbox v-model="checkAll_filter" :indeterminate="isIndeterminate_filter" label="全选"
                      style="padding: 8px;" @change="handleCheckAllChange_filter" :disabled="isSee">
                    </el-checkbox>
                  </el-space>
                </el-header>
                <el-main class="main-container" v-show="cur_project.show">
                  <div style="margin-bottom: 5px;">
                    <el-space wrap>
                      <el-tag v-for="(item, id) in (cur_project.labels || [])" :key="id">{{ item }}</el-tag>
                    </el-space>
                  </div>
                  <el-table :data="taskTable" stripe style="width: 100%" size="small">
                    <el-table-column width="50" fixed="left">
                      <template #header>
                        <el-checkbox v-model="checkAll" :indeterminate="isIndeterminate" @change="handleCheckAllChange"
                          :disabled="isSee">
                        </el-checkbox>
                      </template>
                      <template #default="{ row }">
                        <el-checkbox v-model="row.is_select" @change="handleCheckOne(row)"
                          :disabled="(is_add && row.dis) || isSee">
                        </el-checkbox>
                      </template>
                    </el-table-column>
                    <el-table-column prop="id" label="任务id" align="center" fixed="left">
                      <template #default="scope">
                        <el-text size="small">#{{ scope.row.id }}</el-text>
                        <el-text class="iconfont icon-yishanchu" type="danger" v-if="scope.row.del_flg"></el-text>
                      </template>
                    </el-table-column>
                    <el-table-column prop="a_n" label="名称" align="center" fixed="left" />
                    <el-table-column prop="size" label="样本数" align="center" fixed="left" />
                    <el-table-column label="预览图" align="center" fixed="left" v-if="!encode_src">
                      <template #default="{ row }">
                        <authimg v-if="row.first_img" style="height: 30px;" :bm="!isSys && row.a_se == 'p'"
                          :url="basePath_TASK + row.project_id + '/' + row.id + '/images/' + row.first_img"></authimg>
                      </template>
                    </el-table-column>
                    <el-table-column prop="created_date" label="创建时间" align="center">
                      <template #default="scope">
                        <el-text size="small">{{ showDate(scope.row.created_date) }}</el-text>
                      </template>
                    </el-table-column>
                    <el-table-column prop="a_s" label="厂家" align="center" />
                    <el-table-column prop="a_r" label="分辨率" align="center" />
                    <el-table-column prop="a_t" label="时间" align="center" />
                    <el-table-column prop="a_v" label="场地" align="center" />
                    <el-table-column prop="a_p" label="视角" align="center">
                      <template #default="{ row }">
                        {{ perspectiveMap.get(row.a_p) || row.a_p }}
                      </template>
                    </el-table-column>
                    <el-table-column prop="a_a" label="精度" align="center" />
                    <el-table-column prop="a_e" label="场景" align="center" />
                    <el-table-column label="样本集" align="center" fixed="right" v-if="!encode_src">
                      <template #default="{ row }">
                        <el-link :disabled="!isSys && row.a_se == 'p'" @click="showDataset(row)" underline="never">
                          <el-tag size="small" type="success">查看</el-tag>
                        </el-link>
                      </template>
                    </el-table-column>
                  </el-table>
                  <div class="flex-end" v-if="cur_project.tasks">
                    <el-pagination background size="small" v-model:current-page="currentPage2"
                      v-model:page-size="currentSize2" :page-sizes="[5, 10, 20, 30, 40, 50]"
                      layout="total, sizes, prev, pager, next, jumper" :total="filterTable.length" @current-change=""
                      @size-change="" />
                  </div>

                </el-main>
              </el-container>
            </el-container>
          </div>
        </el-tab-pane>
        <el-tab-pane label="验证样本配置" name="val-tab">
          <div class="pane-div">
            <el-container>
              <el-aside class="aside-container not-select">
                <div style="padding-bottom: 8px;">
                  <el-space :size="1">
                    <el-select v-model="filter_l_val" size="small" style="width:65px;" placeholder="光源"  clearable  multiple  collapse-tags  collapse-tags-tooltip>
                    
                      <el-option v-for="(item, id) in a_l_list" :key="id" :value="item"></el-option>
                    </el-select>
                    <el-select v-model="filter_s_val" size="small" style="width: 65px;" placeholder="来源" clearable  multiple  collapse-tags  collapse-tags-tooltip>
                    
                      <el-option v-for="(item, id) in a_s_list" :key="id" :value="item"></el-option>
                    </el-select>
                    <el-select v-model="filter_g_val" size="small" style="width: 65px;" placeholder="用途" clearable  multiple  collapse-tags  collapse-tags-tooltip>
                    
                      <el-option v-for="(item, id) in a_g_list" :key="id" :value="item"></el-option>
                    </el-select>
                    <el-input placeholder="名称" v-model="filter_n_val"></el-input>
                  </el-space>
                </div>
                <div v-for="(item) in filter_project_val" :key="item.id" v-show="item.show"
                  style="padding-bottom: 10px;">
                  <el-badge :value="item.badge" :type="item.badge_type">
                    <el-link @click="showTask_val(item)" underline="never">
                      <el-space :size="1">
                        <el-tag style="width: 65px;height: 25px;" type="info">
                          <el-text truncated>{{ item.a_l }}</el-text>
                        </el-tag>
                        <el-tag style="width: 65px;height: 25px;" type="info">
                          <el-text truncated>{{ item.a_s }}</el-text>
                        </el-tag>
                        <el-tag style="width: 65px;height: 25px;" type="info">
                          <el-text truncated>{{ item.a_g }}</el-text>
                        </el-tag>
                        <el-tag style="width: 102px;height: 25px;"
                          :type="cur_project_val.project_id == item.project_id ? 'danger' : 'info'">
                          <el-text truncated style="width: 92px;"> {{ item.a_n }}</el-text>
                        </el-tag>
                      </el-space>
                    </el-link>
                  </el-badge>
                </div>
              </el-aside>
              <el-container>
                <el-header class="header-container">
                  <el-space wrap :size="0">
                    <el-select v-model="searchS_val" size="small" style="width: 100px;" placeholder="厂家" clearable  multiple  collapse-tags  collapse-tags-tooltip>
                    
                      <el-option v-for="(item, id) in cur_f_list_val.s" :key="id" :value="item"></el-option>
                    </el-select>
                    <el-select v-model="searchR_val" size="small" style="width: 100px;" placeholder="分辨率" clearable  multiple  collapse-tags  collapse-tags-tooltip>
                    
                      <el-option v-for="(item, id) in cur_f_list_val.r" :key="id" :value="item"></el-option>
                    </el-select>
                    <el-select v-model="searchV_val" size="small" style="width: 100px;" placeholder="场地" clearable  multiple  collapse-tags  collapse-tags-tooltip>
                    
                      <el-option v-for="(item, id) in cur_f_list_val.v" :key="id" :value="item"></el-option>
                    </el-select>
                    <el-select v-model="searchP_val" size="small" style="width: 100px;" placeholder="视角" clearable  multiple  collapse-tags  collapse-tags-tooltip>
                    
                      <el-option v-for="(item, id) in cur_f_list_val.p" :key="id" :value="item"
                        :label="perspectiveMap.get(item) || item"></el-option>
                    </el-select>
                    <el-select v-model="searchA_val" size="small" style="width: 100px;" placeholder="精度" clearable  multiple  collapse-tags  collapse-tags-tooltip>
                    
                      <el-option v-for="(item, id) in cur_f_list_val.a" :key="id" :value="item"></el-option>
                    </el-select>
                    <el-select v-model="searchE_val" size="small" style="width: 100px;" placeholder="场景" clearable  multiple  collapse-tags  collapse-tags-tooltip>
                    
                      <el-option v-for="(item, id) in cur_f_list_val.e" :key="id" :value="item"></el-option>
                    </el-select>
                    <el-input v-model="searchN_val" placeholder="名称" size="small" style="max-width: 100px;">
                    </el-input>

                    <el-date-picker size="small" v-model="searchObtainRange_val" type="daterange"
                      start-placeholder="获得时间起:" value-format="YYYYMMDD" format="YYYY-MM-DD" style="max-width: 180px;"
                      end-placeholder="止:" :editable="false"></el-date-picker>
                    <el-button size="small"><el-icon class="iconfont icon-sousuo"></el-icon></el-button>
                    <el-checkbox v-model="checkAll_filter_val" :indeterminate="isIndeterminate_filter_val" label="全选"
                      :disabled="isSee" style="padding: 8px;" @change="handleCheckAllChange_filter_val">
                    </el-checkbox>
                  </el-space>
                </el-header>
                <el-main class="main-container-val" v-show="cur_project_val.show">
                  <div class="main-div">
                    <div style="margin-bottom: 5px;">
                      <el-space wrap>
                        <el-tag v-for="(item, id) in (cur_project_val.labels || [])" :key="id">{{ item }}</el-tag>
                      </el-space>
                    </div>
                    <el-table :data="taskTable_val" stripe style="width: 100%" size="small">
                      <el-table-column width="50" fixed="left">
                        <template #header>
                          <el-checkbox v-model="checkAll_val" :indeterminate="isIndeterminate_val" :disabled="isSee"
                            @change="handleCheckAllChange_val">
                          </el-checkbox>
                        </template>
                        <template #default="{ row }">
                          <el-checkbox v-model="row.is_select" @change="handleCheckOne_val(row)"
                            :disabled="(is_add && row.dis) || isSee">
                          </el-checkbox>
                        </template>
                      </el-table-column>
                      <el-table-column prop="id" label="任务id" align="center" fixed="left">
                        <template #default="scope">
                          <el-text size="small">#{{ scope.row.id }}</el-text>
                          <el-text class="iconfont icon-yishanchu" type="danger" v-if="scope.row.del_flg"></el-text>
                        </template>
                      </el-table-column>
                      <el-table-column prop="a_n" label="名称" align="center" fixed="left" />
                      <el-table-column prop="size" label="样本数" align="center" fixed="left" />
                      <el-table-column label="预览图" align="center" fixed="left" v-if="!encode_src">
                        <template #default="{ row }">
                          <authimg v-if="row.first_img" style="height: 30px;" :bm="!isSys && row.a_se == 'p'"
                            :url="basePath_TASK + row.project_id + '/' + row.id + '/images/' + row.first_img"></authimg>
                        </template>
                      </el-table-column>
                      <el-table-column prop="created_date" label="创建时间" align="center">
                        <template #default="scope">
                          <el-text size="small">{{ showDate(scope.row.created_date) }}</el-text>
                        </template>
                      </el-table-column>
                      <el-table-column prop="a_s" label="厂家" align="center" />
                      <el-table-column prop="a_r" label="分辨率" align="center" />
                      <el-table-column prop="a_t" label="时间" align="center" />
                      <el-table-column prop="a_v" label="场地" align="center" />
                      <el-table-column prop="a_p" label="视角" align="center">
                        <template #default="{ row }">
                          {{ perspectiveMap.get(row.a_p) || row.a_p }}
                        </template>
                      </el-table-column>
                      <el-table-column prop="a_a" label="精度" align="center" />
                      <el-table-column prop="a_e" label="场景" align="center" />
                      <el-table-column prop="export_img" label="样本集" align="center" fixed="right" v-if="!encode_src">
                        <template #default="{ row }">
                          <el-link :disabled="!isSys && row.a_se == 'p'" @click="showDataset(row)" underline="never">
                            <el-tag size="small" type="success">查看</el-tag>
                          </el-link>
                        </template>
                      </el-table-column>
                    </el-table>
                    <div class="flex-end" v-if="cur_project_val.tasks">
                      <el-pagination background size="small" v-model:current-page="currentPage2_val"
                        v-model:page-size="currentSize2_val" :page-sizes="[5, 10, 20, 30, 40, 50]"
                        layout="total, sizes, prev, pager, next, jumper" :total="filterTable_val.length"
                        @current-change="" @size-change="" />
                    </div>
                  </div>
                </el-main>
              </el-container>
            </el-container>
          </div>
        </el-tab-pane>
        <el-tab-pane label="网络参数配置" name="net-tab">
          <div class="pane-div">
            <el-form-item label="初始权重来源" required>
              <el-switch v-model="addForm.use_self_weights" :active-value="true" :inactive-value="false"
                :disabled="isSee" active-text="使用上传权重文件" inactive-text="使用配置库权重文件" inline-prompt></el-switch>
            </el-form-item>
            <el-form-item label="初始权重(weights)" v-if="addForm.use_self_weights" required>
              <el-upload action="#" :auto-upload="false" :on-change="(f, l) => uploadChange(f, l, 'pt')" accept=".pt"
                ref="refPtAdd">
                <template #trigger>
                  <el-button size="small">{{ (addForm.old_weights === 0) ? '更新权重文件' : '上传权重文件'
                  }}</el-button>
                </template>
              </el-upload>
            </el-form-item>
            <el-form-item label="初始权重(weights)" required v-else>
              <el-input disabled class="width-200" v-model="addForm.yolo_weights_id">
                <template #prepend>
                  <el-button>#</el-button>
                </template>
                <template #append>
                  <el-button @click="openDraw('weights')">选择</el-button>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item label="模型配置(cfg)" required v-show="cur_cmd == 'python'">
              <el-input disabled class="width-200" v-model="addForm.yolo_cfg_id">
                <template #prepend>
                  <el-button>#</el-button>
                </template>
                <template #append>
                  <el-button @click="openDraw('cfg')">选择</el-button>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item label="超参配置(hyp)" required v-show="cur_cmd == 'python'">
              <el-input disabled class="width-200" v-model="addForm.yolo_hyp_id">
                <template #prepend>
                  <el-button>#</el-button>
                </template>
                <template #append>
                  <el-button @click="openDraw('hyp')">选择</el-button>
                </template>
              </el-input>
            </el-form-item>

          </div>

        </el-tab-pane>
        <el-tab-pane label="训练参数配置" name="args-tab">
          <div class="pane-div">
            <el-form-item label="小标签过滤参数(px)" required>
              <el-space>
                <el-input v-model="addForm.f_max" style="width: 120px;" :disabled="isSee">
                  <template #prepend>长边></template>
                </el-input>
                <el-input v-model="addForm.f_min" style="width: 120px;" :disabled="isSee">
                  <template #prepend>短边></template>
                </el-input>
                <el-input v-model="addForm.f_area" style="width: 120px;" :disabled="isSee">
                  <template #prepend>面积></template>
                </el-input>
                <el-tooltip content="1.图片按img_size缩放后,标签尺寸低于要求的会被剔除; 2.修改过滤参数或img_size会导致标注文件重新生成" placement="top">
                  <el-icon class="iconfont icon-bangzhu"></el-icon>
                </el-tooltip>
              </el-space>
            </el-form-item>
            <el-form-item label="批大小(bath_size)" required>
              <el-input-number :controls="false" v-model="addForm.train_bath_size" :disabled="isSee"></el-input-number>
            </el-form-item>
            <el-form-item label="图尺寸(img_w,h)" required v-if="cur_cmd == 'mmdet'" :disabled="isSee">
              <el-space>
                <el-input-number :controls="false" v-model="addForm.train_img_w" :disabled="isSee"></el-input-number>
                <el-input-number :controls="false" v-model="addForm.train_img_h" :disabled="isSee"></el-input-number>
                <el-tooltip content="1.当小标签过滤参数不全部为0时,修改尺寸会导致标注文件重新生成" placement="top">
                  <el-icon class="iconfont icon-bangzhu"></el-icon>
                </el-tooltip>
              </el-space>
            </el-form-item>
            <el-form-item label="图尺寸(img_size)" required v-else>
              <el-space>
                <el-input-number :controls="false" v-model="addForm.train_img_size" :disabled="isSee"></el-input-number>
                <el-tooltip content="1.当小标签过滤参数不全部为0时,修改img_size会导致标注文件重新生成" placement="top">
                  <el-icon class="iconfont icon-bangzhu"></el-icon>
                </el-tooltip>
              </el-space>
            </el-form-item>

            <el-form-item label="训练轮次(epoch)" required>
              <el-input-number :controls="false" v-model="addForm.train_epoch" :disabled="isSee"></el-input-number>
            </el-form-item>
            <el-form-item label="保存策略(period)" required>
              <el-space>
                <el-input-number :controls="false" v-model="addForm.train_period" :disabled="isSee"></el-input-number>
                <el-tooltip content="对训练中间结果保留的策略,间隔几轮保存一次,<1代表不保存,只保存最后结果" placement="top">
                  <el-icon class="iconfont icon-bangzhu"></el-icon>
                </el-tooltip>
              </el-space>
            </el-form-item>
            <el-form-item label="硬件(device)" required>
              <el-space>
                <el-switch :disabled="isSee" inactive-text="使用CPU" active-text="使用GPU" v-model="addForm.train_use_gpu"
                  inline-prompt style="--el-switch-on-color: #409eff; --el-switch-off-color: #e6a23c"></el-switch>
                <el-space v-show="addForm.train_use_gpu" spacer="|">
                  <el-checkbox-group v-model="addForm.train_gpu_list" :disabled="isSee">
                    <el-checkbox v-for="gpu in gpu_list" :key="gpu" :label="gpu" :value="gpu">
                    </el-checkbox>
                  </el-checkbox-group>
                  <el-checkbox v-model="checkAll_yolo" :indeterminate="isIndeterminate_yolo" :disabled="isSee"
                    @change="handleCheckAllChange_yolo">
                    全选
                  </el-checkbox>
                </el-space>
              </el-space>
            </el-form-item>
            <el-form-item label="备注说明">
              <el-input type="textarea" v-model="addForm.remark" :disabled="isSee"></el-input>
            </el-form-item>
          </div>
        </el-tab-pane>

        <el-tab-pane label="其他参数配置" name="expand-tab">
          <div v-show="cur_cmd != 'mmdet'">
            <el-form-item label="拓展参数(json格式)" prop="ext_params">
              <div style="height:200px;width:50%">
                <aceEdit ref='aceEditRef' :readOnly="isSee ? true : false" v-model="addForm.ext_params" :height="200" />
              </div>
            </el-form-item>
            <el-form-item label="是否更新文件" prop="ext_file_update" v-if="!is_create">
              <el-switch :disabled="isSee" v-model="addForm.ext_file_update" inline-prompt active-text="更新文件"
                inactive-text="不更新" />
            </el-form-item>
            <el-form-item label="拓展文件(ext_file)" prop="ext_file">
              <el-upload :disabled="isSee" v-model:file-list="extFileList" ref="uploadRef" action="" :limit="1"
                :on-exceed="handleExceed" :auto-upload="false">
                <template #trigger>
                  <el-button type="primary">选择文件</el-button>
                </template>
              </el-upload>
            </el-form-item>
          </div>
          <div v-show="cur_cmd == 'mmdet'">
            <el-form-item label="配置文件(py格式)" required>
              <div style="height:430px;width:100%">
                <aceEdit ref='aceEditRef2' :readOnly="isSee ? true : false" lang="python" v-model="addForm.mmdet_cfg"
                  :height="430" />
              </div>
            </el-form-item>
          </div>
        </el-tab-pane>
      </el-tabs>
      
      <!-- <div v-else class="mmdet-div">
        <el-divider />

        <el-row>
          <el-col :span="10">
            <div v-if="addForm.temp!=='DETR'">
              <div class="config-text">网络结构配置</div>
              <div style="padding-left: 20px;border: 1px solid #e4e7ed; border-radius: 4px; padding: 12px;margin-right:30px;margin-left: 15px;">

                <el-form-item label="网络模板名称：" required>
                  <el-select v-model="mmdetParameter.selected_template" class="width-200" :disabled="isSee">
                    <el-option v-for="item in netTemplateName" :key="item" :value="item" :label="item"></el-option>
                  </el-select>
                </el-form-item>

                <el-form-item label="主干网名称：" required>
                  <el-select v-model="mmdetParameter.selected_network" class="width-200" :disabled="isSee">
                    <el-option v-for="item in availableBackboneNetworks"
                    :key="item.value" 
                    :value="item.value" 
                    :label="item.label"
                    :disabled="item.disabled"></el-option>
                  </el-select>
                </el-form-item>

               

                

              </div>

              <div>
                <div class="config-text">主干网参数配置</div>

                  <div v-if="mmdetParameter.selected_network==='ConvNext'||mmdetParameter.selected_network==='SwinTransformer'" style="padding-left: 20px;
                  border: 1px solid #e4e7ed; border-radius: 4px; padding: 12px;margin-right:30px;margin-left: 15px;">


                    <el-form-item label="规模选择：" required>
                      <el-select v-model="mmdetParameter.scale" class="width-200" :disabled="isSee">
                        <el-option v-for="item in scaleList" :key="item" :value="item" :label="item"></el-option>
                      </el-select>
                    </el-form-item>

                    <el-form-item v-if="mmdetParameter.selected_network==='SwinTransformer'" label="窗尺寸：" required>
                      <el-input-number  v-model="mmdetParameter.window_size" class="width-200" :controls="false"    :disabled="isSee"></el-input-number>
                    </el-form-item>

                    <el-form-item label="预训练权值：" required>
                      <el-upload :disabled="isSee" v-model:file-list="weightFileList" ref="uploadWeightRef" action="" :limit="1"
                         :on-exceed="handleWeightExceed" :auto-upload="false">
                        <template #trigger>
                           <el-button type="primary">选择文件</el-button>
                        </template>
                      </el-upload>
                    </el-form-item>

                      

                  </div>

                  <div v-else style="padding-left: 20px;border: 1px solid #e4e7ed; border-radius: 4px; padding: 12px;margin-right:30px;margin-left: 15px;">

                    

                    <el-form-item label="深度：" required>
                      <el-select v-model="mmdetParameter.deep" class="width-200" :disabled="isSee">
                        <el-option v-for="item in deepList" :key="item" :value="item" :label="item"></el-option>
                      </el-select>
                    </el-form-item>

                    

                    <el-form-item label="存在阶段：" required>
                      <el-input  v-model="mmdetParameter.exist_stage" class="width-200" :disabled="isSee"></el-input>
                    </el-form-item>

                    <el-row>
                      <el-col :span="10">
                        <el-form-item label="预训练权值：" required>
                          <el-upload :disabled="isSee" v-model:file-list="weightFileList" ref="uploadWeightRef" action="" :limit="1"
                            :on-exceed="handleWeightExceed" :auto-upload="false">
                            <template #trigger>
                              <el-button type="primary">选择文件</el-button>
                            </template>
                          </el-upload>
                        </el-form-item>

                      </el-col>
                      <el-col :span="8">
                        <el-checkbox v-model="mmdetParameter.dcn_sac_use" label="DCN/SAC使用" style="padding-left: 20px;" size="small" />

                      </el-col>
                      
                    </el-row>

                  </div>

                  



              </div>

              <div v-if="mmdetParameter.selected_template === 'DetectoRS'">
                <div class="config-text">Neck参数配置</div>

                  <div style="padding-left: 20px;border: 1px solid #e4e7ed; border-radius: 4px; padding: 12px;margin-right:30px;margin-left: 15px;">


                    <el-row>
                      <el-col :span="10">
                        <el-form-item label="RFP迭代次数：" required>
                          <el-input-number  v-model="mmdetParameter.RFP" class="width-150" :controls="false"    :disabled="isSee"></el-input-number>
                        </el-form-item>

                      </el-col>
                      <el-col :span="8">
                        <el-checkbox v-model="mmdetParameter.dcn_sac_use" label="SAC使用" style="padding-left: 20px;" size="small" />

                      </el-col>
                      
                    </el-row>

                    <el-form-item label="ASPP稀释度：" required>
                      <el-input  v-model="mmdetParameter.ASPP" class="width-200" :disabled="isSee"></el-input>
                    </el-form-item>


                    <el-form-item label="存在阶段：" required>
                      <el-input  v-model="mmdetParameter.exist_stage" class="width-200" :disabled="isSee"></el-input>
                    </el-form-item>
                    

                    

                  </div>

              </div>

              <div v-if="mmdetParameter.selected_template!==null&&mmdetParameter.selected_template!=='Faster R-CNN'">
                <div class="config-text">检测头参数配置</div>

                  <div style="padding-left: 20px;border: 1px solid #e4e7ed; border-radius: 4px; padding: 12px;margin-right:30px;margin-left: 15px;">

                    <el-form-item label="迭代次数：" required>
                      <el-input-number  v-model="mmdetParameter.iter_count" class="width-200" :controls="false"    :disabled="isSee"></el-input-number>
                    </el-form-item>

                    

                    

                  </div>

              </div>

              
              


            </div>

            <div v-if="addForm.temp==='DETR'">
              <div class="config-text">网络结构配置</div>
              <div style="padding-left: 20px;border: 1px solid #e4e7ed; border-radius: 4px; padding: 12px;margin-right:30px;margin-left: 15px;">

                <el-form-item label="网络模板名称：" required>
                  <el-select v-model="mmdetParameter.selected_template" class="width-200" :disabled="isSee">
                    <el-option v-for="item in netTemplateName_DETR" :key="item" :value="item" :label="item"></el-option>
                  </el-select>
                </el-form-item>

                <el-form-item label="主干网名称：" required>
                  <el-select v-model="mmdetParameter.selected_network" class="width-200" :disabled="isSee">
                    <el-option v-for="item in availableBackboneNetworks"
                    :key="item.value" 
                    :value="item.value" 
                    :label="item.label"
                    :disabled="item.disabled"></el-option>
                  </el-select>
                </el-form-item>

               

                

              </div>

              <div>
                <div class="config-text">主干网参数配置</div>

                  <div v-if="mmdetParameter.selected_network==='ConvNext'||mmdetParameter.selected_network==='SwinTransformer'" style="padding-left: 20px;
                  border: 1px solid #e4e7ed; border-radius: 4px; padding: 12px;margin-right:30px;margin-left: 15px;">


                    <el-form-item label="规模选择：" required>
                      <el-select v-model="mmdetParameter.scale" class="width-200" :disabled="isSee">
                        <el-option v-for="item in scaleList" :key="item" :value="item" :label="item"></el-option>
                      </el-select>
                    </el-form-item>

                    <el-form-item v-if="mmdetParameter.selected_network==='SwinTransformer'" label="窗尺寸：" required>
                      <el-input-number  v-model="mmdetParameter.window_size" class="width-200" :controls="false"    :disabled="isSee"></el-input-number>
                    </el-form-item>

                    <el-form-item label="预训练权值：" required>
                      <el-upload :disabled="isSee" v-model:file-list="weightFileList" ref="uploadWeightRef" action="" :limit="1"
                         :on-exceed="handleWeightExceed" :auto-upload="false">
                        <template #trigger>
                           <el-button type="primary">选择文件</el-button>
                        </template>
                      </el-upload>
                    </el-form-item>

                      

                  </div>

                  <div v-else style="padding-left: 20px;border: 1px solid #e4e7ed; border-radius: 4px; padding: 12px;margin-right:30px;margin-left: 15px;">

                    

                    <el-form-item label="深度：" required>
                      <el-select v-model="mmdetParameter.deep" class="width-200" :disabled="isSee">
                        <el-option v-for="item in deepList" :key="item" :value="item" :label="item"></el-option>
                      </el-select>
                    </el-form-item>

                    

                    <el-form-item label="存在阶段：" required>
                      <el-input  v-model="mmdetParameter.exist_stage" class="width-200" :disabled="isSee"></el-input>
                    </el-form-item>

                    <el-row>
                      <el-col :span="10">
                        <el-form-item label="预训练权值：" required>
                          <el-upload :disabled="isSee" v-model:file-list="weightFileList" ref="uploadWeightRef" action="" :limit="1"
                            :on-exceed="handleWeightExceed" :auto-upload="false">
                            <template #trigger>
                              <el-button type="primary">选择文件</el-button>
                            </template>
                          </el-upload>
                        </el-form-item>

                      </el-col>
                      <el-col :span="8">
                        <el-checkbox v-model="mmdetParameter.dcn_sac_use" label="DCN/SAC使用" style="padding-left: 20px;" size="small" />

                      </el-col>
                      
                    </el-row>

                  </div>
              </div>
              <div class="config-text">Neck网络</div>
              <div style="padding-left: 20px;border: 1px solid #e4e7ed; border-radius: 4px; padding: 12px;margin-right:30px;margin-left: 15px;">
                <el-form-item label="尺度" required>
                  <el-select v-model="mmdetParameter.measure" class="width-200" :disabled="isSee">
                    <el-option v-for="item in measureList" :key="item" :value="item.value " :label="item.label"></el-option>
                  </el-select>
                </el-form-item>
              </div>

              <div class="config-text">编/解码器</div>
              <div style="padding-left: 20px;border: 1px solid #e4e7ed; border-radius: 4px; padding: 12px; margin-right:30px;margin-left: 15px;">
                <el-row>
                  <el-col :span="12">

                    <el-form-item label="嵌入维度">
                      <el-input-number class="width-200" :controls="false" v-model="mmdetParameter.embedding_dimension" :disabled="isSee"></el-input-number>
                    </el-form-item>

                    <el-form-item label="解码器层数">
                      <el-input-number class="width-200" :controls="false" v-model="mmdetParameter.encoder_layers" :disabled="isSee"></el-input-number>
                    </el-form-item>

                  </el-col>

                  <el-col :span="9">
                    <el-form-item label="编码器层数">
                      <el-input-number class="width-200" :controls="false" v-model="mmdetParameter.decoder_layers" :disabled="isSee"></el-input-number>
                    </el-form-item>

                  </el-col>

                </el-row>
                <div class="config-text-sub">注意力参数</div>
                <el-row>
                  <el-col :span="12">

                    <el-form-item label="注意力头数">
                      <el-input-number class="width-200" :controls="false" v-model="mmdetParameter.attention_num" :disabled="isSee"></el-input-number>
                    </el-form-item>

                  </el-col>

                  <el-col :span="9">
                    <el-form-item label="丢弃率">
                      <el-input-number class="width-200" :controls="false" v-model="mmdetParameter.attention_discard_rate" :disabled="isSee"></el-input-number>
                    </el-form-item>

                  </el-col>

                </el-row>

                <div class="config-text-sub">FFN参数</div>
                <el-row>
                  <el-col :span="12">

                    <el-form-item label="中间层维度">
                      <el-input-number class="width-200" :controls="false" v-model="mmdetParameter.FFN_intermediate_layer_dimension" :disabled="isSee"></el-input-number>
                    </el-form-item>

                    <el-form-item label="丢弃率">
                      <el-input-number class="width-200" :controls="false" v-model="mmdetParameter.FFN_discard_rate" :disabled="isSee"></el-input-number>
                    </el-form-item>

                  </el-col>

                  <el-col :span="9">
                    <el-form-item label="线性层数量">
                      <el-input-number class="width-200" :controls="false" v-model="mmdetParameter.FFN_linear_layer_num" :disabled="isSee"></el-input-number>
                    </el-form-item>

                    <el-form-item label="激活函数" required>
                      <el-select v-model="mmdetParameter.FFN_active_func" class="width-200" :disabled="isSee">
                        <el-option v-for="item in activationFuncList" :key="item" :value="item " :label="item"></el-option>
                      </el-select>
                    </el-form-item>

                  </el-col>

                </el-row>
                

              </div>

              <div class="config-text">位置编码</div>
              <div style="padding-left: 20px;border: 1px solid #e4e7ed; border-radius: 4px; padding: 12px;margin-right:30px;margin-left: 15px;">
                <el-form-item label="温度参数">
                  <el-input-number class="width-200" :controls="false" v-model="mmdetParameter.temperature" :disabled="isSee"></el-input-number>
                </el-form-item>
              </div>

              <div class="config-text">检测头</div>
              <div style="padding-left: 20px;border: 1px solid #e4e7ed; border-radius: 4px; padding: 12px;margin-right:30px;margin-left: 15px;">
                <el-row>
                  <el-col :span="12">

                    <el-form-item label="分类损失" required>
                      <el-select v-model="mmdetParameter.loss_cls" class="width-200" :disabled="isSee">
                        <el-option v-for="item in clsLossList" :key="item" :value="item " :label="item"></el-option>
                      </el-select>
                    </el-form-item>

                    <el-form-item label="边界框损失" required>
                      <el-select v-model="mmdetParameter.loss_bbox" class="width-200" :disabled="isSee">
                        <el-option v-for="item in bboxLossList" :key="item" :value="item " :label="item"></el-option>
                      </el-select>
                    </el-form-item>

                    <el-form-item label="IoU损失" required>
                      <el-select v-model="mmdetParameter.loss_iou" class="width-200" :disabled="isSee">
                        <el-option v-for="item in iouLossList" :key="item" :value="item " :label="item"></el-option>
                      </el-select>
                    </el-form-item>

                  </el-col>

                  <el-col :span="9">
                    <el-form-item label="损失权重参数">
                      <el-input-number class="width-200" :controls="false" v-model="mmdetParameter.loss_cls_weight" :disabled="isSee"></el-input-number>
                    </el-form-item>

                    <el-form-item label="损失权重参数">
                      <el-input-number class="width-200" :controls="false" v-model="mmdetParameter.loss_bbox_weight" :disabled="isSee"></el-input-number>
                    </el-form-item>

                    <el-form-item label="损失权重参数">
                      <el-input-number class="width-200" :controls="false" v-model="mmdetParameter.loss_iou_weight" :disabled="isSee"></el-input-number>
                    </el-form-item>

                  </el-col>

                </el-row>
              </div>

            </div>
          
          </el-col>
          <el-col :span="7">
            <div>
              <div class="config-text">训练参数配置</div>

              <div style="padding-left: 20px;border: 1px solid #e4e7ed; border-radius: 4px; padding: 16px; ">
                <el-form-item label="实例数据集" required>
                  <el-select v-model="mmdetParameter.selected_dataset" class="width-200" :disabled="isSee" placeholder="仅显示磁盘可用数据集">
                    <el-option v-for="item in exampleList" :key="item" :value="item" :label="item"></el-option>
                  </el-select>
                  <div v-if="!exampleList.length && !isSee" class="el-form-item__tip" style="color: #909399; font-size: 12px; line-height: 1.4; margin-top: 4px;">
                    无可用项时请确认预处理已落盘；后端配置的实例数据集根目录（如 INSTANCE_DATA_ROOT / sys.instancecfg.instancedata-root）下需存在 images/train、annotations/train 等目录，且训练集内至少有一张图与一个 DOTA txt。
                  </div>
                </el-form-item>

                <el-form-item label="图片尺寸" required>
                  <el-input v-model="mmdetParameter.photo_width" style="width: 150px;" :disabled="isSee">
                    <template #prepend>长边></template>
                  </el-input>
                  <div style="margin: 0 8px;">×</div>
                  <el-input v-model="mmdetParameter.photo_height" style="width: 150px;" :disabled="isSee">
                    <template #prepend>短边></template>
                  </el-input>
                </el-form-item>

                <el-form-item label="批大小(bath_size)" required>
                  <el-input-number class="width-200" :controls="false" v-model="mmdetParameter.train_bath_size" :disabled="isSee"></el-input-number>
                </el-form-item>

                <el-form-item label="优化器" required>
                  <el-select v-model="mmdetParameter.optimizer" class="width-200" :disabled="isSee">
                    <el-option v-for="item in optimizerList" :key="item" :value="item" :label="item"></el-option>
                  </el-select>
                </el-form-item>

                <el-form-item label="初始学习率" required>
                  <el-input  v-model="mmdetParameter.stu_rate" class="width-200" :disabled="isSee"></el-input>
                </el-form-item>

                <el-form-item label="训练轮次(epoch)" required>
                  <el-input-number class="width-200" :controls="false" v-model="mmdetParameter.train_epoch" :disabled="isSee"></el-input-number>
                </el-form-item>

                <el-form-item label="学习率下降轮次" required>
                  <el-input-number  v-model="mmdetParameter.down_round" class="width-200" :controls="false"    :disabled="isSee"></el-input-number>
                </el-form-item>

                <el-form-item label="权值保存轮次间隔" required>
                  <el-input  v-model="mmdetParameter.weight_round" class="width-200"  :disabled="isSee"></el-input>
                </el-form-item>

                <el-form-item label="验证集评测轮次间隔" required>
                  <el-input v-model="mmdetParameter.valid_round" class="width-200" :disabled="isSee"></el-input>
                </el-form-item>


              </div>
            </div>
            
          </el-col>
        </el-row>
        
        
      </div> -->

      <div v-else class="mmdet-div">
        <el-divider />
        <el-row :gutter="20">
          <el-col :span="14">
            <div class="mmdet-file-block">
              <span class="mmdet-file-title">model.py (模型结构配置)</span>
              
              <div v-if="addForm.temp!=='DETR'">
                <div class="config-text">网络架构</div>
                <div class="sub-param-box">
                  <el-form-item label="网络模板名称：" required>
                    <el-select v-model="mmdetParameter.selected_template" class="width-200" :disabled="isSee">
                      <el-option v-for="item in netTemplateName" :key="item" :value="item" :label="item"></el-option>
                    </el-select>
                  </el-form-item>
                  <el-form-item label="主干网名称：" required>
                    <el-select v-model="mmdetParameter.selected_network" class="width-200" :disabled="isSee">
                      <el-option v-for="item in availableBackboneNetworks" :key="item.value" :value="item.value"
                        :label="item.label" :disabled="item.disabled"></el-option>
                    </el-select>
                  </el-form-item>
                </div>

                <div class="config-text">主干网参数</div>
                <div v-if="mmdetParameter.selected_network==='ConvNext'||mmdetParameter.selected_network==='SwinTransformer'"
                  class="sub-param-box">
                  <el-form-item label="规模选择：" required>
                    <el-select v-model="mmdetParameter.scale" class="width-200" :disabled="isSee">
                      <el-option v-for="item in scaleList" :key="item" :value="item" :label="item"></el-option>
                    </el-select>
                  </el-form-item>
                  <el-form-item v-if="mmdetParameter.selected_network==='SwinTransformer'" label="窗尺寸：" required>
                    <el-input-number v-model="mmdetParameter.window_size" class="width-200" :controls="false"
                      :disabled="isSee"></el-input-number>
                  </el-form-item>
                  <el-form-item label="预训练权值来源：">
                    <el-radio-group v-model="mmdetParameter.use_custom_pretrained" :disabled="isSee">
                      <el-radio :label="false">使用模板默认（torchvision / 配置内网络地址）</el-radio>
                      <el-radio :label="true">自定义预训练权值（上传或填写地址）</el-radio>
                    </el-radio-group>
                  </el-form-item>
                  <template v-if="mmdetParameter.use_custom_pretrained">
                    <el-form-item label="权值文件：">
                      <el-upload :disabled="isSee" v-model:file-list="weightFileList" ref="uploadWeightRef" action=""
                        :limit="1" :on-exceed="handleWeightExceed" :auto-upload="false">
                        <template #trigger>
                          <el-button type="primary" size="small">选择文件</el-button>
                        </template>
                      </el-upload>
                    </el-form-item>
                    <el-form-item label="或权值地址：">
                      <el-input v-model="mmdetParameter.pretrained_address" :disabled="isSee" type="textarea" :rows="2"
                        placeholder="https://…、torchvision://resnet50 、本地绝对路径等（可与上传二选一，优先使用上传文件）" />
                    </el-form-item>
                  </template>
                  <el-form-item v-else label="说明：">
                    <span style="color: var(--el-text-color-secondary); font-size: 12px;">保留模板中的预训练配置，仅替换类别数。</span>
                  </el-form-item>
                </div>

                <div v-else class="sub-param-box">
                  <el-form-item label="深度：" required>
                    <el-select v-model="mmdetParameter.deep" class="width-200" :disabled="isSee">
                      <el-option v-for="item in deepList" :key="item" :value="item" :label="item"></el-option>
                    </el-select>
                  </el-form-item>
                  <el-form-item label="存在阶段：" required>
                    <el-input v-model="mmdetParameter.exist_stage" class="width-200" :disabled="isSee"></el-input>
                  </el-form-item>
                  <el-row>
                    <el-col :span="12">
                      <el-form-item label="预训练权值来源：">
                        <el-radio-group v-model="mmdetParameter.use_custom_pretrained" :disabled="isSee">
                          <el-radio :label="false">使用模板默认</el-radio>
                          <el-radio :label="true">自定义（上传或地址）</el-radio>
                        </el-radio-group>
                      </el-form-item>
                      <template v-if="mmdetParameter.use_custom_pretrained">
                        <el-form-item label="权值文件：">
                          <el-upload :disabled="isSee" v-model:file-list="weightFileList" ref="uploadWeightRef" action=""
                            :limit="1" :on-exceed="handleWeightExceed" :auto-upload="false">
                            <template #trigger>
                              <el-button type="primary" size="small">选择文件</el-button>
                            </template>
                          </el-upload>
                        </el-form-item>
                        <el-form-item label="或权值地址：">
                          <el-input v-model="mmdetParameter.pretrained_address" :disabled="isSee" type="textarea" :rows="2"
                            placeholder="https://… 、torchvision:// 、本地路径等" />
                        </el-form-item>
                      </template>
                    </el-col>
                    <el-col :span="12">
                      <el-checkbox v-model="mmdetParameter.dcn_sac_use" label="DCN/SAC使用" style="padding-left: 20px;"
                        size="small" />
                    </el-col>
                  </el-row>
                </div>

                <div v-if="mmdetParameter.selected_template === 'DetectoRS'">
                  <div class="config-text">Neck参数</div>
                  <div class="sub-param-box">
                    <el-row>
                      <el-col :span="12">
                        <el-form-item label="RFP迭代次数：" required>
                          <el-input-number v-model="mmdetParameter.RFP" class="width-150" :controls="false"
                            :disabled="isSee"></el-input-number>
                        </el-form-item>
                      </el-col>
                      <el-col :span="12">
                        <el-checkbox v-model="mmdetParameter.dcn_sac_use" label="SAC使用" style="padding-left: 20px;"
                          size="small" />
                      </el-col>
                    </el-row>
                    <el-form-item label="ASPP稀释度：" required>
                      <el-input v-model="mmdetParameter.ASPP" class="width-200" :disabled="isSee"></el-input>
                    </el-form-item>
                    <el-form-item label="存在阶段：" required>
                      <el-input v-model="mmdetParameter.exist_stage" class="width-200" :disabled="isSee"></el-input>
                    </el-form-item>
                  </div>
                </div>

                <div
                  v-if="mmdetParameter.selected_template!==null&&mmdetParameter.selected_template!=='Faster R-CNN'">
                  <div class="config-text">检测头参数</div>
                  <div class="sub-param-box">
                    <el-form-item label="迭代次数：" required>
                      <el-input-number v-model="mmdetParameter.iter_count" class="width-200" :controls="false"
                        :disabled="isSee"></el-input-number>
                    </el-form-item>
                  </div>
                </div>
              </div>

              <div v-if="addForm.temp==='DETR'">
                <div class="config-text">网络架构</div>
                <div class="sub-param-box">
                  <el-form-item label="网络模板名称：" required>
                    <el-select v-model="mmdetParameter.selected_template" class="width-200" :disabled="isSee">
                      <el-option v-for="item in netTemplateName_DETR" :key="item" :value="item"
                        :label="item"></el-option>
                    </el-select>
                  </el-form-item>
                  <el-form-item label="主干网名称：" required>
                    <el-select v-model="mmdetParameter.selected_network" class="width-200" :disabled="isSee">
                      <el-option v-for="item in availableBackboneNetworks" :key="item.value" :value="item.value"
                        :label="item.label" :disabled="item.disabled"></el-option>
                    </el-select>
                  </el-form-item>
                </div>

                <div class="config-text">主干网参数</div>
                <div v-if="mmdetParameter.selected_network==='ConvNext'||mmdetParameter.selected_network==='SwinTransformer'"
                  class="sub-param-box">
                  <el-form-item label="规模选择：" required>
                    <el-select v-model="mmdetParameter.scale" class="width-200" :disabled="isSee">
                      <el-option v-for="item in scaleList" :key="item" :value="item" :label="item"></el-option>
                    </el-select>
                  </el-form-item>
                  <el-form-item v-if="mmdetParameter.selected_network==='SwinTransformer'" label="窗尺寸：" required>
                    <el-input-number v-model="mmdetParameter.window_size" class="width-200" :controls="false"
                      :disabled="isSee"></el-input-number>
                  </el-form-item>
                  <el-form-item label="预训练权值来源：">
                    <el-radio-group v-model="mmdetParameter.use_custom_pretrained" :disabled="isSee">
                      <el-radio :label="false">使用模板默认（torchvision / 配置内网络地址）</el-radio>
                      <el-radio :label="true">自定义预训练权值（上传或填写地址）</el-radio>
                    </el-radio-group>
                  </el-form-item>
                  <template v-if="mmdetParameter.use_custom_pretrained">
                    <el-form-item label="权值文件：">
                      <el-upload :disabled="isSee" v-model:file-list="weightFileList" ref="uploadWeightRef" action=""
                        :limit="1" :on-exceed="handleWeightExceed" :auto-upload="false">
                        <template #trigger>
                          <el-button type="primary" size="small">选择文件</el-button>
                        </template>
                      </el-upload>
                    </el-form-item>
                    <el-form-item label="或权值地址：">
                      <el-input v-model="mmdetParameter.pretrained_address" :disabled="isSee" type="textarea" :rows="2"
                        placeholder="https://…、torchvision://resnet50 、本地绝对路径等" />
                    </el-form-item>
                  </template>
                  <el-form-item v-else label="说明：">
                    <span style="color: var(--el-text-color-secondary); font-size: 12px;">保留模板中的预训练配置，仅替换类别数。</span>
                  </el-form-item>
                </div>
                <div v-else class="sub-param-box">
                  <el-form-item label="深度：" required>
                    <el-select v-model="mmdetParameter.deep" class="width-200" :disabled="isSee">
                      <el-option v-for="item in deepList" :key="item" :value="item" :label="item"></el-option>
                    </el-select>
                  </el-form-item>
                  <el-form-item label="存在阶段：" required>
                    <el-input v-model="mmdetParameter.exist_stage" class="width-200" :disabled="isSee"></el-input>
                  </el-form-item>
                  <el-row>
                    <el-col :span="12">
                      <el-form-item label="预训练权值来源：">
                        <el-radio-group v-model="mmdetParameter.use_custom_pretrained" :disabled="isSee">
                          <el-radio :label="false">使用模板默认</el-radio>
                          <el-radio :label="true">自定义（上传或地址）</el-radio>
                        </el-radio-group>
                      </el-form-item>
                      <template v-if="mmdetParameter.use_custom_pretrained">
                        <el-form-item label="权值文件：">
                          <el-upload :disabled="isSee" v-model:file-list="weightFileList" ref="uploadWeightRef" action=""
                            :limit="1" :on-exceed="handleWeightExceed" :auto-upload="false">
                            <template #trigger>
                              <el-button type="primary" size="small">选择文件</el-button>
                            </template>
                          </el-upload>
                        </el-form-item>
                        <el-form-item label="或权值地址：">
                          <el-input v-model="mmdetParameter.pretrained_address" :disabled="isSee" type="textarea" :rows="2"
                            placeholder="https://… 、torchvision:// 、本地路径等" />
                        </el-form-item>
                      </template>
                    </el-col>
                    <el-col :span="12">
                      <el-checkbox v-model="mmdetParameter.dcn_sac_use" label="DCN/SAC使用" style="padding-left: 20px;"
                        size="small" />
                    </el-col>
                  </el-row>
                </div>

                <div class="config-text">Neck参数</div>
                <div class="sub-param-box">
                  <el-form-item label="尺度" required>
                    <el-select v-model="mmdetParameter.measure" class="width-200" :disabled="isSee">
                      <el-option v-for="item in measureList" :key="item" :value="item.value "
                        :label="item.label"></el-option>
                    </el-select>
                  </el-form-item>
                </div>

                <div class="config-text">编/解码器</div>
                <div class="sub-param-box">
                  <el-row>
                    <el-col :span="12">
                      <el-form-item label="嵌入维度">
                        <el-input-number class="width-200" :controls="false"
                          v-model="mmdetParameter.embedding_dimension" :disabled="isSee"></el-input-number>
                      </el-form-item>
                      <el-form-item label="解码器层数">
                        <el-input-number class="width-200" :controls="false" v-model="mmdetParameter.encoder_layers"
                          :disabled="isSee"></el-input-number>
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="编码器层数">
                        <el-input-number class="width-200" :controls="false" v-model="mmdetParameter.decoder_layers"
                          :disabled="isSee"></el-input-number>
                      </el-form-item>
                    </el-col>
                  </el-row>
                  <div class="config-text-sub">注意力参数</div>
                  <el-row>
                    <el-col :span="12">
                      <el-form-item label="注意力头数">
                        <el-input-number class="width-200" :controls="false" v-model="mmdetParameter.attention_num"
                          :disabled="isSee"></el-input-number>
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="丢弃率">
                        <el-input-number class="width-200" :controls="false"
                          v-model="mmdetParameter.attention_discard_rate" :disabled="isSee"></el-input-number>
                      </el-form-item>
                    </el-col>
                  </el-row>
                  <div class="config-text-sub">FFN参数</div>
                  <el-row>
                    <el-col :span="12">
                      <el-form-item label="中间层维度">
                        <el-input-number class="width-200" :controls="false"
                          v-model="mmdetParameter.FFN_intermediate_layer_dimension" :disabled="isSee"></el-input-number>
                      </el-form-item>
                      <el-form-item label="丢弃率">
                        <el-input-number class="width-200" :controls="false" v-model="mmdetParameter.FFN_discard_rate"
                          :disabled="isSee"></el-input-number>
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="线性层数量">
                        <el-input-number class="width-200" :controls="false"
                          v-model="mmdetParameter.FFN_linear_layer_num" :disabled="isSee"></el-input-number>
                      </el-form-item>
                      <el-form-item label="激活函数" required>
                        <el-select v-model="mmdetParameter.FFN_active_func" class="width-200" :disabled="isSee">
                          <el-option v-for="item in activationFuncList" :key="item" :value="item "
                            :label="item"></el-option>
                        </el-select>
                      </el-form-item>
                    </el-col>
                  </el-row>
                </div>

                <div class="config-text">位置编码</div>
                <div class="sub-param-box">
                  <el-form-item label="温度参数">
                    <el-input-number class="width-200" :controls="false" v-model="mmdetParameter.temperature"
                      :disabled="isSee"></el-input-number>
                  </el-form-item>
                </div>

                <div class="config-text">检测头</div>
                <div class="sub-param-box">
                  <el-row>
                    <el-col :span="12">
                      <el-form-item label="分类损失" required>
                        <el-select v-model="mmdetParameter.loss_cls" class="width-200" :disabled="isSee">
                          <el-option v-for="item in clsLossList" :key="item" :value="item " :label="item"></el-option>
                        </el-select>
                      </el-form-item>
                      <el-form-item label="边界框损失" required>
                        <el-select v-model="mmdetParameter.loss_bbox" class="width-200" :disabled="isSee">
                          <el-option v-for="item in bboxLossList" :key="item" :value="item " :label="item"></el-option>
                        </el-select>
                      </el-form-item>
                      <el-form-item label="IoU损失" required>
                        <el-select v-model="mmdetParameter.loss_iou" class="width-200" :disabled="isSee">
                          <el-option v-for="item in iouLossList" :key="item" :value="item " :label="item"></el-option>
                        </el-select>
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="损失权重参数">
                        <el-input-number class="width-200" :controls="false" v-model="mmdetParameter.loss_cls_weight"
                          :disabled="isSee"></el-input-number>
                      </el-form-item>
                      <el-form-item label="损失权重参数">
                        <el-input-number class="width-200" :controls="false" v-model="mmdetParameter.loss_bbox_weight"
                          :disabled="isSee"></el-input-number>
                      </el-form-item>
                      <el-form-item label="损失权重参数">
                        <el-input-number class="width-200" :controls="false" v-model="mmdetParameter.loss_iou_weight"
                          :disabled="isSee"></el-input-number>
                      </el-form-item>
                    </el-col>
                  </el-row>
                </div>
              </div>
            </div>
          </el-col>

          <el-col :span="10">
            
            <div class="mmdet-file-block">
              <span class="mmdet-file-title">dataset.py (数据加载配置)</span>
              <el-form-item label="实例数据集" required>
                <div class="mmdet-instance-dataset-block">
                  <el-table
                    v-loading="instanceReadinessLoading"
                    :data="instanceReadinessList"
                    size="small"
                    class="mmdet-instance-readiness-table"
                    max-height="280"
                    empty-text="暂无实例数据集，请先在「数据集管理」中完成预处理"
                  >
                    <el-table-column width="44" align="center">
                      <template #default="{ row }">
                        <el-radio
                          class="mmdet-dataset-radio"
                          v-model="mmdetParameter.selected_dataset"
                          :label="row.name"
                          :disabled="isSee || !row.qualified"
                        />
                      </template>
                    </el-table-column>
                    <el-table-column prop="name" label="名称" min-width="96" show-overflow-tooltip />
                    <el-table-column prop="fatherName" label="父任务" min-width="88" show-overflow-tooltip>
                      <template #default="{ row }">
                        <span>{{ row.fatherName || '—' }}</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="状态" width="72" align="center">
                      <template #default="{ row }">
                        <el-tag v-if="row.qualified" type="success" size="small">可选</el-tag>
                        <el-tag v-else type="info" size="small">不可用</el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column label="说明" min-width="140">
                      <template #default="{ row }">
                        <span v-if="row.qualified" class="mmdet-readiness-ok">满足训前条件</span>
                        <el-tooltip v-else :content="(row.reasons || []).join('；')" placement="top">
                          <span class="mmdet-readiness-reason">{{ (row.reasons || []).join('；') }}</span>
                        </el-tooltip>
                      </template>
                    </el-table-column>
                    <el-table-column label="操作" width="88" align="center">
                      <template #default="{ row }">
                        <el-button
                          v-if="!row.qualified"
                          type="primary"
                          link
                          size="small"
                          @click="goInstanceDatasetFix(row)"
                        >去处理</el-button>
                        <span v-else class="mmdet-readiness-dash">—</span>
                      </template>
                    </el-table-column>
                  </el-table>
                  <div
                    v-if="!instanceReadinessLoading && !qualifiedMmdetDatasetCount && !isSee"
                    class="el-form-item__tip mmdet-readiness-tip"
                  >
                    当前没有可参与训练的实例数据集。请确认预处理已落盘、训练/测试目录有效，并在「实例数据集」中完成训测划分。
                  </div>
                </div>
              </el-form-item>

              <el-form-item label="图片尺寸" required>
                <el-input v-model="mmdetParameter.photo_width" style="width: 150px;" :disabled="isSee">
                  <template #prepend>长边></template>
                </el-input>
                <div style="margin: 0 8px;">×</div>
                <el-input v-model="mmdetParameter.photo_height" style="width: 150px;" :disabled="isSee">
                  <template #prepend>短边></template>
                </el-input>
              </el-form-item>

              <el-form-item label="批大小(batch_size)" required>
                <el-input-number class="width-200" :controls="false" v-model="mmdetParameter.train_bath_size"
                  :disabled="isSee"></el-input-number>
              </el-form-item>
            </div>

            <div class="mmdet-file-block">
              <span class="mmdet-file-title">schedule.py (训练策略配置)</span>
              <el-form-item label="优化器" required>
                <el-select v-model="mmdetParameter.optimizer" class="width-200" :disabled="isSee">
                  <el-option v-for="item in optimizerList" :key="item" :value="item" :label="item"></el-option>
                </el-select>
              </el-form-item>

              <el-form-item label="初始学习率" required>
                <el-input v-model="mmdetParameter.stu_rate" class="width-200" :disabled="isSee"></el-input>
              </el-form-item>

              <el-form-item label="训练轮次(epoch)" required>
                <el-input-number class="width-200" :controls="false" v-model="mmdetParameter.train_epoch"
                  :disabled="isSee"></el-input-number>
              </el-form-item>

              <el-form-item label="学习率下降轮次" required>
                <el-input-number v-model="mmdetParameter.down_round" class="width-200" :controls="false"
                  :disabled="isSee"></el-input-number>
              </el-form-item>
            </div>

            <div class="mmdet-file-block">
              <span class="mmdet-file-title">default_runtime.py (运行环境配置)</span>
              <el-form-item label="权值保存轮次间隔" required>
                <el-input v-model="mmdetParameter.weight_round" class="width-200" :disabled="isSee"></el-input>
              </el-form-item>

              <el-form-item label="验证集评测轮次间隔" required>
                <el-input v-model="mmdetParameter.valid_round" class="width-200" :disabled="isSee"></el-input>
              </el-form-item>
            </div>

          </el-col>
        </el-row>
      </div>

    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="addVisible = false">关闭</el-button>
        <el-button @click="handleSave" :disabled="addForm.status == 3 || loading_saveRecord"
          v-loading="loading_saveRecord" v-if="!isSee" type="primary">确定</el-button>
      </span>
    </template>
  </el-dialog>
  <el-dialog top="1vh" v-model="dataVisible" width="70%" title="查看" draggable :close-on-click-modal="false"
    @close="base_path = null">
    <div class="fileViewDialog">
      <fileview :trainTaskRow="trainTaskRow" :base_type="base_type" :base_uri="base_uri" :base_path="base_path"
        v-if="base_path" :key="base_uri">
      </fileview>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="dataVisible = false">关闭</el-button>
      </span>
    </template>
  </el-dialog>
  <el-drawer v-model="drawer" title="配置文件列表" size="50%">
    <div class="content-div">
      <div class="search-div flex-between">
        <div class="flex-start">
          <el-select v-model="searchType_yolo" placeholder="选择配置类型" size="small" class="width-200" disabled>
            <el-option label="权重文件" value="weights"></el-option>
            <el-option label="模型配置" value="cfg"></el-option>
            <el-option label="超参配置" value="hyp"></el-option>
          </el-select>
          <el-input v-model="searchId_yolo" placeholder="id查询" size="small" class="width-150">
          </el-input>
          <el-input v-model="searchName_yolo" placeholder="名称查询" size="small">
          </el-input>
          <el-button @click="queryYoloFiles" size="small"><el-icon><i
                class="iconfont icon-sousuo"></i></el-icon></el-button>
        </div>
      </div>
      <div class="table-div">
        <el-table class="my-table" ref="yolo_table_ref" :data="tableData_yolo" stripe style="width: 100%" size="small">
          <el-table-column prop="id" label="状态" align="center" width="50">
            <template #default="scope">
              <el-text class="iconfont icon-zhengchang" type="success" v-show="curFileId == scope.row.id"></el-text>
            </template>
          </el-table-column>
          <el-table-column prop="id" label="id" align="center" width="50">
            <template #default="scope">
              <el-text size="small">#{{ scope.row.id }}</el-text>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="名称" align="center" />
          <el-table-column prop="created_date" label="创建时间" align="center">
            <template #default="scope">
              <el-text size="small">{{ showDate(scope.row.created_date) }}</el-text>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" align="center" show-overflow-tooltip />

          <el-table-column label="操作" align="center" fixed="right" width="100">
            <template #default="scope">
              <el-space>
                <el-button link size="small" @click="selectCurrentFile(scope.row)" v-if="!isSee">
                  <span class="btnSpan">选中</span>
                </el-button>
                <el-button link size="small" @click="viewFile(scope.row)">
                  <span class="btnSpan">查看</span>
                </el-button>
              </el-space>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div class="flex-end">
        <el-pagination background size="small" v-model:current-page="currentPage_yolo"
          v-model:page-size="currentSize_yolo" :page-sizes="[5, 10, 20, 30, 40, 50]"
          layout="total, sizes, prev, pager, next, jumper" :total="total_yolo" @size-change="queryYoloFiles"
          @current-change="queryYoloFiles" />
      </div>
    </div>
  </el-drawer>

  <el-dialog v-model="txtVisible" width="60%" top="5vh" title="查看" draggable :close-on-click-modal="true"
    :destroy-on-close="true">
    <pre class="preBox">{{ cur_text }}</pre>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="txtVisible = false">关闭</el-button>
      </span>
    </template>
  </el-dialog>

  <el-dialog top="1vh" v-model="transVisible" width="60%" :title="'创建模型转换任务[#' + transForm.id + ']'" draggable
    :close-on-click-modal="false">
    <div class="flex-end" v-show="transForm.trans_name"> <el-button @click="goToTransTask(transForm.name)" link
        size="small"><el-tag size="small" type="success">跳转关联转换任务</el-tag></el-button></div>
    <el-form label-position="left" label-width="auto">
      <el-row :gutter="22">
        <el-col :span="12">
          <el-form-item label="任务名称" required>
            <el-input v-model="transForm.name" disabled></el-input>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="22">
        <el-col :span="12">
          <el-form-item label="源模型(weights)" required>
            <el-input v-model="transForm.weights" disabled></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="模型配置(data)" required>
            <el-input v-model="transForm.data" disabled></el-input>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="22">
        <el-col :span="12">
          <el-form-item label="转换算法" required>
            <el-select v-model="transForm.type" style="width: 100%;">
              <el-option v-for="(item, id) in transAlgList" :key="id" :value="item.name"></el-option>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <!-- 模型转换算法模板 -->
          <el-form-item label="算法模板">
            <el-select v-model="templateAlgorithm" class="width-150" style="width:calc(100% - 30px);"
              @change="(params) => { changeTemplate('trans', params) }" value-key="id">
              <el-option v-for="item in templateAlgorithmList" :key="item.id" :value="item"
                :label="item.name"></el-option>
            </el-select>
            <el-tooltip content="选择算法模板将'一键式'填写【其他参数】" placement="top">
              <el-icon class="iconfont icon-bangzhu" style="margin-left: 10px;"></el-icon>
            </el-tooltip>
          </el-form-item>
        </el-col>
      </el-row>
      <el-divider><el-text size="small">其他参数</el-text></el-divider>
      <div v-if="transForm.type == 'pt2onnx'">
        <el-form-item label="图像尺寸(imgsz)" required>
          <el-input-number v-model="transForm.params.imgsz" style="width: 120px;" :controls="false"
            :min="1"></el-input-number>
        </el-form-item>
      </div>
      <div v-else>
        <div class="flex-between">
          <el-form-item label="模型类型(type)" required>
            <el-select v-model="transForm.params.type" style="width: 120px;">
              <el-option value="5s"></el-option>
              <el-option value="5m"></el-option>
              <el-option value="5l"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="图片类型(chn)" required>
            <el-select v-model="transForm.params.chn" style="width: 120px;">
              <el-option value="vis"></el-option>
              <el-option value="inf"></el-option>
            </el-select>
          </el-form-item>
        </div>
        <div class="flex-between">
          <el-form-item label="模型宽(model_w)" required>
            <el-input-number v-model="transForm.params.model_w" style="width: 120px;" :controls="false"
              :min="1"></el-input-number>
          </el-form-item>
          <el-form-item label="模型高(model_h)" required>
            <el-input-number v-model="transForm.params.model_h" style="width: 120px;" :controls="false"
              :min="1"></el-input-number>
          </el-form-item>
        </div>
        <div class="flex-between">
          <el-form-item label="转换日期(date)" required>
            <el-date-picker v-model="transForm.params.date" value-format="YYYYMMDD"
              style="width: 120px;"></el-date-picker>
          </el-form-item>
          <el-form-item label="名称量化(quantise)" required>
            <el-switch v-model="transForm.params.quantise" :active-value="1" :inactive-value="0" style="width: 120px;"
              active-text="开" inactive-text="关" inline-prompt></el-switch>
          </el-form-item>
        </div>
        <div class="flex-between">
          <el-form-item label="基础校验集(check)" required>
            <el-select v-model="transForm.params.base_check" style="width: 120px;">
              <!-- <el-option :value="'auto'" label="自动"></el-option> -->
              <el-option v-for="(item, id) in checkList" :key="id" :value="item"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="基础验证集(val)" required>
            <el-select v-model="transForm.params.base_val" style="width: 120px;">
              <!-- <el-option :value="'auto'" label="自动"></el-option> -->
              <el-option v-for="(item, id) in valList" :key="id" :value="item"></el-option>
            </el-select>
          </el-form-item>
        </div>
      </div>
      <el-form-item label="&nbsp;&nbsp;备注说明">
        <el-input v-model="transForm.remark" type="textarea"></el-input>
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="transVisible = false">关闭</el-button>
        <el-button @click="addTransTask" type="primary">确定</el-button>
      </span>
    </template>
  </el-dialog>

  <el-dialog top="1vh" v-model="valVisible" width="98%" :close-on-press-escape="false"
    :title="valForm.is_val ? '模型验证' : '模型预测'" draggable :close-on-click-modal="false">
    <el-form size="small" label-width="130" label-position="left">
      <el-form-item label="训练任务名称" required>
        <el-text>{{ "#" + valForm.id + "[" + valForm.name + "]" }}</el-text>
      </el-form-item>
      <el-form-item label="模型标签" required>
        <el-space wrap>
          <el-tag v-for="(item, id) in valForm.labels" :key="id" :type="item.merge ? 'warning' : 'primary'">
            {{ id }}.{{ item.name }}{{ item.merge ? (' [' + item.children) + ']' : '' }}
          </el-tag>
        </el-space>
      </el-form-item>

      <el-tabs v-model="activeValTab" class="tabs-div">
        <el-tab-pane label="样本配置" name="val-tab">
          <div class="pane-div">
            <el-container>
              <el-aside class="aside-container not-select">
                <div style="padding-bottom: 8px;">
                  <el-space :size="1">
                    <el-select v-model="filter_l_val" size="small" style="width:65px;" placeholder="光源"   clearable  multiple  collapse-tags  collapse-tags-tooltip>
                      <el-option v-for="(item, id) in a_l_list" :key="id" :value="item"></el-option>
                    </el-select>
                    <el-select v-model="filter_s_val" size="small" style="width: 65px;" placeholder="来源" clearable  multiple  collapse-tags  collapse-tags-tooltip>
                      <el-option v-for="(item, id) in a_s_list" :key="id" :value="item"></el-option>
                    </el-select>
                    <el-select v-model="filter_g_val" size="small" style="width: 65px;" placeholder="用途" clearable  multiple  collapse-tags  collapse-tags-tooltip>
                      <el-option v-for="(item, id) in a_g_list" :key="id" :value="item"></el-option>
                    </el-select>
                    <el-input placeholder="名称" v-model="filter_n_val"></el-input>
                  </el-space>
                </div>
                <div v-for="(item) in filter_project_val" :key="item.id" v-show="item.show"
                  style="padding-bottom: 10px;">
                  <el-badge :value="item.badge" :type="item.badge_type">
                    <el-link @click="showTask_val(item)" underline="never">
                      <el-space :size="1">
                        <el-tag style="width: 65px;height: 25px;" type="info">
                          <el-text truncated>{{ item.a_l }}</el-text>
                        </el-tag>
                        <el-tag style="width: 65px;height: 25px;" type="info">
                          <el-text truncated>{{ item.a_s }}</el-text>
                        </el-tag>
                        <el-tag style="width: 65px;height: 25px;" type="info">
                          <el-text truncated>{{ item.a_g }}</el-text>
                        </el-tag>
                        <el-tag style="width: 102px;height: 25px;"
                          :type="cur_project_val.project_id == item.project_id ? 'danger' : 'info'">
                          <el-text truncated style="width: 92px;"> {{ item.a_n }}</el-text>
                        </el-tag>
                      </el-space>
                    </el-link>
                  </el-badge>
                </div>
              </el-aside>
              <el-container>
                <el-header class="header-container">
                  <el-space wrap :size="0">
                    <el-select v-model="searchS_val" size="small" style="width: 100px;" placeholder="厂家"   clearable  multiple  collapse-tags  collapse-tags-tooltip>
                      <el-option v-for="(item, id) in cur_f_list_val.s" :key="id" :value="item"></el-option>
                    </el-select>
                    <el-select v-model="searchR_val" size="small" style="width: 100px;" placeholder="分辨率"  clearable  multiple  collapse-tags  collapse-tags-tooltip>
                      <el-option v-for="(item, id) in cur_f_list_val.r" :key="id" :value="item"></el-option>
                    </el-select>
                    <el-select v-model="searchV_val" size="small" style="width: 100px;" placeholder="场地"  clearable  multiple  collapse-tags  collapse-tags-tooltip>
                      <el-option v-for="(item, id) in cur_f_list_val.v" :key="id" :value="item"></el-option>
                    </el-select>
                    <el-select v-model="searchP_val" size="small" style="width: 100px;" placeholder="视角"  clearable  multiple  collapse-tags  collapse-tags-tooltip>
                      <el-option v-for="(item, id) in cur_f_list_val.p" :key="id" :value="item"
                        :label="perspectiveMap.get(item) || item"></el-option>
                    </el-select>
                    <el-select v-model="searchA_val" size="small" style="width: 100px;" placeholder="精度"  clearable  multiple  collapse-tags  collapse-tags-tooltip>
                      <el-option v-for="(item, id) in cur_f_list_val.a" :key="id" :value="item"></el-option>
                    </el-select>
                    <el-select v-model="searchE_val" size="small" style="width: 100px;" placeholder="场景"  clearable  multiple  collapse-tags  collapse-tags-tooltip>
                      <el-option key="-1" value="" label="全部"></el-option>
                      <el-option v-for="(item, id) in cur_f_list_val.e" :key="id" :value="item"></el-option>
                    </el-select>
                    <el-input v-model="searchN_val" placeholder="名称" size="small" style="max-width: 100px;">
                    </el-input>

                    <el-date-picker size="small" v-model="searchObtainRange_val" type="daterange"
                      start-placeholder="获得时间起:" value-format="YYYYMMDD" format="YYYY-MM-DD" style="max-width: 180px;"
                      end-placeholder="止:" :editable="false"></el-date-picker>
                    <el-button size="small"><el-icon class="iconfont icon-sousuo"></el-icon></el-button>
                    <el-checkbox v-model="checkAll_filter_val" :indeterminate="isIndeterminate_filter_val" label="全选"
                      style="padding: 8px;" @change="handleCheckAllChange_filter_val">
                    </el-checkbox>
                  </el-space>
                </el-header>
                <el-main class="main-container-val" v-show="cur_project_val.show">
                  <div class="main-div">
                    <div style="margin-bottom: 5px;">
                      <el-space wrap>
                        <el-tag v-for="(item, id) in (cur_project_val.labels || [])" :key="id">{{ item }}</el-tag>
                      </el-space>
                    </div>
                    <el-table :data="taskTable_val" stripe style="width: 100%" size="small">
                      <el-table-column width="50" fixed="left">
                        <template #header>
                          <el-checkbox v-model="checkAll_val" :indeterminate="isIndeterminate_val"
                            @change="handleCheckAllChange_val">
                          </el-checkbox>
                        </template>
                        <template #default="{ row }">
                          <el-checkbox v-model="row.is_select" @change="handleCheckOne_val(row)">
                          </el-checkbox>
                        </template>
                      </el-table-column>
                      <el-table-column prop="id" label="任务id" align="center" fixed="left">
                        <template #default="scope">
                          <el-text size="small">#{{ scope.row.id }}</el-text>
                          <el-text class="iconfont icon-yishanchu" type="danger" v-if="scope.row.del_flg"></el-text>
                        </template>
                      </el-table-column>
                      <el-table-column prop="a_n" label="名称" align="center" fixed="left" />
                      <el-table-column prop="size" label="样本数" align="center" fixed="left" />
                      <el-table-column label="预览图" align="center" fixed="left" v-if="!encode_src">
                        <template #default="{ row }">
                          <authimg v-if="row.first_img" style="height: 30px;" :bm="!isSys && row.a_se == 'p'"
                            :url="basePath_TASK + row.project_id + '/' + row.id + '/images/' + row.first_img"></authimg>
                        </template>
                      </el-table-column>
                      <el-table-column prop="created_date" label="创建时间" align="center">
                        <template #default="scope">
                          <el-text size="small">{{ showDate(scope.row.created_date) }}</el-text>
                        </template>
                      </el-table-column>
                      <el-table-column prop="a_s" label="厂家" align="center" />
                      <el-table-column prop="a_r" label="分辨率" align="center" />
                      <el-table-column prop="a_t" label="时间" align="center" />
                      <el-table-column prop="a_v" label="场地" align="center" />
                      <el-table-column prop="a_p" label="视角" align="center">
                        <template #default="{ row }">
                          {{ perspectiveMap.get(row.a_p) || row.a_p }}
                        </template>
                      </el-table-column>
                      <el-table-column prop="a_a" label="精度" align="center" />
                      <el-table-column prop="a_e" label="场景" align="center" />
                      <el-table-column prop="export_img" label="样本集" align="center" fixed="right" v-if="!encode_src">
                        <template #default="{ row }">
                          <el-link :disabled="!isSys && row.a_se == 'p'" @click="showDataset(row)" underline="never">
                            <el-tag size="small" type="success">查看</el-tag>
                          </el-link>
                        </template>
                      </el-table-column>
                    </el-table>
                    <div class="flex-end" v-if="cur_project_val.tasks">
                      <el-pagination background size="small" v-model:current-page="currentPage2_val"
                        v-model:page-size="currentSize2_val" :page-sizes="[5, 10, 20, 30, 40, 50]"
                        layout="total, sizes, prev, pager, next, jumper" :total="filterTable_val.length"
                        @current-change="" @size-change="" />
                    </div>
                  </div>
                </el-main>
              </el-container>
            </el-container>
          </div>
        </el-tab-pane>
        <el-tab-pane label="参数配置" name="args-tab">
          <div class="pane-div">
            <el-form-item label="待预测文件(source)" required v-if="!valForm.is_val">
              <el-space>
                <el-upload v-model:file-list="srcFileList" ref="uploadSrcRef" action="" :limit="1"
                  :on-exceed="handleSrcExceed" :auto-upload="false">
                  <template #trigger>
                    <el-button type="primary">选择文件</el-button>
                  </template>
                </el-upload>
                <el-text>说明:1.样本配置+上传的预测文件合并构成待预测的资源;2.只允许上传单个文件,需要上传多个,请压缩成zip包后上传;</el-text>
              </el-space>
            </el-form-item>
            <el-form-item label="权重文件(weights)" required>
              <el-select v-model="valForm.weights" style="width: 120px;" filterable>
                <el-option v-for="item in weightsList" :key="item" :value="item"></el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="批大小(batch_size)" required v-if="valForm.is_val">
              <el-input-number :controls="false" v-model="valForm.batch_size"></el-input-number>
            </el-form-item>
            <el-form-item label="图尺寸(img_size)" required>
              <el-space>
                <el-input-number :controls="false" v-model="valForm.img_size"></el-input-number>
              </el-space>
            </el-form-item>
            <el-form-item label="置信度(conf_thres)" required>
              <el-space>
                <el-input-number :controls="false" v-model="valForm.conf_thres"></el-input-number>
              </el-space>
            </el-form-item>
            <el-form-item label="生成标签(save_txt)" required v-if="!valForm.is_val">
              <el-radio-group v-model="valForm.save_txt">
                <el-radio :value="true">生成</el-radio>
                <el-radio :value="false">不生成</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="硬件(device)" required>
              <el-space>
                <el-switch inactive-text="使用CPU" active-text="使用GPU" v-model="valForm.use_gpu" inline-prompt
                  style="--el-switch-on-color: #409eff; --el-switch-off-color: #e6a23c"></el-switch>
                <el-space v-show="valForm.use_gpu" spacer="|">
                  <el-checkbox-group v-model="valForm.gpu_list">
                    <el-checkbox v-for="gpu in gpu_list" :key="gpu" :label="gpu" :value="gpu">
                    </el-checkbox>
                  </el-checkbox-group>
                  <el-checkbox v-model="checkAll_gpu" :indeterminate="isIndeterminate_gpu"
                    @change="handleCheckAllChange_gpu">
                    全选
                  </el-checkbox>
                </el-space>
              </el-space>
            </el-form-item>
            <el-form-item label="拓展参数(json格式)" prop="ext_params">
              <div style="height:200px;width:50%">
                <aceEdit ref='aceEditRef3' v-model="valForm.ext_params" :height="200" />
              </div>
            </el-form-item>
          </div>
        </el-tab-pane>
      </el-tabs>
      

    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="valVisible = false">关闭</el-button>
        <el-button @click="startValModel" type="primary" :loading="valLoading">{{ valForm.is_val ? '点击开始验证' : '点击开始预测'
        }}</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup>
import { VueDraggable } from 'vue-draggable-plus'
import { computed, reactive, ref, toRaw, watch } from "@vue/reactivity";
import { useRouter } from "vue-router";
import { useUserStore } from "../../stores/index";
import { FileService, EngineProjectService, EngineTaskService, TrainLabelService, TrainTaskService, TrainScriptService, TrainYoloService, ApiService, ModelTransService, trainService, transService , InstanceDatasetService } from "../../api/api";
import { ElMessage, dayjs, ElMessageBox, ElMain, genFileId } from "element-plus";
import { nextTick, onBeforeUnmount, onMounted, watchEffect } from "@vue/runtime-core";
import { basePath_TASK, basePath_YOLO, basePath_TRAIN, basePath_WS_TASK, nginx_tensorboard, apiRequest } from "../../api/axios";
import fileview from "../../components/fileview.vue";
import { isNum } from "../../utils/regex";
import { taskStatusMap, taskStatusList, perspectiveMap } from '../../utils/selfmaps'
import { getTimeDif } from '../../utils/time'
import { uuid } from 'vue-uuid'
import authimg from '../../components/authimg.vue'
import logview from '../../components/logger.vue'
import AceEdit from '@/components/AceEdit/index.vue'
import { showRemark } from "../../utils/str";
import { add } from 'lodash';
const encode_src = ref(false);
ApiService.getSysInfo().then(res => {
  if (res.code === 0) {
    encode_src.value = res.data.encode_src;
  }
})

//查看任务 
const isSee = ref(false)


//虚拟下拉框
const selectV2Props = {
  label: 'name',
  value: 'id',
}
const selectV2CheckAll = ref(false)
const selectV2Indeterminate = ref(false)


//拓展参数 文件
const extFileList = ref([])


// 权重文件
const weightFileList = ref([])
// 实例数据集训前检查（全量列表；合格行可选，不合格展示原因与「去处理」）
const instanceReadinessList = ref([])
const instanceReadinessLoading = ref(false)
const qualifiedMmdetDatasetCount = computed(() => instanceReadinessList.value.filter((r) => r.qualified).length)
// 网络模板名称
const netTemplateName=ref(["Faster R-CNN","Cascade R-CNN","DetectoRS"])
// 网络模板名称-DETR
const netTemplateName_DETR=ref(["DETR","Deformable DETR","DINO"])
// 主干网
const backboneNetwork=ref(["ResNet","ConvNext","SwinTransformer"])

// 优化器
const optimizerList = ref(["AdamW","SGD"])

// 规模选择
const scaleList = ref(["tiny","small","base","large"])

// 尺度
const measureList = ref([{
  label:"单尺度",
  value:"single"

},{
  label:"多尺度",
  value:"multi"
}])

// 激活函数
const activationFuncList = ref(["ReLU"])

// 分类损失
const clsLossList = ref(["FocalLoss","CrossEntropyLoss"])

// 边界框损失
const bboxLossList = ref(["L1Loss","SmoothL1Loss","BalancedL1Loss"])

// IoU损失
const iouLossList = ref(["GIoULoss","IoULoss","CIoULoss","DIoULoss"])

// 深度选择
const deepList = ref([50,101])


const aceEditRef = ref()
const aceEditRef2 = ref()
const props = defineProps({
  msg: Number,
});
const isSys = useUserStore().user.type == 1;
const curUser = useUserStore().user.username;
const router = useRouter();
const searchName = ref("");
const searchStatus = ref(-1);
const searchUser = ref('');
const userList = ref([]);
const searchCreateRange = ref()
const currentPage = ref(1);
const currentSize = ref(10);
const total = ref(0);
const tableData = ref([]);
const showExt = ref(false)

const runnerHealthOk = ref(null)
const runnerHealthDetail = ref('正在检测 Runner 服务…')
let runnerHealthTimer = null
const refreshRunnerHealth = async () => {
  try {
    const res = await TrainTaskService.runnerHealth({})
    if (res.code === 0 && res.data) {
      const d = res.data
      runnerHealthOk.value = !!d.ok
      const bits = []
      if (d.healthUrl) bits.push(d.healthUrl)
      if (d.latencyMs != null) bits.push(`延迟 ${d.latencyMs} ms`)
      if (d.httpStatus != null) bits.push(`HTTP ${d.httpStatus}`)
      if (d.bodyPreview) bits.push(d.bodyPreview)
      if (d.error) bits.push(d.error)
      runnerHealthDetail.value = d.ok
        ? `Runner 正常，MMDet 训练可发起。${bits.length ? ' ' + bits.join(' · ') : ''} 若仍失败请手动执行 mmdet_run/mmdet_runner_srv/start_runner.sh 或检查 8009 端口。`
        : `Runner 不可用，发布 mmdet 任务将很快失败。${d.error ? ' ' + d.error : bits.length ? ' ' + bits.join(' · ') : ' 请启动 Runner（IDE 可设 RUNNER_AUTO_START=true）或执行 start_runner.sh；停止后端时 Runner 默认保留。'}`
    } else {
      runnerHealthOk.value = false
      runnerHealthDetail.value = (res && res.msg) ? res.msg : '健康检查接口返回异常'
    }
  } catch (e) {
    runnerHealthOk.value = false
    runnerHealthDetail.value = '请求健康检查失败：' + (e && e.message ? e.message : String(e))
  }
}

const handleCurrentChange = () => {
  let time_range = searchCreateRange.value
  let start_time = null
  let end_time = null
  if (time_range && time_range.length == 2) {
    start_time = time_range[0]
    end_time = time_range[1]
  }
  TrainTaskService.queryList({
    current: currentPage.value,
    size: currentSize.value,
    status: searchStatus.value,
    name: searchName.value,
    start_time: start_time,
    end_time: end_time,
    username: searchUser.value || null,
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
const default_mmdet_header = `# ~~~~~~~默认头部,注入训练相关环境变量~~~~~~~~~~~~~
import os

# 训练任务根路径
APP_TRAIN_DIR = os.getenv("APP_TRAIN_DIR") + os.getenv("TRAIN_TASK_ID")
# batch-size
mmdet_batch_size = int(os.getenv("MMDET_BATCH_SIZE"), base=10)
# 图片尺寸
mmdet_image_width = int(os.getenv("MMDET_IMAGE_WIDTH"), base=10)
mmdet_image_height = int(os.getenv("MMDET_IMAGE_HEIGHT"), base=10)
# 训练轮次
mmdet_epoch = int(os.getenv("MMDET_EPOCH"), base=10)
# 保存策略
mmdet_save_period = int(os.getenv("MMDET_SAVE_PERIOD"), base=10)
# 标签类别
mmdet_classes = os.getenv("MMDET_CLASSES").split(",")
# 标签数目
mmdet_num_classes = len(mmdet_classes)
# 图片根目录
mmdet_data_root = os.getenv("APP_TASK_DIR")
# 初始权重文件
mmdet_weights = os.getenv("MMDET_WEIGHTS")
# 训练集标签路径
mmdet_ann_file_train = APP_TRAIN_DIR + "/labels/train.json"
# 验证集标签路径
mmdet_ann_file_val = APP_TRAIN_DIR + "/labels/val.json"
# 设备环境
mmdet_device = os.getenv("MMDET_DEVICE")
gpus = []
device = "cpu"
if mmdet_device != "cpu":
    gpus = list(map(int, mmdet_device.split(",")))
    device = "cuda"
# 结果输出目录
work_dir = os.getenv("MMDET_WORK_DIR")
# ~~~~~~~~默认头部结束,后续内容自定义,注意使用上述变量~~~~~~~~~~~~~~~~
`
const addForm = reactive({
  type: null,
  temp: null,
  yolo_weights_id: null,
  yolo_cfg_id: null,
  yolo_hyp_id: null,
  train_gpu_list: [],
  train_use_gpu: true,
  train_bath_size: 640,
  train_img_size: 1024,
  train_img_w: 1020,
  train_img_h: 1024,
  train_epoch: 100,
  train_period: -1,
  f_max: 0,
  f_min: 0,
  f_area: 0,
  remark: null,
  //拓展参数
  ext_params: "",
  ext_file_update: false,
  ext_file: null,
  mmdet_cfg: default_mmdet_header,
})
const addVisible = ref(false)
const activeTab = ref('data-tab')
const is_create = ref(false)   // 创建/编辑
const is_add = ref(false) // 追加模式
const cur_task_id = ref(0);
const algList = ref([])
const templateList = ref(["CNN","DETR"])
const algMap = ref(Map);
const cur_type = computed(() => {
  return algMap.value.get(addForm.type)?.name;
})
const cur_cmd = computed(() => {
  return algMap.value.get(addForm.type)?.cmd;
})
const queryAlgs = () => {
  TrainScriptService.queryAll({ type: 'train' }).then(res => {
    if (res.code === 0) {
      algList.value = res.data
      let map = new Map;
      res.data.forEach(item => {
        map.set(item.id + '', item)
      })
      algMap.value = map;
    }
  })
}

/**新建模式 */
const showAddModal = () => {
  isSee.value = false
  //算法模版初始化
  templateAlgorithm.value = null
  templateAlgorithmList.value = []
  addForm.type = null
  addForm.temp = null
  addForm.name = formatQuickTaskName()
  addForm.children = null
  addForm.status = 0
  addForm.f_max = 0
  addForm.f_min = 0
  addForm.f_area = 0
  addForm.remark = null
  addForm.clone_from = null;
  addForm.use_self_weights = false
  addForm.old_weights = null
  addVisible.value = true
  is_create.value = true
  is_add.value = false
  cur_task_id.value = 0
  //拓展参数
  addForm.ext_params = ""
  addForm.ext_file_update = false
  addForm.ext_file = null

  applyMmdetCnnQuickDefaults()
  mmdetParameter.selected_dataset = null
  mmdetParameter.use_custom_pretrained = false
  mmdetParameter.pretrained_address = ''
  mmdetParameter.embedding_dimension = 256
  mmdetParameter.encoder_layers = 6
  mmdetParameter.decoder_layers = 6
  mmdetParameter.attention_num = 8
  mmdetParameter.attention_discard_rate = 0.1
  mmdetParameter.FFN_intermediate_layer_dimension = 2048
  mmdetParameter.FFN_linear_layer_num = 2
  mmdetParameter.FFN_discard_rate = 0.1
  mmdetParameter.temperature = 20
  mmdetParameter.loss_cls_weight = 1
  mmdetParameter.loss_bbox_weight = 5
  mmdetParameter.loss_iou_weight = 2

  weightFileList.value = []

  addForm.mmdet_cfg = default_mmdet_header;

  const mmdetAlg = algList.value.find((item) => item.name === 'mmdet')
  const firstAlg = algList.value[0]
  addForm.type = mmdetAlg ? String(mmdetAlg.id) : (firstAlg ? String(firstAlg.id) : null)

  nextTick(() => {
    if (isMMDetSelected.value) {
      addForm.temp = 'CNN'
      fetchMmdetInstanceDatasets()
    } else {
      addForm.temp = null
      addForm.labels = label_list.value.length ? [label_list.value[0].id] : []
      addForm.train_bath_size = 16
      addForm.train_img_size = 640
      addForm.train_epoch = 100
      addForm.train_period = 1
      addForm.train_use_gpu = true
      addForm.train_gpu_list = [0]
    }
  })
}
/**编辑模式 */
const showEditModal = (row) => {
  isSee.value = row.run_name   //设置是否编辑
  //算法模版初始化
  templateAlgorithm.value = null
  templateAlgorithmList.value = []
  is_create.value = false;
  is_add.value = false;
  cur_task_id.value = row.id;
  addForm.name = row.name
  addForm.type = row.type
  addForm.status = row.status;
  addForm.remark = row.remark;
  addForm.clone_from = null;
  //查询train_label信息
  TrainTaskService.queryArgs({ id: row.id }).then(res => {
    if (res.code === 0) {
      let args = res.data
      addForm.yolo_weights_id = args.weights;
      addForm.old_weights = args.weights;//修改之前的
      addForm.use_self_weights = args.weights === 0
      addForm.yolo_cfg_id = args.cfg
      addForm.yolo_hyp_id = args.hyp
      addForm.train_bath_size = args.batch_size
      addForm.train_img_size = args.img_size
      addForm.train_img_w = args.img_w
      addForm.train_img_h = args.img_h
      addForm.train_epoch = args.epoch
      addForm.train_period = args.period
      //过滤
      addForm.f_max = args.f_max || 0
      addForm.f_min = args.f_min || 0
      addForm.f_area = args.f_area || 0

      let device = args.device;
      if (device) {
        if (device == 'cpu') {
          addForm.train_use_gpu = false
          addForm.train_gpu_list = []
        } else {
          addForm.train_use_gpu = true
          addForm.train_gpu_list =
            device.split(',').map(i => parseInt(i))
        }
      }
    }
  })
  TrainTaskService.queryData({ id: row.id }).then(res => {
    if (res.code === 0) {
      let labels = JSON.parse(res.data.labels)
      addForm.labels = labels.map(item => item.id);

      /**训练集 */
      let data = JSON.parse(res.data.data)
      let map = new Map()
      data.forEach(el => {
        map.set(el.pid, new Set(el.tasks))
      });
      let pList = all_project.value
      pList.forEach((item, idx) => {
        let id = item.project_id
        let set = map.get(id);
        if (set) {
          let tasks = item.tasks
          if (tasks) {
            tasks.forEach(t => {
              t.is_select = set.has(t.id)
            })
            setBadge(pList[idx])
          } else {//网络请求
            EngineTaskService.queryAll({ project_id: id, export_img: 1, status: null }).then(res2 => {
              if (res2.code === 0) {
                let tasks2 = res2.data || []
                tasks2.forEach(t => {
                  t.is_select = set.has(t.id)
                })
                pList[idx].tasks = tasks2;
                setBadge(pList[idx])
              }
            })
          }
        } else {
          item.tasks?.forEach(t => {
            t.is_select = false
          })
          setBadge(pList[idx])
        }
      })
      /**验证集 */
      let data_val = JSON.parse(res.data.val)
      let map_val = new Map()
      data_val.forEach(el => {
        map_val.set(el.pid, new Set(el.tasks))
      });
      let pList_val = all_project_val.value
      pList_val.forEach((item, idx) => {
        let id = item.project_id
        let set = map_val.get(id);
        if (set) {
          let tasks = item.tasks
          if (tasks) {
            tasks.forEach(t => {
              t.is_select = set.has(t.id)
            })
            setBadge(pList_val[idx])
          } else {//网络请求
            EngineTaskService.queryAll({ project_id: id, export_img: 1, status: null }).then(res2 => {
              if (res2.code === 0) {
                let tasks2 = res2.data || []
                tasks2.forEach(t => {
                  t.is_select = set.has(t.id)
                })
                pList_val[idx].tasks = tasks2;
                setBadge(pList_val[idx])
              }
            })
          }
        } else {
          item.tasks?.forEach(t => {
            t.is_select = false
          })
          setBadge(pList_val[idx])
        }
      })
      onChangeLabel()
    }
  })
  TrainTaskService.getExtQuery({ id: row.id }).then(res => {
    if (res.code === 0) {
      addForm.ext_params = res.data?.params || ""
      addForm.ext_file_update = false
      extFileList.value = []
    }
  })
  // 回显mmdet_cfg
  if (algMap.value.get(row.type)?.cmd == 'mmdet') {
    FileService.getFile(basePath_TRAIN + '/' + row.id + "/file/cfg.py").then(res => {
      addForm.mmdet_cfg = res
    })
  } else {
    addForm.mmdet_cfg = default_mmdet_header;
  }
  //查询train_data信息
  //查询train_args信息
  addVisible.value = true
  nextTick(() => {
    if (algMap.value.get(row.type)?.cmd === 'mmdet') {
      fetchMmdetInstanceDatasets()
    }
  })
}
const showCloneModal = (row) => {
  isSee.value = false
  ElMessageBox.confirm( 
    "选择克隆模式: 追加模式下,任务标签不可修改,原有训练集不允许剔除",
    '',
    {
      distinguishCancelAndClose: true,
      confirmButtonText: '采用追加模式',
      cancelButtonText: '采用普通模式',
      type: 'warning',
    }
  )
    .then(() => {
      openCloneModal(row, true)
    })
    .catch((action) => {
      if (action === 'cancel') {
        openCloneModal(row, false)
      }
    })
}
//拷贝样本集
const fillList = (jsonstr, pList, is_train) => {
  let data = JSON.parse(jsonstr)
  let map = new Map()
  data.forEach(el => {
    map.set(el.pid, new Set(el.tasks))
  });
  pList.forEach((item, idx) => {
    let id = item.project_id
    let set = map.get(id);
    if (set) {
      let tasks = item.tasks
      if (tasks) {
        tasks.forEach(t => {
          if (set.has(t.id)) {
            t.is_select = true
            t.dis = is_train
          } else {
            t.is_select = false
            t.dis = false
          }
        })
        setBadge(pList[idx])
      } else {//网络请求
        EngineTaskService.queryAll({ project_id: id, export_img: 1, status: null }).then(res2 => {
          if (res2.code === 0) {
            let tasks2 = res2.data || []
            tasks2.forEach(t => {
              if (set.has(t.id)) {
                t.is_select = true
                t.dis = is_train
              } else {
                t.is_select = false
                t.dis = false
              }

            })
            pList[idx].tasks = tasks2;
            setBadge(pList[idx])
          }
        })
      }
    } else {
      item.tasks?.forEach(t => {
        t.is_select = false
        t.dis = false
      })
      setBadge(pList[idx])
    }
  })
}
/**追加模式 */
const openCloneModal = (row1, flg) => {
  //算法模版初始化
  templateAlgorithm.value = null
  templateAlgorithmList.value = []
  let row = JSON.parse(JSON.stringify(row1))
  is_create.value = true;
  is_add.value = flg;
  cur_task_id.value = false;
  addForm.name = '';
  addForm.type = row.type
  //查询train_label信息
  TrainTaskService.queryArgs({ id: row.id }).then(res => {
    if (res.code === 0) {
      let args = res.data
      addForm.yolo_weights_id = args.weights;
      addForm.old_weights = args.weights;//修改之前的
      addForm.use_self_weights = args.weights === 0
      addForm.clone_from = row.id; // clone的任务id
      addForm.yolo_cfg_id = args.cfg
      addForm.yolo_hyp_id = args.hyp
      addForm.train_bath_size = args.batch_size
      addForm.train_img_size = args.img_size
      addForm.train_img_w = args.img_w
      addForm.train_img_h = args.img_h
      addForm.train_epoch = args.epoch
      addForm.train_period = args.period
      //过滤
      addForm.f_max = args.f_max || 0;
      addForm.f_min = args.f_min || 0;
      addForm.f_area = args.f_area || 0;

      let device = args.device;
      if (device) {
        if (device == 'cpu') {
          addForm.train_use_gpu = false
          addForm.train_gpu_list = []
        } else {
          addForm.train_use_gpu = true
          addForm.train_gpu_list =
            device.split(',').map(i => parseInt(i))
        }
      }
    }
  })
  TrainTaskService.queryData({ id: row.id }).then(res => {
    if (res.code === 0) {
      let labels = JSON.parse(res.data.labels)
      addForm.labels = labels.map(item => item.id);
      //训练集
      fillList(res.data.data, all_project.value, true)
      //测试集
      fillList(res.data.val, all_project_val.value, false)
      onChangeLabel()
    }
  })
  //查询拓展参数配置
  TrainTaskService.getExtQuery({ id: row.id }).then(res => {
    if (res.code === 0) {
      addForm.ext_params = res.data?.params || ""
      addForm.ext_file_update = false
      extFileList.value = []
    }
  })
  if (algMap.value.get(row.type)?.cmd == 'mmdet') {
    FileService.getFile(basePath_TRAIN + '/' + row.id + "/file/cfg.py").then(res => {
      addForm.mmdet_cfg = res
    })
  } else {
    addForm.mmdet_cfg = default_mmdet_header;
  }
  addVisible.value = true
  nextTick(() => {
    if (algMap.value.get(row.type)?.cmd === 'mmdet') {
      fetchMmdetInstanceDatasets()
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
      TrainTaskService.delete({ id: row.id }).then(res => {
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

const label_list = ref([])
const label_map = computed(() => {
  let map = new Map()
  label_list.value.forEach(element => {
    map.set(element.id, element)
  });
  return map
})
const queryLabels = () => {
  TrainLabelService.queryAll().then(res => {
    if (res.code === 0) {
      res.data.forEach(item => {
        if (item.merge) {
          item.children = item.children.split(',')

        }
      })
      label_list.value = res.data
    }
  })
}
const all_project = ref([])
const a_l_list = ref([])
const a_s_list = ref([])
const a_g_list = ref([])
const filter_l = ref([])
const filter_s = ref([])
const filter_g = ref([])
const filter_n = ref('')
const filter_project = computed(() => {
  let l = filter_l.value.length>0?filter_l.value : null
  let s = filter_s.value.length>0?filter_s.value:null
  let g = filter_g.value.length>0?filter_g.value: null
  let n = filter_n.value
  return all_project.value.filter(project => {
    const lFindItem=l?.find(val=>val==project.a_l)
    const sFindItem=s?.find(val=>val==project.a_s)
    const gFindItem=g?.find(val=>val==project.a_g)

    return (!l||lFindItem)&&(!s||sFindItem)&&(!g||gFindItem)&&project.a_n.indexOf(n) != -1
    // return (!l || project.a_l == l) && (!s || s == project.a_s) && (!g || g == project.a_g) && project.a_n.indexOf(n) != -1
  })
})
const cur_project = ref({ project_id: -1 });

const cur_f_list = computed(() => {
  let list = cur_project.value.tasks || [];
  const s_set = new Set()
  const r_set = new Set()
  const v_set = new Set()
  const p_set = new Set()
  const a_set = new Set()
  const e_set = new Set()
  list.forEach(item => {
    if (item.a_s) {
      s_set.add(item.a_s)
    }
    if (item.a_r) {
      r_set.add(item.a_r)
    }
    if (item.a_v) {
      v_set.add(item.a_v)
    }
    if (item.a_p) {
      p_set.add(item.a_p)
    }
    if (item.a_a) {
      a_set.add(item.a_a)
    }
    if (item.a_e) {
      e_set.add(item.a_e)
    }
  })
  return {
    s: [...s_set],
    r: [...r_set],
    v: [...v_set],
    p: [...p_set],
    a: [...a_set],
    e: [...e_set],
  }
})

const all_project_val = ref([])

const filter_l_val = ref([])
const filter_s_val = ref([])
const filter_g_val = ref([])
const filter_n_val = ref('')
const filter_project_val = computed(() => {
  let l = filter_l_val.value.length>0?filter_l_val.value:null
  let s = filter_s_val.value.length>0?filter_s_val.value:null
  let g = filter_g_val.value.length>0?filter_g_val.value:null
  let n = filter_n_val.value
  return all_project_val.value.filter(project => {
    const lFindItem=l?.find(val=>val==project.a_l)
    const sFindItem=s?.find(val=>val==project.a_s)
    const gFindItem=g?.find(val=>val==project.a_g)
    return (!l || lFindItem) && (!s || sFindItem) && (!g || gFindItem) && project.a_n.indexOf(n) != -1
  })
})
const cur_project_val = ref({ project_id: -1 });
const cur_f_list_val = computed(() => {
  let list = cur_project_val.value.tasks || [];
  const s_set = new Set()
  const r_set = new Set()
  const v_set = new Set()
  const p_set = new Set()
  const a_set = new Set()
  const e_set = new Set()
  list.forEach(item => {
    if (item.a_s) {
      s_set.add(item.a_s)
    }
    if (item.a_r) {
      r_set.add(item.a_r)
    }
    if (item.a_v) {
      v_set.add(item.a_v)
    }
    if (item.a_p) {
      p_set.add(item.a_p)
    }
    if (item.a_a) {
      a_set.add(item.a_a)
    }
    if (item.a_e) {
      e_set.add(item.a_e)
    }
  })
  return {
    s: [...s_set],
    r: [...r_set],
    v: [...v_set],
    p: [...p_set],
    a: [...a_set],
    e: [...e_set],
  }
})

const queryAllproject = () => {
  EngineProjectService.queryAll().then(res => {
    if (res.code === 0) {
      res.data.forEach(item => {
        item.labels = item.labels.split(',')
      });
      all_project.value = res.data
      let set_a_s = new Set();
      let set_a_l = new Set();
      let set_a_g = new Set();
      res.data.forEach(item => {
        if (item.a_s) {
          set_a_s.add(item.a_s)
        }
        if (item.a_l) {
          set_a_l.add(item.a_l)
        }
        if (item.a_g) {
          set_a_g.add(item.a_g)
        }
      })
      a_l_list.value = [...set_a_l];
      a_s_list.value = [...set_a_s];
      a_g_list.value = [...set_a_g];
      all_project_val.value = JSON.parse(JSON.stringify(res.data));
    }
  })
}

const onChangeLabel = () => {
  let val = addForm.labels;
  /**转换合并标签 */
  let val_all = []
  val.forEach(id => {
    let item = label_map.value.get(id)
    if (item.merge) {
      val_all.push(...item.children)
    } else {
      val_all.push(item.name)
    }
  });
  //遍历项目
  filter_project.value.forEach(project => {
    let labels = project.labels
    let show_val = false
    for (let j = 0; j < val_all.length; j++) {
      let lable = val_all[j]
      if (labels.includes(lable)) {
        show_val = true
        break
      }
    }
    project.show = show_val
  })

  filter_project_val.value.forEach(project => {
    let labels = project.labels
    let show_val = false
    for (let j = 0; j < val_all.length; j++) {
      let lable = val_all[j]
      if (labels.includes(lable)) {
        show_val = true
        break
      }
    }
    project.show = show_val
  })
}

const showTask = (project) => {
  if (cur_project.value?.project_id == project.project_id) {
    return
  }
  cur_project.value = project

  if (!project.tasks) {
    EngineTaskService.queryAll({ project_id: project.project_id, export_img: 1, status: null }).then(res => {
      if (res.code === 0) {
        project.tasks = res.data
        setBadge(project)
      }
    })
  }
}

const showTask_val = (project) => {
  if (cur_project_val.value?.project_id == project.project_id) {
    return
  }
  cur_project_val.value = project
  if (!project.tasks) {
    EngineTaskService.queryAll({ project_id: project.project_id, export_img: 1, status: null }).then(res => {
      if (res.code === 0) {
        project.tasks = res.data
        setBadge(project)
      }
    })
  }
}
/**第一次,需要遍历 */
const setBadge = (project) => {
  let tasks = project.tasks
  if (!tasks) {
    return
  }
  let select = 0
  let total = tasks.length
  tasks.forEach(element => {
    if (element.is_select) {
      select++
    }
  });
  project.select = select
  project.total = total
  project.badge = select + '/' + total
  project.badge_type = select == 0 ? 'info' : (select == total ? 'success' : 'warning')
}
/**只需要增量即可 */
const updateBadge = (project, change_num) => {
  project.select = project.select + change_num
  let total = project.total
  let select = project.select
  project.badge = select + '/' + total
  project.badge_type = select == 0 ? 'info' : (select == total ? 'success' : 'warning')
}

const currentSize2 = ref(10)
const currentPage2 = ref(1)
const searchN = ref('')
const searchS = ref([])
const searchR = ref([])
const searchV = ref([])
const searchP = ref([])
const searchA = ref([])
const searchE = ref([])
const searchObtainRange = ref([])

const currentSize2_val = ref(10)
const currentPage2_val = ref(1)
const searchN_val = ref('')
const searchS_val = ref([])
const searchR_val = ref([])
const searchV_val = ref([])
const searchP_val = ref([])
const searchA_val = ref([])
const searchE_val = ref([])
const searchObtainRange_val = ref([])

const filterTable = computed(() => {
  let tasks = cur_project.value.tasks || []
  return tasks.filter((item) => {
    let range = searchObtainRange.value
    if (range && range.length == 2) {
      let a_obtained_time = item.a_t
      if (a_obtained_time < range[0] || a_obtained_time > range[1]) {
        return false
      }
    }
    let s = searchS.value.length>0? searchS.value : null
    let r = searchR.value.length>0? searchR.value : null
    let v = searchV.value.length>0? searchV.value : null
    let p = searchP.value.length>0? searchP.value : null
    let a = searchA.value.length>0? searchA.value : null
    let e = searchE.value.length>0? searchE.value : null

    let sFindItem=s?.find(val=>val==item.a_s)
    let rFindItem=r?.find(val=>val==item.a_r)
    let vFindItem=v?.find(val=>val==item.a_v)
    let pFindItem=p?.find(val=>val==item.a_p)
    let aFindItem=a?.find(val=>val==item.a_a)
    let eFindItem=e?.find(val=>val==item.a_e)


    return (
      (!s || sFindItem) &&
      (!r || rFindItem) &&
      (!v || vFindItem) &&
      (!p || pFindItem) &&
      (!a || aFindItem) &&
      (!e || eFindItem) &&
      (item.a_n?.indexOf(searchN.value) != -1)
    )
  })
})


// 选择mmdet时修改界面
const isMMDetSelected=computed(() => {
  const selectedItem = algList.value.find(item => item.id == addForm.type)
  return selectedItem && selectedItem.name === 'mmdet'
})


const taskTable = computed(() => {
  return filterTable.value.slice((currentPage2.value - 1) * currentSize2.value, currentPage2.value * currentSize2.value)
})

const filterTable_val = computed(() => {
  let tasks = cur_project_val.value.tasks || []
  return tasks.filter((item) => {
    let range = searchObtainRange_val.value
    if (range && range.length == 2) {
      let a_obtained_time = item.a_t
      if (a_obtained_time < range[0] || a_obtained_time > range[1]) {
        return false
      }
    }
    let s = searchS_val.value.length>0?searchS_val.value:null
    let r = searchR_val.value.length>0?searchR_val.value:null
    let v = searchV_val.value.length>0?searchV_val.value:null
    let p = searchP_val.value.length>0?searchP_val.value:null
    let a = searchA_val.value.length>0?searchA_val.value:null
    let e = searchE_val.value.length>0?searchE_val.value:null

    let sFindItem=s?.find(val=>val==item.a_s)
    let rFindItem=r?.find(val=>val==item.a_r)
    let vFindItem=v?.find(val=>val==item.a_v)
    let pFindItem=p?.find(val=>val==item.a_p)
    let aFindItem=a?.find(val=>val==item.a_a)
    let eFindItem=e?.find(val=>val==item.a_e)
    return (
      (!s || sFindItem) &&
      (!r || rFindItem) &&
      (!v || vFindItem) &&
      (!p || pFindItem) &&
      (!a || aFindItem) &&
      (!e || eFindItem) &&
      (item.a_n?.indexOf(searchN_val.value) != -1)
    )
  })
})
const taskTable_val = computed(() => {
  return filterTable_val.value.slice((currentPage2_val.value - 1) * currentSize2_val.value, currentPage2_val.value * currentSize2_val.value)
})

const showDate = (time) => {
  return time ? dayjs(time).format('YYYY/MM/DD') : '';
}
const showDateTime = (time) => {
  return time ? dayjs(time).format('YYYY/MM/DD HH:mm:ss') : '';
}

const isIndeterminate = ref(false)
const checkAll = ref(false)
const isIndeterminate_filter = ref(false)
const checkAll_filter = ref(false)

const isIndeterminate_val = ref(false)
const checkAll_val = ref(false)
const isIndeterminate_filter_val = ref(false)
const checkAll_filter_val = ref(false)

const handleCheckOne = (row) => {
  updateBadge(cur_project.value, row.is_select ? 1 : -1);
}
const handleCheckOne_val = (row) => {
  updateBadge(cur_project_val.value, row.is_select ? 1 : -1);
}

const handleCheckAllChange = (val) => {
  let cnt = 0;
  if (val) {
    taskTable.value.forEach(item => {
      if (!item.is_select) {
        cnt++
        item.is_select = true
      }
    })
  } else {
    if (is_add.value) {//追加模式
      taskTable.value.forEach(item => {
        if (item.is_select && !item.dis) {
          cnt--
          item.is_select = false
        }
      })
    } else {//普通模式
      taskTable.value.forEach(item => {
        if (item.is_select) {
          cnt--
          item.is_select = false
        }
      })
    }

  }
  updateBadge(cur_project.value, cnt)
}
const handleCheckAllChange_filter = (val) => {
  let cnt = 0;
  if (val) {
    filterTable.value.forEach(item => {
      if (!item.is_select) {
        cnt++
        item.is_select = true
      }
    })
  } else {
    if (is_add.value) {
      filterTable.value.forEach(item => {
        if (item.is_select && !item.dis) {
          cnt--
          item.is_select = false
        }
      })
    } else {
      filterTable.value.forEach(item => {
        if (item.is_select) {
          cnt--
          item.is_select = false
        }
      })
    }
  }
  updateBadge(cur_project.value, cnt)
}

const handleCheckAllChange_val = (val) => {
  let cnt = 0;
  if (val) {
    taskTable_val.value.forEach(item => {
      if (!item.is_select) {
        cnt++
        item.is_select = true
      }
    })
  } else {
    if (is_add.value) {//追加模式
      taskTable_val.value.forEach(item => {
        if (item.is_select && !item.dis) {
          cnt--
          item.is_select = false
        }
      })
    } else {//普通模式
      taskTable_val.value.forEach(item => {
        if (item.is_select) {
          cnt--
          item.is_select = false
        }
      })
    }

  }
  updateBadge(cur_project_val.value, cnt)
}
const handleCheckAllChange_filter_val = (val) => {
  let cnt = 0;
  if (val) {
    filterTable_val.value.forEach(item => {
      if (!item.is_select) {
        cnt++
        item.is_select = true
      }
    })
  } else {
    if (is_add.value) {
      filterTable_val.value.forEach(item => {
        if (item.is_select && !item.dis) {
          cnt--
          item.is_select = false
        }
      })
    } else {
      filterTable_val.value.forEach(item => {
        if (item.is_select) {
          cnt--
          item.is_select = false
        }
      })
    }
  }
  updateBadge(cur_project_val.value, cnt)
}

const dataVisible = ref(false);
const base_type = ref('task');
const base_uri = ref();
const base_path = ref();
const trainTaskRow = ref()

const showDataset = (row) => {
  trainTaskRow.value = { ...row }
  base_type.value = 'task';
  base_path.value = row.project_id + '/' + row.id;//文件路径相对根目录
  base_uri.value = basePath_TASK + '/' + base_path.value;//url的完整根路径
  dataVisible.value = true
}
const viewResult = (row) => {
  trainTaskRow.value = { ...row }
  base_type.value = 'train';
  base_path.value = row.id;//文件路径相对根目录
  base_uri.value = basePath_TRAIN + '/' + base_path.value;//url的完整根路径
  dataVisible.value = true
}

/**配置文件 */
const yolo_table_ref = ref()
const tableData_yolo = ref([])
const total_yolo = ref(0)
const currentPage_yolo = ref(1)
const currentSize_yolo = ref(10)
const drawer = ref(false)
const searchId_yolo = ref()
const searchName_yolo = ref('')
const searchType_yolo = ref('')
const curFileId = ref(0)
const queryYoloFiles = () => {
  let id = searchId_yolo.value || ''
  if (isNum(id)) {
    id = parseInt(id)
  } else {
    id = null
  }
  TrainYoloService.queryList({
    current: currentPage_yolo.value,
    size: currentSize_yolo.value,
    type: searchType_yolo.value,
    name: searchName_yolo.value?.trim(),
    id: id
  }).then(function (res) {
    if (res.code === 0) {
      nextTick(() => {
        tableData_yolo.value = res.data.records;
        total_yolo.value = res.data.total;
      });
    } else {
      ElMessage.warning(res.msg);
    }
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
    const anchor = document.createElement('a');
    anchor.href = url2;
    anchor.download = url.substring(url.lastIndexOf('/') + 1);
    anchor.click();
    // 释放内存
    URL.revokeObjectURL(url2);
  })
}

const openDraw = (type, name) => {
  if (type == 'weights') {

  } else if (type == 'cfg') {

  } else if (type == 'hyp') {

  }

  searchType_yolo.value = type
  searchId_yolo.value = ''
  drawer.value = true
  queryYoloFiles()
}
const selectCurrentFile = (row) => {
  curFileId.value = row.id
  if (row.type == 'weights') {
    addForm.yolo_weights_id = row.id
  } else if (row.type == 'cfg') {
    addForm.yolo_cfg_id = row.id
  } else if (row.type == 'hyp') {
    addForm.yolo_hyp_id = row.id
  }
}

const gpu_list = [0, 1, 2, 3, 4, 5, 6, 7]
const checkAll_yolo = ref(false)
const isIndeterminate_yolo = ref(false)
const handleCheckAllChange_yolo = (val) => {
  if (val) {
    addForm.train_gpu_list = JSON.parse(JSON.stringify(gpu_list));
  } else {
    addForm.train_gpu_list = []
  }
}
const tensorboard_url = ref('6008');
let iswin = false
const getTensorboardUrl = () => {
  ApiService.getTensorboardUrl().then(res => {
    if (res.code === 0) {
      tensorboard_url.value = res.data.url
      iswin = res.data.iswin || false
    }
  })
}
getTensorboardUrl();
const openLog = (row) => {
  let reg = row.id + (iswin ? "\\\\run\\\\" : "/run/") + row.run_name;
  let path = "?runFilter=" + reg + "#scalars&regexInput=" + reg;
  window.open(nginx_tensorboard + tensorboard_url.value + path)
}
const ptList = ref([]);
/**监听 文件列表变化 */
const uploadChange = (file, list, type) => {
  if (list.length > 1 && file.status !== "fail") {
    list.splice(0, 1);
  } else if (file.status === "fail") {
    ElMessage.warning("上传失败，请重新上传！");
    list.splice(0, 1);
  }
  if (type == 'pt') {
    ptList.value = list
  }
};

const loading_saveRecord = ref(false)
const saveRecord = () => {
  let arr = []
  let list = filter_project.value
  let prj_num = 0;
  let task_num = 0;
  let img_num = 0;
  for (let i = 0; i < list.length; i++) {
    let project = list[i];
    if (project.select) {
      prj_num++;
      let p = { pid: project.project_id, labels: project.labels, tasks: [] }
      project.tasks.forEach(item => {
        if (item.is_select) {
          task_num++;
          img_num += item.size || 0;
          p.tasks.push(item.id);
        }
      })
      arr.push(p)
    }
  }
  let list_val = filter_project_val.value
  let arr_val = []
  for (let i = 0; i < list_val.length; i++) {
    let project = list_val[i];
    if (project.select) {
      let p = { pid: project.project_id, labels: project.labels, tasks: [] }
      project.tasks.forEach(item => {
        if (item.is_select) {
          p.tasks.push(item.id);
        }
      })
      arr_val.push(p)
    }
  }

  // children格式是 xx,xxx 不是["xx","xx"]
  let model_labels = addForm.labels.map(item => {
    let o = label_map.value.get(item) || {};
    o = JSON.parse(JSON.stringify(o));
    if (o.merge) {
      o.children = o.children.join(',')
    }
    return o;
  })
  let device = null
  let node_num = 1
  if (!addForm.train_use_gpu) {
    device = 'cpu'
    node_num = 1
  } else {
    device = addForm.train_gpu_list.join(',')
    node_num = addForm.train_gpu_list.length
  }
  if (!addForm.name) {
    ElMessage.warning('任务名称不能为空');
    return;
  }
  let alg_cmd = cur_cmd.value;
  if (!alg_cmd) {
    ElMessage.warning('请选择模型类别')
    return;
  }

  if (!model_labels?.length) {
    ElMessage.warning('请选择模型标签')
    return;
  }
  if (!arr.length) {
    ElMessage.warning('请选择训练样本');
    return;
  }
  if (!arr_val.length) {
    ElMessage.warning('请选择验证样本');
    return;
  }
  /**判断初始权重文件 */
  if (addForm.use_self_weights) {
    //自定义上传文件  原来就是使用自定义上传文件,可以不再上传
    if (addForm.old_weights !== 0 && ptList.value.length === 0) {
      ElMessage.warning('请上传初始权重文件')
      return;
    }
    addForm.yolo_weights_id = 0;
  } else if (!addForm.yolo_weights_id) {
    ElMessage.warning('请选择初始权重文件')
    return;
  }
  if (alg_cmd == 'yolo') {
    addForm.yolo_hyp_id = 0;
    addForm.yolo_cfg_id = 0;
    // if (!addForm.yolo_cfg_id) {
    //   ElMessage.warning('请选择网络配置文件')
    //   return;
    // }
  } else if (alg_cmd == 'mmdet') {
    if (!addForm.mmdet_cfg) {
      ElMessage.warning('算法配置文件内容为空');
      return;
    }
  } else if (!addForm.yolo_cfg_id || !addForm.yolo_hyp_id) {
    ElMessage.warning('请选择网络配置文件')
    return;
  }

  let f_max = addForm.f_max
  let f_min = addForm.f_min
  let f_area = addForm.f_area
  let reg = /^\d+$/;
  if (!reg.test(f_max) || !reg.test(f_min) || !reg.test(f_area)) {
    ElMessage.warning('请输入正确的小标签过滤参数')
    return;
  }
  f_max = parseInt(f_max)
  f_min = parseInt(f_min)
  f_area = parseInt(f_area)
  if (f_min > f_max) {
    ElMessage.warning('短边的值不能高于长边值')
    return
  }
  // if (!addForm.train_bath_size || !addForm.train_img_size || !addForm.train_epoch || !node_num) {
  //   ElMessage.warning('请完善训练参数配置')
  //   return;
  // }

  //mmdet 、 yolo算法 训练参数配置字段不一样 
  let mmdetCheck = alg_cmd == 'mmdet' && (!addForm.train_img_w || !addForm.train_img_h || !addForm.train_bath_size || !addForm.train_epoch || !node_num)
  let yoloCheck = (alg_cmd == 'python' || alg_cmd == 'yolo') && (!addForm.train_img_size || !addForm.train_bath_size || !addForm.train_epoch || !node_num)
  if (mmdetCheck || yoloCheck) {
    ElMessage.warning('请完善训练参数配置')
    return;
  }





  let error = aceEditRef.value.checkAce()
  if (error) return ElMessage.error("拓展参数存在错误，请输入正确的格式")

  let params = {
    cmd: alg_cmd,//明确指令类型 
    remark: addForm.remark,
    id: cur_task_id.value || null,
    name: addForm.name,
    type: addForm.type,
    cls_num: model_labels.length,
    prj_num: prj_num,
    task_num: task_num,
    img_num: img_num,
    data: {
      labels: model_labels,
      data: arr,
      val: arr_val,
    },
    args: {
      weights: addForm.yolo_weights_id,
      cfg: addForm.yolo_cfg_id,
      hyp: addForm.yolo_hyp_id,
      batch_size: addForm.train_bath_size,
      img_size: addForm.train_img_size || null,
      img_w: addForm.train_img_w || null,
      img_h: addForm.train_img_h || null,
      epoch: addForm.train_epoch,
      period: addForm.train_period,
      device: device,
      node: node_num,
      f_max: f_max,
      f_min: f_min,
      f_area: f_area,
    },
  }
  let fd = new FormData();
  fd.append("params", JSON.stringify(params));
  if (addForm.use_self_weights) {
    if (ptList.value.length > 0) {
      fd.append("weight_file", ptList.value[0].raw);
    } else if (addForm.old_weights === 0 && addForm.clone_from) {
      //没上传,但是可以从其他任务复制
      fd.append("clone_from", addForm.clone_from)
    }
  }
  // mmdet
  if (alg_cmd == 'mmdet') {
    fd.append('mmdet_cfg', addForm.mmdet_cfg);
  }
  //拓展参数配置
  fd.append("ext_params", addForm.ext_params)
  if (extFileList.value && extFileList.value.length > 0) {
    fd.append("ext_file", extFileList.value[0].raw)
  }
  !is_create.value && fd.append("ext_file_update", addForm.ext_file_update)
  loading_saveRecord.value = true
  if (is_create.value) {
    TrainTaskService.add(fd).then(res => {
      if (res.code === 0) {
        ElMessage.success(res.msg)
        addVisible.value = false
        loading_saveRecord.value = false
        queryUsers();
        setTimeout(() => {
          handleCurrentChange()
        }, 1000);
      } else {
        loading_saveRecord.value = false
        ElMessage.warning(res.msg)
      }
    }).catch(() => {
      loading_saveRecord.value = false
    })
  } else {
    TrainTaskService.update(fd).then(res => {
      if (res.code === 0) {
        ElMessage.success(res.msg)
        handleCurrentChange()
        addVisible.value = false
      } else {
        ElMessage.warning(res.msg)
      }
      loading_saveRecord.value = false
    }).catch(() => {
      loading_saveRecord.value = false
    })
  }

}






const handleSave = () => {
  if (isMMDetSelected.value) {
    saveMMdetRecord()
  } else {
    saveRecord()
  }
}



const mmdetParameter = reactive({
  
  selected_template: 'Faster R-CNN',
  selected_network: 'ResNet',
  /** false：沿用模板内默认预训练（torchvision://、https 等）；true：上传文件或填写 mmdet_pretrained_address */
  use_custom_pretrained: false,
  pretrained_address: '',
  deep: 50,
  dcn_sac_use: false,
  RFP: 2,

  ASPP: '3,6,9',

  exist_stage: '0,1,2,3',
  iter_count: 6,
  scale: 'small',
  window_size: 7,

  train_bath_size: 2,
  train_epoch: 12,
  selected_dataset: null,
  photo_width: 1333,
  photo_height: 800,
  optimizer: 'AdamW',
  stu_rate: 0.0001,
  down_round: '8, 11',
  weight_round: 1,
  valid_round: 1,


  measure: 'single',
  embedding_dimension: 256,
  encoder_layers: 6,
  decoder_layers: 6,
  attention_num: 8,
  attention_discard_rate: 0.1,
  FFN_intermediate_layer_dimension: 2048,
  FFN_linear_layer_num: 2,
  FFN_discard_rate: 0.1,
  FFN_active_func: 'ReLU',
  temperature: 20,
  loss_cls: 'CrossEntropyLoss',
  loss_cls_weight: 1,
  loss_bbox: 'L1Loss',
  loss_bbox_weight: 5,
  loss_iou: 'GIoULoss',
  loss_iou_weight: 2,



})

/**新建弹窗用：带时间戳的任务名 */
const formatQuickTaskName = () => {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `快速任务_${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}_${p(d.getHours())}${p(d.getMinutes())}`
}

/** CNN + Faster R-CNN + ResNet 常用默认，便于一键提交 */
const applyMmdetCnnQuickDefaults = () => {
  mmdetParameter.use_custom_pretrained = false
  mmdetParameter.pretrained_address = ''
  mmdetParameter.selected_template = 'Faster R-CNN'
  mmdetParameter.selected_network = 'ResNet'
  mmdetParameter.deep = 50
  mmdetParameter.exist_stage = '0,1,2,3'
  mmdetParameter.dcn_sac_use = false
  mmdetParameter.RFP = 2
  mmdetParameter.ASPP = '3,6,9'
  mmdetParameter.iter_count = 6
  mmdetParameter.scale = 'small'
  mmdetParameter.window_size = 7
  mmdetParameter.train_bath_size = 2
  mmdetParameter.train_epoch = 12
  mmdetParameter.photo_width = 1333
  mmdetParameter.photo_height = 800
  mmdetParameter.optimizer = 'AdamW'
  mmdetParameter.stu_rate = 0.0001
  mmdetParameter.down_round = '8, 11'
  mmdetParameter.weight_round = 1
  mmdetParameter.valid_round = 1
  mmdetParameter.measure = 'single'
  mmdetParameter.FFN_active_func = 'ReLU'
  mmdetParameter.loss_cls = 'CrossEntropyLoss'
  mmdetParameter.loss_bbox = 'L1Loss'
  mmdetParameter.loss_iou = 'GIoULoss'
  addForm.train_img_w = 1333
  addForm.train_img_h = 800
  addForm.train_bath_size = 2
  addForm.train_epoch = 12
}

function suggestInstanceDatasetScroll(row) {
  const reasons = (row && row.reasons) || []
  const t = reasons.join(' ')
  if (t.includes('class_list') || t.includes('类别') || t.includes('预处理')) {
    return 'sec-preprocess'
  }
  return 'sec-instance'
}

function goInstanceDatasetFix(row) {
  const scroll = suggestInstanceDatasetScroll(row)
  router.push({ path: '/datasetManageUnified', query: { scroll } })
}

const fetchMmdetInstanceDatasets = () => {
  instanceReadinessLoading.value = true
  InstanceDatasetService.listTrainingReadiness()
    .then((res) => {
      if (res.code === 0) {
        instanceReadinessList.value = res.data || []
        const names = instanceReadinessList.value.filter((r) => r.qualified).map((r) => r.name)
        const cur = mmdetParameter.selected_dataset
        if (cur && names.includes(cur)) {
          // keep
        } else if (names.length) {
          mmdetParameter.selected_dataset = names[0]
        } else {
          mmdetParameter.selected_dataset = null
          if (!isSee.value) {
            ElMessage.warning('当前没有可参与训练的实例数据集，请完成预处理、训测划分或检查数据目录')
          }
        }
      }
    })
    .catch(() => {})
    .finally(() => {
      instanceReadinessLoading.value = false
    })
}

watch(
  () => [mmdetParameter.selected_template, mmdetParameter.selected_network],
  () => {
    const t = mmdetParameter.selected_template
    const b = mmdetParameter.selected_network
    if ((t === 'Faster R-CNN' || t === 'Cascade R-CNN') && (b === 'ConvNext' || b === 'SwinTransformer')) {
      mmdetParameter.use_custom_pretrained = true
    }
  }
)

// DETR / CNN 算法模板切换（不要用 immediate: true，否则 temp 为 null 时会清空已设好的 CNN 默认）
watch(() => addForm.temp, (newType) => {
  if (newType === 'DETR') {
    mmdetParameter.use_custom_pretrained = false
    mmdetParameter.pretrained_address = ''
    mmdetParameter.selected_template = 'DETR'
    mmdetParameter.selected_network = 'ResNet'
    mmdetParameter.deep = 50
    mmdetParameter.measure = 'single'
    mmdetParameter.loss_cls = 'CrossEntropyLoss'
    mmdetParameter.loss_bbox = 'L1Loss'
    mmdetParameter.loss_iou = 'GIoULoss'
    mmdetParameter.activationFuncList = 'ReLU'

    addForm.train_img_w = 800
    addForm.train_img_h = 800
    addForm.train_bath_size = 2
    addForm.train_epoch = 50
    addForm.stu_rate = 0.0001
    addForm.down_round = 30
    addForm.weight_round = 1
  } else if (newType === 'CNN') {
    applyMmdetCnnQuickDefaults()
  }
})

watch(() => mmdetParameter.selected_template, (newTemp) => {
  if (newTemp === 'DINO') {
    mmdetParameter.measure = 'multi'
    mmdetParameter.loss_cls = 'FocalLoss'
  } else if(newTemp === 'Deformable DETR'){ 
    mmdetParameter.measure = 'multi'
    mmdetParameter.loss_cls = 'FocalLoss'
  } else if(newTemp === 'DETR'){ 
    mmdetParameter.measure = 'single'
    mmdetParameter.loss_cls = 'CrossEntropyLoss'
  }
}, { immediate: true })

const handleTempChange = () => {
  /* addForm.temp 的联动由 watch 处理 */
}

const handleTypeChange = () => {
  if (isMMDetSelected.value) {
    fetchMmdetInstanceDatasets()
  }
}



// 计算属性 - 可用的主干网选项
const availableBackboneNetworks = computed(() => {
  // 当选择DetectoRS时，只有ResNet可用，其他选项禁用
  if (mmdetParameter.selected_template === "DetectoRS") {
    return backboneNetwork.value.map(item => ({
      value: item,
      label: item,
      disabled: item !== "ResNet"
    }))
  }
  
  // 其他情况下所有选项都可用
  return backboneNetwork.value.map(item => ({
    value: item,
    label: item,
    disabled: false
  }))
})

// 监听模板选择变化
watch(() => mmdetParameter.selected_template, (newVal) => {
  if (newVal === "DetectoRS" && mmdetParameter.selected_network !== "ResNet") {
    mmdetParameter.selected_network = "ResNet"
  }
})


const saveMMdetRecord = () =>{
  if (!addForm.name) {
    ElMessage.warning('任务名称不能为空');
    return;
  }
  let alg_type = cur_type.value;
  if (!alg_type) {
    ElMessage.warning('请选择模型类别')
    return;
  }

  // if (!model_labels?.length) {
  //   ElMessage.warning('请选择模型标签')
  //   return;
  // }

  if (mmdetParameter.selected_template==null) {
    ElMessage.warning('请选择网络模板')
    return;
  }
  

  if (mmdetParameter.selected_network==null) {
    ElMessage.warning('请选择主干网')
    return;
  }

  if (mmdetParameter.use_custom_pretrained) {
    const hasFile = weightFileList.value.length > 0
    const hasAddr = (mmdetParameter.pretrained_address || '').trim().length > 0
    if (!hasFile && !hasAddr) {
      ElMessage.warning('自定义预训练时请上传权值文件或填写权值地址')
      return
    }
  }

  if (mmdetParameter.selected_dataset == null) {
    ElMessage.warning('请选择实例数据集')
    return;
  }
  const selReadiness = instanceReadinessList.value.find((r) => r.name === mmdetParameter.selected_dataset)
  if (!selReadiness || !selReadiness.qualified) {
    ElMessage.warning('请选择在列表中标记为「可选」的实例数据集，或先到数据集管理完成预处理与训测划分')
    return
  }

  if (mmdetParameter.optimizer == null) {
    ElMessage.warning('请选择优化器')
    return;
  }

  
  let params ={
    taskName: addForm.name,
// 模型类别
    taskType: addForm.type,
// 算法模板
    mmdetType:addForm.temp,

    dataset:mmdetParameter.selected_dataset,
    mmdet_network:mmdetParameter.selected_template,
    mmdet_backbone:mmdetParameter.selected_network,
    mmdet_depth:mmdetParameter.deep,
    mmdet_dcn:mmdetParameter.dcn_sac_use,
    mmdet_dcnStage:mmdetParameter.exist_stage,
   
    mmdet_conv_arch:mmdetParameter.scale,
    mmdet_swint_arch:mmdetParameter.scale,
    mmdet_window:mmdetParameter.window_size,
    mmdet_input_width:mmdetParameter.photo_width,
    mmdet_input_height:mmdetParameter.photo_height,
    mmdet_batchsize:mmdetParameter.train_bath_size,
    mmdet_opt:mmdetParameter.optimizer,
    mmdet_inlr:mmdetParameter.stu_rate,
    mmdet_epoch:mmdetParameter.train_epoch,
    mmdet_step:mmdetParameter.down_round,
    mmdet_weight_interval:mmdetParameter.weight_round,
    mmdet_val_interval:mmdetParameter.valid_round,

    mmdet_rfp_steps:mmdetParameter.RFP,
    mmdet_aspp_dilation:mmdetParameter.ASPP,
    mmdet_neck_sac:mmdetParameter.dcn_sac_use,
    mmdet_neck_sacStage:mmdetParameter.exist_stage,
    mmdet_detectors_itrnum:mmdetParameter.iter_count,

    detr_neck_mode:mmdetParameter.measure,
    detr_embed_dims:mmdetParameter.embedding_dimension,
    detr_encoder_layers:mmdetParameter.encoder_layers,
    detr_decoder_layers:mmdetParameter.decoder_layers,

    detr_num_heads:mmdetParameter.attention_num,
    detr_attn_dropout:mmdetParameter.attention_discard_rate,

    detr_ffn_channels:mmdetParameter.FFN_intermediate_layer_dimension,
    detr_ffn_num_fcs:mmdetParameter.FFN_linear_layer_num,
    detr_ffn_dropout:mmdetParameter.FFN_discard_rate,
    detr_ffn_act:mmdetParameter.FFN_active_func,

    detr_pos_temperature:mmdetParameter.temperature,
    detr_loss_cls_type:mmdetParameter.loss_cls,
    detr_loss_bbox_type:mmdetParameter.loss_bbox,
    detr_loss_iou_type:mmdetParameter.loss_iou,
    detr_loss_cls_weight:mmdetParameter.loss_cls_weight,
    detr_loss_bbox_weight:mmdetParameter.loss_bbox_weight,
    detr_loss_iou_weight:mmdetParameter.loss_iou_weight,

    mmdet_use_custom_pretrained: mmdetParameter.use_custom_pretrained,
    mmdet_pretrained_address: (mmdetParameter.pretrained_address || '').trim(),

  }

  let fd = new FormData();

  fd.append("params", JSON.stringify(params));
  if (mmdetParameter.use_custom_pretrained && weightFileList.value.length > 0) {
    fd.append("weight_file", weightFileList.value[0].raw)
  }


  loading_saveRecord.value = true
  TrainTaskService.addMMD(fd).then(res => {
      if (res.code === 0) {
        ElMessage.success(res.msg)
        addVisible.value = false
        loading_saveRecord.value = false
        queryUsers();
        setTimeout(() => {
          handleCurrentChange()
        }, 1000);
      } else {
        loading_saveRecord.value = false
        ElMessage.warning(res.msg)
      }
    }).catch(() => {
      loading_saveRecord.value = false
    })

  
}


/**启动任务 */
const enqueueTask = (row) => {
  ElMessageBox.confirm(
    `确定要发布任务[${row.name}]?`,
    '',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  )
    .then(() => {
      TrainTaskService.enqueue({ id: row.id }).then(res => {
        if (res.code === 0) {
          ElMessage.success(res.msg)
          setTimeout(() => {
            handleCurrentChange()
          }, 1000);

        } else {
          ElMessage.warning(res.msg)
        }
      })
    })
    .catch(() => {

    })

}
/**终止任务 */
const stopTask = (row) => {
  ElMessageBox.confirm(
    `确定要中止任务[${row.name}]?`,
    '',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  )
    .then(() => {
      TrainTaskService.stop({ id: row.id }).then(res => {
        if (res.code === 0) {
          ElMessage.success(res.msg)
          setTimeout(() => {
            handleCurrentChange()
          }, 1000);
        } else {
          ElMessage.warning(res.msg)
        }
      })
    })
    .catch(() => {
    })
}
/**置顶任务 */
const topTask = (row) => {
  TrainTaskService.topTask({ id: row.id }).then(res => {
    if (res.code === 0) {
      ElMessage.success(res.msg)
      setTimeout(() => {
        handleCurrentChange()
      }, 1000);
    } else {
      ElMessage.warning(res.msg)
    }
  })
}
/**取消任务 */
const cancelTask = (row) => {
  ElMessageBox.confirm(
    `确定要取消任务[${row.name}]?`,
    '',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  )
    .then(() => {
      TrainTaskService.cancelTask({ id: row.id }).then(res => {
        if (res.code === 0) {
          ElMessage.success(res.msg)
          setTimeout(() => {
            handleCurrentChange()
          }, 1000);
        } else {
          ElMessage.warning(res.msg)
        }
      })
    })
    .catch(() => {
    })
}
/**创建模型转换任务 */
const transVisible = ref(false)
const transForm = ref({ params: {} });
const transLoading = ref(false);
//跳转模型转换任务
const goToTransTask = (name) => {
  // let name = "train_" + row.id + "_" + row.run_name
  router.push({ path: "/modelTrans", query: { name: name } });
}
const showTransModal = (row) => {
  //算法模版初始化
  templateAlgorithm.value = null
  templateAlgorithmList.value = []
  let name = "train_" + row.id + "_" + row.run_name
  transForm.value = { params: {}, id: row.id, weights: row.run_name + "/best.pt", data: "data.yaml", name: name, trans_name: row.trans_name }
  transVisible.value = true;
}
//创建模型转换任务
const addTransTask = () => {
  let form = transForm.value
  let type = form.type
  let name = form.name
  let params = form.params;
  if (!name) {
    ElMessage.warning('转换任务名称不能为空')
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
  fd.append("taskId", form.id)
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
    fd.append("params", JSON.stringify(params));
  }
  transLoading.value = true
  ModelTransService.addByTask(fd).then(res => {
    if (res.code === 0) {
      ElMessage.success(res.msg);
      handleCurrentChange();
      transVisible.value = false;
    } else {
      ElMessage.warning(res.msg)
    }
    transLoading.value = false
  }).catch(() => {
    transLoading.value = false
  })
}
const aceEditRef3 = ref();
const activeValTab = ref('val-tab')
const valVisible = ref(false);
const weightsList = ref([]);
const uploadSrcRef = ref();
const srcFileList = ref([]);
const valForm = reactive({
  labels: [],
  weights: 'best.pt',
  batch_size: 1,
  use_gpu: true,
  gpu_list: [0],
  conf_thres: 0.25,
  img_size: 640,
  ext_params: '',
  run_name: '',
  is_val: true,//验证/预测
  save_txt: false,//是否生成标签
})
const handleSrcExceed = (files) => {
  uploadSrcRef.value?.clearFiles()
  const file = files[0]
  file.uid = genFileId()
  uploadSrcRef.value?.handleStart(file);
}
const showValModal = (row, is_val) => {
  //算法模版初始化
  cur_task_id.value = row.id;
  valForm.id = row.id;
  valForm.name = row.name;
  valForm.is_val = is_val;
  // valForm.ext_params = '';
  valForm.run_name = row.run_name;
  uploadSrcRef.value?.clearFiles()
  // 查询要转换的权重List
  FileService.queryFiles({ type: "train", base: row.id + "/run/" + row.run_name + "/weights" }).then(res => {
    if (res.code === 0) {
      let files = res.data.files || []
      files = files.filter(item => item.endsWith('.pt'));
      if (files.length == 0) {
        ElMessage.warning('没有找到可用的权重文件');
      } else {
        weightsList.value = files;
        valVisible.value = true;
      }
    } else {
      ElMessage.warning('获取权重文件列表失败')
      weightsList.value = []
    }
  })
  TrainTaskService.queryData({ id: row.id }).then(res => {
    if (res.code === 0) {
      let labels = JSON.parse(res.data.labels)
      valForm.labels = labels;
      let val_all = [];
      labels.forEach(item => {
        if (item.merge) {
          val_all.push(...item.children.split(','))
        } else {
          val_all.push(item.name)
        }
      });

      let pList = all_project_val.value
      pList.forEach((item) => {
        item.tasks?.forEach(t => {
          t.is_select = false
        })
        setBadge(item)
      })

      filter_project_val.value.forEach(project => {
        let pLabels = project.labels
        let show_val = false
        for (let j = 0; j < val_all.length; j++) {
          let lable = val_all[j]
          if (pLabels.includes(lable)) {
            show_val = true
            break
          }
        }
        project.show = show_val
      })
    }
  })

}
const valLoading = ref(false);
const startValModel = () => {
  let is_val = valForm.is_val;
  let list_val = filter_project_val.value
  let arr_val = []
  for (let i = 0; i < list_val.length; i++) {
    let project = list_val[i];
    if (project.select) {
      let p = { pid: project.project_id, labels: project.labels, tasks: [] }
      project.tasks.forEach(item => {
        if (item.is_select) {
          p.tasks.push(item.id);
        }
      })
      arr_val.push(p)
    }
  }
  if (is_val) {
    if (arr_val.length == 0) {
      ElMessage.warning('请选择样本集')
      return;
    }
  } else {
    if (arr_val.length == 0 && srcFileList.value.length == 0) {
      ElMessage.warning('样本集和上传资源文件不能都为空')
      return;
    }
  }

  if (!valForm.weights) {
    ElMessage.warning('请选择权重文件')
    return;
  }
  if ((is_val && !valForm.batch_size) || !valForm.conf_thres || !valForm.img_size) {
    ElMessage.warning('参数填写不完整')
    return;
  }
  if (valForm.use_gpu && valForm.gpu_list.length == 0) {
    ElMessage.warning('请选择运行使用的硬件')
    return;
  }
  let error = aceEditRef3.value.checkAce()
  if (error) {
    ElMessage.warning("拓展参数存在错误,请输入正确的json格式")
    return;
  }
  let device = 'cpu'
  if (valForm.use_gpu) {
    device = valForm.gpu_list.join(',');
    if (is_val && valForm.batch_size % valForm.gpu_list.length != 0) {
      ElMessage.warning('batch_size必须是gpu数目的整数倍')
      return;
    }
  }
  let params = {
    id: valForm.id,
    labels: valForm.labels,
    data: arr_val,
    run_name: valForm.run_name,
    weights: valForm.weights,
    batch_size: is_val ? valForm.batch_size : null,
    conf_thres: valForm.conf_thres,
    device: device,
    img_size: valForm.img_size,
    ext_params: valForm.ext_params,
    is_val: is_val,
    save_txt: valForm.save_txt ? true : false,
  }
  valLoading.value = true;
  if (is_val) {
    TrainTaskService.addValTask(params).then(res => {
      if (res.code === 0) {
        valVisible.value = false;
      } else {
        ElMessage.warning(res.msg)
      }
    }).catch().finally(() => {
      valLoading.value = false;
    })
  } else {
    let fd = new FormData();
    fd.append("info", JSON.stringify(params));
    if (srcFileList.value.length > 0) {
      fd.append("file", srcFileList.value[0].raw);
    }
    TrainTaskService.addPredictTask(fd).then(res => {
      if (res.code === 0) {
        valVisible.value = false;
      } else {
        ElMessage.warning(res.msg)
      }
    }).catch().finally(() => {
      valLoading.value = false;
    })
  }

}
const checkAll_gpu = ref(false)
const isIndeterminate_gpu = ref(false)
const handleCheckAllChange_gpu = (val) => {
  if (val) {
    valForm.gpu_list = JSON.parse(JSON.stringify(gpu_list));
  } else {
    valForm.gpu_list = []
  }
}

const queryUsers = () => {
  TrainTaskService.queryUsers().then(res => {
    if (res.code === 0) {
      userList.value = res.data;
    }
  })
}
queryUsers();
queryAlgs();
queryLabels();
queryAllproject();

handleCurrentChange();

/**初始化模型转换的数据 开始 */
const transAlgList = ref([]);
/**查询支持的模型转换算法 */
const queryTransAlgs = () => {
  TrainScriptService.queryAll({ type: 'trans' }).then(res => {
    if (res.code === 0) {
      transAlgList.value = res.data
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
queryTransAlgs();
/**初始化模型转换的数据 结束 */


watchEffect(() => {
  let select = 0;
  let total = taskTable.value.length
  taskTable.value.forEach(item => {
    if (item.is_select) {
      select++
    }
  })

  let select_filter = 0;
  let total_filter = filterTable.value.length
  filterTable.value.forEach(item => {
    if (item.is_select) {
      select_filter++
    }
  })

  checkAll.value = select > 0
  isIndeterminate.value = select > 0 && select != total

  checkAll_filter.value = select_filter > 0
  isIndeterminate_filter.value = select_filter > 0 && select_filter != total_filter

  //验证集
  let select_val = 0;
  let total_val = taskTable_val.value.length
  taskTable_val.value.forEach(item => {
    if (item.is_select) {
      select_val++
    }
  })

  let select_filter_val = 0;
  let total_filter_val = filterTable_val.value.length
  filterTable_val.value.forEach(item => {
    if (item.is_select) {
      select_filter_val++
    }
  })

  checkAll_val.value = select_val > 0
  isIndeterminate_val.value = select_val > 0 && select_val != total_val

  checkAll_filter_val.value = select_filter_val > 0
  isIndeterminate_filter_val.value = select_filter_val > 0 && select_filter_val != total_filter_val


  let gpu_cnt = addForm.train_gpu_list.length
  isIndeterminate_yolo.value = gpu_cnt > 0 && gpu_cnt != gpu_list.length
  checkAll_yolo.value = gpu_cnt > 0
})

const showStatus = (row) => {
  let run_name = row.run_name;
  if (run_name) {
    return taskStatusMap.get(row.status) + '[' + run_name + ']';
  } else {
    return taskStatusMap.get(row.status);
  }
}

/**任务通知机制 */
let ws;
let wscloseflg = false;
let reconnectInterval = 2000; // 重新连接的时间间隔，单位为毫秒
let heartInter = null;
const wsConnect = () => {
  ws = new WebSocket(basePath_WS_TASK + uuid.v1());
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
    let msg_type = data.msg_type;
    for (let i = 0; i < tableData.value.length; i++) {
      let item = tableData.value[i]
      if (item.id === id) {
        if (msg_type == 'val') {
          item.val_state = data.val_state;
        } else if (msg_type == 'predict') {
          item.predict_state = data.predict_state;
        } else {
          item.status = status;
          if (status == 1) {
            item.obj_num = data.obj_num;
          } else if (status == 3) {
            item.finish_date = null
            item.run_name = data.run_name
            item.started_date = data.started_date;
          } else if (status == 4) {
            item.finish_date = data.finish_date
            item.run_name = data.run_name
          }
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
  refreshRunnerHealth();
  runnerHealthTimer = setInterval(refreshRunnerHealth, 30000);
  // 列表在 setup 时已请求一次；此处再拉一次避免登录态/Pinia 尚未就绪时首次为空
  nextTick(() => {
    queryUsers();
    handleCurrentChange();
  });
})

onBeforeUnmount(() => {
  if (runnerHealthTimer) {
    clearInterval(runnerHealthTimer);
    runnerHealthTimer = null;
  }
  clearInterval(heartInter);
  wscloseflg = true;
  if (ws) {
    try {
      ws.close();
    } catch (error) {
    }
  }
});


const uploadRef = ref()
//拓展参数配置，文件上传
const handleExceed = (files) => {
  uploadRef.value?.clearFiles()
  const file = files[0]
  file.uid = genFileId()
  uploadRef.value?.handleStart(file)
}



const uploadWeightRef = ref()
//拓展参数配置，文件上传
const handleWeightExceed = (files) => {
  uploadWeightRef.value?.clearFiles()
  const file = files[0]
  file.uid = genFileId()
  uploadWeightRef.value?.handleStart(file)
}

//算法模板
const templateAlgorithm = ref()
const templateAlgorithmList = ref([])
//监听模型类别 获取算法模板下拉数据  同时默认选中
watch(() => addForm.type, async (newV) => {
  if (newV) {
    templateAlgorithmList.value = (await apiRequest(trainService.allTrain, { alg_id: newV })) || []
    const findItem = templateAlgorithmList.value.find(val => val.id == templateAlgorithm.value?.id)
    if (!findItem) {
      templateAlgorithm.value = templateAlgorithmList.value[0] || null
    }
    templateAlgorithm.value && changeTemplate('train', { ...templateAlgorithm.value })
  }
}, { immediate: true })

//监听转换算法，获取算法模版下拉数据
watch(() => transForm.value.type, async (newV) => {
  if (newV) {
    templateAlgorithmList.value = (await apiRequest(transService.allTrain, { type: newV })) || []
    const findItem = templateAlgorithmList.value.find(val => val.id == templateAlgorithm.value?.id)
    if (!findItem) {
      templateAlgorithm.value = templateAlgorithmList.value[0] || null
    }
    templateAlgorithm.value && changeTemplate('trans', { ...templateAlgorithm.value })
  }
}, { immediate: true })



//字符串转为json对象
const parseJSON = (jsonString) => {
  try {
    const result = JSON.parse(jsonString)
    return { success: true, data: result }
  } catch (error) {
    console.log(error, 'error')
    return { success: false, error: error.message };
  }
}

//算法模板的change事件
const changeTemplate = (type, param) => {
  if (type == 'train') {
    let { device, weights, hyp, cfg, batch_size, img_size, epoch, period, val_ratio, f_max, f_min, f_area, remark, img_w, img_h } = param
    addForm.yolo_weights_id = weights;
    addForm.old_weights = weights;//修改之前的
    addForm.use_self_weights = weights === 0
    addForm.yolo_cfg_id = cfg
    addForm.yolo_hyp_id = hyp
    addForm.train_bath_size = batch_size
    addForm.train_img_size = img_size
    addForm.train_epoch = epoch
    addForm.train_period = period
    addForm.remark = remark
    addForm.train_img_w = img_w
    addForm.train_img_h = img_h

    //过滤
    addForm.f_max = f_max || 0
    addForm.f_min = f_min || 0
    addForm.f_area = f_area || 0
    if (device == 'cpu') {
      addForm.train_use_gpu = false
      addForm.train_gpu_list = []
    } else {
      addForm.train_use_gpu = true
      addForm.train_gpu_list =
        device.split(',').map(i => parseInt(i))
    }
  }
  if (type == 'trans') {
    const { params, remark } = param
    transForm.value.remark = remark
    let parseParams = parseJSON(params)
    transForm.value.params = parseParams.success ? parseParams.data : {}
  }

}

//虚拟下拉框 相关
const selectV2HandleCheckAll = (val) => {
  selectV2Indeterminate.value = false
  if (val) {
    addForm.labels = label_list.value.map((item) => item.id)
  } else {
    addForm.labels = []
  }
  onChangeLabel()
}

watch(() => addForm.labels, (newV) => {
  if (newV.length === 0) {
    selectV2CheckAll.value = false
    selectV2Indeterminate.value = false
  } else if (newV.length == label_list.value.length) {
    selectV2CheckAll.value = true
    selectV2Indeterminate.value = false
  } else {
    selectV2Indeterminate.value = true
  }
})

//拖拽
const labelsHandleClose = (val) => {
  addForm.labels = addForm.labels.filter(item => item != val)
  onChangeLabel()
}
</script>

<style scoped>
.content-div {
  padding: 10px;
}

.runner-status-footer {
  margin-top: 10px;
}

.runner-status-footer :deep(.el-alert) {
  border-radius: 4px;
  align-items: flex-start;
}

.runner-status-title {
  font-weight: 500;
}

.runner-status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 4px;
}

.runner-status-text {
  flex: 1;
  min-width: 200px;
  color: var(--el-text-color-regular);
}

.mmdet-instance-dataset-block {
  width: 100%;
  max-width: 100%;
}

.mmdet-instance-readiness-table {
  width: 100%;
}

.mmdet-instance-readiness-table :deep(.mmdet-dataset-radio .el-radio__label) {
  display: none;
  padding: 0;
}

.mmdet-readiness-ok {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.mmdet-readiness-reason {
  display: inline-block;
  max-width: 100%;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

.mmdet-readiness-dash {
  color: var(--el-text-color-placeholder);
}

.mmdet-readiness-tip {
  color: #909399;
  font-size: 12px;
  line-height: 1.45;
  margin-top: 8px;
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

:deep(.el-space.my-space) {
  vertical-align: middle;
  margin-left: 10px;

}

.aside-container {
  background: rgb(252, 252, 252);
  width: 320px;
  padding-right: 10px;
}

.main-div {
  height: 50vh;
  overflow: auto;
}

.main-container {
  background: rgb(214, 247, 246);
  padding: 0px 10px;
  height: 50vh;
}

.main-container-val {
  background: rgb(210, 227, 253);
  padding: 0px 10px;
  height: 50vh;
}

.header-container {
  background: rgb(231, 238, 246);
  padding: 0%;
  height: auto;
  padding: 0px 10px;
}

.pane-div {
  width: 100%;
  /* height: 50vh */
}

.mmdet-div {
  width: 100%;
  /* height: 50vh */
}

.config-text {
  padding-left: 20px;        /* 与左边的间隔 */
  font-size: 16px;           /* 字体大小 */
  height: 50px;              /* 组件高度 */
  line-height: 30px;         /* 文字垂直居中 */
  font-weight: 500;          /* 字体粗细 */
}

.config-text-sub {
  padding-left: 25px;        /* 与左边的间隔 */
  font-size: 14px;           /* 字体大小 */
  height: 40px;              /* 组件高度 */
  line-height: 30px;         /* 文字垂直居中 */
  font-weight: 400;          /* 字体粗细 */
}

.tabs-div {
  /* height: calc(60vh - 0px); */
  overflow: auto;
}

.tagItem {
  margin-left: 5px;
  cursor: move;
}

/* 新增：MMDetection 四大文件分块样式 */
.mmdet-file-block {
  border: 1px solid #409eff; /* 蓝色边框代表文件 */
  border-radius: 6px;
  position: relative;
  margin-bottom: 20px;
  padding: 20px 10px 10px 10px;
  background-color: #fff;
}

.mmdet-file-title {
  position: absolute;
  top: -12px;
  left: 15px;
  background-color: #fff;
  padding: 0 8px;
  color: #409eff;
  font-weight: bold;
  font-size: 14px;
}

/* 调整原有 config-text 样式，使其在框内更协调 */
.config-text {
  padding-left: 10px;
  font-size: 15px;
  height: 40px;
  line-height: 40px;
  font-weight: 600;
  color: #303133;
  border-left: 4px solid #409eff; /* 左侧加个小竖条增强层级感 */
  margin: 10px 0 10px 15px;
  background: #f5f7fa;
}

.sub-param-box {
  padding-left: 20px;
  border: 1px dashed #e4e7ed; /* 内部改为虚线，区分层级 */
  border-radius: 4px;
  padding: 12px;
  margin-right: 15px;
  margin-left: 15px;
}

</style>
