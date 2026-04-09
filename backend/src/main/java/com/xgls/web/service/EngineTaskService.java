package com.xgls.web.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.zip.ZipInputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xgls.web.base.AjaxResult;
import com.xgls.web.base.CodeMap;
import com.xgls.web.entity.EngineProject;
import com.xgls.web.entity.EngineTask;
import com.xgls.web.mapper.EngineProjectMapper;
import com.xgls.web.mapper.EngineTaskMapper;
import com.xgls.web.secontroller.CustomEvent;
import com.xgls.web.utils.CvatApiUtil;
import com.xgls.web.utils.ImageUtil;
import com.xgls.web.utils.MyUtils;
import com.xgls.web.utils.TransFormUtils;
import com.xgls.web.vo.MyTask;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.exceptions.UtilException;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class EngineTaskService extends ServiceImpl<EngineTaskMapper, EngineTask> {
    @Value("${sys.root-upload}")
    String rootPath;
    @Value("${sys.encode-src:false}")
    boolean encodeSrc;
    @Autowired
    SyncCvatService syncCvatService;
    @Autowired
    EngineProjectMapper engineProjectMapper;
    @Autowired
    ApplicationEventPublisher eventPublisher;

    public boolean saveLink(EngineTask task) {
        if (baseMapper.insert(task) > 0) {
            Integer project_id = task.getProject_id();
            EngineProject project = engineProjectMapper.selectById(project_id);
            if (project == null) {
                // 需要同步project的信息
                ResponseEntity<String> res = CvatApiUtil.getProject(project_id);
                if (res.getStatusCode().is2xxSuccessful()) {
                    project = JSONUtil.toBean(res.getBody(), EngineProject.class);
                    EngineProject.parsePName(project);
                    // 同步标签
                    if (engineProjectMapper.insert(project) > 0) {
                        try {
                            syncCvatService.syncLabels(project_id);
                        } catch (Exception e) {
                        }
                    }
                }
            }
            return true;
        }
        return false;
    }

    public List<EngineTask> getDistinctFields(String field, Integer project_id) {
        QueryWrapper<EngineTask> wrapper = new QueryWrapper<>();
        wrapper.select("DISTINCT " + field).isNotNull(field).ne(field, "");
        if (project_id != null) {
            wrapper.eq("project_id", project_id);
        }
        return baseMapper.selectList(wrapper);
    }

    /** 处理导出任务 */
    public void startExport(MyTask task) {
        Integer id = task.getId();
        EngineTask engineTask = baseMapper.selectById(id);
        if (engineTask == null) {
            return;
        }
        // publishEvent(engineTask);
        EngineTask task1 = new EngineTask();
        task1.setId(id);
        task1.setExport_status(CodeMap.EXPORT_STATUS_RUN);
        task1.setExport_time(LocalDateTime.now());
        baseMapper.updateById(task1);
        publishEvent(task1);

        Integer project_id = engineTask.getProject_id();
        boolean save_image = StrUtil.equals(task.getName(), "1");
        // 先创建任务
        AjaxResult res = CvatApiUtil.initDatasetExport(id, CodeMap.DATASET_FORMAT, save_image);
        if (!res.isSuccess()) {
            // 失败了,更新状态
            handleFail(id);
            return;
        }
        String downLoadurl = null;
        JSONObject json = new JSONObject(res.getData().toString());
        String rq_id = json.getStr("rq_id");
        if (rq_id == null) {
            handleFail(id);
            return;
        }
        // 等待3秒,给cvat最低的准备时间
        MyUtils.sleep(3000L);
        // 开始轮询任务
        for (int i = 0; i < 1000; i++) {
            res = CvatApiUtil.queryRequestStatus(rq_id);
            if (!res.isSuccess()) {
                handleFail(id);
                return;
            }
            json = new JSONObject(res.getData());
            String status = json.getStr("status");
            if (StrUtil.equals("failed", status)) {
                handleFail(id);
                return;
            } else if (StrUtil.equals("finished", status)) {
                downLoadurl = json.getStr("result_url");
                break;
            } else {
                MyUtils.sleep(5000L);
            }
        }
        if (downLoadurl == null) {
            handleFail(id);
            return;
        }
        Path tempFilePath = Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TEMP,
                id + "_" + DateTime.now().getTime() + ".zip");
        try {
            tempFilePath = Files.createFile(tempFilePath);
        } catch (IOException e) {
            handleFail(id);
            return;
        }

        res = CvatApiUtil.downloadFile(downLoadurl, tempFilePath.toString());
        log.info("down file: {},msg:{},data:{}", res.getCode(), res.getMsg(), res.getData());
        if (!res.isSuccess()) {
            handleFail(id);
            return;
        }

        Path dir = Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_CVAT_TASK, project_id.toString(), id.toString());

        try (ZipInputStream in = new ZipInputStream(
                Files.newInputStream(tempFilePath))) {
            // 先删除
            if (save_image) {
                FileUtil.del(dir);
            }
            ZipUtil.unzip(in, dir.toFile());
            EngineTask record = new EngineTask();
            record.setId(id);
            if (save_image) {
                record.setExport_img(CodeMap.EXPROTED_YES);
            }
            // 获取第一张图片的名称
            record.setFirst_img(MyUtils.getFirstFileName(dir.resolve(CodeMap.DIR_TRAIN_IMAGES)));
            record.setExport_time(LocalDateTime.now());
            record.setExport_status(CodeMap.EXPORT_STATUS_SUCCESS);
            if (baseMapper.updateById(record) > 0) {
                // 执行igor的转换处理脚本
                ImageUtil.processIgor(dir.toString());
            }
            // 转换图片
            log.info("encodeSrc:{}", encodeSrc);
            if (encodeSrc) {
                TransFormUtils.INSTANCE.transform_directory(dir.resolve(CodeMap.DIR_TRAIN_IMAGES).toString());
            }
            publishEvent(record);
            // 保存最新的project对应的labels,便于删除项目后的标记恢复
            saveLablesFile(project_id);
        } catch (UtilException | IOException e) {
            handleFail(id);
        } finally {
            try {
                Files.delete(tempFilePath);
            } catch (IOException e) {
                log.warn("del temFile fail[{}]:{}", tempFilePath, e.getMessage());
            }
        }

    }

    /**
     * 保存project对应的标签,用于以后项目删除时的恢复
     * 
     * @param project_id
     */
    private void saveLablesFile(Integer project_id) {
        try {
            ResponseEntity<String> res = CvatApiUtil.getLabelList(project_id);
            if (res.getStatusCode().is2xxSuccessful()) {
                FileUtil.writeString(res.getBody(),
                        Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_CVAT_TASK, project_id.toString(), "labels.json")
                                .toFile(),
                        StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("project_lables save fial:{}", e.getMessage());
        }
    }

    private void handleFail(Integer id) {
        EngineTask task = new EngineTask();
        task.setId(id);
        task.setExport_status(CodeMap.EXPORT_STATUS_FAIL);
        baseMapper.updateById(task);
        // 推送结果
        publishEvent(task);
    }

    // 推送消息
    public void publishEvent(EngineTask task) {
        eventPublisher.publishEvent(new CustomEvent(task.getId(), JSONUtil.toJsonStr(task)));
    }
}
