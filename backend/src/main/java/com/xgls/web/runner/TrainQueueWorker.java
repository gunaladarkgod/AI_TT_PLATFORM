package com.xgls.web.runner;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xgls.web.base.CodeMap;
import com.xgls.web.entity.TrainScript;
import com.xgls.web.entity.TrainTask;
import com.xgls.web.service.TrainScriptService;
import com.xgls.web.service.TrainTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONObject;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrainQueueWorker {
    private final TrainScriptService trainScriptService;
    private final TrainTaskService taskService;
    private final TrainRunnerService trainRunnerService;

    @Value("${sys.queue.auto-dispatch:true}")
    private boolean autoDispatch;

    /** 每2秒扫描队列；没有 RUN 任务时拉起队首任务 */
    @Scheduled(fixedDelay = 2000L, initialDelay = 2000L)
    public void tick() {
        if (!autoDispatch) return;

        // 有任务在跑就跳过
        long running = taskService.count(
                new LambdaQueryWrapper<TrainTask>()
                        .eq(TrainTask::getStatus, CodeMap.TRAIN_TASK_STATUS_RUN));
        if (running > 0) return;

        // 看队首
        Integer headId = TaskQueue.peekHeadId();
        if (headId == null) return;

        TrainTask head = taskService.getById(headId);
        TrainScript script = trainScriptService.getById(Integer.valueOf(head.getType()));
        String algName = script != null ? script.getName() : null;
        if (head == null) {
            TaskQueue.takeIfHead(headId); // 清理脏队列
            return;
        }
        if (!CodeMap.TRAIN_TASK_STATUS_QUEUE.equals(head.getStatus())) {
            TaskQueue.takeIfHead(headId); // 清理异常状态
            return;
        }

        // 原子出队（确保当前线程就是队首执行者）
        if (!TaskQueue.takeIfHead(headId)) {
            return;
        }

        // 分流：mmdet -> Python Runner；其他 -> 旧脚本 startTrain
        if ("mmdet".equalsIgnoreCase(algName)) {
            log.info("Auto-dispatch mmdet task: id={}, runId={}", headId, head.getName());

            // 标记 RUN
            TrainTask upd = new TrainTask();
            upd.setId(headId);
            upd.setStatus(CodeMap.TRAIN_TASK_STATUS_RUN);
            upd.setStarted_date(LocalDateTime.now());
            upd.setUpdated_date(LocalDateTime.now());
            taskService.updateById(upd);

            boolean ok = false;
            String remarkTail;
            try {
                // 同步等待 Python Runner 返回
                String resp = trainRunnerService.startByRunId(head.getName());
                JSONObject jo = JSONUtil.parseObj(resp);
                ok = jo.getBool("ok", false);

                String workDir    = jo.getStr("work_dir");
                String logPath    = jo.getStr("log"); // 避免遮蔽 @Slf4j 的 log
                // 兼容老/新字段：result_text vs results_txt
                String resultsTxt = jo.getStr("results_txt");
                if (resultsTxt == null) {
                    resultsTxt = jo.getStr("result_text");
                }

                try {
                    taskService.saveCocoResultFromRunner(head, jo);
                } catch (Exception e) {
                    // 这里用 @Slf4j 的 log，不会被局部变量遮蔽
                    log.warn("saveCocoResultFromRunner failed, id={}, name={}, err={}", headId, head.getName(), e.toString());
                }

                remarkTail = (ok ? "python-runner:success" : "python-runner:error")
                        + (workDir    != null ? (", workDir=" + workDir) : "")
                        + (logPath    != null ? (", log=" + logPath) : "")
                        + (resultsTxt != null ? (", result=" + resultsTxt) : "");
            } catch (Exception e) {
                ok = false;
                remarkTail = "python-runner:exception:" + e.getMessage();
            }


            // 收尾 FINISH
            TrainTask fin = new TrainTask();
            fin.setId(headId);
            fin.setFinish_date(LocalDateTime.now());
            fin.setRun_state(ok ? CodeMap.TRAIN_FINISH_SUCCESS : CodeMap.TRAIN_FINISH_ERROR);
            fin.setStatus(CodeMap.TRAIN_TASK_STATUS_FINISH);
            try {
                String base = head.getRemark();
                fin.setRemark((base == null || base.isEmpty()) ? remarkTail : (base + "; " + remarkTail));
            } catch (Exception ignore) {}
            taskService.updateById(fin);
        } else {
            // 非 mmdet：沿用原脚本执行（startTrain 内部负责 RUN/FINISH 状态）
            log.info("Auto-dispatch legacy task: id={}, type={}", headId, head.getType());
            taskService.startTrain(headId);
        }
    }
}
