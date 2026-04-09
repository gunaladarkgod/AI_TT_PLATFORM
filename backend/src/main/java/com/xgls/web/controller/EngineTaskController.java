package com.xgls.web.controller;

import java.io.IOException;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xgls.web.base.AjaxResult;
import com.xgls.web.base.CodeMap;
import com.xgls.web.base.ErrorCode;
import com.xgls.web.entity.EngineTask;
import com.xgls.web.runner.ExportQueue;
import com.xgls.web.service.DataTransServcie;
import com.xgls.web.service.EngineTaskService;
import com.xgls.web.service.RedisService;
import com.xgls.web.service.UserProjectService;
import com.xgls.web.utils.CvatApiUtil;
import com.xgls.web.utils.ImageUtil;
import com.xgls.web.utils.MyUtils;
import com.xgls.web.utils.SessionUtil;
import com.xgls.web.vo.DataTransParams;
import com.xgls.web.vo.MyTask;
import com.xgls.web.vo.query.EngineTaskQuery;

import cn.hutool.core.collection.ConcurrentHashSet;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.exceptions.UtilException;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Tag(name = "标注任务管理")
@RestController
@RequestMapping("/engineTask")
@Slf4j
public class EngineTaskController {
    static ConcurrentHashSet<Integer> exportSet = new ConcurrentHashSet<>();

    @Value("${sys.root-upload}")
    String rootPath;
    @Autowired
    EngineTaskService engineTaskService;
    @Autowired
    RedisService redisService;
    @Autowired
    UserProjectService userProjectService;
    @Autowired
    DataTransServcie dataTransServcie;

    @Operation(summary = "分页获取列表", description = "分页获取列表")
    @PostMapping("list")
    public AjaxResult queryList(EngineTaskQuery query) {
        /** 查询条件 */
        LambdaQueryWrapper<EngineTask> wrapper = new LambdaQueryWrapper<>();

        if (query.getId() != null) {
            wrapper.eq(EngineTask::getId, query.getId());
        }
        String pids = query.getProject_ids();
        if (pids != null && ReUtil.isMatch(CodeMap.RE_IDS, pids)) {
            List<Integer> arr = new ArrayList<>();
            for (String pid : pids.split(",")) {
                arr.add(Integer.parseInt(pid));
            }
            if (!arr.isEmpty()) {
                wrapper.in(EngineTask::getProject_id, arr);
            }
        }

        if (query.getProject_id() != null) {
            wrapper.eq(EngineTask::getProject_id, query.getProject_id());
        }
        if (StrUtil.isNotBlank(query.getStatus())) {
            wrapper.eq(EngineTask::getStatus, query.getStatus());
        }
        // 删除标记
        if (query.getDel_flg() != null && query.getDel_flg() != -1) {
            wrapper.eq(EngineTask::getDel_flg, query.getDel_flg());
        }

        String a_s = query.getA_s();
        if (StrUtil.isNotBlank(a_s)) {
            String[] arr_s = a_s.split(",");
            if (arr_s.length > 1) {
                wrapper.in(EngineTask::getA_s, (Object[]) arr_s);
            } else {
                wrapper.eq(EngineTask::getA_s, a_s);
            }
        }

        String a_r = query.getA_r();
        if (StrUtil.isNotBlank(a_r)) {
            String[] arr_r = a_r.split(",");
            if (arr_r.length > 1) {
                wrapper.in(EngineTask::getA_r, (Object[]) arr_r);
            } else {
                wrapper.eq(EngineTask::getA_r, a_r);
            }
        }

        String a_v = query.getA_v();
        if (StrUtil.isNotBlank(a_v)) {
            String[] arr_v = a_v.split(",");
            if (arr_v.length > 1) {
                wrapper.in(EngineTask::getA_v, (Object[]) arr_v);
            } else {
                wrapper.eq(EngineTask::getA_v, a_v);
            }
        }

        String a_p = query.getA_p();
        if (StrUtil.isNotBlank(a_p)) {
            String[] arr_p = a_p.split(",");
            if (arr_p.length > 1) {
                wrapper.in(EngineTask::getA_p, (Object[]) arr_p);
            } else {
                wrapper.eq(EngineTask::getA_p, a_p);
            }
        }

        String a_a = query.getA_a();
        if (StrUtil.isNotBlank(a_a)) {
            String[] arr_a = a_a.split(",");
            if (arr_a.length > 1) {
                wrapper.in(EngineTask::getA_a, (Object[]) arr_a);
            } else {
                wrapper.eq(EngineTask::getA_a, a_a);
            }
        }

        String a_e = query.getA_e();
        if (StrUtil.isNotBlank(a_e)) {
            String[] arr_e = a_e.split(",");
            if (arr_e.length > 1) {
                wrapper.in(EngineTask::getA_e, (Object[]) arr_e);
            } else {
                wrapper.eq(EngineTask::getA_e, a_e);
            }
        }

        if (StrUtil.isNotBlank(query.getStart_time()) && StrUtil.isNotBlank(query.getEnd_time())) {
            wrapper.between(EngineTask::getA_t, query.getStart_time(), query.getEnd_time());
        }
        /** 模糊检索 */
        if (StrUtil.isNotBlank(query.getA_n())) {
            wrapper.like(EngineTask::getA_n, query.getA_n());
        }
        /** project限制 */
        if (!SessionUtil.isAdminOrHeigh()) {
            Long userId = SessionUtil.getCurUserId();
            List<Integer> ids = userProjectService.getProjects(userId);
            if (ids.isEmpty()) {
                return AjaxResult.success(Page.of(query.getCurrent(), query.getSize(), 0));
            }
            wrapper.in(EngineTask::getProject_id, ids);
        }

        /** 分页信息 */
        Long current = query.getCurrent();
        Long size = query.getSize();
        if (current == null) {
            current = CodeMap.PAGE_NO_DEFAULT;
        }
        if (size == null) {
            size = CodeMap.PAGE_SIZE_DEFAULT;
        }
        Page<EngineTask> page = new Page<>(current, size);

        /** 排序信息 */
        List<OrderItem> orders = query.getOrders();
        if (orders != null && !orders.isEmpty()) {
            page.addOrder(orders);
        } else {
            page.addOrder(OrderItem.descs("id"));
        }
        return AjaxResult.success(engineTaskService.page(page, wrapper));
    }

