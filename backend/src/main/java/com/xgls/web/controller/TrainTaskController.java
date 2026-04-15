package com.xgls.web.controller;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import com.xgls.web.utils.InstanceDatasetPathUtil;
import com.xgls.web.utils.MmdetConfigUtil;
import com.xgls.web.entity.*;
import org.apache.shiro.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import jakarta.servlet.http.HttpServletRequest;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.xgls.web.base.AjaxResult;
import com.xgls.web.base.CodeMap;
import com.xgls.web.base.ErrorCode;
import com.xgls.web.runner.TaskQueue;
import com.xgls.web.service.*;
import com.xgls.web.utils.MyUtils;
import com.xgls.web.utils.SessionUtil;
import com.xgls.web.vo.MyTask;
import com.xgls.web.vo.TrainForm;
import com.xgls.web.vo.TrainItem;
import com.xgls.web.vo.ValParams;
import com.xgls.web.vo.query.TrainTaskQuery;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

@Tag(name = "模型训练任务管理")
@RestController
@RequestMapping("/trainTask")
@Slf4j
public class TrainTaskController {

    @Value("${sys.root-upload}")
    private String rootPath;

    /** 与预处理落盘一致：实例数据集根目录（INSTANCE_DATA_ROOT / sys.instancecfg.instancedata-root） */
    @Value("${sys.instancecfg.instancedata-root:/home/omen1/AI_TT_Platform/data/instance_dataset/}")
    private String instanceDataRoot;

    // 三个基础模板：data / train / runtime
    @Value("${sys.modelcfg.local-data-path}")
    private String localDataPath;
    @Value("${sys.modelcfg.local-train-path}")
    private String localTrainPath;
    @Value("${sys.modelcfg.local-runtime-path}")
    private String localRuntimePath;

    // faster-rcnn 的三种主干模板
    @Value("${sys.modelcfg.local-model-path.faster-rcnn.resnet}")
    private String tplFrcnnResnet;
    @Value("${sys.modelcfg.local-model-path.faster-rcnn.convnext}")
    private String tplFrcnnConvnext;
    @Value("${sys.modelcfg.local-model-path.faster-rcnn.swint}")
    private String tplFrcnnSwint;
    // cascade-rcnn 的三种主干模板
    @Value("${sys.modelcfg.local-model-path.cascade-rcnn.resnet}")
    private String tplCasrcnnResnet;
    @Value("${sys.modelcfg.local-model-path.cascade-rcnn.convnext}")
    private String tplCasrcnnConvnext;
    @Value("${sys.modelcfg.local-model-path.cascade-rcnn.swint}")
    private String tplCasrcnnSwint;
    // detectors 的三种主干模板
    @Value("${sys.modelcfg.local-model-path.detectors.resnet}")
    private String tplDetectorsResnet;

    // DETR 的三种主干模板
    @Value("${sys.modelcfg.local-model-path.detr.resnet}")
    private String tplDetrResnet;
    @Value("${sys.modelcfg.local-model-path.detr.swint}")
    private String tplDetrSwint;
    // DINO 的三种主干模板
    @Value("${sys.modelcfg.local-model-path.dino.resnet}")
    private String tplDinoResnet;
    @Value("${sys.modelcfg.local-model-path.dino.swint}")
    private String tplDinoSwint;
    // Deformable-Detr 的三种主干模板
    @Value("${sys.modelcfg.local-model-path.dino.resnet}")
    private String tplDeformableDetrResnet;
    @Value("${sys.modelcfg.local-model-path.dino.swint}")
    private String tplDeformableDetrSwint;

    @Autowired
    TrainTaskService taskService;
    @Autowired
    ValTaskService valTaskService;
    @Autowired
    TrainArgsService tArgsService;
    @Autowired
    TrainDataService tDataService;
    @Autowired
    TransformService transformService;
    @Autowired
    TrainExtService trainExtService;
    @Autowired
    TrainScriptService tScriptService;
    @Autowired
    PredictTaskService predictTaskService;
    @Autowired
    private InstanceDatasetinfoService instanceDatasetinfoService;

