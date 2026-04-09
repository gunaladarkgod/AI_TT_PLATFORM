package com.xgls.web.service;

import java.io.IOException;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xgls.web.base.CodeMap;
import com.xgls.web.entity.TrainScript;
import com.xgls.web.entity.TrainTask;
import com.xgls.web.mapper.TrainScriptMapper;
import com.xgls.web.mapper.TrainTaskMapper;
import com.xgls.web.utils.StreamGobbler;
import com.xgls.web.vo.ValParams;
import com.xgls.web.wscontroller.WsTrainController;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import java.nio.file.Paths;

@Service
@Slf4j
public class PredictTaskService extends ServiceImpl<TrainTaskMapper, TrainTask> {
    @Value("${sys.root-upload}")
    String rootPath;

    @Autowired
    TrainScriptMapper tScriptMapper;

    @Async
    public void startPredictTask(ValParams params) {
        Integer id = params.getId();
        TrainTask task = baseMapper.selectById(id);
        String type = task.getType();
        TrainScript script = tScriptMapper.selectById(type);
        String expName = params.getRun_name();
        String predicName = params.getPredict_name();

        log.info("^_* start predict:#{}:{}", id);
        Path tPath = Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TRAIN_TASK, id.toString());
        Path weightPath = tPath.resolve("run/" + expName + "/weights/" + params.getWeights());
        Path predictPath = tPath.resolve(CodeMap.SCRIPT_TYPE_PREDICT + "/" + predicName);
        Path dataPath = predictPath.resolve("src");