    @Operation(summary = "获取所有标注任务", description = "获取所有标注任务")
    @PostMapping("all")
    public AjaxResult queryAll(EngineTaskQuery query) {
        Integer project_id = query.getProject_id();
        if (project_id == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        /** 查询条件 */
        LambdaQueryWrapper<EngineTask> wrapper = new LambdaQueryWrapper<>();
        /** project */
        wrapper.eq(EngineTask::getProject_id, project_id);
        /** 状态 */
        if (StrUtil.isNotBlank(query.getStatus())) {
            wrapper.eq(EngineTask::getStatus, query.getStatus());
        }
        /** 删除标记 */
        if (query.getDel_flg() != null && query.getDel_flg() != -1) {
            wrapper.eq(EngineTask::getDel_flg, query.getDel_flg());
        }
        /** 导出状态 */
        if (query.getExport_img() != null) {
            wrapper.eq(EngineTask::getExport_img, query.getExport_img());
        }
        /** 名称模糊检索 */
        if (StrUtil.isNotBlank(query.getA_a())) {
            wrapper.like(EngineTask::getA_a, query.getA_a());
        }
        /** 按创建时间倒排 */
        wrapper.orderByDesc(EngineTask::getCreated_date);

        return AjaxResult.success(engineTaskService.list(wrapper));
    }

    @Operation(summary = "添加", description = "添加")
    @PostMapping("add")
    public AjaxResult add(Integer id) {
        EngineTask task = engineTaskService.getById(id);
        if (task != null) {
            return AjaxResult.error("任务已存在");
        }
        ResponseEntity<String> res = CvatApiUtil.getTask(id);
        if (res.getStatusCode().is2xxSuccessful()) {
            task = JSONUtil.toBean(res.getBody(), EngineTask.class);
            EngineTask.parseTName(task);
            return engineTaskService.saveLink(task) ? AjaxResult.success() : AjaxResult.error();
        }
        return AjaxResult.error();
    }

    @Operation(summary = "修改", description = "修改")
    @PostMapping("update")
    public AjaxResult update(EngineTask record) {
        Integer id = record.getId();
        if (id == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        EngineTask task = new EngineTask();
        task.setId(id);
        task.setA_n(record.getA_n());
        task.setA_s(record.getA_s());
        task.setA_r(record.getA_r());
        task.setA_t(record.getA_t());
        task.setA_v(record.getA_v());
        task.setA_p(record.getA_p());
        task.setA_a(record.getA_a());
        task.setA_e(record.getA_e());

        return engineTaskService.updateById(task) ? AjaxResult.success() : AjaxResult.error();
    }

    @Operation(summary = "删除", description = "删除")
    @PostMapping("del")
    public AjaxResult del(Integer id) {
        log.warn("del task:{},userId:{}", id, SessionUtil.getCurUserId());
        EngineTask task = engineTaskService.getById(id);
        if (task == null) {
            return AjaxResult.error("任务不存在");
        }
        boolean flg = engineTaskService.removeById(id);
        if (flg) {
            // 删除目录
            try {
                FileUtil.del(
                        Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_CVAT_TASK,
                                task.getProject_id().toString(),
                                id.toString()));
            } catch (Exception e) {
                log.warn("del err:{}", e);
            }
        }
        return flg ? AjaxResult.success() : AjaxResult.error();
    }

    /**
     * 
     * @param id         任务id
     * @param format     导出格式
     * @param save_image 是否包含图片
     * @return
     */
    @Operation(summary = "导出数据", description = "导出数据")
    // @PostMapping("export/dataset")
    @Deprecated
    public AjaxResult exportDataset(Integer id, String format, Boolean save_image) {
        if (exportSet.contains(id)) {
            return AjaxResult.error("数据导出正在进行中");
        }

        EngineTask task = engineTaskService.getById(id);
        if (task == null) {
            exportSet.remove(id);
            return AjaxResult.error("任务不存在");
        }
        Integer project_id = task.getProject_id();
        if (project_id == null) {
            exportSet.remove(id);
            return AjaxResult.error("project_id 为空");
        }
        /** 必须完结状态才能导数据,暂时放开 */
        // if (!StrUtil.equals(task.getStatus(), "completed")) {
        // return AjaxResult.error("任务未完成");
        // }
        // 如果没有图片没有导出过,必须先导出
        if (StrUtil.isBlank(task.getFirst_img())) {
            save_image = true;
        }
        if (save_image == null) {
            save_image = false;
        }
        // 先创建任务
        AjaxResult res = CvatApiUtil.initDatasetExport(id, format, save_image);
        if (!res.isSuccess()) {
            exportSet.remove(id);
            return AjaxResult.error(res.getMsg());
        }
        // 加入
        exportSet.add(id);
        String downLoadurl = null;

        JSONObject json = new JSONObject(res.getData().toString());
        log.info(json.toStringPretty());
        String rq_id = json.getStr("rq_id");
        if (rq_id == null) {
            exportSet.remove(id);
            return AjaxResult.error("rq_id is null");
        }
        // 等待3秒,给cvat最低的准备时间
        MyUtils.sleep(3000L);
        // 开始轮询任务
        for (int i = 0; i < 1000; i++) {
            res = CvatApiUtil.queryRequestStatus(rq_id);
            if (!res.isSuccess()) {
                exportSet.remove(id);
                return AjaxResult.error(res.getMsg());
            }
            json = new JSONObject(res.getData());
            String status = json.getStr("status");
            if (StrUtil.equals("failed", status)) {
                exportSet.remove(id);
                return AjaxResult.error("request failed");
            } else if (StrUtil.equals("finished", status)) {
                downLoadurl = json.getStr("result_url");
                break;
            } else {
                MyUtils.sleep(5000L);
            }
        }
        if (downLoadurl == null) {
            exportSet.remove(id);
            return AjaxResult.error();
        }
        Path tempFilePath = Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TEMP,
                id + "_" + DateTime.now().getTime() + ".zip");
        try {
            tempFilePath = Files.createFile(tempFilePath);
        } catch (IOException e) {
            return AjaxResult.error();
        }

        log.info("down-url:{}", downLoadurl);
        log.info("down-path:{}", tempFilePath);
        res = CvatApiUtil.downloadFile(downLoadurl, tempFilePath.toString());
        log.info("down file: {},msg:{},data:{}", res.getCode(), res.getMsg(), res.getData());
        if (!res.isSuccess()) {
            exportSet.remove(id);
            return AjaxResult.error(res.getMsg());
        }

        Path dir = Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_CVAT_TASK, project_id.toString(), id.toString());

