package com.xgls.web.service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xgls.web.base.CodeMap;
import com.xgls.web.entity.EngineLabel;
import com.xgls.web.entity.EngineProject;
import com.xgls.web.entity.EngineTask;
import com.xgls.web.entity.TrainLabel;
import com.xgls.web.mapper.EngineLabelMapper;
import com.xgls.web.mapper.EngineProjectMapper;
import com.xgls.web.mapper.EngineTaskMapper;
import com.xgls.web.mapper.TrainLableMapper;
import com.xgls.web.utils.CvatApiUtil;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class WebhookService {
    @Value("${sys.root-upload}")
    String rootPath;
    @Autowired
    EngineTaskMapper engineTaskMapper;
    @Autowired
    EngineProjectMapper engineProjectMapper;
    @Autowired
    EngineLabelMapper engineLabelMapper;
    @Autowired
    TrainLableMapper trainLableMapper;

    public boolean onCreateTask(JSONObject json) {
        EngineTask task = JSONUtil.toBean(json, EngineTask.class);
        // 转换名称
        EngineTask.parseTName(task);
        log.info("create: {}", task);
        Integer project_id = task.getProject_id();
        // 必须project下的task才同步
        if (task.getId() == null || project_id == null) {
            return false;
        }
        // 写入数据库
        boolean flg = engineTaskMapper.insert(task) > 0;
        EngineProject project = engineProjectMapper.selectById(project_id);
        if (project == null) {
            // 需要同步project的信息
            ResponseEntity<String> res = CvatApiUtil.getProject(project_id);
            if (res.getStatusCode().is2xxSuccessful()) {
                project = JSONUtil.toBean(res.getBody(), EngineProject.class);
                EngineProject.parsePName(project);
                engineProjectMapper.insert(project);
            }
        }
        return flg;
    }

    public void onUpdateTask(JSONObject json, JSONObject before_json) {
        if (before_json.containsKey("status") || before_json.containsKey("size")) {
            EngineTask task = JSONUtil.toBean(json, EngineTask.class);
            // 转换名称
            EngineTask.parseTName(task);
            EngineTask exist = engineTaskMapper.selectById(task.getId());
            boolean flg = false;
            if (exist == null) {
                flg = onCreateTask(json);
            } else {
                flg = engineTaskMapper.updateById(task) > 0;
            }
            if (flg) {// 更新
                JSONObject new_json = new JSONObject();
                before_json.keySet().forEach(key -> {
                    new_json.set(key, json.get(key));
                });
                log.info("update task:  {} <---- {}", new_json, before_json);
            }
        }
    }

    public void onDelTask(JSONObject json, JSONObject sender) {
        Integer task_id = json.getInt("id");
        if (task_id == null) {
            return;
        }
        String username = sender.getStr("username");
        EngineTask task = new EngineTask();
        task.setId(task_id);
        task.setDel_flg(CodeMap.CVAT_DEL_FLG);
        task.setDel_time(LocalDateTime.now());
        task.setDel_user(username);
        engineTaskMapper.updateById(task);
    }

    public boolean onCreateProject(JSONObject json) {
        EngineProject project = JSONUtil.toBean(json, EngineProject.class);
        // 转换名称
        EngineProject.parsePName(project);
        log.info("create: {}", project);
        return engineProjectMapper.insert(project) > 0;
    }

    public void onUpdateProject(JSONObject json, JSONObject before_json) {
        Integer project_id = json.getInt("id");
        if (project_id == null) {
            return;
        }
        int size = before_json.keySet().size();
        if (size > 1) {
            EngineProject project = JSONUtil.toBean(json, EngineProject.class);
            // 转换名称
            EngineProject.parsePName(project);
            engineProjectMapper.updateById(project);
            // /** 打印更新内容,如果label监听后,就不需要在这里同步了 */
            // JSONObject new_json = new JSONObject();
            // before_json.keySet().forEach(key -> {
            // new_json.set(key, json.get(key));
            // });
            // log.info("update project: {} <---- {}", new_json, before_json);
            // /** 同步标签 */
            // syncCvatService.syncLabels(project_id);
        }
    }

    public void onDelProject(JSONObject json, JSONObject sender) {
        Integer project_id = json.getInt("id");
        if (project_id == null) {
            return;
        }
        String username = sender.getStr("username");
        EngineProject project = new EngineProject();
        project.setId(project_id);
        project.setDel_flg(CodeMap.CVAT_DEL_FLG);
        project.setDel_time(LocalDateTime.now());
        project.setDel_user(username);
        engineProjectMapper.updateById(project);
        /** 所有关联的task也要修改状态 */
        LambdaUpdateWrapper<EngineTask> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(EngineTask::getProject_id, project_id);
        EngineTask record = new EngineTask();
        record.setDel_flg(CodeMap.CVAT_DEL_FLG);
        record.setDel_time(project.getDel_time());
        record.setDel_user(username);
        engineTaskMapper.update(record, wrapper);
        /** 根据labels.json进行最后导出标签的恢复 */
        File LabelFile = Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_CVAT_TASK, project_id.toString(),
                "labels.json").toFile();
        if (LabelFile.exists()) {//
            String content = FileUtil.readString(LabelFile, StandardCharsets.UTF_8);
            JSONObject body = new JSONObject(content);
            List<EngineLabel> list = body.getBeanList("results", EngineLabel.class);
            list.forEach(label -> {
                try {
                    if (engineLabelMapper.insert(label) > 0) {
                        // 查询label name 是否存相同的训练原始标签;如果不存在,需要插入
                        LambdaQueryWrapper<TrainLabel> wp = new LambdaQueryWrapper<>();
                        wp.eq(TrainLabel::getName, label.getName()).eq(TrainLabel::getMerge, CodeMap.LABEL_IS_ORG);
                        if (!trainLableMapper.exists(wp)) {
                            TrainLabel tl = new TrainLabel();
                            tl.setMerge(CodeMap.LABEL_IS_ORG);
                            tl.setName(label.getName());
                            trainLableMapper.insert(tl);
                        }
                    }
                } catch (Exception e) {
                }
            });
        }
    }

    public void onCreateLabel(JSONObject jsonObject) {
        EngineLabel label = JSONUtil.toBean(jsonObject, EngineLabel.class);
        if (engineLabelMapper.insert(label) > 0) {
            // 查询label name 是否存相同的训练原始标签;如果不存在,需要插入
            LambdaQueryWrapper<TrainLabel> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(TrainLabel::getName, label.getName()).eq(TrainLabel::getMerge, CodeMap.LABEL_IS_ORG);
            if (!trainLableMapper.exists(wrapper)) {
                TrainLabel tl = new TrainLabel();
                tl.setMerge(CodeMap.LABEL_IS_ORG);
                tl.setName(label.getName());
                trainLableMapper.insert(tl);
            }
        }
    }

    public void onUpdateLabel(JSONObject jsonObject) {
        EngineLabel lable = JSONUtil.toBean(jsonObject, EngineLabel.class);
        Integer id = lable.getId();
        String name = lable.getName();// 新名称
        // 先查当前旧的标签
        EngineLabel old_label = engineLabelMapper.selectById(id);
        if (old_label == null) {
            return;
        }
        String old_name = old_label.getName();// 旧名称
        if (engineLabelMapper.updateById(lable) > 0) {
            // 只有名称改变了,才需要更新训练标签
            if (!StrUtil.equals(name, old_name)) {
                Integer project_id = lable.getProject_id();
                // 检查新旧name是否存在于其他项目中
                boolean new_exist = nameInOtherProject(name, project_id);
                boolean old_exist = nameInOtherProject(old_name, project_id);
                // 如果新的存在,意味着训练标签不用再添加了
                if (new_exist) {
                    if (!old_exist) {
                        // 如果不存在,代表需要删除旧的old_name记录
                        LambdaQueryWrapper<TrainLabel> wrapper = new LambdaQueryWrapper<>();
                        wrapper.eq(TrainLabel::getName, old_name);
                        trainLableMapper.delete(wrapper);
                    }
                } else {
                    // 新的需要添加
                    if (old_exist) {// 新的需要添加,而且旧的不能删除
                        TrainLabel tl = new TrainLabel();
                        tl.setMerge(CodeMap.LABEL_IS_ORG);
                        tl.setName(name);
                        trainLableMapper.insert(tl);
                    } else { // 新的需要添加,旧的要删除,那么可以把旧的替换成新的
                        LambdaUpdateWrapper<TrainLabel> wrapper = new LambdaUpdateWrapper<>();
                        wrapper.set(TrainLabel::getName, name);
                        wrapper.eq(TrainLabel::getName, old_name).eq(TrainLabel::getMerge, CodeMap.LABEL_IS_ORG);
                        trainLableMapper.update(wrapper);
                    }
                }
            }
        }
    }

    public void onDelLabel(JSONObject jsonObject) {
        EngineLabel lable = JSONUtil.toBean(jsonObject, EngineLabel.class);
        String name = lable.getName();
        if (engineLabelMapper.deleteById(lable.getId()) > 0) {
            // 查询在其他项目中是否有同名name,如果没有,需要在训练标签中删除
            boolean name_exist = nameInOtherProject(name, lable.getProject_id());
            if (!name_exist) {
                LambdaQueryWrapper<TrainLabel> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(TrainLabel::getName, name);
                trainLableMapper.delete(wrapper);
            }
        }
    }

    /**
     * 查询标签名称是否存在于其他项目中
     * 
     * @param name
     * @param project_id
     * @return
     */
    private boolean nameInOtherProject(String name, Integer project_id) {
        LambdaQueryWrapper<EngineLabel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EngineLabel::getName, name).ne(EngineLabel::getProject_id, project_id);
        return engineLabelMapper.exists(wrapper);
    }
}
