package com.xgls.web.runner;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xgls.web.base.CodeMap;
import com.xgls.web.entity.EngineTask;
import com.xgls.web.entity.ModelTrans;
import com.xgls.web.entity.TrainTask;
import com.xgls.web.entity.TrainResult;
import com.xgls.web.service.EngineTaskService;
import com.xgls.web.service.ModelTransService;
import com.xgls.web.service.TrainTaskService;
import com.xgls.web.service.TrainResultService;
import com.xgls.web.vo.MyTask;

import lombok.extern.slf4j.Slf4j;

/**
 * 初始化创建系统必要的文件夹
 */
@Component
@Slf4j
@Order(4)
public class TaskInit implements ApplicationRunner {
  @Autowired
  EngineTaskService engineTaskService;

  @Autowired
  TrainTaskService trainTaskService;

  @Autowired
  TrainResultService trainResultService;

  @Autowired
  TrainRunnerService trainRunnerService;

  @Autowired
  ModelTransService modelTransService;

  @Override
  public void run(ApplicationArguments args) throws Exception {
    // 数据转换状态切换成默认
    LambdaUpdateWrapper<EngineTask> wrapper_data = new LambdaUpdateWrapper<>();
    wrapper_data.set(EngineTask::getData_trans, CodeMap.STATE_DATA_TRANS_DEFAULT);
    wrapper_data.eq(EngineTask::getData_trans, CodeMap.STATE_DATA_TRANS_RUNNING);
    engineTaskService.update(wrapper_data);

    // 清理非正常关闭的转换状态
    LambdaUpdateWrapper<ModelTrans> wrapper_trans = new LambdaUpdateWrapper<>();
    wrapper_trans.eq(ModelTrans::getStatus, CodeMap.MODEL_TRANS_STATUS_RUN);
    wrapper_trans.set(ModelTrans::getStatus, CodeMap.MODEL_TRANS_STATUS_FINISH);
    wrapper_trans.set(ModelTrans::getEndtime, LocalDateTime.now());
    modelTransService.update(wrapper_trans);

    // 配置生成是内存异步任务，后端重启后无法续跑；遗留的 PREPARING 必须明确标记为配置出错。
    LambdaUpdateWrapper<TrainTask> stalePreparing = new LambdaUpdateWrapper<>();
    stalePreparing.eq(TrainTask::getStatus, CodeMap.TRAIN_TASK_STATUS_DEFAULT);
    stalePreparing.set(TrainTask::getStatus, CodeMap.TRAIN_TASK_STATUS_CFG_FAIL);
    stalePreparing.set(TrainTask::getUpdated_date, LocalDateTime.now());
    trainTaskService.update(stalePreparing);

    // 清理上次异常退出遗留的 RUN 状态。若本轮已有正式结果，按成功恢复；否则终止残留 Runner 并标记失败。
    List<TrainTask> staleRunning = trainTaskService.list(
        new LambdaQueryWrapper<TrainTask>().eq(TrainTask::getStatus, CodeMap.TRAIN_TASK_STATUS_RUN));
    for (TrainTask task : staleRunning) {
      TrainResult latestResult = trainResultService.getOne(
          new LambdaQueryWrapper<TrainResult>()
              .eq(TrainResult::getTaskId, task.getId())
              .ge(task.getStarted_date() != null, TrainResult::getTime, task.getStarted_date())
              .orderByDesc(TrainResult::getTime)
              .last("LIMIT 1"), false);
      boolean completed = latestResult != null;
      if (!completed) {
        try {
          trainRunnerService.stopByRunId(task.getName());
        } catch (Exception e) {
          log.warn("stale train process not active: id={}, runId={}, detail={}",
              task.getId(), task.getName(), e.getMessage());
        }
      }
      TrainTask recovered = new TrainTask();
      recovered.setId(task.getId());
      recovered.setStatus(CodeMap.TRAIN_TASK_STATUS_FINISH);
      recovered.setRun_state(completed ? CodeMap.TRAIN_FINISH_SUCCESS : CodeMap.TRAIN_FINISH_ERROR);
      recovered.setFinish_date(completed ? latestResult.getTime() : LocalDateTime.now());
      trainTaskService.updateById(recovered);
      log.warn("recovered stale train task: id={}, runId={}, completed={}",
          task.getId(), task.getName(), completed);
    }

    // 初始化训练队列
    LambdaQueryWrapper<TrainTask> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(TrainTask::getStatus, CodeMap.TRAIN_TASK_STATUS_QUEUE);
    wrapper.orderByAsc(TrainTask::getEnqueue);
    List<TrainTask> list = trainTaskService.list(wrapper);
    for (int i = 0; i < list.size(); i++) {
      TrainTask item = list.get(i);
      TaskQueue.addTask(new MyTask(item.getId(), item.getName(), item.getEnqueue()));
    }
    log.info("train task queue init:{}", list.size());
    // 训练队列仅由 TrainQueueWorker 消费；不要再启动旧 TaskQueue.processTasks 线程，避免双消费者并发覆盖状态。

  }

}
