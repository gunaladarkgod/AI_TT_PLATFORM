// src/main/java/com/xgls/web/runner/TaskQueue.java
package com.xgls.web.runner;

import com.xgls.web.service.TrainTaskService;
import com.xgls.web.vo.MyTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TaskQueue {
    private static final LinkedBlockingQueue<MyTask> queue = new LinkedBlockingQueue<>();
    private static final List<MyTask> taskList = new ArrayList<>();
    private static final Lock lock = new ReentrantLock();

    /** 入队并按 priority 重排（最多100个） */
    public static boolean addTask(MyTask task) {
        lock.lock();
        try {
            if (taskList.size() >= 100) return false;
            if (taskList.add(task)) {
                reorderTasksUnsafe();
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /** 置顶：把 priority 调成最小，再整体重排 */
    public static Long topTask(Integer taskId) {
        lock.lock();
        try {
            if (taskList.isEmpty()) return null;
            MyTask t = taskList.stream().filter(x -> x.getId().equals(taskId)).findFirst().orElse(null);
            if (t == null) return null;
            long min = taskList.get(0).getPriority();
            t.setPriority(min - 100);
            reorderTasksUnsafe();
            return min - 100;
        } finally {
            lock.unlock();
        }
    }

    /** 取消：从队列与列表移除对应任务 */
    public static boolean cancelTask(Integer taskId) {
        lock.lock();
        try {
            MyTask t = taskList.stream().filter(x -> x.getId().equals(taskId)).findFirst().orElse(null);
            if (t == null) return false;
            taskList.remove(t);
            queue.remove(t);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** 返回队首任务 id（仅窥视，不出队） */
    public static Integer peekHeadId() {
        lock.lock();
        try {
            if (taskList.isEmpty()) return null;
            return taskList.get(0).getId();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 若当前队首就是指定 id，则原子地将其出队（从 taskList 与 queue 同时移除），返回 true；否则返回 false。
     * 供 TrainRunnerService.runFromQueue 做“队首校验 + 原子出队”。
     */
    public static boolean takeIfHead(Integer taskId) {
        lock.lock();
        try {
            if (taskList.isEmpty()) return false;
            MyTask head = taskList.get(0);
            if (!head.getId().equals(taskId)) return false;
            taskList.remove(0);
            // 重新构建 queue（保持与 taskList 一致）
            rebuildQueueUnsafe();
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** 只读快照 */
    public static List<MyTask> getTasks() {
        lock.lock();
        try {
            return new ArrayList<>(taskList);
        } finally {
            lock.unlock();
        }
    }

    /** 与 getTasks 同义（兼容旧代码） */
    public static List<MyTask> getTaskList() {
        return getTasks();
    }

    /** 旧实现：顺序消费。现在建议停用，改用 TrainQueueWorker + TrainRunnerService */
    @Deprecated
    public static void processTasks(TrainTaskService trainTaskService) {
        while (true) {
            try {
                MyTask task = queue.take();
                taskList.remove(task);

                // 保持老调用，不再调用 startTrainByQueue
                trainTaskService.startTrain(task.getId());

                try { Thread.sleep(1000L); } catch (Exception ignore) {}
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /* ----------------- 内部工具 ----------------- */

    /** 排序 + 重建 queue（需在持有 lock 时调用） */
    private static void reorderTasksUnsafe() {
        Collections.sort(taskList);
        rebuildQueueUnsafe();
    }

    /** 用 taskList 重建 queue（需在持有 lock 时调用） */
    private static void rebuildQueueUnsafe() {
        queue.clear();
        queue.addAll(taskList);
    }
}