    @Operation(summary = "打包配置并准备训练")
    @PostMapping(value = "/pack",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public AjaxResult pack(HttpServletRequest request) {
        if (!(request instanceof MultipartHttpServletRequest mreq)) {
            return AjaxResult.error("请使用 multipart/form-data 提交");
        }
        String paramsJson = mreq.getParameter("params");
        if (!StringUtils.hasText(paramsJson)) {
            return AjaxResult.error("缺少表单字段 params（JSON 字符串）");
        }
        MultipartFile weightFile = mreq.getFile("weight_file");
        try {
            JSONObject params = JSONUtil.parseObj(paramsJson);

            // mmdet_network：Faster R-CNN / C/ DetectoRS / Dascade R-CNN ETR
            String rawNetwork = params.getStr("mmdet_network");
            String rawBackbone = params.getStr("mmdet_backbone");

            String net = normalizeNetwork(rawNetwork);    // faster-rcnn / cascade-rcnn / detectors / detr
            String back = normalizeBackbone(rawBackbone); // resnet / convnext / swint

            if (!StringUtils.hasText(net))  return AjaxResult.error("缺少参数：mmdet_network");
            if (!StringUtils.hasText(back)) return AjaxResult.error("缺少参数：mmdet_backbone");

            boolean useCustomPretrained = resolveUseCustomPretrained(params, weightFile);
            AjaxResult pretrainedErr = validatePretrainedChoice(net, back, useCustomPretrained, weightFile, params);
            if (pretrainedErr != null) return pretrainedErr;

            // === 新增：DETR 分支 ===
//            if ("detr".equals(net)) {
//                if (!"resnet".equals(back)) {
//                    return AjaxResult.error("DETR 当前只支持 ResNet 主干");
//                }
//                return packDetr(params, weightFile);
//            }
            if ("detr".equals(net)) {
                return packDetr(params, back, weightFile, useCustomPretrained);
            }else if ("dino".equals(net)) {
                return packDINO(params, back, weightFile, useCustomPretrained);
            }else if ("deformable detr".equals(net)) {
                return packDeformableDetr(params, back, weightFile, useCustomPretrained);
            }
            if ("faster-rcnn".equals(net)) {
                return packFasterRcnn(params, back, weightFile, useCustomPretrained);
            } else if ("cascade-rcnn".equals(net)) {
                return packCascadeRcnn(params, back, weightFile, useCustomPretrained);
            } else if ("detectors".equals(net)) {
                return packDetectors(params, back, weightFile, useCustomPretrained);
            } else {
                return AjaxResult.error("不支持的网络类型：" + rawNetwork);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return AjaxResult.error("处理失败: " + e.getMessage());
        }
    }

    /**
     * 是否使用「自定义预训练」（上传文件或填写地址）。未传参时：有上传文件则视为自定义，否则沿用模板内默认（如 torchvision://、https://）。
     */
    private boolean resolveUseCustomPretrained(JSONObject params, MultipartFile weightFile) {
        if (!params.containsKey("mmdet_use_custom_pretrained")) {
            return weightFile != null && !weightFile.isEmpty();
        }
        Object v = params.get("mmdet_use_custom_pretrained");
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        String s = String.valueOf(v).trim();
        if ("true".equalsIgnoreCase(s) || "1".equals(s)) return true;
        if ("false".equalsIgnoreCase(s) || "0".equals(s)) return false;
        return weightFile != null && !weightFile.isEmpty();
    }

    /**
     * Faster/Cascade R-CNN 的 ConvNeXt、Swin 模板依赖占位变量，没有写死网络下载地址，必须自定义预训练。
     */
    private AjaxResult validatePretrainedChoice(String net, String back, boolean useCustom,
                                                MultipartFile weightFile, JSONObject params) {
        if (useCustom) {
            boolean hasFile = weightFile != null && !weightFile.isEmpty();
            String addr = params.getStr("mmdet_pretrained_address");
            boolean hasAddr = StringUtils.hasText(addr != null ? addr.trim() : null);
            if (!hasFile && !hasAddr) {
                return AjaxResult.error("已选择自定义预训练权值时，请上传权重文件或在 mmdet_pretrained_address 中填写地址（http(s) URL、torchvision://、本地路径等）");
            }
            return null;
        }
        if (("faster-rcnn".equals(net) || "cascade-rcnn".equals(net))
                && ("convnext".equals(back) || "swint".equals(back))) {
            return AjaxResult.error("Faster/Cascade R-CNN 的 ConvNeXt、Swin 主干在模板中未配置默认下载地址，请开启「自定义预训练权值」并上传或填写地址");
        }
        return null;
    }

    /** 自定义预训练时解析 checkpoint 的 Python 字面量及回显路径；非自定义时返回 null,null */
    private String[] resolveCheckpointForPack(boolean useCustom, JSONObject params, MultipartFile weightFile, Path ckptDir)
            throws IOException {
        if (!useCustom) {
            return new String[]{null, null};
        }
        if (weightFile != null && !weightFile.isEmpty()) {
            Files.createDirectories(ckptDir);
            String orig = weightFile.getOriginalFilename();
            String ckptName = System.currentTimeMillis() + "_" + (StringUtils.hasText(orig) ? orig : "weights.pth");
            Path ckptPath = ckptDir.resolve(ckptName);
            weightFile.transferTo(ckptPath.toFile());
            return new String[]{MmdetConfigUtil.toPyQuotedPath(ckptPath.toString()), ckptPath.toString()};
        }
        String addr = params.getStr("mmdet_pretrained_address");
        String t = addr != null ? addr.trim() : "";
        if (!StringUtils.hasText(t)) {
            throw new IllegalStateException("缺少权值地址");
        }
        return new String[]{MmdetConfigUtil.toPyQuotedPath(t), t};
    }

    private MultipartFile weightForDb(boolean useCustom, MultipartFile weightFile) {
        return (useCustom && weightFile != null && !weightFile.isEmpty()) ? weightFile : null;
    }

    /* ========================= 下面是三个具体 pack 方法 ========================= */

    private AjaxResult packFasterRcnn(JSONObject params, String backbone, MultipartFile weightFile, boolean useCustomPretrained) throws Exception {
        // 1. 通用参数
        String taskName = params.getStr("taskName");
        String taskType = params.getStr("taskType");
        String dataset = params.getStr("dataset");

        String scale = "(" + params.getStr("mmdet_input_width") + ", " + params.getStr("mmdet_input_height") + ")";
        Integer batchSize = params.getInt("mmdet_batchsize");
        Integer maxEpochs = params.getInt("mmdet_epoch");
        Integer valInterval = params.getInt("mmdet_val_interval");
        String milestones = params.getStr("mmdet_step");
        String optType = params.getStr("mmdet_opt");
        Double lr = params.getDouble("mmdet_inlr");
        Integer weightInterval = params.getInt("mmdet_weight_interval");

        if (!StringUtils.hasText(taskName)) return AjaxResult.error("缺少参数：taskName");
        if (!StringUtils.hasText(taskType)) return AjaxResult.error("缺少参数：taskType");
        if (!StringUtils.hasText(dataset))  return AjaxResult.error("缺少参数：dataset");
        if (!Set.of("resnet", "convnext", "swint").contains(backbone)) {
            return AjaxResult.error("Faster R-CNN 的主干只支持：ResNet / ConvNext / SwinTransformer");
        }

        // 2. 基于 InstanceDatasetinfo 推导 dataroot / ann* / prefix* / numClasses / classNames
        DatasetCfg dsCfg;
        try {
            dsCfg = buildDatasetCfg(dataset);
        } catch (Exception e) {
            return AjaxResult.error("解析实例数据集失败: " + e.getMessage());
        }
        String numClassesStr = dsCfg.numClassesStr;
        if (!StringUtils.hasText(numClassesStr)) {
            return AjaxResult.error("实例数据集缺少类别数量信息");
        }

        // 3. 创建训练任务（类数用 dsCfg.numClassesStr）
        TrainForm form = new TrainForm();
        form.setName(taskName);
        form.setType(taskType);
        form.setCls_num(Integer.parseInt(numClassesStr));
        form.setPrj_num(1);
        form.setTask_num(1);
        form.setImg_num(0);
        form.setObj_num(0L);
        form.setImg_val_num(0);
        form.setObj_val_num(0L);
        form.setRemark(taskType + "-faster-rcnn-" + backbone + " 训练任务 - " + taskName);
        form.setArgs(null);
        form.setData(new com.xgls.web.entity.TrainData());

        boolean saveSuccess = taskService.saveLink(
                form, SessionUtil.getCurUser(), weightForDb(useCustomPretrained, weightFile), null, null, null, null);
        if (!saveSuccess) return AjaxResult.error("训练任务入库失败");

        Integer taskId = null;
        LambdaQueryWrapper<TrainTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrainTask::getName, taskName);
        TrainTask savedTask = taskService.getOne(wrapper);
        if (savedTask != null) {
            taskId = savedTask.getId();
            TrainTask updateRecord = new TrainTask();
            updateRecord.setId(taskId);
            updateRecord.setStatus(CodeMap.TRAIN_TASK_STATUS_READY);
            updateRecord.setUpdated_date(LocalDateTime.now());
            taskService.updateById(updateRecord);
        }

        // 4. 运行目录 & checkpoint（非自定义时不落盘权重）
        Path runDir = MmdetConfigUtil.ensureRunDir(rootPath, taskName);
        Path ckptDir = runDir.resolve("checkpoints");
        String[] ckptPair = resolveCheckpointForPack(useCustomPretrained, params, weightFile, ckptDir);
        String ckptPy = ckptPair[0];
        String checkpointResponse = ckptPair[1];

        // 5. 选模板
        String chosenTpl;
        switch (backbone) {
            case "resnet":
                chosenTpl = tplFrcnnResnet;
                break;
            case "convnext":
                chosenTpl = tplFrcnnConvnext;

                break;
            case "swint":
                chosenTpl = tplFrcnnSwint;
                break;
            default:
                throw new IllegalStateException("不可能来到这里");
        }
        String baseTxt = MmdetConfigUtil.readString(Paths.get(chosenTpl));

        // ==== resnet 分支 ====
        if ("resnet".equals(backbone)) {
            Integer depth = params.getInt("mmdet_depth");
            if (depth == null) return AjaxResult.error("缺少参数：mmdet_depth");
            baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(depth\\s*=\\s*)\\d+", "$1" + depth);

            if (params.containsKey("mmdet_dcn")) {
                Object raw = params.get("mmdet_dcn");
                boolean useDcn = (raw instanceof Boolean) ? (Boolean) raw
                        : ("true".equalsIgnoreCase(String.valueOf(raw)));
                String dcnVal = useDcn
                        ? "dict(type='DCNv2', deform_groups=1, fallback_on_stride=False)"
                        : "None";
                baseTxt = MmdetConfigUtil.replaceFirst(
                        baseTxt, "(dcn\\s*=\\s*)(None|dict\\([^\\)]*\\))", "$1" + dcnVal);
            }
            if (params.containsKey("mmdet_dcnStage")) {
                String tuple = buildStageTuple(params.getStr("mmdet_dcnStage", ""));
                baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(stage_with_dcn\\s*=\\s*)\\([^\\)]*\\)", "$1" + tuple);
            }
        }

        // ==== convnext 分支 ====
        if ("convnext".equals(backbone)) {
            String arch = params.getStr("mmdet_conv_arch");
            if (!StringUtils.hasText(arch))
                return AjaxResult.error("缺少参数：mmdet_conv_arch ∈ {tiny,small,base,large}");
            arch = arch.trim().toLowerCase();
            baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(arch\\s*=\\s*)'[^']*'", "$1'" + arch + "'");
            String inC;
            switch (arch) {
                case "tiny":
                case "small":
                    inC = "[96, 192, 384, 768]";
                    break;
                case "base":
                    inC = "[128, 256, 512, 1024]";
                    break;
                case "large":
                    inC = "[192, 384, 768, 1536]";
                    break;
                default:
                    inC = "[96, 192, 384, 768]";
            }
            baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(in_channels\\s*=\\s*)\\[[^\\]]*\\]", "$1" + inC);
        }

        // ==== swint 分支 ====
        if ("swint".equals(backbone)) {
            String arch = params.getStr("mmdet_swint_arch");
            if (!StringUtils.hasText(arch))
                return AjaxResult.error("缺少参数：mmdet_swint_arch ∈ {tiny,small,base,large}");
            arch = arch.trim().toLowerCase();

            String embed, depths, heads, inC;
            switch (arch) {
                case "tiny":
                    embed = "96";
                    depths = "[2, 2, 6, 2]";
                    heads = "[3, 6, 12, 24]";
                    inC = "[96, 192, 384, 768]";
                    break;
                case "small":
                    embed = "96";
                    depths = "[2, 2, 18, 2]";
                    heads = "[3, 6, 12, 24]";
                    inC = "[96, 192, 384, 768]";
                    break;
                case "base":
                    embed = "128";
                    depths = "[2, 2, 18, 2]";
                    heads = "[4, 8, 16, 32]";
                    inC = "[128, 256, 512, 1024]";
                    break;
                case "large":
                    embed = "192";
                    depths = "[2, 2, 18, 2]";
                    heads = "[6, 12, 24, 48]";
                    inC = "[192, 384, 768, 1536]";
                    break;
                default:
                    return AjaxResult.error("mmdet_swint_arch 不合法");
            }
            baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(embed_dims\\s*=\\s*)\\d+", "$1" + embed);
            baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(depths\\s*=\\s*)\\[[^\\]]*\\]", "$1" + depths);
            baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(num_heads\\s*=\\s*)\\[[^\\]]*\\]", "$1" + heads);
            // 关键：neck 里的 in_channels 也要改
            baseTxt = MmdetConfigUtil.replaceAll(baseTxt, "(in_channels\\s*=\\s*)\\[[^\\]]*\\]", "$1" + inC);

            if (params.containsKey("mmdet_window")) {
                Integer w = params.getInt("mmdet_window");
                if (w != null) {
                    baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(window_size\\s*=\\s*)\\d+", "$1" + w);
                }
            }
        }

        // 6. 注入权重（可选）与类别数
        if (StringUtils.hasText(ckptPy)) {
            baseTxt = injectCheckpointAndNumClasses(baseTxt, ckptPy, numClassesStr);
        } else {
            baseTxt = injectNumClassesOnly(baseTxt, numClassesStr);
        }

        // 落盘 base_model.py
        Path baseSaved = MmdetConfigUtil.writeTextFile(runDir, "base_model.py", baseTxt);

        // 7. data/train/runtime（用 DatasetCfg + 自动注入 metainfo）
        Map<String, Object> common = writeCommonFiles(
                runDir, dsCfg,
                scale, batchSize, maxEpochs, valInterval,
                milestones, optType, lr, weightInterval
        );

        // 8. 保存网络名字
        saveNetworkName(taskId, "faster-rcnn_" + backbone);

        // 9. 返回
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskName", taskName);
        data.put("taskId", taskId);
        data.put("dataset", dataset);
        data.put("mmdet_network", "faster-rcnn");
        data.put("mmdet_backbone", backbone);
        data.putAll(common);
        data.put("checkpoint", StringUtils.hasText(checkpointResponse) ? checkpointResponse : "template-default");
        data.put("type", taskType);
        return AjaxResult.success(data);
    }

    private AjaxResult packCascadeRcnn(JSONObject params, String backbone, MultipartFile weightFile, boolean useCustomPretrained) throws Exception {
        String taskName = params.getStr("taskName");
        String taskType = params.getStr("taskType");
        String dataset = params.getStr("dataset");

        String scale = "(" + params.getStr("mmdet_input_width") + ", " + params.getStr("mmdet_input_height") + ")";
        Integer batchSize = params.getInt("mmdet_batchsize");
        Integer maxEpochs = params.getInt("mmdet_epoch");
        Integer valInterval = params.getInt("mmdet_val_interval");
        String milestones = params.getStr("mmdet_step");
        String optType = params.getStr("mmdet_opt");
        Double lr = params.getDouble("mmdet_inlr");
        Integer weightInterval = params.getInt("mmdet_weight_interval");

        if (!StringUtils.hasText(taskName)) return AjaxResult.error("缺少参数：taskName");
        if (!StringUtils.hasText(taskType)) return AjaxResult.error("缺少参数：taskType");
        if (!StringUtils.hasText(dataset))  return AjaxResult.error("缺少参数：dataset");
        if (!Set.of("resnet", "convnext", "swint").contains(backbone)) {
            return AjaxResult.error("Cascade R-CNN 的主干只支持：ResNet / ConvNext / SwinTransformer");
        }

        DatasetCfg dsCfg;
        try {
            dsCfg = buildDatasetCfg(dataset);
        } catch (Exception e) {
            return AjaxResult.error("解析实例数据集失败: " + e.getMessage());
        }
        String numClassesStr = dsCfg.numClassesStr;
        if (!StringUtils.hasText(numClassesStr)) {
            return AjaxResult.error("实例数据集缺少类别数量信息");
        }

        TrainForm form = new TrainForm();
        form.setName(taskName);
        form.setType(taskType);
        form.setCls_num(Integer.parseInt(numClassesStr));
        form.setPrj_num(1);
        form.setTask_num(1);
        form.setImg_num(0);
        form.setObj_num(0L);
        form.setImg_val_num(0);
        form.setObj_val_num(0L);
        form.setRemark(taskType + "-cascade-rcnn-" + backbone + " 训练任务 - " + taskName);
        form.setArgs(null);
        form.setData(new com.xgls.web.entity.TrainData());

        boolean saveSuccess = taskService.saveLink(
                form, SessionUtil.getCurUser(), weightForDb(useCustomPretrained, weightFile), null, null, null, null);
        if (!saveSuccess) return AjaxResult.error("训练任务入库失败");

        Integer taskId = null;
        LambdaQueryWrapper<TrainTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrainTask::getName, taskName);
        TrainTask savedTask = taskService.getOne(wrapper);
        if (savedTask != null) {
            taskId = savedTask.getId();
            TrainTask updateRecord = new TrainTask();
            updateRecord.setId(taskId);
            updateRecord.setStatus(CodeMap.TRAIN_TASK_STATUS_READY);
            updateRecord.setUpdated_date(LocalDateTime.now());
            taskService.updateById(updateRecord);
        }

        Path runDir = MmdetConfigUtil.ensureRunDir(rootPath, taskName);
        Path ckptDir = runDir.resolve("checkpoints");
        String[] ckptPair = resolveCheckpointForPack(useCustomPretrained, params, weightFile, ckptDir);
        String ckptPy = ckptPair[0];
        String checkpointResponse = ckptPair[1];

        String chosenTpl;
        switch (backbone) {
            case "resnet":
                chosenTpl = tplCasrcnnResnet;
                break;
            case "convnext":
                chosenTpl = tplCasrcnnConvnext;
                break;
            case "swint":
                chosenTpl = tplCasrcnnSwint;
                break;
            default:
                throw new IllegalStateException("不可能来到这里");
        }
        String baseTxt = MmdetConfigUtil.readString(Paths.get(chosenTpl));

        // 跟 Faster R-CNN 的三段改法一样
        if ("resnet".equals(backbone)) {
            Integer depth = params.getInt("mmdet_depth");
            if (depth == null) return AjaxResult.error("缺少参数：mmdet_depth");
            baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(depth\\s*=\\s*)\\d+", "$1" + depth);

            if (params.containsKey("mmdet_dcn")) {
                Object raw = params.get("mmdet_dcn");
                boolean useDcn = (raw instanceof Boolean) ? (Boolean) raw
                        : ("true".equalsIgnoreCase(String.valueOf(raw)));
                String dcnVal = useDcn
                        ? "dict(type='DCNv2', deform_groups=1, fallback_on_stride=False)"
                        : "None";
                baseTxt = MmdetConfigUtil.replaceFirst(
                        baseTxt, "(dcn\\s*=\\s*)(None|dict\\([^\\)]*\\))", "$1" + dcnVal);
            }
            if (params.containsKey("mmdet_dcnStage")) {
                String tuple = buildStageTuple(params.getStr("mmdet_dcnStage", ""));
                baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(stage_with_dcn\\s*=\\s*)\\([^\\)]*\\)", "$1" + tuple);
            }
        }
        if ("convnext".equals(backbone)) {
            String arch = params.getStr("mmdet_conv_arch");
            if (!StringUtils.hasText(arch)) return AjaxResult.error("缺少参数：mmdet_conv_arch");
            arch = arch.trim().toLowerCase();
            baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(arch\\s*=\\s*)'[^']*'", "$1'" + arch + "'");
            String inC;
            switch (arch) {
                case "tiny":
                case "small":
                    inC = "[96, 192, 384, 768]";
                    break;
                case "base":
                    inC = "[128, 256, 512, 1024]";
                    break;
                case "large":
                    inC = "[192, 384, 768, 1536]";
                    break;
                default:
                    inC = "[96, 192, 384, 768]";
            }
            baseTxt = MmdetConfigUtil.replaceAll(baseTxt, "(in_channels\\s*=\\s*)\\[[^\\]]*\\]", "$1" + inC);
        }
        if ("swint".equals(backbone)) {
            String arch = params.getStr("mmdet_swint_arch");
            if (!StringUtils.hasText(arch)) return AjaxResult.error("缺少参数：mmdet_swint_arch");
            arch = arch.trim().toLowerCase();

            String embed, depths, heads, inC;
            switch (arch) {
                case "tiny":
                    embed = "96";
                    depths = "[2, 2, 6, 2]";
                    heads = "[3, 6, 12, 24]";
                    inC = "[96, 192, 384, 768]";
                    break;
                case "small":
                    embed = "96";
                    depths = "[2, 2, 18, 2]";
                    heads = "[3, 6, 12, 24]";
                    inC = "[96, 192, 384, 768]";
                    break;
                case "base":
                    embed = "128";
                    depths = "[2, 2, 18, 2]";
                    heads = "[4, 8, 16, 32]";
                    inC = "[128, 256, 512, 1024]";
                    break;
                case "large":
                    embed = "192";
                    depths = "[2, 2, 18, 2]";
                    heads = "[6, 12, 24, 48]";
                    inC = "[192, 384, 768, 1536]";
                    break;
                default:
                    return AjaxResult.error("mmdet_swint_arch 不合法");
            }
            baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(embed_dims\\s*=\\s*)\\d+", "$1" + embed);
            baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(depths\\s*=\\s*)\\[[^\\]]*\\]", "$1" + depths);
            baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(num_heads\\s*=\\s*)\\[[^\\]]*\\]", "$1" + heads);
            baseTxt = MmdetConfigUtil.replaceAll(baseTxt, "(in_channels\\s*=\\s*)\\[[^\\]]*\\]", "$1" + inC);

            if (params.containsKey("mmdet_window")) {
                Integer w = params.getInt("mmdet_window");
                if (w != null) {
                    baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(window_size\\s*=\\s*)\\d+", "$1" + w);
                }
            }
        }

        // 注入 checkpoint（可选）+ num_classes
        if (StringUtils.hasText(ckptPy)) {
            baseTxt = injectCheckpointAndNumClasses(baseTxt, ckptPy, numClassesStr);
        } else {
            baseTxt = injectNumClassesOnly(baseTxt, numClassesStr);
        }

        Path baseSaved = MmdetConfigUtil.writeTextFile(runDir, "base_model.py", baseTxt);

        Map<String, Object> common = writeCommonFiles(
                runDir, dsCfg,
                scale, batchSize, maxEpochs, valInterval,
                milestones, optType, lr, weightInterval
        );

        saveNetworkName(taskId, "cascade-rcnn_" + backbone);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskName", taskName);
        data.put("taskId", taskId);
        data.put("dataset", dataset);
        data.put("mmdet_network", "cascade-rcnn");
        data.put("mmdet_backbone", backbone);
        data.putAll(common);
        data.put("checkpoint", StringUtils.hasText(checkpointResponse) ? checkpointResponse : "template-default");
        data.put("type", taskType);
        return AjaxResult.success(data);
    }

    private AjaxResult packDetectors(JSONObject params, String backbone, MultipartFile weightFile, boolean useCustomPretrained) throws Exception {
        // DetectoRS 目前只做 ResNet
        if (!"resnet".equals(backbone)) {
            return AjaxResult.error("DetectoRS 暂时只支持 ResNet 主干");
        }

        String taskName = params.getStr("taskName");
        String taskType = params.getStr("taskType");
        String dataset = params.getStr("dataset");

        String scale = "(" + params.getStr("mmdet_input_width") + ", " + params.getStr("mmdet_input_height") + ")";
        Integer batchSize = params.getInt("mmdet_batchsize");
        Integer maxEpochs = params.getInt("mmdet_epoch");
        Integer valInterval = params.getInt("mmdet_val_interval");
        String milestones = params.getStr("mmdet_step");
        String optType = params.getStr("mmdet_opt");
        Double lr = params.getDouble("mmdet_inlr");
        Integer weightInterval = params.getInt("mmdet_weight_interval");

        if (!StringUtils.hasText(taskName)) return AjaxResult.error("缺少参数：taskName");
        if (!StringUtils.hasText(taskType)) return AjaxResult.error("缺少参数：taskType");
        if (!StringUtils.hasText(dataset))  return AjaxResult.error("缺少参数：dataset");

        DatasetCfg dsCfg;
        try {
            dsCfg = buildDatasetCfg(dataset);
        } catch (Exception e) {
            return AjaxResult.error("解析实例数据集失败: " + e.getMessage());
        }
        String numClassesStr = dsCfg.numClassesStr;
        if (!StringUtils.hasText(numClassesStr)) {
            return AjaxResult.error("实例数据集缺少类别数量信息");
        }

        // 入库
        TrainForm form = new TrainForm();
        form.setName(taskName);
        form.setType(taskType);
        form.setCls_num(Integer.parseInt(numClassesStr));
        form.setPrj_num(1);
        form.setTask_num(1);
        form.setImg_num(0);
        form.setObj_num(0L);
        form.setImg_val_num(0);
        form.setObj_val_num(0L);
        form.setRemark(taskType + "-detectors-resnet 训练任务 - " + taskName);
        form.setArgs(null);
        form.setData(new com.xgls.web.entity.TrainData());

        boolean saveSuccess = taskService.saveLink(
                form, SessionUtil.getCurUser(), weightForDb(useCustomPretrained, weightFile), null, null, null, null);
        if (!saveSuccess) return AjaxResult.error("训练任务入库失败");

        Integer taskId = null;
        LambdaQueryWrapper<TrainTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrainTask::getName, taskName);
        TrainTask savedTask = taskService.getOne(wrapper);
        if (savedTask != null) {
            taskId = savedTask.getId();
            TrainTask updateRecord = new TrainTask();
            updateRecord.setId(taskId);
            updateRecord.setStatus(CodeMap.TRAIN_TASK_STATUS_READY);
            updateRecord.setUpdated_date(LocalDateTime.now());
            taskService.updateById(updateRecord);
        }

        // 运行目录 & checkpoint（可选）
        Path runDir = MmdetConfigUtil.ensureRunDir(rootPath, taskName);
        Path ckptDir = runDir.resolve("checkpoints");
        String[] ckptPair = resolveCheckpointForPack(useCustomPretrained, params, weightFile, ckptDir);
        String uploadCkptPy = ckptPair[0];
        String checkpointResponse = ckptPair[1];

        // 读 DetectoRS 模板
        String baseTxt = MmdetConfigUtil.readString(Paths.get(tplDetectorsResnet));

        /* ========= ① 先做“通用替换”：checkpoint / load_from / pretrained + num_classes ========== */
        if (StringUtils.hasText(uploadCkptPy)) {
            baseTxt = injectCheckpointAndNumClasses(baseTxt, uploadCkptPy, numClassesStr);
        } else {
            baseTxt = injectNumClassesOnly(baseTxt, numClassesStr);
        }

        /* ========= ② DetectoRS 专用：depth / rfp_steps / aspp_dilations / 两个 SAC ========== */

        // depth -> neck.rfp_backbone.depth
        Integer detDepth = params.getInt("mmdet_depth");
        if (detDepth != null) {
            baseTxt = MmdetConfigUtil.replaceFirst(
                    baseTxt,
                    "(?s)(rfp_backbone\\s*=\\s*dict\\([^)]*?depth\\s*=\\s*)\\d+",
                    "$1" + detDepth
            );
        }

        // rfp_steps
        Integer rfpSteps = params.getInt("mmdet_rfp_steps");
        if (rfpSteps != null) {
            baseTxt = MmdetConfigUtil.replaceFirst(
                    baseTxt,
                    "(?s)(rfp_steps\\s*=\\s*)\\d+",
                    "$1" + rfpSteps
            );
        }

        // aspp_dilations
        String dilStr = params.getStr("mmdet_aspp_dilation");
        if (StringUtils.hasText(dilStr)) {
            String[] parts = dilStr.split(",");
            List<String> arr = new ArrayList<>();
            for (String s : parts) {
                s = s.trim();
                if (!s.isEmpty()) arr.add(s);
            }
            if (!arr.isEmpty()) {
                String tuple = "(" + String.join(", ", arr) + ")";
                baseTxt = MmdetConfigUtil.replaceFirst(
                        baseTxt,
                        "(?s)(aspp_dilations\\s*=\\s*)\\([^)]*\\)",
                        "$1" + tuple
                );
            }
        }

        // neck.rfp_backbone.pretrained = mmdet_checkpoint
        String rfpPre = params.getStr("mmdet_checkpoint");
        if (StringUtils.hasText(rfpPre)) {
            String escaped = rfpPre.replace("'", "\\'");
            baseTxt = MmdetConfigUtil.replaceFirst(
                    baseTxt,
                    "(?s)(rfp_backbone\\s*=\\s*dict\\([^)]*?pretrained\\s*=\\s*)'[^']*'",
                    "$1'" + escaped + "'"
            );
        }

        /* ---------------------- (a) backbone 上的 SAC ---------------------- */
        Object rawDcn = params.get("mmdet_dcn");
        boolean useBackboneSac = (rawDcn instanceof Boolean)
                ? (Boolean) rawDcn
                : "true".equalsIgnoreCase(String.valueOf(rawDcn));

        String dcnStageStr = params.getStr("mmdet_dcnStage", "");
        String backboneStageTuple = buildStageTuple(dcnStageStr);

        java.util.regex.Pattern pBackbone = java.util.regex.Pattern.compile(
                "backbone\\s*=\\s*dict\\((.*?)\\),\\s*neck=",
                java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher mBackbone = pBackbone.matcher(baseTxt);
        if (mBackbone.find()) {
            String backboneBlock = mBackbone.group(1);
            if (useBackboneSac) {
                backboneBlock = backboneBlock
                        .replaceAll("sac\\s*=\\s*None\\s*,?", "sac=dict(type='SAC', use_deform=True),");
                backboneBlock = backboneBlock
                        .replaceAll("sac\\s*=\\s*dict\\([^)]*\\)", "sac=dict(type='SAC', use_deform=True)");
                backboneBlock = backboneBlock
                        .replaceAll("stage_with_sac\\s*=\\s*\\([^)]*\\)", "stage_with_sac=" + backboneStageTuple);
            } else {
                backboneBlock = backboneBlock
                        .replaceAll("sac\\s*=\\s*dict\\([^)]*\\)\\s*,?", "sac=None,");
                backboneBlock = backboneBlock
                        .replaceAll("stage_with_sac\\s*=\\s*\\([^)]*\\)", "stage_with_sac=(False, False, False, False)");
            }
            baseTxt = baseTxt.substring(0, mBackbone.start(1))
                    + backboneBlock
                    + baseTxt.substring(mBackbone.end(1));
        }

        /* ---------------------- (b) neck.rfp_backbone 上的 SAC ---------------------- */
        Object rawNeckSac = params.get("mmdet_neck_sac");
        boolean useNeckSac = (rawNeckSac instanceof Boolean)
                ? (Boolean) rawNeckSac
                : "true".equalsIgnoreCase(String.valueOf(rawNeckSac));

        String neckStageStr = params.getStr("mmdet_neck_sacStage", "");
        String neckStageTuple = buildStageTuple(neckStageStr);

        java.util.regex.Pattern pRfp = java.util.regex.Pattern.compile(
                "rfp_backbone\\s*=\\s*dict\\((.*?)\\)\\)",
                java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher mRfp = pRfp.matcher(baseTxt);
        if (mRfp.find()) {
            String rfpBlock = mRfp.group(1);
            if (useNeckSac) {
                rfpBlock = rfpBlock
                        .replaceAll("sac\\s*=\\s*None\\s*,?", "sac=dict(type='SAC', use_deform=True),");
                rfpBlock = rfpBlock
                        .replaceAll("sac\\s*=\\s*dict\\([^)]*\\)", "sac=dict(type='SAC', use_deform=True)");
                rfpBlock = rfpBlock
                        .replaceAll("stage_with_sac\\s*=\\s*\\([^)]*\\)", "stage_with_sac=" + neckStageTuple);
            } else {
                rfpBlock = rfpBlock
                        .replaceAll("sac\\s*=\\s*dict\\([^)]*\\)\\s*,?", "sac=None,");
                rfpBlock = rfpBlock
                        .replaceAll("stage_with_sac\\s*=\\s*\\([^)]*\\)", "stage_with_sac=(False, False, False, False)");
            }
            baseTxt = baseTxt.substring(0, mRfp.start(1))
                    + rfpBlock
                    + baseTxt.substring(mRfp.end(1));
        }

        // 落盘 base_model.py
        Path baseSaved = MmdetConfigUtil.writeTextFile(runDir, "base_model.py", baseTxt);

        // data/train/runtime（包含 metainfo 注入）
        Map<String, Object> common = writeCommonFiles(
                runDir, dsCfg,
                scale, batchSize, maxEpochs, valInterval,
                milestones, optType, lr, weightInterval
        );

        // 存 network_name
        saveNetworkName(taskId, "detectors_resnet");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskName", taskName);
        data.put("taskId", taskId);
        data.put("dataset", dataset);
        data.put("mmdet_network", "detectors");
        data.put("mmdet_backbone", "resnet");
        data.putAll(common);
        data.put("checkpoint", StringUtils.hasText(checkpointResponse) ? checkpointResponse : "template-default");
        data.put("type", taskType);
        return AjaxResult.success(data);
    }
    private AjaxResult packDetr(JSONObject params, String backbone, MultipartFile weightFile, boolean useCustomPretrained) throws Exception {
        // 1. 通用参数
        String taskName = params.getStr("taskName");
        String taskType = params.getStr("taskType");
        String dataset  = params.getStr("dataset");

        String scale = "(" + params.getStr("mmdet_input_width") + ", "
                + params.getStr("mmdet_input_height") + ")";
        Integer batchSize    = params.getInt("mmdet_batchsize");
        Integer maxEpochs    = params.getInt("mmdet_epoch");
        Integer valInterval  = params.getInt("mmdet_val_interval");
        String  milestones   = params.getStr("mmdet_step");
        String  optType      = params.getStr("mmdet_opt");
        Double  lr           = params.getDouble("mmdet_inlr");
        Integer weightInterval = params.getInt("mmdet_weight_interval");

        if (!StringUtils.hasText(taskName))  return AjaxResult.error("缺少参数：taskName");
        if (!StringUtils.hasText(taskType))  return AjaxResult.error("缺少参数：taskType");
        if (!StringUtils.hasText(dataset))   return AjaxResult.error("缺少参数：dataset");

        // 2. 基于 InstanceDatasetinfo 构造 COCO 路径 & 类别
        DatasetCfg dsCfg;
        try {
            dsCfg = buildDatasetCfg(dataset);
        } catch (Exception e) {
            return AjaxResult.error("解析实例数据集失败: " + e.getMessage());
        }
        String numClassesStr = dsCfg.numClassesStr;
        if (!StringUtils.hasText(numClassesStr)) {
            return AjaxResult.error("实例数据集缺少类别数量信息");
        }

        // 3. 入库 TrainTask（和其他 packXXX 一样）
        TrainForm form = new TrainForm();
        form.setName(taskName);
        form.setType(taskType);
        form.setCls_num(Integer.parseInt(numClassesStr));
        form.setPrj_num(1);
        form.setTask_num(1);
        form.setImg_num(0);
        form.setObj_num(0L);
        form.setImg_val_num(0);
        form.setObj_val_num(0L);
        form.setRemark(taskType + "-detr-resnet 训练任务 - " + taskName);
        form.setArgs(null);
        form.setData(new com.xgls.web.entity.TrainData());

        boolean saveSuccess = taskService.saveLink(
                form, SessionUtil.getCurUser(), weightForDb(useCustomPretrained, weightFile), null, null, null, null);
        if (!saveSuccess) return AjaxResult.error("训练任务入库失败");

        Integer taskId = null;
        LambdaQueryWrapper<TrainTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrainTask::getName, taskName);
        TrainTask savedTask = taskService.getOne(wrapper);
        if (savedTask != null) {
            taskId = savedTask.getId();
            TrainTask updateRecord = new TrainTask();
            updateRecord.setId(taskId);
            updateRecord.setStatus(CodeMap.TRAIN_TASK_STATUS_READY);
            updateRecord.setUpdated_date(LocalDateTime.now());
            taskService.updateById(updateRecord);
        }

        // 4. 运行目录 & checkpoint（可选）
        Path runDir = MmdetConfigUtil.ensureRunDir(rootPath, taskName);
        Path ckptDir = runDir.resolve("checkpoints");
        String[] ckptPairDetr = resolveCheckpointForPack(useCustomPretrained, params, weightFile, ckptDir);
        String ckptPy = ckptPairDetr[0];
        String checkpointResponseDetr = ckptPairDetr[1];

//        // 5. 读 DETR 模板（ResNet 版）
//        String baseTxt = MmdetConfigUtil.readString(Paths.get(tplDetrResnet));

        // 5. 选模板
        String chosenTpl;
        switch (backbone) {
            case "resnet":
                chosenTpl = tplDetrResnet;
                break;
            case "convnext":
                return AjaxResult.error("DETR的主干只支持：ResNet / SwinTransformer");
            case "swint":
                chosenTpl = tplDetrSwint;
                break;
            default:
                throw new IllegalStateException("不可能来到这里");
        }
        String baseTxt = MmdetConfigUtil.readString(Paths.get(chosenTpl));

        // ==== resnet 分支 ====
        if ("resnet".equals(backbone)) {
            Integer depth = params.getInt("mmdet_depth");
            if (depth == null) return AjaxResult.error("缺少参数：mmdet_depth");
            baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(depth\\s*=\\s*)\\d+", "$1" + depth);

            if (params.containsKey("mmdet_dcn")) {
                Object raw = params.get("mmdet_dcn");
                boolean useDcn = (raw instanceof Boolean) ? (Boolean) raw
                        : ("true".equalsIgnoreCase(String.valueOf(raw)));
                String dcnVal = useDcn
                        ? "dict(type='DCNv2', deform_groups=1, fallback_on_stride=False)"
                        : "None";
                baseTxt = MmdetConfigUtil.replaceFirst(
                        baseTxt, "(dcn\\s*=\\s*)(None|dict\\([^\\)]*\\))", "$1" + dcnVal);
            }
            if (params.containsKey("mmdet_dcnStage")) {
                String tuple = buildStageTuple(params.getStr("mmdet_dcnStage", ""));
                baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(stage_with_dcn\\s*=\\s*)\\([^\\)]*\\)", "$1" + tuple);
            }
        }

        // ==== swint 分支 ====
        if ("swint".equals(backbone)) {
            String arch = params.getStr("mmdet_swint_arch");
            if (!StringUtils.hasText(arch))
                return AjaxResult.error("缺少参数：mmdet_swint_arch ∈ {tiny,small,base,large}");
            arch = arch.trim().toLowerCase();

            String embed, depths, heads, inC;
            switch (arch) {
                case "tiny":
                    embed = "96";
                    depths = "[2, 2, 6, 2]";
                    heads = "[3, 6, 12, 24]";
                    inC = "[96, 192, 384, 768]";
                    break;
                case "small":
                    embed = "96";
                    depths = "[2, 2, 18, 2]";
                    heads = "[3, 6, 12, 24]";
                    inC = "[96, 192, 384, 768]";
                    break;
                case "base":
                    embed = "128";
                    depths = "[2, 2, 18, 2]";
                    heads = "[4, 8, 16, 32]";
                    inC = "[128, 256, 512, 1024]";
                    break;
                case "large":
                    embed = "192";
                    depths = "[2, 2, 18, 2]";
                    heads = "[6, 12, 24, 48]";
                    inC = "[192, 384, 768, 1536]";
                    break;
                default:
                    return AjaxResult.error("mmdet_swint_arch 不合法");
            }
            baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(embed_dims\\s*=\\s*)\\d+", "$1" + embed);
            baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(depths\\s*=\\s*)\\[[^\\]]*\\]", "$1" + depths);
            baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(num_heads\\s*=\\s*)\\[[^\\]]*\\]", "$1" + heads);
            // 关键：neck 里的 in_channels 也要改
            baseTxt = MmdetConfigUtil.replaceAll(baseTxt, "(in_channels\\s*=\\s*)\\[[^\\]]*\\]", "$1" + inC);

            if (params.containsKey("mmdet_window")) {
                Integer w = params.getInt("mmdet_window");
                if (w != null) {
                    baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(window_size\\s*=\\s*)\\d+", "$1" + w);
                }
            }
        }

        // 6. 通用：注入 checkpoint / num_classes
        if (StringUtils.hasText(ckptPy)) {
            baseTxt = injectCheckpointAndNumClasses(baseTxt, ckptPy, numClassesStr);
        } else {
            baseTxt = injectNumClassesOnly(baseTxt, numClassesStr);
        }

        // 7. DETR 专用：根据 params 正则替换（见下一节）
        baseTxt = applyDetrHyperParams(baseTxt, params);

        // 8. 写 base_model.py
        Path baseSaved = MmdetConfigUtil.writeTextFile(runDir, "base_model.py", baseTxt);

        // 9. 写 data_pipeline.py / train_opt.py / default_runtime.py / combined_base.py
        Map<String, Object> common = writeCommonFiles(
                runDir, dsCfg,
                scale, batchSize, maxEpochs, valInterval,
                milestones, optType, lr, weightInterval
        );

        // 10. 存 network_name
        saveNetworkName(taskId, "detr_" + backbone);

        // 11. 返回信息
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskName", taskName);
        data.put("taskId", taskId);
        data.put("dataset", dataset);
        data.put("mmdet_network", "detr");
        data.put("mmdet_backbone", backbone);
        data.putAll(common);
        data.put("checkpoint", StringUtils.hasText(checkpointResponseDetr) ? checkpointResponseDetr : "template-default");
        data.put("type", taskType);
        return AjaxResult.success(data);
    }

    private AjaxResult packDINO(JSONObject params, String backbone, MultipartFile weightFile, boolean useCustomPretrained) throws Exception {
        // 1. 通用参数
        String taskName = params.getStr("taskName");
        String taskType = params.getStr("taskType");
        String dataset  = params.getStr("dataset");

        String scale = "(" + params.getStr("mmdet_input_width") + ", "
                + params.getStr("mmdet_input_height") + ")";
        Integer batchSize    = params.getInt("mmdet_batchsize");
        Integer maxEpochs    = params.getInt("mmdet_epoch");
        Integer valInterval  = params.getInt("mmdet_val_interval");
        String  milestones   = params.getStr("mmdet_step");
        String  optType      = params.getStr("mmdet_opt");
        Double  lr           = params.getDouble("mmdet_inlr");
        Integer weightInterval = params.getInt("mmdet_weight_interval");

        if (!StringUtils.hasText(taskName))  return AjaxResult.error("缺少参数：taskName");
        if (!StringUtils.hasText(taskType))  return AjaxResult.error("缺少参数：taskType");
        if (!StringUtils.hasText(dataset))   return AjaxResult.error("缺少参数：dataset");

        // 2. 基于 InstanceDatasetinfo 构造 COCO 路径 & 类别
        DatasetCfg dsCfg;
        try {
            dsCfg = buildDatasetCfg(dataset);
        } catch (Exception e) {
            return AjaxResult.error("解析实例数据集失败: " + e.getMessage());
        }
        String numClassesStr = dsCfg.numClassesStr;
        if (!StringUtils.hasText(numClassesStr)) {
            return AjaxResult.error("实例数据集缺少类别数量信息");
        }

        // 3. 入库 TrainTask（和其他 packXXX 一样）
        TrainForm form = new TrainForm();
        form.setName(taskName);
        form.setType(taskType);
        form.setCls_num(Integer.parseInt(numClassesStr));
        form.setPrj_num(1);
        form.setTask_num(1);
        form.setImg_num(0);
        form.setObj_num(0L);
        form.setImg_val_num(0);
        form.setObj_val_num(0L);
        form.setRemark(taskType + "-detr-resnet 训练任务 - " + taskName);
        form.setArgs(null);
        form.setData(new com.xgls.web.entity.TrainData());

        boolean saveSuccess = taskService.saveLink(
                form, SessionUtil.getCurUser(), weightForDb(useCustomPretrained, weightFile), null, null, null, null);
        if (!saveSuccess) return AjaxResult.error("训练任务入库失败");

        Integer taskId = null;
        LambdaQueryWrapper<TrainTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrainTask::getName, taskName);
        TrainTask savedTask = taskService.getOne(wrapper);
        if (savedTask != null) {
            taskId = savedTask.getId();
            TrainTask updateRecord = new TrainTask();
            updateRecord.setId(taskId);
            updateRecord.setStatus(CodeMap.TRAIN_TASK_STATUS_READY);
            updateRecord.setUpdated_date(LocalDateTime.now());
            taskService.updateById(updateRecord);
        }

        // 4. 运行目录 & checkpoint（可选）
        Path runDir = MmdetConfigUtil.ensureRunDir(rootPath, taskName);
        Path ckptDir = runDir.resolve("checkpoints");
        String[] ckptPairDino = resolveCheckpointForPack(useCustomPretrained, params, weightFile, ckptDir);
        String ckptPy = ckptPairDino[0];
        String checkpointResponseDino = ckptPairDino[1];

