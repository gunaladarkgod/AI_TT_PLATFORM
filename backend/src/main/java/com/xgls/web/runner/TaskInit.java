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
import com.xgls.web.service.EngineTaskService;
import com.xgls.web.service.ModelTransService;
import com.xgls.web.service.TrainTaskService;
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
    /**
     * 开始消费线程
     */
    Thread processingThread = new Thread(() -> TaskQueue.processTasks(trainTaskService));
    processingThread.setDaemon(true); // 设置为后台线程
    processingThread.start();

  }

}