        try (ZipInputStream in = new ZipInputStream(
                Files.newInputStream(tempFilePath))) {
            ZipUtil.unzip(in, dir.toFile());
            EngineTask record = new EngineTask();
            record.setId(id);
            if (save_image) {
                record.setExport_img(CodeMap.EXPROTED_YES);
            }
            // 获取第一张图片的名称
            record.setFirst_img(MyUtils.getFirstFileName(dir.resolve(CodeMap.DIR_TRAIN_IMAGES)));
            record.setExport_time(LocalDateTime.now());
            engineTaskService.updateById(record);
            // 执行igor的转换处理脚本
            ImageUtil.processIgor(dir.toString());
            return AjaxResult.success();
        } catch (UtilException | IOException e) {
            return AjaxResult.error();
        } finally {
            exportSet.remove(id);
            try {
                Files.delete(tempFilePath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Operation(summary = "创建导出数据任务", description = "创建导出数据任务")
    @PostMapping("export/enqueue")
    public AjaxResult exportEnqueue(Integer id, String format, Boolean save_image) {
        EngineTask task = engineTaskService.getById(id);
        if (task == null) {
            return AjaxResult.error("任务不存在");
        }
        format = CodeMap.DATASET_FORMAT;
        Integer export_status = task.getExport_status();
        if (export_status != null) {
            if (export_status.equals(CodeMap.EXPORT_STATUS_RUN)) {
                return AjaxResult.error("导出任务正在执行中");
            }
            if (export_status.equals(CodeMap.EXPORT_STATUS_QUEUE)) {
                return AjaxResult.error("任务正在排队中");
            }
        }

        Integer project_id = task.getProject_id();
        if (project_id == null) {
            return AjaxResult.error("project_id 为空");
        }
        /** 必须完结状态才能导数据,暂时放开 */
        // 如果没有图片没有导出过,必须先导出
        if (save_image == null) {
            save_image = false;
        }
        if (StrUtil.isBlank(task.getFirst_img())) {
            save_image = true;
        }
        long enqueue = DateTime.now().getTime();
        EngineTask record = new EngineTask();
        record.setId(id);
        record.setExport_status(CodeMap.TRAIN_TASK_STATUS_QUEUE);
        record.setExport_queue(enqueue);
        if (engineTaskService.updateById(record)) {
            if (ExportQueue.addTask(new MyTask(id, save_image ? "1" : "0", enqueue))) {
                engineTaskService.publishEvent(record);
                return AjaxResult.success();
            } else {
                EngineTask task2 = new EngineTask();
                task2.setId(id);
                task2.setExport_status(export_status);
                engineTaskService.updateById(task2);
                return AjaxResult.error();
            }
        }
        return AjaxResult.error();
    }

    @Operation(summary = "任务置顶", description = "任务置顶")
    @PostMapping("export/top")
    public AjaxResult topTask(Integer id, HttpServletRequest request) {
        EngineTask task = engineTaskService.getById(id);
        if (task == null) {
            return AjaxResult.error("任务不存在");
        }
        Integer status = task.getExport_status();
        if (status != CodeMap.EXPORT_STATUS_QUEUE) {
            return AjaxResult.error("任务不在队列中");
        }
        Long enqueu = ExportQueue.topTask(id);
        if (enqueu == null) {
            return AjaxResult.error();
        }
        EngineTask record = new EngineTask();
        record.setId(id);
        record.setExport_queue(enqueu);
        engineTaskService.updateById(record);
        return AjaxResult.success();
    }

    @Operation(summary = "取消任务", description = "取消任务")
    @PostMapping("export/cancel")
    public AjaxResult cancelTask(Integer id, HttpServletRequest request) {
        EngineTask task = engineTaskService.getById(id);
        if (task == null) {
            return AjaxResult.error("任务不存在");
        }
        Integer status = task.getExport_status();
        if (status != CodeMap.EXPORT_STATUS_QUEUE) {
            return AjaxResult.error("任务不在队列中");
        }
        if (!ExportQueue.cancelTask(id)) {
            return AjaxResult.error();
        }
        EngineTask record = new EngineTask();
        record.setId(id);
        record.setExport_status(CodeMap.EXPORT_STATUS_DEFAULT);
        engineTaskService.updateById(record);
        return AjaxResult.success();
    }

    @Operation(summary = "同步单个", description = "同步单个")
    @PostMapping("sync/one")
    public AjaxResult syncOne(Integer id) {
        ResponseEntity<String> res = CvatApiUtil.getTask(id);
        if (res.getStatusCode().is2xxSuccessful()) {
            EngineTask task = JSONUtil.toBean(res.getBody(), EngineTask.class);
            EngineTask.parseTName(task);
            return engineTaskService.updateById(task) ? AjaxResult.success() : AjaxResult.error();
        } else {
            String body = res.getBody();
            if (StrUtil.equals(body, CodeMap.CVAT_HAS_DEL)) {
                EngineTask task = new EngineTask();
                LambdaUpdateWrapper<EngineTask> wrapper = new LambdaUpdateWrapper<>();
                wrapper.eq(EngineTask::getId, id).ne(EngineTask::getDel_flg, CodeMap.CVAT_DEL_FLG);
                task.setId(id);
                task.setDel_flg(CodeMap.CVAT_DEL_FLG);
                engineTaskService.update(task, wrapper);
                log.info("async task[{}]: task has del", id);
            }
        }
        return AjaxResult.error(res.getBody());
    }

    @Operation(summary = "获取项目指定属性的范围列表", description = "获取项目指定属性的范围列表")
    @PostMapping("distinct")
    public AjaxResult getDistinctFields(String field, Integer project_id) {
        if (StrUtil.isBlank(field)) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        List<EngineTask> list = engineTaskService.getDistinctFields(field, project_id);
        return AjaxResult.success(list);
    }

    @Operation(summary = "更新所有标注任务的首图信息", description = "更新所有标注任务的首图信息")
    @PostMapping("/update/first")
    public AjaxResult updateFirstName() {
        LambdaQueryWrapper<EngineTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(EngineTask::getFirst_img);
        List<EngineTask> list = engineTaskService.list(wrapper);
        for (int i = 0; i < list.size(); i++) {
            EngineTask task = list.get(i);
            EngineTask record = new EngineTask();
            record.setId(task.getId());
            Path dir = Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_CVAT_TASK,
                    task.getProject_id().toString(),
                    task.getId().toString(), CodeMap.DIR_TRAIN_IMAGES);
            record.setFirst_img(MyUtils.getFirstFileName(dir));
            log.info("{}:{}", task.getId(), task.getFirst_img());
            engineTaskService.updateById(record);
        }
        return AjaxResult.success(list.size() + "");
    }

    @Operation(summary = "清理全部标记删除的任务", description = "清理全部标记删除的任务")
    @PostMapping("clear/del")
    public AjaxResult cleartDel() {
        if (!SessionUtil.isAdminOrHeigh()) {
            return AjaxResult.error(ErrorCode.AUTH_FAILED);
        }
        log.info("clear cvat has deled start");
        LambdaQueryWrapper<EngineTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EngineTask::getDel_flg, CodeMap.CVAT_DEL_FLG);
        List<EngineTask> list = engineTaskService.list(wrapper);

        List<Integer> ids = list.stream().map(item -> item.getId()).toList();
        boolean flg = engineTaskService.removeByIds(ids);
        if (flg) {
            for (int i = 0; i < list.size(); i++) {
                EngineTask task = list.get(i);
                try {
                    FileUtil.del(
                            Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_CVAT_TASK, task.getProject_id().toString(),
                                    task.getId().toString()));
                } catch (Exception e) {
                    log.warn("del err:{}", e);
                }
            }
        }
        log.info("clear cvat has deled finish:{}", list.size());
        return AjaxResult.success("删除任务数量:" + list.size());
    }

    @PostMapping("/trans")
    public AjaxResult dataTrans(@RequestBody DataTransParams body) {
        log.info("{}", body);
        Integer id = body.getId();
        Integer alg_id = body.getAlg_id();
        if (id == null || alg_id == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        /** 检查task的状态 */
        EngineTask task = engineTaskService.getById(id);
        if (task == null) {
            return AjaxResult.error("任务不存在");
        }
        if (task.getData_trans() == CodeMap.STATE_DATA_TRANS_RUNNING) {
            return AjaxResult.error("转换任务正在进行中");
        }
        /** 检查标签集是否存在 */
        Path basePath = Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_CVAT_TASK, task.getProject_id().toString(),
                task.getId().toString());
        Path xmlPath = basePath.resolve("annotations.xml");
        if (!xmlPath.toFile().exists()) {
            return AjaxResult.error("标签文件不存在,请检查任务是否导入");
        }
        /** 开始转换 */
        EngineTask one = new EngineTask();
        one.setId(id);
        one.setData_trans(CodeMap.STATE_DATA_TRANS_RUNNING);
        if (engineTaskService.updateById(one)) {
            dataTransServcie.startDataTrans(body, task);
            return AjaxResult.success();
        }
        return AjaxResult.error();
    }
}