//        // 5. 读 DETR 模板（ResNet 版）
//        String baseTxt = MmdetConfigUtil.readString(Paths.get(tplDetrResnet));

        // 5. 选模板
        String chosenTpl;
        switch (backbone) {
            case "resnet":
                chosenTpl = tplDinoResnet;
                break;
            case "convnext":
                return AjaxResult.error("DINO的主干只支持：ResNet / SwinTransformer");
            case "swint":
                chosenTpl = tplDinoSwint;
                break;
            default:
                throw new IllegalStateException("不可能来到这里");
        }
        String baseTxt = MmdetConfigUtil.readString(Paths.get(chosenTpl));

        // ==== resnet 分支 ====
        if ("resnet".equals(backbone)) {
            Integer depth = params.getInt("mmdet_depth");
            if (depth == null) return AjaxResult.error("缺少参数：mmdet_depth");
            baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(depth\\s*=\\s*)\\d+", "$1" + depth);

            if (params.containsKey("mmdet_dcn")) {
                Object raw = params.get("mmdet_dcn");
                boolean useDcn = (raw instanceof Boolean) ? (Boolean) raw
                        : ("true".equalsIgnoreCase(String.valueOf(raw)));
                String dcnVal = useDcn
                        ? "dict(type='DCNv2', deform_groups=1, fallback_on_stride=False)"
                        : "None";
                baseTxt = MmdetConfigUtil.replaceFirst(
                        baseTxt, "(dcn\\s*=\\s*)(None|dict\\([^\\)]*\\))", "$1" + dcnVal);
            }
            if (params.containsKey("mmdet_dcnStage")) {
                String tuple = buildStageTuple(params.getStr("mmdet_dcnStage", ""));
                baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(stage_with_dcn\\s*=\\s*)\\([^\\)]*\\)", "$1" + tuple);
            }
        }

        // ==== swint 分支 ====
        if ("swint".equals(backbone)) {
            String arch = params.getStr("mmdet_swint_arch");
            if (!StringUtils.hasText(arch))
                return AjaxResult.error("缺少参数：mmdet_swint_arch ∈ {tiny,small,base,large}");
            arch = arch.trim().toLowerCase();

            String embed, depths, heads, inC;
            switch (arch) {
                case "tiny":
                    embed = "96";
                    depths = "[2, 2, 6, 2]";
                    heads = "[3, 6, 12, 24]";
                    inC = "[96, 192, 384, 768]";
                    break;
                case "small":
                    embed = "96";
                    depths = "[2, 2, 18, 2]";
                    heads = "[3, 6, 12, 24]";
                    inC = "[96, 192, 384, 768]";
                    break;
                case "base":
                    embed = "128";
                    depths = "[2, 2, 18, 2]";
                    heads = "[4, 8, 16, 32]";
                    inC = "[128, 256, 512, 1024]";
                    break;
                case "large":
                    embed = "192";
                    depths = "[2, 2, 18, 2]";
                    heads = "[6, 12, 24, 48]";
                    inC = "[192, 384, 768, 1536]";
                    break;
                default:
                    return AjaxResult.error("mmdet_swint_arch 不合法");
            }
            baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(embed_dims\\s*=\\s*)\\d+", "$1" + embed);
            baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(depths\\s*=\\s*)\\[[^\\]]*\\]", "$1" + depths);
            baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(num_heads\\s*=\\s*)\\[[^\\]]*\\]", "$1" + heads);
            // 关键：neck 里的 in_channels 也要改
            baseTxt = MmdetConfigUtil.replaceAll(baseTxt, "(in_channels\\s*=\\s*)\\[[^\\]]*\\]", "$1" + inC);

            if (params.containsKey("mmdet_window")) {
                Integer w = params.getInt("mmdet_window");
                if (w != null) {
                    baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(window_size\\s*=\\s*)\\d+", "$1" + w);
                }
            }
        }

        // 6. 通用：注入 checkpoint / num_classes
        if (StringUtils.hasText(ckptPy)) {
            baseTxt = injectCheckpointAndNumClasses(baseTxt, ckptPy, numClassesStr);
        } else {
            baseTxt = injectNumClassesOnly(baseTxt, numClassesStr);
        }

        // 7. DETR 专用：根据 params 正则替换（见下一节）
        baseTxt = applyDetrHyperParams(baseTxt, params);

        // 8. 写 base_model.py
        Path baseSaved = MmdetConfigUtil.writeTextFile(runDir, "base_model.py", baseTxt);

        // 9. 写 data_pipeline.py / train_opt.py / default_runtime.py / combined_base.py
        Map<String, Object> common = writeCommonFiles(
                runDir, dsCfg,
                scale, batchSize, maxEpochs, valInterval,
                milestones, optType, lr, weightInterval
        );

        // 10. 存 network_name
        saveNetworkName(taskId, "dino_" + backbone);

        // 11. 返回信息
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskName", taskName);
        data.put("taskId", taskId);
        data.put("dataset", dataset);
        data.put("mmdet_network", "dino");
        data.put("mmdet_backbone", backbone);
        data.putAll(common);
        data.put("checkpoint", StringUtils.hasText(checkpointResponseDino) ? checkpointResponseDino : "template-default");
        data.put("type", taskType);
        return AjaxResult.success(data);
    }
    private AjaxResult packDeformableDetr(JSONObject params, String backbone, MultipartFile weightFile, boolean useCustomPretrained) throws Exception {
        // 1. 通用参数
        String taskName = params.getStr("taskName");
        String taskType = params.getStr("taskType");
        String dataset  = params.getStr("dataset");

        String scale = "(" + params.getStr("mmdet_input_width") + ", "
                + params.getStr("mmdet_input_height") + ")";
        Integer batchSize    = params.getInt("mmdet_batchsize");
        Integer maxEpochs    = params.getInt("mmdet_epoch");
        Integer valInterval  = params.getInt("mmdet_val_interval");
        String  milestones   = params.getStr("mmdet_step");
        String  optType      = params.getStr("mmdet_opt");
        Double  lr           = params.getDouble("mmdet_inlr");
        Integer weightInterval = params.getInt("mmdet_weight_interval");

        if (!StringUtils.hasText(taskName))  return AjaxResult.error("缺少参数：taskName");
        if (!StringUtils.hasText(taskType))  return AjaxResult.error("缺少参数：taskType");
        if (!StringUtils.hasText(dataset))   return AjaxResult.error("缺少参数：dataset");

        // 2. 基于 InstanceDatasetinfo 构造 COCO 路径 & 类别
        DatasetCfg dsCfg;
        try {
            dsCfg = buildDatasetCfg(dataset);
        } catch (Exception e) {
            return AjaxResult.error("解析实例数据集失败: " + e.getMessage());
        }
        String numClassesStr = dsCfg.numClassesStr;
        if (!StringUtils.hasText(numClassesStr)) {
            return AjaxResult.error("实例数据集缺少类别数量信息");
        }

        // 3. 入库 TrainTask（和其他 packXXX 一样）
        TrainForm form = new TrainForm();
        form.setName(taskName);
        form.setType(taskType);
        form.setCls_num(Integer.parseInt(numClassesStr));
        form.setPrj_num(1);
        form.setTask_num(1);
        form.setImg_num(0);
        form.setObj_num(0L);
        form.setImg_val_num(0);
        form.setObj_val_num(0L);
        form.setRemark(taskType + "-detr-resnet 训练任务 - " + taskName);
        form.setArgs(null);
        form.setData(new com.xgls.web.entity.TrainData());

        boolean saveSuccess = taskService.saveLink(
                form, SessionUtil.getCurUser(), weightForDb(useCustomPretrained, weightFile), null, null, null, null);
        if (!saveSuccess) return AjaxResult.error("训练任务入库失败");

        Integer taskId = null;
        LambdaQueryWrapper<TrainTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrainTask::getName, taskName);
        TrainTask savedTask = taskService.getOne(wrapper);
        if (savedTask != null) {
            taskId = savedTask.getId();
            TrainTask updateRecord = new TrainTask();
            updateRecord.setId(taskId);
            updateRecord.setStatus(CodeMap.TRAIN_TASK_STATUS_READY);
            updateRecord.setUpdated_date(LocalDateTime.now());
            taskService.updateById(updateRecord);
        }

        // 4. 运行目录 & checkpoint（可选）
        Path runDir = MmdetConfigUtil.ensureRunDir(rootPath, taskName);
        Path ckptDir = runDir.resolve("checkpoints");
        String[] ckptPairDef = resolveCheckpointForPack(useCustomPretrained, params, weightFile, ckptDir);
        String ckptPy = ckptPairDef[0];
        String checkpointResponseDef = ckptPairDef[1];

