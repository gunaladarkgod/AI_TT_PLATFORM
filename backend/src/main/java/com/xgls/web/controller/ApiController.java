package com.xgls.web.controller;

import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xgls.web.base.AjaxResult;
import com.xgls.web.base.CodeMap;
import com.xgls.web.base.ErrorCode;
import com.xgls.web.entity.EngineTask;
import com.xgls.web.service.EngineTaskService;
import com.xgls.web.service.WebhookService;
import com.xgls.web.utils.SessionUtil;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;

@Tag(name = "API管理-外部系统调用")
@RestController
@RequestMapping("/api")
@Slf4j
public class ApiController {
    @Value("${sys.encode-src:false}")
    boolean encodeSrc;
    @Value("${sys.root-upload}")
    String rootPath;
    @Value("${sys.tensorboard.url:6008}")
    String tensorboardUrl;
    @Autowired
    WebhookService webhookService;
    @Autowired
    EngineTaskService engineTaskService;

    @RequestMapping("webhook/project")
    public AjaxResult project(@RequestBody String body) {
        JSONObject json = new JSONObject(body);
        String event = json.getStr("event");
        switch (event) {
            case "create:project":
                webhookService.onCreateProject(json.getJSONObject("project"));
                break;
            case "update:project":
                webhookService.onUpdateProject(json.getJSONObject("project"), json.getJSONObject("before_update"));
                break;
            case "delete:project":
                webhookService.onDelProject(json.getJSONObject("project"), json.getJSONObject("sender"));
                break;
            case "create:label":
                webhookService.onCreateLabel(json.getJSONObject("label"));
                break;
            case "update:label":
                webhookService.onUpdateLabel(json.getJSONObject("label"));
                break;
            case "delete:label":
                webhookService.onDelLabel(json.getJSONObject("label"));
                break;
            default:
                break;
        }
        return AjaxResult.success();
    }

    @RequestMapping("webhook/task")
    public AjaxResult task(@RequestBody String body) {
        JSONObject json = new JSONObject(body);
        String event = json.getStr("event");
        switch (event) {
            case "create:task":
                webhookService.onCreateTask(json.getJSONObject("task"));
                break;
            case "update:task":
                webhookService.onUpdateTask(json.getJSONObject("task"), json.getJSONObject("before_update"));
                break;
            case "delete:task":
                webhookService.onDelTask(json.getJSONObject("task"), json.getJSONObject("sender"));
                break;
            default:
                break;
        }
        return AjaxResult.success();
    }

    @PostMapping("/tensorboard/url")
    public AjaxResult getTensorboardUrl() {
        JSONObject jo = new JSONObject();
        jo.set("url", tensorboardUrl);
        jo.set("iswin", FileUtil.isWindows());
        return AjaxResult.success(jo);
    }

    @GetMapping("reset/firstImage")
    public AjaxResult getMethodName() {
        if (!SessionUtil.isAdminOrHeigh()) {
            return AjaxResult.error(ErrorCode.AUTH_FAILED);
        }

        LambdaQueryWrapper<EngineTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(EngineTask::getFirst_img);
        List<EngineTask> list = engineTaskService.list(wrapper);
        List<Integer> idList = list.stream().filter(task -> {
            return !Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_CVAT_TASK, task.getProject_id().toString(),
                    task.getId().toString(), CodeMap.DIR_TRAIN_IMAGES, task.getFirst_img()).toFile().exists();
        }).map(item -> item.getId()).toList();

        if (!idList.isEmpty()) {
            LambdaUpdateWrapper<EngineTask> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.in(EngineTask::getId, idList);
            updateWrapper.set(EngineTask::getFirst_img, null);
            updateWrapper.set(EngineTask::getExport_img, null);
            boolean flg = engineTaskService.update(updateWrapper);
            if (flg) {
                return AjaxResult.success("update:" + idList.size());
            }
            return AjaxResult.error();
        } else {
            return AjaxResult.success("do not need update");
        }
    }

    /** 获取系统信息 */
    @PostMapping("/sys")
    public AjaxResult getSysInfo() {
        JSONObject jo = new JSONObject();
        jo.set("encode_src", encodeSrc);
        return AjaxResult.success(jo);
    }
}
