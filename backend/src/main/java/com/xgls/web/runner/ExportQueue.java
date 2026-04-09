package com.xgls.web.runner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.xgls.web.service.EngineTaskService;
import com.xgls.web.vo.MyTask;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExportQueue {
    private static final LinkedBlockingQueue<MyTask> queue = new LinkedBlockingQueue<>();
    private static List<MyTask> taskList = new ArrayList<>();
    private static final Lock lock = new ReentrantLock();

    public static boolean addTask(MyTask task) {
        lock.lock();
        if (taskList.size() >= 100) {// 最多 100个排序
            return false;
        }
        try {
            if (taskList.add(task)) {
                reorderTasks();
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /** 任务置顶,返回优先级 */
    public static Long topTask(Integer taskId) {
        if (taskList.size() == 0) {
            return null;
        }
        lock.lock();
        try {
            MyTask taskUpdate = taskList.stream()
                    .filter(task -> task.getId().equals(taskId))
                    .findFirst()
                    .orElse(null);
            if (taskUpdate != null) {
                long min = taskList.get(0).getPriority();
                taskUpdate.setPriority(min - 100);
                reorderTasks();
                return min - 100;
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    public static boolean cancelTask(Integer taskId) {
        lock.lock();
        try {
            // 找到并移除任务
            MyTask taskToRemove = taskList.stream()
                    .filter(task -> task.getId().equals(taskId))
                    .findFirst()
                    .orElse(null);

            if (taskToRemove != null) {
                taskList.remove(taskToRemove);
                queue.remove(taskToRemove);
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public static void reorderTasks() {
        Collections.sort(taskList);
        queue.clear();
        queue.addAll(taskList);
    }

    public static List<MyTask> getTasks() {
        lock.lock();
        try {
            return new ArrayList<>(taskList);
        } finally {
            lock.unlock();
        }
    }

    public static List<MyTask> getTaskList() {
        lock.lock();
        try {
            return new ArrayList<>(taskList);
        } finally {
            lock.unlock();
        }
    }

    public static void processTasks(EngineTaskService engineTaskService) {
        while (true) {
            try {
                MyTask task = queue.take(); // 阻塞直到有任务可用
                taskList.remove(task);
                // 处理任务
                try {
                    engineTaskService.startExport(task);
                } catch (Exception e) {
                    log.error("Failed to process task {}: {}", task.getId(), e.getMessage(), e);
                    continue; // 当前示例中直接继续处理下一个任务
                }
                try {
                    Thread.sleep(1000L);
                } catch (Exception e) {

                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 处理中断
                log.error("export task  interrupt err:{}", e.getMessage());
                break;
            }
        }
    }

}
