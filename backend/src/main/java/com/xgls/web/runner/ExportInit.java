package com.xgls.web.runner;

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
import com.xgls.web.service.EngineTaskService;
import com.xgls.web.vo.MyTask;

import lombok.extern.slf4j.Slf4j;

/**
 * 初始化创建系统必要的文件夹
 */
@Component
@Slf4j
@Order(3)
public class ExportInit implements ApplicationRunner {
  @Autowired
  EngineTaskService engineTaskService;

  @Override
  public void run(ApplicationArguments args) throws Exception {

    // 清理非正常关闭的转换状态
    LambdaUpdateWrapper<EngineTask> wrapper_task = new LambdaUpdateWrapper<>();
    wrapper_task.eq(EngineTask::getExport_status, CodeMap.EXPORT_STATUS_RUN);
    wrapper_task.set(EngineTask::getExport_status, CodeMap.EXPORT_STATUS_FAIL);
    engineTaskService.update(wrapper_task);

    // 初始化训练队列
    LambdaQueryWrapper<EngineTask> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(EngineTask::getExport_status, CodeMap.EXPORT_STATUS_QUEUE);
    wrapper.orderByAsc(EngineTask::getExport_queue);
    List<EngineTask> list = engineTaskService.list(wrapper);
    for (int i = 0; i < list.size(); i++) {
      EngineTask item = list.get(i);
      ExportQueue.addTask(new MyTask(item.getId(), item.getExport_type().toString(), item.getExport_queue()));
    }
    log.info("export queue init:{}", list.size());
    /**
     * 开始消费线程
     */
    Thread processingThread = new Thread(() -> ExportQueue.processTasks(engineTaskService));
    processingThread.setDaemon(true); // 设置为后台线程
    processingThread.start();

  }
}
