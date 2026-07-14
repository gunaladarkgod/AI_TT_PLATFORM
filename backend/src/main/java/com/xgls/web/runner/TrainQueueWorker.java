package com.xgls.web.runner;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xgls.web.base.CodeMap;
import com.xgls.web.entity.TrainScript;
import com.xgls.web.entity.TrainTask;
import com.xgls.web.entity.TrainResult;
import com.xgls.web.service.TrainScriptService;
import com.xgls.web.service.TrainTaskService;
import com.xgls.web.service.TrainResultService;
import com.xgls.web.wscontroller.WsTrainController;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrainQueueWorker {
    private final TrainScriptService trainScriptService;
    private final TrainTaskService taskService;
    private final TrainRunnerService trainRunnerService;
    private final TrainResultService trainResultService;

    @Value("${sys.queue.auto-dispatch:true}")
    private boolean autoDispatch;

    @Value("${sys.queue.prepare-timeout-minutes:60}")
    private long prepareTimeoutMinutes;

    /** 每2秒扫描队列；没有 RUN 任务时拉起队首任务 */
    @Scheduled(fixedDelay = 2000L, initialDelay = 2000L)
    public void tick() {
        if (!autoDispatch) return;

        failTimedOutPreparingTasks();
        reconcileCompletedRuns();

        // 有任务在跑就跳过
        long running = taskService.count(
                new LambdaQueryWrapper<TrainTask>()
                        .eq(TrainTask::getStatus, CodeMap.TRAIN_TASK_STATUS_RUN));
        if (running > 0) return;

        // 看队首
        Integer headId = TaskQueue.peekHeadId();
        if (headId == null) return;

        TrainTask head = taskService.getById(headId);
        if (head == null) {
            TaskQueue.takeIfHead(headId); // 清理脏队列
            return;
        }
        TrainScript script = null;
        try {
            script = trainScriptService.getById(Integer.valueOf(head.getType()));
        } catch (Exception ignore) {
            // 自定义 fixed runner 任务的 type 可以是 custom/自定义，不对应 train_script 数字主键。
        }
        String algName = script != null ? script.getName() : null;
        if (!CodeMap.TRAIN_TASK_STATUS_QUEUE.equals(head.getStatus())) {
            TaskQueue.takeIfHead(headId); // 清理异常状态
            return;
        }

        // 原子出队（确保当前线程就是队首执行者）
        if (!TaskQueue.takeIfHead(headId)) {
            return;
        }

        // 分流：mmdet/original 与自定义/fixed 都走 Python Runner；其他 -> 旧脚本 startTrain
        boolean runnerBacked = "mmdet".equalsIgnoreCase(algName) || "custom".equalsIgnoreCase(algName)
                || "自定义".equalsIgnoreCase(algName) || "custom".equalsIgnoreCase(head.getType())
                || "自定义".equalsIgnoreCase(head.getType());
        if (runnerBacked) {
            log.info("Auto-dispatch runner-backed task: id={}, runId={}, algName={}", headId, head.getName(), algName);

            // 标记 RUN
            TrainTask upd = new TrainTask();
            upd.setId(headId);
            upd.setStatus(CodeMap.TRAIN_TASK_STATUS_RUN);
            upd.setStarted_date(LocalDateTime.now());
            upd.setUpdated_date(LocalDateTime.now());
            taskService.updateById(upd);
            upd.setMsg_type(CodeMap.SCRIPT_TYPE_TRAIN);
            WsTrainController.senMsgToAll(JSONUtil.toJsonStr(upd));

            boolean ok = false;
            String remarkTail;
            try {
                // 同步等待 Python Runner 返回
                RunnerTrainResponse runnerResp = trainRunnerService.startByRunId(
                        head.getName(), taskService.runnerOptionsForTask(head.getId()));
                ok = runnerResp.isOk();
                remarkTail = taskService.applyRunnerResult(head, runnerResp, "queueWorker");
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
            if (!taskService.updateById(fin)) {
                log.error("Failed to finalize train task: id={}, runId={}, ok={}", headId, head.getName(), ok);
            } else {
                fin.setMsg_type(CodeMap.SCRIPT_TYPE_TRAIN);
                WsTrainController.senMsgToAll(JSONUtil.toJsonStr(fin));
            }
            // 备注与状态分开更新，防止 varchar(255) 超长导致整条状态收尾 SQL 失败。
            try {
                String base = head.getRemark();
                String merged = (base == null || base.isEmpty()) ? remarkTail : (base + "; " + remarkTail);
                TrainTask remarkUpdate = new TrainTask();
                remarkUpdate.setId(headId);
                remarkUpdate.setRemark(merged.length() <= 255 ? merged : merged.substring(0, 252) + "...");
                taskService.updateById(remarkUpdate);
            } catch (Exception e) {
                log.warn("Failed to append train remark: id={}, runId={}, detail={}",
                        headId, head.getName(), e.getMessage());
            }
        } else {
            // 非 mmdet：沿用原脚本执行（startTrain 内部负责 RUN/FINISH 状态）
            log.info("Auto-dispatch legacy task: id={}, type={}", headId, head.getType());
            taskService.startTrain(headId);
        }
    }

    /**
     * 兜底修复：任务已有本轮正式结果且 Runner 已返回时，状态不应继续停留在 RUN。
     * 这也能自动解除历史版本因备注超长造成的队列堵塞。
     */
    private void reconcileCompletedRuns() {
        List<TrainTask> runningTasks = taskService.list(
                new LambdaQueryWrapper<TrainTask>()
                        .eq(TrainTask::getStatus, CodeMap.TRAIN_TASK_STATUS_RUN));
        for (TrainTask task : runningTasks) {
            TrainResult latest = trainResultService.getOne(
                    new LambdaQueryWrapper<TrainResult>()
                            .eq(TrainResult::getTaskId, task.getId())
                            .ge(task.getStarted_date() != null, TrainResult::getTime, task.getStarted_date())
                            .orderByDesc(TrainResult::getTime)
                            .last("LIMIT 1"), false);
            if (latest == null) continue;

            TrainTask recovered = new TrainTask();
            recovered.setId(task.getId());
            recovered.setStatus(CodeMap.TRAIN_TASK_STATUS_FINISH);
            recovered.setRun_state(CodeMap.TRAIN_FINISH_SUCCESS);
            recovered.setFinish_date(latest.getTime());
            if (taskService.updateById(recovered)) {
                log.warn("Reconciled completed train task from result: id={}, runId={}, resultId={}",
                        task.getId(), task.getName(), latest.getId());
                recovered.setMsg_type(CodeMap.SCRIPT_TYPE_TRAIN);
                WsTrainController.senMsgToAll(JSONUtil.toJsonStr(recovered));
            }
        }
    }

    private void failTimedOutPreparingTasks() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(Math.max(1L, prepareTimeoutMinutes));
        List<TrainTask> timedOut = taskService.list(
                new LambdaQueryWrapper<TrainTask>()
                        .eq(TrainTask::getStatus, CodeMap.TRAIN_TASK_STATUS_DEFAULT)
                        .lt(TrainTask::getUpdated_date, cutoff));
        for (TrainTask task : timedOut) {
            LambdaUpdateWrapper<TrainTask> update = new LambdaUpdateWrapper<>();
            update.eq(TrainTask::getId, task.getId())
                    .eq(TrainTask::getStatus, CodeMap.TRAIN_TASK_STATUS_DEFAULT)
                    .set(TrainTask::getStatus, CodeMap.TRAIN_TASK_STATUS_CFG_FAIL)
                    .set(TrainTask::getUpdated_date, LocalDateTime.now());
            if (taskService.update(update)) {
                TrainTask message = new TrainTask();
                message.setId(task.getId());
                message.setStatus(CodeMap.TRAIN_TASK_STATUS_CFG_FAIL);
                message.setUpdated_date(LocalDateTime.now());
                message.setMsg_type(CodeMap.SCRIPT_TYPE_TRAIN);
                WsTrainController.senMsgToAll(JSONUtil.toJsonStr(message));
                log.warn("Preparing train task timed out: id={}, name={}, timeoutMinutes={}",
                        task.getId(), task.getName(), prepareTimeoutMinutes);
            }
        }
    }
}