//        // 5. 读 DETR 模板（ResNet 版）
//        String baseTxt = MmdetConfigUtil.readString(Paths.get(tplDetrResnet));

        // 5. 选模板
        String chosenTpl;
        switch (backbone) {
            case "resnet":
                chosenTpl = tplDeformableDetrResnet;
                break;
            case "convnext":
                return AjaxResult.error("Deformable DETR 的主干只支持：ResNet / SwinTransformer");
            case "swint":
                chosenTpl = tplDeformableDetrSwint;
                break;
            default:
                throw new IllegalStateException("不可能来到这里");
        }
        String baseTxt = MmdetConfigUtil.readString(Paths.get(chosenTpl));

        // ==== resnet 分支 ====
        if ("resnet".equals(backbone)) {
            Integer depth = params.getInt("mmdet_depth");
            if (depth == null) return AjaxResult.error("缺少参数：mmdet_depth");
            baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(depth\\s*=\\s*)\\d+", "$1" + depth);

            if (params.containsKey("mmdet_dcn")) {
                Object raw = params.get("mmdet_dcn");
                boolean useDcn = (raw instanceof Boolean) ? (Boolean) raw
                        : ("true".equalsIgnoreCase(String.valueOf(raw)));
                String dcnVal = useDcn
                        ? "dict(type='DCNv2', deform_groups=1, fallback_on_stride=False)"
                        : "None";
                baseTxt = MmdetConfigUtil.replaceFirst(
                        baseTxt, "(dcn\\s*=\\s*)(None|dict\\([^\\)]*\\))", "$1" + dcnVal);
            }
            if (params.containsKey("mmdet_dcnStage")) {
                String tuple = buildStageTuple(params.getStr("mmdet_dcnStage", ""));
                baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(stage_with_dcn\\s*=\\s*)\\([^\\)]*\\)", "$1" + tuple);
            }
        }

        // ==== swint 分支 ====
        if ("swint".equals(backbone)) {
            String arch = params.getStr("mmdet_swint_arch");
            if (!StringUtils.hasText(arch))
                return AjaxResult.error("缺少参数：mmdet_swint_arch ∈ {tiny,small,base,large}");
            arch = arch.trim().toLowerCase();

            String embed, depths, heads, inC;
            switch (arch) {
                case "tiny":
                    embed = "96";
                    depths = "[2, 2, 6, 2]";
                    heads = "[3, 6, 12, 24]";
                    inC = "[96, 192, 384, 768]";
                    break;
                case "small":
                    embed = "96";
                    depths = "[2, 2, 18, 2]";
                    heads = "[3, 6, 12, 24]";
                    inC = "[96, 192, 384, 768]";
                    break;
                case "base":
                    embed = "128";
                    depths = "[2, 2, 18, 2]";
                    heads = "[4, 8, 16, 32]";
                    inC = "[128, 256, 512, 1024]";
                    break;
                case "large":
                    embed = "192";
                    depths = "[2, 2, 18, 2]";
                    heads = "[6, 12, 24, 48]";
                    inC = "[192, 384, 768, 1536]";
                    break;
                default:
                    return AjaxResult.error("mmdet_swint_arch 不合法");
            }
            baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(embed_dims\\s*=\\s*)\\d+", "$1" + embed);
            baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(depths\\s*=\\s*)\\[[^\\]]*\\]", "$1" + depths);
            baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(num_heads\\s*=\\s*)\\[[^\\]]*\\]", "$1" + heads);
            // 关键：neck 里的 in_channels 也要改
            baseTxt = MmdetConfigUtil.replaceAll(baseTxt, "(in_channels\\s*=\\s*)\\[[^\\]]*\\]", "$1" + inC);

            if (params.containsKey("mmdet_window")) {
                Integer w = params.getInt("mmdet_window");
                if (w != null) {
                    baseTxt = MmdetConfigUtil.replaceFirst(baseTxt, "(window_size\\s*=\\s*)\\d+", "$1" + w);
                }
            }
        }

        // 6. 通用：注入 checkpoint / num_classes
        if (StringUtils.hasText(ckptPy)) {
            baseTxt = injectCheckpointAndNumClasses(baseTxt, ckptPy, numClassesStr);
        } else {
            baseTxt = injectNumClassesOnly(baseTxt, numClassesStr);
        }

        // 7. DETR 专用：根据 params 正则替换（见下一节）
        baseTxt = applyDetrHyperParams(baseTxt, params);

        // 8. 写 base_model.py
        Path baseSaved = MmdetConfigUtil.writeTextFile(runDir, "base_model.py", baseTxt);

        // 9. 写 data_pipeline.py / train_opt.py / default_runtime.py / combined_base.py
        Map<String, Object> common = writeCommonFiles(
                runDir, dsCfg,
                scale, batchSize, maxEpochs, valInterval,
                milestones, optType, lr, weightInterval
        );

        // 10. 存 network_name
        saveNetworkName(taskId, "deformable_detr_" + backbone);

        // 11. 返回信息
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskName", taskName);
        data.put("taskId", taskId);
        data.put("dataset", dataset);
        data.put("mmdet_network", "deformable detr");
        data.put("mmdet_backbone", backbone);
        data.putAll(common);
        data.put("checkpoint", StringUtils.hasText(checkpointResponseDef) ? checkpointResponseDef : "template-default");
        data.put("type", taskType);
        return AjaxResult.success(data);
    }


    /* ==================== 小工具 ==================== */

    private String normalizeNetwork(String raw) {
        if (raw == null) return null;
        raw = raw.trim();
        if ("Faster R-CNN".equalsIgnoreCase(raw)) return "faster-rcnn";
        if ("Cascade R-CNN".equalsIgnoreCase(raw)) return "cascade-rcnn";
        if ("DetectoRS".equalsIgnoreCase(raw)) return "detectors";
        if ("DETR".equalsIgnoreCase(raw)) return "detr";
        return raw.toLowerCase();
    }

    private String normalizeBackbone(String raw) {
        if (raw == null) return null;
        raw = raw.trim();
        if ("ResNet".equalsIgnoreCase(raw)) return "resnet";
        if ("ConvNext".equalsIgnoreCase(raw)) return "convnext";
        if ("SwinTransformer".equalsIgnoreCase(raw)) return "swint";
        return raw.toLowerCase();
    }

    private String buildStageTuple(String stageStr) {
        boolean[] flags = new boolean[]{false, false, false, false};
        if (StringUtils.hasText(stageStr)) {
            for (String s : stageStr.split(",")) {
                try {
                    int idx = Integer.parseInt(s.trim());
                    if (idx >= 0 && idx < 4) flags[idx] = true;
                } catch (Exception ignored) {
                }
            }
        }
        return "("
                + (flags[0] ? "True" : "False") + ", "
                + (flags[1] ? "True" : "False") + ", "
                + (flags[2] ? "True" : "False") + ", "
                + (flags[3] ? "True" : "False") + ")";
    }
    /**
     * 根据前端 params 调整 DETR 模板中的各类超参数。
     * 所有替换都是“尽量温和”的，只改目标字段，尽量不影响其它结构。
     */
    private String applyDetrHyperParams(String txt, JSONObject params) {

        String result = txt;

        // ===== 1. neck 单/多尺度 =====
        // 前端约定：detr_neck_mode = "single" / "multi"（也可以用 bool/枚举，你自己定）
        String neckMode = params.getStr("detr_neck_mode");
        if (StringUtils.hasText(neckMode)) {
            String inChannels;
            String numOuts;
            if ("multi".equalsIgnoreCase(neckMode)) {
                // 多尺度
                inChannels = "[512, 1024, 2048]";
                numOuts    = "4";
            } else {
                // 默认单尺度
                inChannels = "[2048]";
                numOuts    = "1";
            }
            // 只在 neck=dict(...) 里替换 in_channels / num_outs
            result = MmdetConfigUtil.replaceFirst(
                    result,
                    "(?s)(neck\\s*=\\s*dict\\([^)]*?in_channels\\s*=\\s*)\\[[^\\]]*\\]",
                    "$1" + inChannels
            );
            result = MmdetConfigUtil.replaceFirst(
                    result,
                    "(?s)(neck\\s*=\\s*dict\\([^)]*?num_outs\\s*=\\s*)\\d+",
                    "$1" + numOuts
            );
        }

        // ===== 2. 嵌入维度 embed_dims + num_feats =====
        Integer embedDims = params.getInt("detr_embed_dims");
        if (embedDims != null && embedDims > 0) {
            int numFeats = embedDims / 2;
            // encoder / decoder 里所有 embed_dims 一律替换
            result = MmdetConfigUtil.replaceAll(
                    result,
                    "(embed_dims\\s*=\\s*)\\d+",
                    "$1" + embedDims
            );
            // positional_encoding.num_feats
            result = MmdetConfigUtil.replaceFirst(
                    result,
                    "(?s)(positional_encoding\\s*=\\s*dict\\([^)]*?num_feats\\s*=\\s*)\\d+",
                    "$1" + numFeats
            );
        }

        // ===== 3. 编解码层数 =====
        Integer encLayers = params.getInt("detr_encoder_layers");
        if (encLayers != null && encLayers > 0) {
            result = MmdetConfigUtil.replaceFirst(
                    result,
                    "(?s)(encoder\\s*=\\s*dict\\([^)]*?num_layers\\s*=\\s*)\\d+",
                    "$1" + encLayers
            );
        }
        Integer decLayers = params.getInt("detr_decoder_layers");
        if (decLayers != null && decLayers > 0) {
            result = MmdetConfigUtil.replaceFirst(
                    result,
                    "(?s)(decoder\\s*=\\s*dict\\([^)]*?num_layers\\s*=\\s*)\\d+",
                    "$1" + decLayers
            );
        }

        // ===== 4. 注意力 num_heads / dropout =====
        Integer numHeads = params.getInt("detr_num_heads");
        if (numHeads != null && numHeads > 0) {
            // 所有 num_heads 一起改（encoder self_attn + decoder self_attn + cross_attn）
            result = MmdetConfigUtil.replaceAll(
                    result,
                    "(num_heads\\s*=\\s*)\\d+",
                    "$1" + numHeads
            );
        }
        Double attnDrop = params.getDouble("detr_attn_dropout");
        if (attnDrop != null) {
            result = MmdetConfigUtil.replaceAll(
                    result,
                    "(dropout\\s*=\\s*)[0-9.]+",
                    "$1" + attnDrop
            );
        }

        // ===== 5. FFN 参数：中间层维度、层数、dropout、激活函数 =====
        Integer ffnChannels = params.getInt("detr_ffn_channels");
        if (ffnChannels != null && ffnChannels > 0) {
            result = MmdetConfigUtil.replaceAll(
                    result,
                    "(feedforward_channels\\s*=\\s*)\\d+",
                    "$1" + ffnChannels
            );
        }
        Integer ffnNumFcs = params.getInt("detr_ffn_num_fcs");
        if (ffnNumFcs != null && ffnNumFcs > 0) {
            result = MmdetConfigUtil.replaceAll(
                    result,
                    "(num_fcs\\s*=\\s*)\\d+",
                    "$1" + ffnNumFcs
            );
        }
        Double ffnDrop = params.getDouble("detr_ffn_dropout");
        if (ffnDrop != null) {
            result = MmdetConfigUtil.replaceAll(
                    result,
                    "(ffn_drop\\s*=\\s*)[0-9.]+",
                    "$1" + ffnDrop
            );
        }
        String ffnAct = params.getStr("detr_ffn_act");
        if (StringUtils.hasText(ffnAct)) {
            ffnAct = ffnAct.trim();
            // encoder / decoder ffn_cfg 里 act_cfg=dict(type='ReLU', inplace=True)
            result = MmdetConfigUtil.replaceAll(
                    result,
                    "(act_cfg\\s*=\\s*dict\\(type=)'[^']*'",
                    "$1'" + ffnAct + "'"
            );
        }

        // ===== 6. 位置编码 temperature =====
        Integer posTemp = params.getInt("detr_pos_temperature");
        if (posTemp != null && posTemp > 0) {
            result = MmdetConfigUtil.replaceFirst(
                    result,
                    "(?s)(positional_encoding\\s*=\\s*dict\\([^)]*?temperature\\s*=\\s*)\\d+",
                    "$1" + posTemp
            );
        }

        // ===== 7. 检测头：损失类型 + 权重 =====
        String lossClsType = params.getStr("detr_loss_cls_type");
        if (StringUtils.hasText(lossClsType)) {
            result = MmdetConfigUtil.replaceFirst(
                    result,
                    "(?s)(loss_cls\\s*=\\s*dict\\([^)]*?type=')([^']*)'",
                    "$1" + lossClsType.trim() + "'"
            );
        }
        String lossBBoxType = params.getStr("detr_loss_bbox_type");
        if (StringUtils.hasText(lossBBoxType)) {
            result = MmdetConfigUtil.replaceFirst(
                    result,
                    "(?s)(loss_bbox\\s*=\\s*dict\\([^)]*?type=')([^']*)'",
                    "$1" + lossBBoxType.trim() + "'"
            );
        }
        String lossIouType = params.getStr("detr_loss_iou_type");
        if (StringUtils.hasText(lossIouType)) {
            result = MmdetConfigUtil.replaceFirst(
                    result,
                    "(?s)(loss_iou\\s*=\\s*dict\\([^)]*?type=')([^']*)'",
                    "$1" + lossIouType.trim() + "'"
            );
        }

        // 权重
        Double wCls  = params.getDouble("detr_loss_cls_weight");
        Double wBBox = params.getDouble("detr_loss_bbox_weight");
        Double wIou  = params.getDouble("detr_loss_iou_weight");
        if (wCls != null) {
            result = MmdetConfigUtil.replaceFirst(
                    result,
                    "(?s)(loss_cls\\s*=\\s*dict\\([^)]*?loss_weight\\s*=\\s*)[0-9.]+",
                    "$1" + wCls
            );
        }
        if (wBBox != null) {
            result = MmdetConfigUtil.replaceFirst(
                    result,
                    "(?s)(loss_bbox\\s*=\\s*dict\\([^)]*?loss_weight\\s*=\\s*)[0-9.]+",
                    "$1" + wBBox
            );
        }
        if (wIou != null) {
            result = MmdetConfigUtil.replaceFirst(
                    result,
                    "(?s)(loss_iou\\s*=\\s*dict\\([^)]*?loss_weight\\s*=\\s*)[0-9.]+",
                    "$1" + wIou
            );
        }

        // （可选）8. HungarianAssigner 的 match_cost 权重也可以按需要同步调整
        // 这里先不动，保持模板默认逻辑，后面你如果想“跟着损失权重动”，再补一层正则即可。

        return result;
    }



    private String injectNumClassesOnly(String txt, String numClassesStr) {
        return MmdetConfigUtil.replaceAll(txt, "(?s)(num_classes\\s*=\\s*)\\d+", "$1" + numClassesStr);
    }

    /**
     * 把权重地址注入到 init_cfg / checkpoint / pretrained / load_from（不改变 num_classes）
     */
    private String injectCheckpointOnly(String txt, String ckptPy) {
        String before = txt;
        String replaced = MmdetConfigUtil.replaceFirst(
                txt,
                "(?s)(init_cfg\\s*=\\s*dict\\([^\\)]*?checkpoint\\s*=\\s*)(?:\"[^\"]*\"|'[^']*'|[^,\\)]+)",
                "$1" + Matcher.quoteReplacement(ckptPy)
        );
        if (replaced.equals(before)) {
            replaced = MmdetConfigUtil.replaceFirst(
                    txt,
                    "(?s)(checkpoint\\s*=\\s*)(?:\"[^\"]*\"|'[^']*'|[^,\\)]+)",
                    "$1" + Matcher.quoteReplacement(ckptPy)
            );
        }
        if (replaced.equals(before)) {
            replaced = MmdetConfigUtil.replaceFirst(
                    txt,
                    "(?s)(^|\\n)\\s*(pretrained\\s*=\\s*)(?:\"[^\"]*\"|'[^']*'|[^\\n#]+)",
                    "$1$2" + Matcher.quoteReplacement(ckptPy)
            );
        }
        if (replaced.equals(before)) {
            replaced = MmdetConfigUtil.replaceFirst(
                    txt,
                    "(?s)(load_from\\s*=\\s*)(?:\"[^\"]*\"|'[^']*'|[^,\\)]+)",
                    "$1" + Matcher.quoteReplacement(ckptPy)
            );
        }
        return replaced;
    }

    /**
     * 把权重注入到 init_cfg / checkpoint / pretrained / load_from，并替换 num_classes
     */
    private String injectCheckpointAndNumClasses(String txt, String ckptPy, String numClassesStr) {
        return injectNumClassesOnly(injectCheckpointOnly(txt, ckptPy), numClassesStr);
    }

    /**
     * 把 data/train/runtime 三个文件写到 runDir，并返回它们的路径，方便前端显示
     * 使用 DatasetCfg（带 dataroot / annXXX / prefixXXX / classNames）
     * 会在 data_pipeline.py 末尾自动追加 metainfo 注入代码。
     */
    private Map<String, Object> writeCommonFiles(
            Path runDir,
            DatasetCfg ds,
            String scale,
            Integer batchSize,
            Integer maxEpochs,
            Integer valInterval,
            String milestones,
            String optType,
            Double lr,
            Integer weightInterval
    ) throws IOException {

        String dataroot    = ds.dataroot;
        String annTrain    = ds.annTrain;
        String annVal      = ds.annVal;
        String annTest     = ds.annTest;
        String prefixTrain = ds.prefixTrain;
        String prefixVal   = ds.prefixVal;
        String prefixTest  = ds.prefixTest;

        String dataTxt = MmdetConfigUtil.readString(Paths.get(localDataPath));
        String trainTxt = MmdetConfigUtil.readString(Paths.get(localTrainPath));
        String runtimeTxt = MmdetConfigUtil.readString(Paths.get(localRuntimePath));

        // data.py：data_root、Resize.scale、batch_size、ann_file / data_prefix
        if (StringUtils.hasText(dataroot)) {
            dataTxt = MmdetConfigUtil.replaceQuotedAssignment(dataTxt, "data_root", dataroot);
        }
        dataTxt = MmdetConfigUtil.replaceAll(
                dataTxt,
                "(?s)(dict\\(type='Resize',\\s*scale=)\\([^\\)]*\\)(,\\s*keep_ratio=True\\))",
                "$1" + scale + "$2"
        );
        dataTxt = MmdetConfigUtil.replaceAll(dataTxt, "(?s)(batch_size\\s*=\\s*)\\d+", "$1" + batchSize);

        dataTxt = MmdetConfigUtil.replaceAssignmentByOrder(
                dataTxt, "ann_file", "'" + annTrain.replace("'", "\\'") + "'", 0);
        dataTxt = MmdetConfigUtil.replaceAssignmentByOrder(
                dataTxt, "ann_file", "'" + annVal.replace("'", "\\'") + "'", 1);
        dataTxt = MmdetConfigUtil.replaceAssignmentByOrder(
                dataTxt, "ann_file", "'" + annTest.replace("'", "\\'") + "'", 2);
        dataTxt = MmdetConfigUtil.replaceAssignmentByOrder(
                dataTxt, "data_prefix", prefixTrain, 0);
        dataTxt = MmdetConfigUtil.replaceAssignmentByOrder(
                dataTxt, "data_prefix", prefixVal, 1);
        dataTxt = MmdetConfigUtil.replaceAssignmentByOrder(
                dataTxt, "data_prefix", prefixTest, 2);

        if (StringUtils.hasText(dataroot)) {
            String dr = dataroot.endsWith("/") ? dataroot : (dataroot + "/");
            if (StringUtils.hasText(annVal)) {
                String valAnnAbs = MmdetConfigUtil.isAbsoluteLike(annVal) ? annVal : (dr + annVal);
                dataTxt = MmdetConfigUtil.replaceFirst(
                        dataTxt,
                        "(?s)(val_evaluator\\s*=\\s*dict\\([^)]*?ann_file\\s*=\\s*)'[^']*'",
                        "$1'" + Matcher.quoteReplacement(valAnnAbs.replace("'", "\\'")) + "'"
                );
            }
            if (StringUtils.hasText(annTest)) {
                String testAnnAbs = MmdetConfigUtil.isAbsoluteLike(annTest) ? annTest : (dr + annTest);
                dataTxt = MmdetConfigUtil.replaceFirst(
                        dataTxt,
                        "(?s)(test_evaluator\\s*=\\s*dict\\([^)]*?ann_file\\s*=\\s*)'[^']*'",
                        "$1'" + Matcher.quoteReplacement(testAnnAbs.replace("'", "\\'")) + "'"
                );
            }
        }

        // === 自动注入 metainfo / dataset.metainfo ===
        if (ds.classNames != null && !ds.classNames.isEmpty()) {
            StringBuilder sb = new StringBuilder("(");
            for (int i = 0; i < ds.classNames.size(); i++) {
                if (i > 0) sb.append(", ");
                String name = ds.classNames.get(i)
                        .replace("\\", "\\\\")
                        .replace("'", "\\'");
                sb.append("'").append(name).append("'");
            }
            if (ds.classNames.size() == 1) {
                sb.append(",");
            }
            sb.append(")");
            String classesTuple = sb.toString();

            StringBuilder inject = new StringBuilder();
            inject.append("\n# ----- AUTO GENERATED: metainfo for instance dataset -----\n");
            inject.append("_auto_classes = ").append(classesTuple).append("\n");
            inject.append("try:\n");
            inject.append("    metainfo\n");
            inject.append("except NameError:\n");
            inject.append("    metainfo = dict(classes=_auto_classes)\n");
            inject.append("else:\n");
            inject.append("    metainfo['classes'] = _auto_classes\n");
            inject.append("\n");
            inject.append("for _loader_name in ['train_dataloader', 'val_dataloader', 'test_dataloader']:\n");
            inject.append("    if _loader_name in globals():\n");
            inject.append("        _loader = globals()[_loader_name]\n");
            inject.append("        if isinstance(_loader, dict) and 'dataset' in _loader:\n");
            inject.append("            if isinstance(_loader['dataset'], dict):\n");
            inject.append("                _loader['dataset'].setdefault('metainfo', metainfo)\n");
            inject.append("# ----- END AUTO GENERATED -----\n");
            dataTxt += inject.toString();
        }

        Path dataSaved = MmdetConfigUtil.writeTextFile(runDir, "data_pipeline.py", dataTxt);

        // train_opt.py
        trainTxt = MmdetConfigUtil.replaceFirst(trainTxt, "(?s)(max_epochs\\s*=\\s*)\\d+", "$1" + maxEpochs);
        trainTxt = MmdetConfigUtil.replaceFirst(trainTxt, "(?s)(val_interval\\s*=\\s*)\\d+", "$1" + valInterval);
        trainTxt = MmdetConfigUtil.replaceFirst(
                trainTxt,
                "(?s)(type='MultiStepLR'.*?end\\s*=\\s*)\\d+",
                "$1" + maxEpochs
        );

// milestones
        String milestonesArr = "[]";
        if (StringUtils.hasText(milestones)) {
            String ms = milestones.trim();
            if (ms.startsWith("[") && ms.endsWith("]")) {
                milestonesArr = ms;
            } else {
                List<Integer> list = new ArrayList<>();
                for (String s : ms.split(",")) {
                    try {
                        list.add(Integer.parseInt(s.trim()));
                    } catch (Exception ignore) {
                    }
                }
                milestonesArr = "[" + list.stream().map(String::valueOf).collect(Collectors.joining(", ")) + "]";
            }
        }
        trainTxt = MmdetConfigUtil.replaceFirst(
                trainTxt,
                "(?s)(milestones\\s*=\\s*)\\[[^\\]]*\\]",
                "$1" + milestonesArr
        );

// ========== 新增：根据 optType 生成 {{OPTIMIZER}} ==========

// optType 从上层传进来的，比如 "SGD" / "AdamW"
        String optLower = (optType == null) ? "" : optType.trim().toLowerCase();

// 默认值，避免 NPE
        Double lrSafe = (lr != null) ? lr : 0.01;

// 这里可以后面再做成可配置，现在先写成常用默认
        String optimizerBlock;
        switch (optLower) {
            case "adamw":
                // AdamW 常见配置：lr 使用用户传入，weight_decay 和 betas 给一个合理默认
                optimizerBlock =
                        "dict(type='AdamW', lr=" + lrSafe +
                                ", betas=(0.9, 0.999), weight_decay=0.05)";
                break;
            case "sgd":
            default:
                // 默认走 SGD
                optimizerBlock =
                        "dict(type='SGD', lr=" + lrSafe +
                                ", momentum=0.9, weight_decay=0.0001)";
                break;
        }

// 把模板里的 {{OPTIMIZER}} 替换掉
        trainTxt = MmdetConfigUtil.replaceFirst(
                trainTxt,
                "\\{\\{OPTIMIZER\\}\\}",
                Matcher.quoteReplacement(optimizerBlock)
        );

        Path trainSaved = MmdetConfigUtil.writeTextFile(runDir, "train_opt.py", trainTxt);


        // runtime.py
        String runtimeOrigin = runtimeTxt;
        // 1) 决定最终要用的 interval
        if (weightInterval == null || weightInterval <= 0) {
            // 如果前端没传，就从模板里的 default_runtime.py 里读 checkpoint 的 interval，当默认值
            weightInterval = MmdetConfigUtil.readRuntimeCheckpointInterval(runtimeOrigin).orElse(1);
        }
        // 2) 只修改 checkpoint 里的 interval，其他配置保持不变
        runtimeTxt = MmdetConfigUtil.forceSingleInterval(runtimeOrigin, weightInterval);
        // 3) 写入当前任务的 default_runtime.py
        Path runtimeSaved = MmdetConfigUtil.writeTextFile(runDir, "default_runtime.py", runtimeTxt);


        // 组合 combined_base.py
        String combinedContent = "_base_ = [\n"
                + "    " + MmdetConfigUtil.toPyRawLiteral(runDir.resolve("base_model.py")) + ",\n"
                + "    " + MmdetConfigUtil.toPyRawLiteral(dataSaved) + ",\n"
                + "    " + MmdetConfigUtil.toPyRawLiteral(trainSaved) + ",\n"
                + "    " + MmdetConfigUtil.toPyRawLiteral(runtimeSaved) + ",\n"
                + "]\n";
        Path combinedSaved = MmdetConfigUtil.writeTextFile(runDir, "combined_base.py", combinedContent);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("base", runDir.resolve("base_model.py").toString());
        res.put("data", dataSaved.toString());
        res.put("train", trainSaved.toString());
        res.put("runtime", runtimeSaved.toString());
        res.put("combined", combinedSaved.toString());
        return res;
    }

    private void saveNetworkName(Integer taskId, String networkName) {
        try {
            if (taskId == null || !StringUtils.hasText(networkName)) return;
            TrainExt ext = trainExtService.getById(taskId);
            cn.hutool.json.JSONObject jo = new cn.hutool.json.JSONObject();
            if (ext != null && cn.hutool.core.util.StrUtil.isNotBlank(ext.getParams())
                    && cn.hutool.json.JSONUtil.isTypeJSONObject(ext.getParams())) {
                jo = cn.hutool.json.JSONUtil.parseObj(ext.getParams());
            }
            jo.set("network_name", networkName);

            TrainExt upd = new TrainExt();
            upd.setId(taskId);
            upd.setParams(jo.toString());
            upd.setUpdate_time(LocalDateTime.now());

            if (ext == null) {
                trainExtService.save(upd);
            } else {
                trainExtService.updateById(upd);
            }
            log.info("[pack] network_name='{}' saved for taskId={}", networkName, taskId);
        } catch (Exception e) {
            log.warn("[pack] save network_name failed, taskId={}, err={}", taskId, e.toString());
        }
    }

    @Operation(summary = "分页获取列表", description = "分页获取列表")
    @PostMapping("list")
    public AjaxResult queryList(TrainTaskQuery query) {
        /** 查询条件 */
        LambdaQueryWrapper<TrainTask> wrapper = new LambdaQueryWrapper<>();
        String nameLike = query.getName();
        if (query.getStatus() != null && query.getStatus() != -1) {
            wrapper.eq(TrainTask::getStatus, query.getStatus());
        }
        if (StrUtil.isNotBlank(nameLike)) {
            wrapper.like(TrainTask::getName, nameLike);
        }
        if (StrUtil.isNotBlank(query.getType())) {
            wrapper.eq(TrainTask::getType, query.getType());
        }
        if (query.getStart_time() != null && query.getEnd_time() != null) {
            wrapper.between(TrainTask::getCreated_date, query.getStart_time(), query.getEnd_time());
        }
        if (StrUtil.isNotBlank(query.getUsername())) {
            wrapper.eq(TrainTask::getUsername, query.getUsername());
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
        Page<TrainTask> page = new Page<>(current, size);

        /** 排序信息 */
        List<OrderItem> orders = query.getOrders();
        if (query.getStatus() != null && query.getStatus().equals(CodeMap.TRAIN_TASK_STATUS_QUEUE)) {
            // 排队的,按照优先级从前到后排
            page.addOrder(OrderItem.asc("enqueue"));
        }

        if (orders != null && !orders.isEmpty()) {
            page.addOrder(orders);
        } else {
            page.addOrder(OrderItem.descs("id"));
        }
        return AjaxResult.success(taskService.page(page, wrapper));
    }

    @Operation(summary = "创建训练任务", description = "创建训练任务")
    @PostMapping("add")
    public AjaxResult add(@Parameter(description = "基本参数,json格式") @RequestParam String params,
                          @Parameter(description = "上传的权重文件") @RequestParam(required = false) MultipartFile weight_file,
                          @Parameter(description = "克隆的任务Id,需要拷贝权重文件时使用") @RequestParam(required = false) Integer clone_from,
                          @Parameter(description = "拓展参数,json格式") @RequestParam(required = false) String ext_params,
                          @Parameter(description = "拓展文件") @RequestParam(required = false) MultipartFile ext_file,
                          @Parameter(description = "mmdet配置文件") @RequestParam(required = false) String mmdet_cfg) {
        TrainForm form = JSONUtil.toBean(params, TrainForm.class);
        String name = form.getName();
        if (StrUtil.isBlank(name)) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        if (form.getArgs().getWeights() == CodeMap.USE_SELF_ID) {
            if (clone_from == null &&
                    (weight_file == null || weight_file.isEmpty())) {
                return AjaxResult.error(ErrorCode.FILE_EMPTY);
            }
        } else {
            weight_file = null;
        }
        // mmdet内容支持
        String cmd = form.getCmd();
        boolean is_mmdet = StrUtil.equals(cmd, "mmdet");
        if (is_mmdet) {
            if (StrUtil.isBlank(mmdet_cfg)) {
                return AjaxResult.error("mmdet 算法配置内容为空");
            }
        } else {
            mmdet_cfg = null;
        }

        LambdaQueryWrapper<TrainTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrainTask::getName, name);
        if (taskService.getOne(wrapper, false) != null) {
            return AjaxResult.error("名称已存在");
        }
        try {
            if (taskService.saveLink(form, SessionUtil.getCurUser(), weight_file, clone_from, ext_params, ext_file,
                    mmdet_cfg)) {
                // 开启一个后台转换任务
                if (is_mmdet) {
                    transformService.handleDataMMdet(form, null);
                } else {
                    transformService.handleData(form, null);
                }

                return AjaxResult.success();
            }
        } catch (IllegalStateException | IOException e) {
            return AjaxResult.error();
        }
        return AjaxResult.error();
    }

    @Operation(summary = "修改训练任务配置", description = "修改训练任务配置")
    @PostMapping("update")
    public AjaxResult update(@Parameter(description = "基本参数,json格式") @RequestParam String params,
                             @Parameter(description = "上传的权重文件") @RequestParam(required = false) MultipartFile weight_file,
                             @Parameter(description = "拓展参数,json格式") @RequestParam(required = false) String ext_params,
                             @Parameter(description = "拓展文件") @RequestParam(required = false) MultipartFile ext_file,
                             @Parameter(description = "是否更新拓展文件,ture时:根据ext_file来更新,即便ext_file=null也有效,代表置空;fase时,不更新ext_file属性") @RequestParam(defaultValue = "false") Boolean ext_file_update,
                             @Parameter(description = "mmdet配置文件") @RequestParam(required = false) String mmdet_cfg) {
        TrainForm form = JSONUtil.toBean(params, TrainForm.class);
        Integer id = form.getId();
        if (id == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        if (form.getCls_num() == null || form.getPrj_num() == null
                || form.getTask_num() == null || form.getImg_num() == null
                || StrUtil.isBlank(form.getName())
                || form.getData() == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        // 补充验证
        TrainTask task = taskService.getById(id);
        if (task == null) {
            return AjaxResult.error("任务不存在");
        }
        // 鉴权
        if (!SessionUtil.hasAdminOrSelf(task.getUsername())) {
            return AjaxResult.error(ErrorCode.PERMISSION_DENIED);
        }
        // mmdet内容支持
        String cmd = form.getCmd();
        boolean is_mmdet = StrUtil.equals(cmd, "mmdet");
        if (is_mmdet) {
            if (StrUtil.isBlank(mmdet_cfg)) {
                return AjaxResult.error("mmdet 算法配置内容为空");
            }
        } else {
            mmdet_cfg = null;
        }

        // name不能重复
        String name = form.getName();
        if (!StrUtil.equals(name, task.getName())) {// 不相等,查重复
            LambdaQueryWrapper<TrainTask> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(TrainTask::getName, name);
            if (taskService.getOne(wrapper, false) != null) {
                return AjaxResult.error("名称已存在");
            }
        }

        TrainArgs args = tArgsService.getById(id);
        TrainData data = tDataService.getById(id);
        if (args == null) {
            return AjaxResult.error("训练任务参数不存在");
        }
        if (data == null) {
            return AjaxResult.error("训练集不存在");
        }
        try {
            if (taskService.updateLink(form, weight_file, ext_params, ext_file, ext_file_update, mmdet_cfg)) {
                TrainForm oldForm = new TrainForm();
                oldForm.setId(task.getId());
                oldForm.setName(task.getName());
                oldForm.setPrj_num(task.getPrj_num());
                oldForm.setTask_num(task.getTask_num());
                oldForm.setImg_num(task.getImg_num());
                oldForm.setCls_num(task.getCls_num());
                oldForm.setArgs(args);
                oldForm.setData(data);
                if (is_mmdet) {
                    transformService.handleDataMMdet(form, oldForm);
                } else {
                    transformService.handleData(form, oldForm);
                }
                return AjaxResult.success();
            }
        } catch (IllegalStateException | IOException e) {
            return AjaxResult.error(e.getMessage());
        }
        return AjaxResult.error();
    }

    @Operation(summary = "删除训练任务", description = "删除训练任务")
    @PostMapping("del")
    public AjaxResult delete(@Parameter(description = "任务id") @RequestParam Integer id) {
        TrainTask task = taskService.getById(id);
        if (task == null) {
            return AjaxResult.error("任务不存在");
        }
        // 鉴权
        if (!SessionUtil.hasAdminOrSelf(task.getUsername())) {
            return AjaxResult.error(ErrorCode.PERMISSION_DENIED);
        }
        return taskService.delLink(id) ? AjaxResult.success() : AjaxResult.error();
    }

    @Operation(summary = "获取基本训练参数信息", description = "获取基本训练参数信息")
    @PostMapping("args/query")
    public AjaxResult queryArgs(@Parameter(description = "任务id") Integer id) {
        return AjaxResult.success(tArgsService.getById(id));
    }

    @Operation(summary = "获取数据集参数信息", description = "获取数据集参数信息")
    @PostMapping("data/query")
    public AjaxResult queryData(@Parameter(description = "任务id") Integer id) {
        return AjaxResult.success(tDataService.getById(id));
    }

    @Operation(summary = "获取拓展训练参数信息", description = "获取拓展训练参数信息")
    @PostMapping("ext/query")
    public AjaxResult queryExt(@Parameter(description = "任务id") Integer id) {
        return AjaxResult.success(trainExtService.getById(id));
    }

    @Operation(summary = "发布任务", description = "发布任务")
    @PostMapping("enqueue")
    public AjaxResult enqueueTask(@Parameter(description = "任务id") Integer id) {
        TrainTask task = taskService.getById(id);
        if (task == null) {
            return AjaxResult.error("任务不存在");
        }
        // 鉴权
        if (!SessionUtil.hasAdminOrSelf(task.getUsername())) {
            return AjaxResult.error(ErrorCode.PERMISSION_DENIED);
        }
        Integer status = task.getStatus();
        if (status == CodeMap.TRAIN_TASK_STATUS_DEFAULT) {
            return AjaxResult.error("配置文件正在准备中,请稍后");
        }
        if (status == CodeMap.TRAIN_TASK_STATUS_QUEUE) {
            return AjaxResult.error("任务正在排队中,请耐心等候");
        }
        if (status == CodeMap.TRAIN_TASK_STATUS_RUN) {
            return AjaxResult.error("任务正在运行中");
        }

        long enqueue = DateTime.now().getTime();
        TrainTask record = new TrainTask();
        record.setId(id);
        record.setStatus(CodeMap.TRAIN_TASK_STATUS_QUEUE);
        record.setEnqueue(enqueue);
        record.setUpdated_date(LocalDateTime.now());
        if (taskService.updateById(record)) {
            if (TaskQueue.addTask(new MyTask(id, task.getName(), enqueue))) {
                return AjaxResult.success();
            } else {
                TrainTask task2 = new TrainTask();
                task2.setId(id);
                task2.setStatus(status);
                taskService.updateById(task2);
                return AjaxResult.error();
            }
        }
        return AjaxResult.error();
    }

    @Operation(summary = "停止任务", description = "停止任务")
    @PostMapping("stop")
    public AjaxResult stopTask(@Parameter(description = "任务id") Integer id) {
        TrainTask task = taskService.getById(id);
        if (task == null) {
            return AjaxResult.error("任务不存在");
        }
        /** 鉴权 */
        if (!SessionUtil.hasAdminOrSelf(task.getUsername())) {
            return AjaxResult.error(ErrorCode.PERMISSION_DENIED);
        }

        if (task.getStatus() != CodeMap.TRAIN_TASK_STATUS_RUN) {
            return AjaxResult.error("只能停止运行状态中的任务");
        }
        taskService.stopTrain();
        TrainTask record = new TrainTask();
        record.setId(id);
        record.setFinish_date(LocalDateTime.now());
        record.setRun_state(CodeMap.TRAIN_FINISH_ERROR);
        record.setStatus(CodeMap.TRAIN_TASK_STATUS_FINISH);
        return taskService.updateById(record) ? AjaxResult.success() : AjaxResult.error();
    }

    @Operation(summary = "置顶任务", description = "置顶任务")
    @PostMapping("top")
    public AjaxResult topTask(@Parameter(description = "任务id") Integer id) {
        TrainTask task = taskService.getById(id);
        if (task == null) {
            return AjaxResult.error("任务不存在");
        }
        /** 鉴权 */
        if (!SessionUtil.hasAdminOrSelf(task.getUsername())) {
            return AjaxResult.error(ErrorCode.PERMISSION_DENIED);
        }
        Integer status = task.getStatus();
        if (status != CodeMap.TRAIN_TASK_STATUS_QUEUE) {
            return AjaxResult.error("任务不在队列中");
        }
        Long enqueu = TaskQueue.topTask(id);
        if (enqueu == null) {
            return AjaxResult.error();
        }
        TrainTask record = new TrainTask();
        record.setId(id);
        record.setEnqueue(enqueu);
        record.setUpdated_date(LocalDateTime.now());
        taskService.updateById(record);
        return AjaxResult.success();
    }

    @Operation(summary = "取消任务", description = "取消任务")
    @PostMapping("cancel")
    public AjaxResult cancelTask(@Parameter(description = "任务id") Integer id) {
        TrainTask task = taskService.getById(id);
        if (task == null) {
            return AjaxResult.error("任务不存在");
        }
        /** 鉴权 */
        if (!SessionUtil.hasAdminOrSelf(task.getUsername())) {
            return AjaxResult.error(ErrorCode.PERMISSION_DENIED);
        }
        Integer status = task.getStatus();
        if (status != CodeMap.TRAIN_TASK_STATUS_QUEUE) {
            return AjaxResult.error("任务不在队列中");
        }
        if (!TaskQueue.cancelTask(id)) {
            return AjaxResult.error();
        }
        TrainTask record = new TrainTask();
        record.setId(id);
        record.setStatus(CodeMap.TRAIN_TASK_STATUS_READY);
        record.setUpdated_date(LocalDateTime.now());
        taskService.updateById(record);
        return AjaxResult.success();
    }

    @Operation(summary = "获取存在训练任务的全部用户列表", description = "获取存在训练任务的全部用户列表")
    @PostMapping("user")
    public AjaxResult getAllUser() {
        List<String> list = taskService.queryDistinctUsernames();
        return AjaxResult.success(list);
    }

    @Operation(summary = "清除中间迭代权重文件", description = "清除中间迭代权重文件")
    @PostMapping("clear/epoches")
    public AjaxResult clearEpoches(@Parameter(description = "训练任务id") @RequestParam Integer id,
                                   @Parameter(description = "指定删除的exp名称,如exp001;如果为空,代表清理全部") @RequestParam String expName) {
        TrainTask task = taskService.getById(id);
        if (task == null) {
            return AjaxResult.error("任务不存在");
        }
        if (StrUtil.isNotBlank(expName) && !ReUtil.isMatch(CodeMap.RE_EXP_NAME, expName)) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        /** 鉴权 */
        if (!SessionUtil.hasAdminOrSelf(task.getUsername())) {
            return AjaxResult.error(ErrorCode.PERMISSION_DENIED);
        }
        String type = task.getType();
        TrainScript alg = tScriptService.getById(type);
        if (alg == null) {
            return AjaxResult.error("算法类别不存在");
        }
        taskService.delEpoches(id, expName, alg.getCmd());
        return AjaxResult.success();
    }

    @PostMapping("/val/add")
    public AjaxResult createValTask(@RequestBody String body) {
        ValParams params = JSONUtil.toBean(body, ValParams.class);
        if (params.getId() == null || params.getLabels().isEmpty() || params.getData().isEmpty()
                || params.getRun_name() == null || params.getWeights() == null || params.getBatch_size() == null
                || params.getImg_size() == null || params.getConf_thres() == null
                || params.getDevice() == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        Path ptPath = Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TRAIN_TASK, params.getId().toString(),
                CodeMap.DIR_TRAIN_RUN,
                params.getRun_name(), "weights", params.getWeights());
        if (!ptPath.toFile().exists()) {
            return AjaxResult.error("选择的模型不存在");
        }
        TrainTask task = taskService.getById(params.getId());
        if (task == null) {
            return AjaxResult.error("训练任务不存在");
        }
        if (task.getVal_state() == CodeMap.STATE_RUNNING) {
            return AjaxResult.error("验证任务正在执行中");
        }
        String val_name = MyUtils.parseTime2(DateTime.now());
        params.setVal_name(val_name);
        // 开始转换
        transformService.transValParams(params);
        // 异步执行
        valTaskService.startValTask(params);
        return AjaxResult.success();
    }

    @PostMapping("/predict/add")
    public AjaxResult createPredictTask(String info, MultipartFile file) {
        ValParams params = JSONUtil.toBean(info, ValParams.class);
        if (params.getId() == null || params.getLabels().isEmpty() || params.getRun_name() == null
                || params.getWeights() == null || params.getImg_size() == null
                || params.getConf_thres() == null || params.getDevice() == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        boolean has_upload = file != null && !file.isEmpty();
        List<TrainItem> list = params.getData();
        boolean has_data = list != null && !list.isEmpty();
        if (!has_upload && !has_data) {
            return AjaxResult.error("需要预测的源文件为空");
        }
        if (params.getSave_txt() == null) {
            params.setSave_txt(false);
        }
        Path ptPath = Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TRAIN_TASK, params.getId().toString(),
                CodeMap.DIR_TRAIN_RUN,
                params.getRun_name(), "weights", params.getWeights());
        if (!ptPath.toFile().exists()) {
            return AjaxResult.error("选择的模型不存在");
        }
        TrainTask task = taskService.getById(params.getId());
        if (task == null) {
            return AjaxResult.error("训练任务不存在");
        }
        if (task.getPredict_state() == CodeMap.STATE_RUNNING) {
            return AjaxResult.error("预测任务正在执行中");
        }
        String predict_name = MyUtils.parseTime2(DateTime.now());
        params.setPredict_name(predict_name);
        // 开始转换预测任务
        transformService.transPredictParams(params, file);
        // 异步执行
        predictTaskService.startPredictTask(params);
        return AjaxResult.success();
    }

    // =================== DatasetCfg + DOTA -> COCO ===================

    // 封装从 InstanceDatasetinfo 推导出来的一套 mmdet 路径配置
    private static class DatasetCfg {
        String dataroot;
        String annTrain;
        String annVal;
        String annTest;
        String prefixTrain;
        String prefixVal;
        String prefixTest;
        String numClassesStr;
        List<String> classNames; // 新增：类别名列表，用于 metainfo
    }

    /**
     * 根据前端传入的 dataset
     * 1）通过 InstanceDatasetinfoService 查库
     * 2）拆出 dataroot / prefixXXX / annXXX
     * 3）从 DOTA txt 生成 COCO train.json / test.json
     *
     * 约定：dataset = instance_datasetinfo.name
     */
    private DatasetCfg buildDatasetCfg(String datasetName) throws IOException {
        if (StrUtil.isBlank(datasetName)) {
            throw new IllegalArgumentException("缺少参数：dataset");
        }

        // 这里用 getAllInstanceDatasets 再过滤（兼容你现有的接口定义）
        List<InstanceDatasetinfo> all = instanceDatasetinfoService.getAllInstanceDatasets();
        InstanceDatasetinfo info = all.stream()
                .filter(it -> datasetName.equals(it.getName()))
                .findFirst()
                .orElse(null);
        if (info == null) {
            throw new IllegalArgumentException("实例数据集不存在：" + datasetName);
        }

        InstanceDatasetPathUtil.ResolvedInstanceDiskPaths disk =
                InstanceDatasetPathUtil.resolveTargetOrThrow(info, instanceDataRoot);

        Path trainImgDir  = Paths.get(disk.trainImgPath()).normalize();
        Path testImgDir   = Paths.get(disk.testImgPath()).normalize();
        Path trainAnnoDir = Paths.get(disk.trainAnnoPath()).normalize();
        Path testAnnoDir  = Paths.get(disk.testAnnoPath()).normalize();

        // 2. 从 train_image_path 推断 dataroot：/.../instance_dataset/xxx/images/train -> .../instance_dataset/xxx
        Path imagesDir = trainImgDir.getParent(); // .../images
        if (imagesDir == null) {
            throw new IllegalStateException("无法从 train_image_path 推断 images 目录: " + disk.trainImgPath());
        }
        Path datarootPath = imagesDir.getParent(); // dataroot
        if (datarootPath == null) {
            throw new IllegalStateException("无法从 train_image_path 推断 dataroot: " + disk.trainImgPath());
        }

        // 3. 相对 img 子路径：images/train / images/test
        String relTrainImg = datarootPath.relativize(trainImgDir).toString().replace("\\", "/");
        if (!relTrainImg.endsWith("/")) relTrainImg += "/";
        String relTestImg  = datarootPath.relativize(testImgDir).toString().replace("\\", "/");
        if (!relTestImg.endsWith("/")) relTestImg += "/";

        // 4. COCO 标注文件：annotations/train.json / annotations/test.json
        Path annDir = datarootPath.resolve("annotations");
        Files.createDirectories(annDir);
        Path trainJson = annDir.resolve("train.json");
        Path testJson  = annDir.resolve("test.json");

        // 5. 类别解析：class_list 是 JSON 字符串，如 {"彩色年":2167, "俄方":717}
        List<String> classNames = new ArrayList<>();
        if (StrUtil.isNotBlank(info.getClassList()) && JSONUtil.isTypeJSONObject(info.getClassList())) {
            cn.hutool.json.JSONObject clsObj = JSONUtil.parseObj(info.getClassList());
            for (String key : clsObj.keySet()) {
                classNames.add(key);   // key 就是类别名
            }
        }
        if (classNames.isEmpty()) {
            throw new IllegalStateException("InstanceDatasetinfo.class_list 为空，无法构造 COCO 类别");
        }

        // 6. DOTA -> COCO
        generateCocoFromDotaDir(trainImgDir, trainAnnoDir, trainJson, classNames);
        generateCocoFromDotaDir(testImgDir,  testAnnoDir,  testJson,  classNames);

        // 7. 回填 DatasetCfg
        DatasetCfg cfg = new DatasetCfg();
        cfg.dataroot = datarootPath.toString().replace("\\", "/");
        cfg.annTrain = "annotations/train.json";
        cfg.annTest  = "annotations/test.json";
        cfg.annVal   = cfg.annTest; // val 用 test 的标注

        cfg.prefixTrain = "dict(img='" + relTrainImg + "')";
        cfg.prefixTest  = "dict(img='" + relTestImg + "')";
        cfg.prefixVal   = cfg.prefixTest;

        cfg.classNames = classNames;

        if (info.getClassNum() != null) {
            cfg.numClassesStr = info.getClassNum().toString();
        } else {
            cfg.numClassesStr = String.valueOf(classNames.size());
        }
        return cfg;
    }

    /**
     * 从 DOTA 标注目录生成 COCO json
     * @param imgDir      对应的图片目录，如 .../images/train
     * @param annoDir     DOTA txt 目录，如 .../annotations/train
     * @param outputJson  输出的 COCO json 文件路径
     * @param classNames  类别名列表，顺序决定 category_id（从 1 开始）
     */
    private void generateCocoFromDotaDir(
            Path imgDir,
            Path annoDir,
            Path outputJson,
            List<String> classNames
    ) throws IOException {

        // 1. category 映射：name -> id（1 ~ N）
        Map<String, Integer> cateIdMap = new LinkedHashMap<>();
        for (int i = 0; i < classNames.size(); i++) {
            cateIdMap.put(classNames.get(i), i + 1);
        }

        List<Map<String, Object>> images = new ArrayList<>();
        List<Map<String, Object>> annotations = new ArrayList<>();

        long imageIdSeq = 1;
        long annIdSeq   = 1;

        String[] exts = new String[]{".jpg", ".jpeg", ".png", ".bmp", ".tif", ".tiff"};

        // 2. 遍历标注目录下所有 txt
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(annoDir, "*.txt")) {
            for (Path txtPath : stream) {
                String fileName = txtPath.getFileName().toString();
                int dot = fileName.lastIndexOf('.');
                String stem = (dot > 0) ? fileName.substring(0, dot) : fileName;   // 如 "9_L0"

                // 找对应图片
                Path imgPath = null;
                for (String ext : exts) {
                    Path candidate = imgDir.resolve(stem + ext);
                    if (Files.exists(candidate)) {
                        imgPath = candidate;
                        break;
                    }
                }
                if (imgPath == null) {
                    // 找不到图片就跳过
                    continue;
                }

                // 读图片尺寸
                BufferedImage bi = ImageIO.read(imgPath.toFile());
                if (bi == null) {
                    continue;
                }
                int width  = bi.getWidth();
                int height = bi.getHeight();

                long imgId = imageIdSeq++;

                // image 节点
                Map<String, Object> imgObj = new LinkedHashMap<>();
                imgObj.put("id", imgId);
                imgObj.put("file_name", imgPath.getFileName().toString());
                imgObj.put("width", width);
                imgObj.put("height", height);
                images.add(imgObj);

                // 3. 读取 txt 中每一行标注
                List<String> lines = Files.readAllLines(txtPath, StandardCharsets.UTF_8);
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    String[] parts = line.split("\\s+");
                    if (parts.length < 10) {
                        // 至少 8 个点 + 类别 + difficult
                        continue;
                    }

                    double[] xs = new double[4];
                    double[] ys = new double[4];
                    try {
                        xs[0] = Double.parseDouble(parts[0]);
                        ys[0] = Double.parseDouble(parts[1]);
                        xs[1] = Double.parseDouble(parts[2]);
                        ys[1] = Double.parseDouble(parts[3]);
                        xs[2] = Double.parseDouble(parts[4]);
                        ys[2] = Double.parseDouble(parts[5]);
                        xs[3] = Double.parseDouble(parts[6]);
                        ys[3] = Double.parseDouble(parts[7]);
                    } catch (NumberFormatException e) {
                        continue;
                    }

                    String clsName = parts[parts.length - 2];
                    Integer cateId = cateIdMap.get(clsName);
                    if (cateId == null) {
                        // 不在 classNames 里的类别，直接跳过
                        continue;
                    }

                    double xmin = Math.min(Math.min(xs[0], xs[1]), Math.min(xs[2], xs[3]));
                    double xmax = Math.max(Math.max(xs[0], xs[1]), Math.max(xs[2], xs[3]));
                    double ymin = Math.min(Math.min(ys[0], ys[1]), Math.min(ys[2], ys[3]));
                    double ymax = Math.max(Math.max(ys[0], ys[1]), Math.max(ys[2], ys[3]));
                    double bw = Math.max(0.0, xmax - xmin);
                    double bh = Math.max(0.0, ymax - ymin);
                    double area = bw * bh;

                    List<Double> seg = Arrays.asList(
                            xs[0], ys[0],
                            xs[1], ys[1],
                            xs[2], ys[2],
                            xs[3], ys[3]
                    );
                    List<List<Double>> segList = new ArrayList<>();
                    segList.add(seg);

                    Map<String, Object> ann = new LinkedHashMap<>();
                    ann.put("id", annIdSeq++);
                    ann.put("image_id", imgId);
                    ann.put("category_id", cateId);
                    ann.put("bbox", Arrays.asList(xmin, ymin, bw, bh));
                    ann.put("area", area);
                    ann.put("iscrowd", 0);
                    ann.put("segmentation", segList);
                    annotations.add(ann);
                }
            }
        }

        // 4. categories
        List<Map<String, Object>> categories = new ArrayList<>();
        for (int i = 0; i < classNames.size(); i++) {
            Map<String, Object> cat = new LinkedHashMap<>();
            cat.put("id", i + 1);
            cat.put("name", classNames.get(i));
            cat.put("supercategory", "none");
            categories.add(cat);
        }

        // 5. 组装 COCO 根对象
        Map<String, Object> coco = new LinkedHashMap<>();
        coco.put("images", images);
        coco.put("annotations", annotations);
        coco.put("categories", categories);

        Files.createDirectories(outputJson.getParent());
        String jsonStr = JSONUtil.toJsonPrettyStr(coco);
        Files.writeString(outputJson, jsonStr, StandardCharsets.UTF_8);
    }

}
