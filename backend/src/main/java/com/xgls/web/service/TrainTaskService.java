// src/main/java/com/xgls/web/service/TrainTaskService.java
package com.xgls.web.service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xgls.web.base.CodeMap;
import com.xgls.web.entity.TrainArgs;
import com.xgls.web.entity.TrainData;
import com.xgls.web.entity.TrainExt;
import com.xgls.web.entity.TrainLabel;
import com.xgls.web.entity.TrainResult;
import com.xgls.web.entity.TrainScript;
import com.xgls.web.entity.TrainTask;
import com.xgls.web.entity.User;
import com.xgls.web.mapper.TrainArgsMapper;
import com.xgls.web.mapper.TrainDataMapper;
import com.xgls.web.mapper.TrainExtMapper;
import com.xgls.web.mapper.TrainScriptMapper;
import com.xgls.web.mapper.TrainTaskMapper;
import com.xgls.web.runner.TrainRunnerService;
import com.xgls.web.utils.StreamGobbler;
import com.xgls.web.vo.TrainForm;
import com.xgls.web.wscontroller.WsTrainController;

import org.apache.ibatis.jdbc.RuntimeSqlException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TrainTaskService extends ServiceImpl<TrainTaskMapper, TrainTask> {
    public static Process cuProcess = null;

    @Value("${sys.root-upload}")
    String rootPath;

    @Autowired private TrainResultService trainResultService;
    @Autowired TrainArgsMapper tArgsMapper;
    @Autowired TrainDataMapper tDataMapper;
    @Autowired TrainScriptMapper tScriptMapper;
    @Autowired TrainExtMapper trainExtMapper;
    @Autowired private TrainRunnerService trainRunnerService;

    @Transactional(rollbackFor = Exception.class)
    public boolean saveLink(TrainForm form, User user, MultipartFile weight_file, Integer clone_from, String ext_params,
                            MultipartFile ext_file, String mmdet_cfg)
            throws IllegalStateException, IOException {
        // 1.插入 TrainTask
        TrainTask task = new TrainTask();
        task.setName(form.getName());
        task.setType(form.getType());
        task.setRemark(form.getRemark());
        task.setCls_num(form.getCls_num());
        task.setPrj_num(form.getPrj_num());
        task.setTask_num(form.getTask_num());
        task.setImg_num(form.getImg_num());
        task.setUsername(user.getUsername());

        LocalDateTime now = LocalDateTime.now();
        task.setCreated_date(now);
        task.setUpdated_date(now);
        task.setStatus(CodeMap.TRAIN_TASK_STATUS_DEFAULT);

        if (baseMapper.insert(task) <= 0) {
            return false;
        }
        form.setId(task.getId());

        // 2.保存训练参数（null 安全）
        TrainArgs args = form.getArgs();
        if (args == null) args = new TrainArgs();
        args.setId(task.getId());
        if (tArgsMapper.insert(args) <= 0) {
            throw new RuntimeSqlException();
        }

        // 3.保存训练集参数（null 安全）
        TrainData data = form.getData();
        if (data == null) data = new TrainData();
        data.setId(task.getId());
        if (tDataMapper.insert(data) <= 0) {
            throw new RuntimeSqlException();
        }

        // 4.保存文件（权重 / 克隆）
        if (weight_file != null) {
            String fileName = String.format("%s_%d%s", CodeMap.FILE_TYPE_WEIGHT, 0, ".pt");
            Path dir = Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TRAIN_TASK, task.getId().toString(),
                    CodeMap.DIR_TRAIN_FILE);
            if (!FileUtil.exist(dir.toString())) {
                FileUtil.mkdir(dir);
            }
            weight_file.transferTo(dir.resolve(fileName));
        } else if (clone_from != null) {
            String fileName = String.format("%s_%d%s", CodeMap.FILE_TYPE_WEIGHT, 0, ".pt");
            Path src = Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TRAIN_TASK, clone_from.toString(),
                    CodeMap.DIR_TRAIN_FILE, fileName);
            Path target = Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TRAIN_TASK, task.getId().toString(),
                    CodeMap.DIR_TRAIN_FILE, fileName);
            FileUtil.copy(src, target, StandardCopyOption.REPLACE_EXISTING);
        }

        // 5.拓展参数
        TrainExt ext = new TrainExt();
        ext.setId(task.getId());
        ext.setUpdate_time(now);
        if (StrUtil.isNotBlank(ext_params) && JSONUtil.isTypeJSONObject(ext_params)) {
            ext.setParams(ext_params);
        }
        if (ext_file != null && !ext_file.isEmpty()) {
            Path dir = Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TRAIN_TASK, task.getId().toString(),
                    CodeMap.DIR_TRAIN_FILE);
            if (!FileUtil.exist(dir.toString())) {
                FileUtil.mkdir(dir);
            }
            String fileName = ext_file.getOriginalFilename();
            ext_file.transferTo(dir.resolve(fileName));
            ext.setFile(fileName);
        }
        if (trainExtMapper.insert(ext) <= 0) {
            throw new RuntimeSqlException();
        }

        // 6.mmdet配置文件
        if (mmdet_cfg != null) {
            FileUtil.writeBytes(mmdet_cfg.getBytes(CharsetUtil.UTF_8),
                    Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TRAIN_TASK, task.getId().toString(),
                            CodeMap.DIR_TRAIN_FILE, "cfg.py").toFile());
        }
        return true;
    }

    /** 从 Python Runner 的 JSON 解析 COCO 指标并落库 */
    public void saveCocoResultFromRunner(TrainTask task, cn.hutool.json.JSONObject jo) {
        if (task == null || jo == null) return;

        // 兼容 results_txt / result_text
        String txt = jo.getStr("results_txt");
        if (StrUtil.isBlank(txt)) {
            txt = jo.getStr("result_text");
        }
        if (StrUtil.isBlank(txt)) {
            // 兜底：尝试读取 results_file
            String resultsFile = jo.getStr("results_file");
            if (StrUtil.isNotBlank(resultsFile)) {
                try {
                    txt = Files.readString(Path.of(resultsFile), StandardCharsets.UTF_8);
                } catch (Exception ignore) {}
            }
        }
        if (StrUtil.isBlank(txt)) {
            log.warn("[train_result] results text empty, skip persist. taskId={}, runId={}", task.getId(), task.getName());
            return; // 没有任何可解析文本就不落库
        }

        // 提取数字：匹配 key 后面的第一个数字
        final String finalTxt = txt;
        java.util.function.Function<String, Double> find = (key) -> {
            Pattern p = Pattern.compile(key + "\\s*:\\s*([-+]?[0-9]*\\.?[0-9]+)", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(finalTxt);
            if (m.find()) {
                try { return Double.parseDouble(m.group(1)); } catch (Exception ignore) {}
            }
            return null;
        };

        Double mAP  = find.apply("mAP");
        Double ap50 = find.apply("AP50");
        Double ap75 = find.apply("AP75");
        Double aps  = find.apply("APs");
        Double apm  = find.apply("APm");
        Double apl  = find.apply("APl");   // large

        // === 新增：读取 network_name ===
        String networkName = null;
        try {
            com.xgls.web.entity.TrainExt ext = trainExtMapper.selectById(task.getId());
            if (ext != null && StrUtil.isNotBlank(ext.getParams()) && JSONUtil.isTypeJSONObject(ext.getParams())) {
                cn.hutool.json.JSONObject extJo = JSONUtil.parseObj(ext.getParams());
                networkName = extJo.getStr("network_name");
            }
        } catch (Exception ignore) {}

        TrainResult tr = new TrainResult();
        tr.setTaskId(task.getId());
        tr.setTaskName(task.getName());
        tr.setUserName(task.getUsername());
        tr.setModelType("mmdet");
        tr.setDataset("coco_small"); // 如需真实数据集名称，可在业务链路中补齐
        tr.setTime(LocalDateTime.now());
        tr.setNetworkName(networkName);
        tr.setMap(mAP);
        tr.setAp50(ap50);
        tr.setAp75(ap75);
        tr.setAps(aps);
        tr.setApm(apm);
        // 你的实体如果是 api(APl)，请把下面一行改成 tr.setApi(apl);
        tr.setApl(apl);

        try {
            trainResultService.save(tr);
            log.info("[train_result] saved: taskId={}, runId={}, mAP={}, AP50={}, AP75={}, APs={}, APm={}, APl={}",
                    task.getId(), task.getName(), mAP, ap50, ap75, aps, apm, apl);
        } catch (Exception e) {
            log.error("save train_result failed, taskId={}, name={}", task.getId(), task.getName(), e);
        }
    }

    @Transactional
    public boolean updateLink(TrainForm form, MultipartFile weight_file, String ext_params, MultipartFile ext_file,
                              Boolean ext_file_update, String mmdet_cfg)
            throws IllegalStateException, IOException {
        TrainTask task = new TrainTask();
        task.setId(form.getId());
        task.setName(form.getName());
        task.setType(form.getType());
        task.setRemark(form.getRemark());
        task.setCls_num(form.getCls_num());
        task.setPrj_num(form.getPrj_num());
        task.setTask_num(form.getTask_num());
        task.setImg_num(form.getImg_num());

        LocalDateTime now = LocalDateTime.now();
        task.setUpdated_date(now);
        task.setStatus(CodeMap.TRAIN_TASK_STATUS_DEFAULT);

        if (baseMapper.updateById(task) <= 0) {
            return false;
        }
        // 权重
        if (weight_file != null) {
            String fileName = String.format("%s_%d%s", CodeMap.FILE_TYPE_WEIGHT, 0, ".pt");
            weight_file.transferTo(Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TRAIN_TASK, task.getId().toString(),
                    CodeMap.DIR_TRAIN_FILE, fileName));
        }
        // 训练参数（null 安全 + 简单 upsert）
        TrainArgs args = form.getArgs();
        if (args == null) args = new TrainArgs();
        args.setId(task.getId());
        if (tArgsMapper.updateById(args) <= 0) {
            if (tArgsMapper.insert(args) <= 0) throw new RuntimeSqlException();
        }

        // 数据参数（null 安全 + 简单 upsert）
        TrainData data = form.getData();
        if (data == null) data = new TrainData();
        data.setId(task.getId());
        if (tDataMapper.updateById(data) <= 0) {
            if (tDataMapper.insert(data) <= 0) throw new RuntimeSqlException();
        }

        // 拓展参数/文件
        TrainExt ext = new TrainExt();
        ext.setId(task.getId());
        ext.setUpdate_time(now);
        if (StrUtil.isNotBlank(ext_params) && JSONUtil.isTypeJSONObject(ext_params)) {
            ext.setParams(ext_params);
        } else {
            ext.setParams("");
        }
        Path dir = Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TRAIN_TASK, task.getId().toString(),
                CodeMap.DIR_TRAIN_FILE);
        if (!FileUtil.exist(dir.toString())) {
            FileUtil.mkdir(dir);
        }

        if (ext_file_update) {
            if (ext_file != null && !ext_file.isEmpty()) {
                String fileName = ext_file.getOriginalFilename();
                ext_file.transferTo(dir.resolve(fileName));
                ext.setFile(fileName);
            } else {
                ext.setFile("");
            }
        }
        TrainExt old_ext = trainExtMapper.selectById(task.getId());
        if (old_ext == null) {
            if (trainExtMapper.insert(ext) <= 0) {
                throw new RuntimeSqlException();
            }
        } else {
            if (trainExtMapper.updateById(ext) <= 0) {
                throw new RuntimeSqlException();
            }
            String oldFileName = old_ext.getFile();
            if (StrUtil.isNotBlank(oldFileName) && !StrUtil.equals(oldFileName, ext.getFile())) {
                try {
                    FileUtil.del(dir.resolve(oldFileName));
                } catch (Exception ignore) {}
            }
        }

        // mmdet cfg
        if (mmdet_cfg != null) {
            FileUtil.writeBytes(mmdet_cfg.getBytes(CharsetUtil.UTF_8),
                    Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TRAIN_TASK, task.getId().toString(),
                            CodeMap.DIR_TRAIN_FILE, "cfg.py").toFile());
        }
        return true;
    }

    public boolean delLink(Integer id) {
        if (baseMapper.deleteById(id) <= 0) {
            return false;
        }
        if (tArgsMapper.deleteById(id) <= 0) {
            throw new RuntimeSqlException();
        }
        if (tDataMapper.deleteById(id) <= 0) {
            throw new RuntimeSqlException();
        }
        trainExtMapper.deleteById(id);
        try {
            FileUtil.del(Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TRAIN_TASK, id.toString()));
        } catch (Exception ignore) {}
        return true;
    }

    public void startTrain(Integer id) {
        // 1) 取任务
        TrainTask task = baseMapper.selectById(id);
        if (task == null) {
            log.warn("[startTrain] task not found, id={}", id);
            return;
        }

        // 2) mmdet 分流（兼容 "mmdet" 与 "1"）
        if ("mmdet".equalsIgnoreCase(task.getType()) || "1".equalsIgnoreCase(task.getType())) {
            log.info("[startTrain] mmdet via Python Runner, taskId={}, runId={}", id, task.getName());

            // 置 RUN（不写 run_name）
            updateStartStatus(id, null);

            boolean ok = false;
            String remarkTail = "runner:unknown";

            try {
                String resp = trainRunnerService.startByRunId(task.getName());
                cn.hutool.json.JSONObject jo = cn.hutool.json.JSONUtil.parseObj(resp);
                ok = jo.getBool("ok", false);

                String workDir    = jo.getStr("work_dir");
                String logPath    = jo.getStr("log");
                String resultsTxt = jo.getStr("results_txt");
                if (resultsTxt == null) {
                    resultsTxt = jo.getStr("result_txt");
                }

                // ★ 解析并入库
                try {
                    saveCocoResultFromRunner(task, jo);
                } catch (Exception e) {
                    log.warn("saveCocoResultFromRunner failed in startTrain, id={}, name={}, err={}",
                            id, task.getName(), e.toString());
                }

                remarkTail = (ok ? "runner:success" : "runner:error")
                        + (workDir    != null ? (", workDir=" + workDir) : "")
                        + (logPath    != null ? (", log=" + logPath) : "")
                        + (resultsTxt != null ? (", result=" + resultsTxt) : "");
            } catch (Exception e) {
                ok = false;
                remarkTail = "runner:exception=" + e.getMessage();
                log.error("Python Runner call failed, runId={}", task.getName(), e);
            } finally {
                updateStopStatus(id, null, ok ? CodeMap.TRAIN_FINISH_SUCCESS : CodeMap.TRAIN_FINISH_ERROR);
                try {
                    TrainTask upd = new TrainTask();
                    upd.setId(id);
                    String base = task.getRemark();
                    upd.setRemark((base == null || base.isEmpty()) ? remarkTail : (base + "; " + remarkTail));
                    baseMapper.updateById(upd);
                } catch (Exception ignore) {}
            }
            return; // mmdet 流程已完成
        }

        // 3) 非 mmdet：沿用原有脚本流程
        String type = task.getType();
        TrainScript script = tScriptMapper.selectById(type);
        if (script == null) {
            log.warn("No TrainScript configured for type={}, cannot start by legacy script. id={}", type, id);
            updateStopStatus(id, null, CodeMap.TRAIN_FINISH_ERROR);
            return;
        }

        TrainArgs args = tArgsMapper.selectById(id);

        log.info("^_* start train:#{}:{}", id);
        Path tPath = Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TRAIN_TASK, id.toString());
        Path filePath = tPath.resolve(CodeMap.DIR_TRAIN_FILE);
        Path runPath = tPath.resolve(CodeMap.DIR_TRAIN_RUN);
        String expName = getExpName(runPath);
        Integer node = args.getNode();

        String suff = script.getSuff();
        Path scriptPath = Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_SCRIPT, script.getId() + suff);

        // 拓展参数
        TrainExt ext = trainExtMapper.selectById(id);
        cn.hutool.json.JSONObject ext_params = new cn.hutool.json.JSONObject();
        String ext_file = null;
        if (ext != null) {
            String e_p = ext.getParams();
            if (StrUtil.isNotBlank(e_p) && JSONUtil.isTypeJSONObject(e_p)) {
                ext_params = new cn.hutool.json.JSONObject(e_p);
            }
            String e_f = ext.getFile();
            if (StrUtil.isNotBlank(e_f)) {
                ext_file = e_f;
            }
        }

        ProcessBuilder pBuilder = null;
        String cmd = script.getCmd();

        if (StrUtil.equals(".sh", suff)) {
            switch (cmd) {
                case "yolo":
                    pBuilder = new ProcessBuilder(
                            "bash",
                            scriptPath.toString(),
                            id.toString(),
                            node.toString(),
                            cmd,
                            "train",
                            "data=" + filePath.resolve("data.yaml"),
                            "model=" + filePath.resolve("weights_" + args.getWeights() + ".pt"),
                            "device=" + args.getDevice(),
                            "batch=" + args.getBatch_size(),
                            "imgsz=" + args.getImg_size(),
                            "epochs=" + args.getEpoch(),
                            "save_period=" + args.getPeriod(),
                            "project=" + runPath,
                            "name=" + expName,
                            "exist_ok=True"
                    );
                    for (String key : ext_params.keySet()) {
                        String val = ext_params.getStr(key);
                        pBuilder.command().add(key + "=" + val);
                    }
                    if (ext_file != null) {
                        pBuilder.command().add("ext_file=" + filePath.resolve(ext_file));
                    }
                    break;
                case "python":
                    pBuilder = new ProcessBuilder(
                            "bash",
                            scriptPath.toString(),
                            id.toString(),
                            node.toString(),
                            cmd,
                            script.getMain(),
                            "--data", filePath.resolve("data.yaml").toString(),
                            "--weights", filePath.resolve("weights_" + args.getWeights() + ".pt").toString(),
                            "--cfg", filePath.resolve("cfg_" + args.getCfg() + ".yaml").toString(),
                            "--hyp", filePath.resolve("hyp_" + args.getHyp() + ".yaml").toString(),
                            "--device", args.getDevice(),
                            "--batch-size", args.getBatch_size().toString(),
                            "--img-size", args.getImg_size().toString(),
                            "--epoch", args.getEpoch().toString(),
                            "--save-period", args.getPeriod().toString(),
                            "--project", runPath.toString(),
                            "--name", expName,
                            "--exist-ok",
                            "--sync-bn"
                    );
                    for (String key : ext_params.keySet()) {
                        String val = ext_params.getStr(key);
                        pBuilder.command().add("--" + key);
                        pBuilder.command().add(val);
                    }
                    if (ext_file != null) {
                        pBuilder.command().add("--ext_file");
                        pBuilder.command().add(filePath.resolve(ext_file).toString());
                    }
                    break;
                case "mmdet":
                    TrainData tData = tDataMapper.selectById(id);
                    if (tData == null || tData.getLabels() == null) {
                        log.warn("tData or tData Labels is empty");
                        updateStopStatus(id, expName, CodeMap.TRAIN_FINISH_ERROR);
                        return;
                    }
                    List<TrainLabel> labels = JSONUtil.toList(tData.getLabels(), TrainLabel.class);
                    int numClasses = labels.size();
                    String classes = labels.stream().map(TrainLabel::getName).collect(java.util.stream.Collectors.joining("+"));
                    pBuilder = new ProcessBuilder(
                            "bash",
                            scriptPath.toString(),
                            id.toString(),
                            node.toString(),
                            cmd,
                            script.getMain(),
                            "--weights",
                            filePath.resolve("weights_" + args.getWeights() + ".pt").toString().replace("\\", "/"),
                            "--device", args.getDevice(),
                            "--batch-size", args.getBatch_size().toString(),
                            "--img-width", args.getImg_w().toString(),
                            "--img-height", args.getImg_h().toString(),
                            "--epoch", args.getEpoch().toString(),
                            "--save-period", args.getPeriod().toString(),
                            "--work-dir", runPath.resolve(expName).toString().replace("\\", "/"),
                            "--num-classes", String.valueOf(numClasses),
                            "--classes", classes
                    );
                    break;
                default:
                    log.warn("script cmd not support:{}  ", cmd);
                    updateStopStatus(id, expName, CodeMap.TRAIN_FINISH_ERROR);
                    return;
            }
        } else if (StrUtil.equals(".bat", suff)) {
            switch (cmd) {
                case "yolo":
                    pBuilder = new ProcessBuilder(
                            "cmd.exe",
                            "/c",
                            scriptPath.toString(),
                            id.toString(),
                            node.toString(),
                            cmd,
                            "train",
                            "data::" + filePath.resolve("data.yaml"),
                            "model::" + filePath.resolve("weights_" + args.getWeights() + ".pt"),
                            "device::" + args.getDevice(),
                            "batch::" + args.getBatch_size(),
                            "imgsz::" + args.getImg_size(),
                            "epochs::" + args.getEpoch(),
                            "save_period::" + args.getPeriod(),
                            "project::" + runPath,
                            "name::" + expName,
                            "exist_ok::True"
                    );
                    for (String key : ext_params.keySet()) {
                        String val = ext_params.getStr(key);
                        pBuilder.command().add(key + "::" + val);
                    }
                    if (ext_file != null) {
                        pBuilder.command().add("ext_file::" + filePath.resolve(ext_file));
                    }
                    break;
                case "python":
                    pBuilder = new ProcessBuilder(
                            "cmd.exe",
                            "/c",
                            scriptPath.toString(),
                            id.toString(),
                            node.toString(),
                            cmd,
                            script.getMain(),
                            "--data", filePath.resolve("data.yaml").toString(),
                            "--weights", filePath.resolve("weights_" + args.getWeights() + ".pt").toString(),
                            "--cfg", filePath.resolve("cfg_" + args.getCfg() + ".yaml").toString(),
                            "--hyp", filePath.resolve("hyp_" + args.getHyp() + ".yaml").toString(),
                            "--device", args.getDevice(),
                            "--batch-size", args.getBatch_size().toString(),
                            "--img-size", args.getImg_size().toString(),
                            "--epoch", args.getEpoch().toString(),
                            "--save-period", args.getPeriod().toString(),
                            "--project", runPath.toString(),
                            "--name", expName,
                            "--exist-ok",
                            "--sync-bn"
                    );
                    for (String key : ext_params.keySet()) {
                        String val = ext_params.getStr(key);
                        pBuilder.command().add("--" + key);
                        pBuilder.command().add(val);
                    }
                    if (ext_file != null) {
                        pBuilder.command().add("--ext_file");
                        pBuilder.command().add(filePath.resolve(ext_file).toString());
                    }
                    break;
                case "mmdet":
                    TrainData tData2 = tDataMapper.selectById(id);
                    if (tData2 == null || tData2.getLabels() == null) {
                        log.warn("tData or tData Labels is empty");
                        updateStopStatus(id, expName, CodeMap.TRAIN_FINISH_ERROR);
                        return;
                    }
                    List<TrainLabel> labels2 = JSONUtil.toList(tData2.getLabels(), TrainLabel.class);
                    int numClasses2 = labels2.size();
                    String classes2 = labels2.stream().map(TrainLabel::getName).collect(java.util.stream.Collectors.joining("+"));
                    pBuilder = new ProcessBuilder(
                            "cmd.exe",
                            "/c",
                            scriptPath.toString(),
                            id.toString(),
                            node.toString(),
                            cmd,
                            script.getMain(),
                            "--weights",
                            filePath.resolve("weights_" + args.getWeights() + ".pt").toString().replace("\\", "/"),
                            "--device", args.getDevice(),
                            "--batch-size", args.getBatch_size().toString(),
                            "--img-width", args.getImg_w().toString(),
                            "--img-height", args.getImg_h().toString(),
                            "--epoch", args.getEpoch().toString(),
                            "--save-period", args.getPeriod().toString(),
                            "--work-dir", runPath.resolve(expName).toString().replace("\\", "/"),
                            "--num-classes", String.valueOf(numClasses2),
                            "--classes", classes2
                    );
                    break;
                default:
                    log.warn("script cmd not support:{}  ", cmd);
                    updateStopStatus(id, expName, CodeMap.TRAIN_FINISH_ERROR);
                    return;
            }
        } else {
            log.warn("script suff not support:{}  ", suff);
            updateStopStatus(id, expName, CodeMap.TRAIN_FINISH_ERROR);
            return;
        }

        log.info("start train: {}", pBuilder.command());
        pBuilder.directory(Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TRAIN_TASK, id.toString()).toFile());
        Integer run_state = null;
        Path expPath = Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TRAIN_TASK, id.toString(), CodeMap.DIR_TRAIN_RUN)
                .resolve(expName);

        updateStartStatus(id, expName);
        try {
            cuProcess = pBuilder.start();
            StreamGobbler errorGobbler = new StreamGobbler(cuProcess.getErrorStream(), CodeMap.SCRIPT_TYPE_TRAIN);
            StreamGobbler outputGobbler = new StreamGobbler(cuProcess.getInputStream(), CodeMap.SCRIPT_TYPE_TRAIN);
            errorGobbler.start();
            outputGobbler.start();

            int exitCode = cuProcess.waitFor();
            if (!expPath.toFile().exists()) {
                exitCode = -1;
            }
            if (!"mmdet".equals(script.getCmd()) && !expPath.resolve("weights/best.pt").toFile().exists()) {
                exitCode = -1;
            }
            run_state = (exitCode == 0) ? CodeMap.TRAIN_FINISH_SUCCESS : CodeMap.TRAIN_FINISH_ERROR;

        } catch (IOException | InterruptedException e) {
            log.warn("train err:{}", e.getMessage());
            run_state = CodeMap.TRAIN_FINISH_ERROR;
        } finally {
            stopTrain();
            updateStopStatus(id, expName, run_state);
            if (run_state == CodeMap.TRAIN_FINISH_ERROR && expPath.toFile().exists()) {
                cn.hutool.core.io.FileUtil.del(expPath);
            }
        }
    }

    private void updateStartStatus(Integer id, String expName) {
        TrainTask upTask = new TrainTask();
        upTask.setId(id);
        upTask.setStarted_date(LocalDateTime.now());
        upTask.setStatus(CodeMap.TRAIN_TASK_STATUS_RUN);

        LambdaUpdateWrapper<TrainTask> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(TrainTask::getId, id);
        updateWrapper.set(TrainTask::getStarted_date, upTask.getStarted_date());
        updateWrapper.set(TrainTask::getFinish_date, null);
        updateWrapper.set(TrainTask::getStatus, CodeMap.TRAIN_TASK_STATUS_RUN);

        // 使用 ServiceImpl 提供的 update(Wrapper) 更稳妥
        this.update(updateWrapper);

        upTask.setMsg_type(CodeMap.SCRIPT_TYPE_TRAIN);
        WsTrainController.senMsgToAll(JSONUtil.toJsonStr(upTask));
    }

    private void updateStopStatus(Integer id, String expName, Integer run_state) {
        TrainTask record = new TrainTask();
        record.setId(id);
        record.setFinish_date(LocalDateTime.now());
        record.setStatus(CodeMap.TRAIN_TASK_STATUS_FINISH);
        record.setRun_state(run_state);
        if (run_state == CodeMap.TRAIN_FINISH_SUCCESS && expName != null) {
            record.setRun_name(expName);
        }
        baseMapper.updateById(record);

        record.setMsg_type(CodeMap.SCRIPT_TYPE_TRAIN);
        WsTrainController.senMsgToAll(JSONUtil.toJsonStr(record));
    }

    public void stopTrain() {
        try {
            if (cuProcess != null) {
                ProcessHandle handle = cuProcess.toHandle();
                handle.descendants().forEach(ProcessHandle::destroy);
                boolean flg = handle.destroy();
                log.info("stop train:[{}]", flg);
            }
        } catch (Exception ignore) {
        } finally {
            cuProcess = null;
        }
    }

    /** 获取 run/exp 的目录名称 */
    private String getExpName(Path runPath) {
        String expName = "exp";
        for (int i = 1;; i++) {
            String name = String.format("%s%03d", expName, i);
            if (!runPath.resolve(name).toFile().exists()) {
                return name;
            }
        }
    }

    public List<String> queryDistinctUsernames() {
        return baseMapper.queryDistinctUsernames();
    }

    public void delEpoches(Integer id, String expName, String cmd) {
        Path taskPath = Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TRAIN_TASK, id.toString(), "run");
        boolean isMmdet = StrUtil.equals(cmd, "mmdet");
        if (StrUtil.isNotBlank(expName)) {
            delOneExp(taskPath.resolve(expName), isMmdet);
        } else {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(taskPath)) {
                for (Path expPath : stream) {
                    if (Files.isDirectory(expPath)) {
                        delOneExp(expPath, isMmdet);
                    }
                }
            } catch (IOException e) {
                log.warn("del epoches err:{}", e.getMessage());
            }
        }
    }

    public void delOneExp(Path expPath, boolean isMmdet) {
        if (isMmdet) {
            delMmdet(expPath);
        } else {
            delYolo(expPath);
        }
    }

    private void delMmdet(Path expPath) {
        Path filePath = expPath.resolve("last_checkpoint");
        String lastName = null;
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
            String firstLine = reader.readLine();
            if (firstLine != null) {
                Path fileName = Paths.get(firstLine).getFileName();
                if (fileName != null) {
                    lastName = fileName.toString();
                }
            }
        } catch (IOException e) {
            log.warn("lastName is err:{}", e.getMessage());
        }
        if (lastName != null) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(expPath)) {
                for (Path entry : stream) {
                    String curName = entry.getFileName().toString();
                    if (curName.matches("^epoch_\\d+\\.pth$") && !curName.equals(lastName)) {
                        Files.delete(entry);
                    }
                }
            } catch (IOException e) {
                log.warn("del yolo pt err:{}", e.getMessage());
            }
        }
    }

    public void delYolo(Path expPath) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(expPath.resolve("weights"))) {
            for (Path entry : stream) {
                if (entry.getFileName().toString().matches("^epoch\\d+\\.pt$")) {
                    Files.delete(entry);
                }
            }
        } catch (IOException e) {
            log.warn("del yolo pt err:{}", e.getMessage());
        }
    }
}
