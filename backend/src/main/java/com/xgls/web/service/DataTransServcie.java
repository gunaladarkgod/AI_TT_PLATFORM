package com.xgls.web.service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import com.xgls.web.base.CodeMap;
import com.xgls.web.entity.EngineTask;
import com.xgls.web.entity.TrainScript;
import com.xgls.web.mapper.EngineTaskMapper;
import com.xgls.web.mapper.TrainScriptMapper;
import com.xgls.web.secontroller.CustomEvent;
import com.xgls.web.utils.MyUtils;
import com.xgls.web.utils.StreamGobbler;
import com.xgls.web.vo.DataTransParams;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import java.nio.file.Paths;

@Service
@Slf4j
public class DataTransServcie {
    @Value("${sys.root-upload}")
    String rootPath;

    @Autowired
    EngineTaskMapper engineTaskMapper;
    @Autowired
    TrainScriptMapper tScriptMapper;
    @Autowired
    ApplicationEventPublisher eventPublisher;

    // 推送消息
    public void publishEvent(EngineTask task) {
        eventPublisher.publishEvent(new CustomEvent(task.getId(), JSONUtil.toJsonStr(task)));
    }

    @Async
    public void startDataTrans(DataTransParams params, EngineTask task) {
        Integer id = params.getId();
        /** 检查算法 */
        TrainScript script = tScriptMapper.selectById(params.getAlg_id());
        params.setAlg_name(script.getName());
        log.info("^_* start data trans:#{}:{}", id, script.getName());
        Path basePath = Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_CVAT_TASK, task.getProject_id().toString(),
                id.toString());
        String suff = script.getSuff();
        Path scriptPath = Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_SCRIPT, script.getId() + suff);
        // 拓展参数
        JSONObject ext_params = params.getParams();
        ProcessBuilder pBuilder = null;
        String cmd = script.getCmd();
        String out_dir = "data_trans" + File.separator + MyUtils.parseTime2(DateTime.now());
        Path cfg_path = basePath.resolve(out_dir).resolve("cfg.json");
        /** 先创建 */
        FileUtil.mkParentDirs(cfg_path);
        /** 保存cfg */
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(Files.newOutputStream(cfg_path), "UTF-8"))) {
            writer.write(JSONUtil.toJsonPrettyStr(params));
        } catch (Exception e) {
            log.warn("save data trans cfg.json err:{}",
                    e.getMessage());
            updateStopStatus(id, CodeMap.STATE_DATA_TRANS_DEFAULT);
            return;
        }
        if (StrUtil.equals(".sh", suff)) {
            switch (cmd) {
                case "python":
                    pBuilder = new ProcessBuilder(
                            "bash",
                            scriptPath.toString(), // 脚本地址 .sh
                            id.toString(), // train_id
                            "1",
                            cmd, // python
                            script.getMain(),
                            "--task_id", id.toString(),
                            "--imgs_dir", basePath.resolve("images").toString(),
                            "--annotations_file", basePath.resolve("annotations.xml").toString(),
                            "--out_dir", basePath.resolve(out_dir).toString());
                    /** 拓展自定义参数 */
                    for (String key : ext_params.keySet()) {
                        String val = ext_params.getStr(key);
                        pBuilder.command().add("--" + key);
                        pBuilder.command().add(val);
                    }
                    break;
                default:
                    log.warn("script cmd not support:{}  ", cmd);
                    updateStopStatus(id, CodeMap.STATE_DATA_TRANS_DEFAULT);
                    return;
            }
        } else if (StrUtil.equals(".bat", suff)) {
            switch (cmd) {
                case "python":
                    pBuilder = new ProcessBuilder(
                            "cmd.exe",
                            "/c",
                            scriptPath.toString(), // .bat文件地址
                            id.toString(), // train_id
                            "1", // node 分布式
                            cmd, // python
                            script.getMain(), //
                            "--task_id", id.toString(),
                            "--imgs_dir", basePath.resolve("images").toString(),
                            "--annotations_file", basePath.resolve("annotations.xml").toString(),
                            "--out_dir", basePath.resolve(out_dir).toString());
                    /** 拓展自定义参数 */
                    for (String key : ext_params.keySet()) {
                        String val = ext_params.getStr(key);
                        pBuilder.command().add("--" + key);
                        pBuilder.command().add(val);
                    }
                    break;

                default:
                    log.warn("script cmd not support:{}  ", cmd);
                    updateStopStatus(id, CodeMap.STATE_DATA_TRANS_DEFAULT);
                    return;
            }
        } else {
            log.warn("script suff not support:{}  ", suff);
            updateStopStatus(id, CodeMap.STATE_DATA_TRANS_DEFAULT);
            return;
        }
        log.info("start data trans: {}", pBuilder.command());
        pBuilder.directory(basePath.toFile());
        /** 更新状态 */
        updateStopStatus(id, CodeMap.STATE_DATA_TRANS_RUNNING);
        Process cuProcess = null;
        try {
            // 启动进程
            cuProcess = pBuilder.start();
            StreamGobbler errorGobbler = new StreamGobbler(cuProcess.getErrorStream(), CodeMap.SCRIPT_TYPE_DATA);
            StreamGobbler outputGobbler = new StreamGobbler(cuProcess.getInputStream(), CodeMap.SCRIPT_TYPE_DATA);
            errorGobbler.start();
            outputGobbler.start();

            // 等待进程结束并获取退出码
            int exitCode = cuProcess.waitFor();
            log.info("data trans process fininsh :{}", exitCode);

        } catch (IOException | InterruptedException e) {
            log.warn("data trans err:{}", e.getMessage());
        } finally {
            // 结束确认
            stopTask(cuProcess);
            // 最后更新状态
            updateStopStatus(id, CodeMap.STATE_DATA_TRANS_DEFAULT);
        }
    }

    private void updateStopStatus(Integer id, Integer state) {
        EngineTask record = new EngineTask();
        record.setId(id);
        record.setData_trans(state);
        engineTaskMapper.updateById(record);
        // 发送状态改变消息
        publishEvent(record);
    }

    public void stopTask(Process process) {
        try {
            if (process != null) {
                ProcessHandle handle = process.toHandle();
                handle.descendants().forEach(item -> {
                    item.destroy();
                });
                boolean flg = handle.destroy();
                log.info("stop data trans :[{}]", flg);
            }
        } catch (Exception e) {
        } finally {
            process = null;
        }
    }

}