        String suff = script.getSuff();
        Path scriptPath = Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_SCRIPT, script.getId() + suff);

        String device = params.getDevice();
        Integer node = device.split(",").length;
        // 拓展参数
        JSONObject ext_params = new JSONObject();
        String e_p = params.getExt_params();
        if (StrUtil.isNotBlank(e_p) && JSONUtil.isTypeJSONObject(e_p)) {
            ext_params = new JSONObject(e_p);
        }

        ProcessBuilder pBuilder = null;
        String cmd = script.getCmd();
        if (StrUtil.equals(".sh", suff)) {
            switch (cmd) {
                case "yolo":
                    pBuilder = new ProcessBuilder(
                            "bash",
                            scriptPath.toString(), // 脚本地址 .sh
                            id.toString(), // train_id
                            node.toString(), // 分布式
                            cmd, // yolo
                            "predict",
                            "source=" + dataPath.toString(),
                            "model=" + weightPath.toString(),
                            "device=" + device,
                            "imgsz=" + params.getImg_size(),
                            "conf=" + params.getConf_thres(),
                            "project=" + predictPath.toString(),
                            "name=" + "res",
                            "line_width=1",
                            "exist_ok=True");
                    if (params.getSave_txt()) {
                        pBuilder.command().add("save_txt=True");
                    }
                    /** 拓展自定义参数 */
                    for (String key : ext_params.keySet()) {
                        String val = ext_params.getStr(key);
                        pBuilder.command().add(key + "=" + val);
                    }
                    break;
                case "python":
                    pBuilder = new ProcessBuilder(
                            "bash",
                            scriptPath.toString(), // 脚本地址 .sh
                            id.toString(), // train_id
                            node.toString(),
                            cmd, // python or yolo
                            script.getDetect(), // val.py 地址
                            "--source", dataPath.toString(),
                            "--weights", weightPath.toString(),
                            "--device", device,
                            "--img-size", params.getImg_size().toString(),
                            "--conf-thres", params.getConf_thres().toString(),
                            "--project", predictPath.toString(),
                            "--name", "res",
                            "--line-thickness", "1",
                            "--exist-ok");
                    if (params.getSave_txt()) {
                        pBuilder.command().add("--save-txt");
                    }
                    /** 拓展自定义参数 */
                    for (String key : ext_params.keySet()) {
                        String val = ext_params.getStr(key);
                        pBuilder.command().add("--" + key);
                        if (!val.equalsIgnoreCase("true")) {
                            pBuilder.command().add(val);
                        }

                    }
                    break;
                default:
                    log.warn("script cmd not support:{}  ", cmd);
                    updateStopStatus(id, CodeMap.STATE_READY);
                    return;
            }
        } else if (StrUtil.equals(".bat", suff)) {
            switch (cmd) {
                case "yolo":
                    pBuilder = new ProcessBuilder(
                            "cmd.exe",
                            "/c",
                            scriptPath.toString(), // .bat文件位置
                            id.toString(), // train_id
                            node.toString(), // 分布式
                            cmd, // python or yolo
                            "predict",
                            "source::" + dataPath.toString(),
                            "model::" + weightPath.toString(),
                            "device::" + device,
                            "imgsz::" + params.getImg_size(),
                            "conf::" + params.getConf_thres(),
                            "project::" + predictPath.toString(),
                            "name::" + "res",
                            "line_width::1",
                            "exist_ok::True");
                    if (params.getSave_txt()) {
                        pBuilder.command().add("save_txt::True");
                    }
                    /** 拓展自定义参数 */
                    for (String key : ext_params.keySet()) {
                        String val = ext_params.getStr(key);
                        pBuilder.command().add(key + "::" + val);
                    }
                    break;
                case "python":
                    pBuilder = new ProcessBuilder(
                            "cmd.exe",
                            "/c",
                            scriptPath.toString(), // .bat文件地址
                            id.toString(), // train_id
                            node.toString(), // node 分布式
                            cmd, // python or yolo
                            script.getDetect(), // val.py 地址
                            "--source", dataPath.toString() + "/",
                            "--weights", weightPath.toString(),
                            "--device", device,
                            "--img-size", params.getImg_size().toString(),
                            "--conf-thres", params.getConf_thres().toString(),
                            "--project", predictPath.toString(),
                            "--name", "res",
                            "--line-thickness", "1",
                            "--exist-ok"); // 指定expName
                    if (params.getSave_txt()) {
                        pBuilder.command().add("--save-txt");
                    }
                    /** 拓展自定义参数 */
                    for (String key : ext_params.keySet()) {
                        String val = ext_params.getStr(key);
                        pBuilder.command().add("--" + key);
                        if (!val.equalsIgnoreCase("true")) {
                            pBuilder.command().add(val);
                        }
                    }
                    break;

                default:
                    log.warn("script cmd not support:{}  ", cmd);
                    updateStopStatus(id, CodeMap.STATE_READY);
                    return;
            }
        } else {
            log.warn("script suff not support:{}  ", suff);
            updateStopStatus(id, CodeMap.STATE_READY);
            return;
        }

        log.info("start predict: {}", pBuilder.command());
        pBuilder.directory(tPath.toFile());
        /** 更新状态 */
        updateStopStatus(id, CodeMap.STATE_RUNNING);
        Process cuProcess = null;
        try {
            // 启动进程
            cuProcess = pBuilder.start();
            StreamGobbler errorGobbler = new StreamGobbler(cuProcess.getErrorStream(), CodeMap.SCRIPT_TYPE_PREDICT);
            StreamGobbler outputGobbler = new StreamGobbler(cuProcess.getInputStream(), CodeMap.SCRIPT_TYPE_PREDICT);
            errorGobbler.start();
            outputGobbler.start();

            // 等待进程结束并获取退出码
            int exitCode = cuProcess.waitFor();
            log.info("predict process fininsh :{}", exitCode);

        } catch (IOException | InterruptedException e) {
            log.warn("predict err:{}", e.getMessage());
        } finally {
            // 结束确认
            stopTrain(cuProcess);
            // 最后更新状态
            updateStopStatus(id, CodeMap.STATE_READY);
        }
    }

    private void updateStopStatus(Integer id, Integer state) {
        TrainTask record = new TrainTask();
        record.setId(id);
        record.setPredict_state(state);
        baseMapper.updateById(record);
        record.setMsg_type(CodeMap.SCRIPT_TYPE_PREDICT);
        // 发送状态改变消息
        WsTrainController.senMsgToAll(JSONUtil.toJsonStr(record));
    }

    public void stopTrain(Process process) {
        try {
            if (process != null) {
                ProcessHandle handle = process.toHandle();
                handle.descendants().forEach(item -> {
                    item.destroy();
                });
                boolean flg = handle.destroy();
                log.info("stop predict:[{}]", flg);
            }
        } catch (Exception e) {
        } finally {
            process = null;
        }
    }

}
