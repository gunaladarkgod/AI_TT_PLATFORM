package com.xgls.web.controller;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xgls.web.base.AjaxResult;
import com.xgls.web.base.CodeMap;
import com.xgls.web.base.ErrorCode;
import com.xgls.web.entity.ModelTrans;
import com.xgls.web.entity.TrainScript;
import com.xgls.web.entity.TrainTask;
import com.xgls.web.service.ModelTransService;
import com.xgls.web.service.TrainScriptService;
import com.xgls.web.service.TrainTaskService;
import com.xgls.web.vo.TransParams;
import com.xgls.web.vo.query.ModelTransQuery;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONConfig;
import cn.hutool.json.JSONUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Tag(name = "模型转换任务管理")
@RestController
@RequestMapping("/modelTrans")
@Slf4j
public class ModelTransController {
    @Value("${sys.root-upload}")
    String rootPath;

    @Autowired
    ModelTransService modelTransService;
    @Autowired
    TrainScriptService trainScriptService;
    @Autowired
    TrainTaskService trainTaskService;

    @Operation(summary = "分页获取列表", description = "分页获取列表")
    @PostMapping("list")
    public AjaxResult queryList(ModelTransQuery query) {
        /** 查询条件 */
        LambdaQueryWrapper<ModelTrans> wrapper = Wrappers.lambdaQuery(ModelTrans.class);

        if (StrUtil.isNotBlank(query.getType())) {
            wrapper.eq(ModelTrans::getType, query.getType());
        }
        if (query.getStatus() != null) {
            wrapper.eq(ModelTrans::getStatus, query.getStatus());
        }
        if (StrUtil.isNotBlank(query.getCreateman())) {
            wrapper.eq(ModelTrans::getCreateman, query.getCreateman());
        }
        /** 用户名 模糊检索 */
        if (StrUtil.isNotBlank(query.getName())) {
            wrapper.like(ModelTrans::getName, query.getName());
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
        Page<ModelTrans> page = new Page<>(current, size);

        /** 排序信息 */
        List<OrderItem> orders = query.getOrders();
        if (orders != null && !orders.isEmpty()) {
            page.addOrder(orders);
        } else {
            page.addOrder(OrderItem.desc("createtime"));
        }
        Page<ModelTrans> res = modelTransService.page(page, wrapper);

        return AjaxResult.success(res);
    }

    @Operation(summary = "添加", description = "添加")
    @PostMapping("add")
    public AjaxResult add(String name, String createman, String remark, String type, String params,
            MultipartFile weight_file, MultipartFile data_file, MultipartFile check_file, MultipartFile val_file)
            throws IOException {
        TransParams info = JSONUtil.toBean(params, TransParams.class);
        // 通用参数校验
        if (StrUtil.isBlank(name) || StrUtil.isBlank(type) || weight_file == null || weight_file.isEmpty()
                || data_file == null || data_file.isEmpty()) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        String weight_file_name = weight_file.getOriginalFilename();
        if (weight_file_name == null || !weight_file_name.endsWith(".pt")) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        String data_file_name = data_file.getOriginalFilename();
        if (data_file_name == null || !data_file_name.endsWith(".yaml")) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        info.setData(data_file_name);
        if (type.equals(CodeMap.ALG_PT_ONNX)) {
            Integer imgsz = info.getImgsz();
            if (imgsz == null || data_file == null) {
                return AjaxResult.error(ErrorCode.PARAMS_WRONG);
            }
        } else if (type.contains(CodeMap.ALG_PT_RKNN)) {
            if (info.getModel_h() == null || info.getModel_w() == null
                    || StrUtil.isBlank(info.getType()) || StrUtil.isBlank(info.getChn())
                    || StrUtil.isBlank(info.getDate())) {
                return AjaxResult.error(ErrorCode.PARAMS_WRONG);
            }
            if (check_file != null) {
                String check_file_name = check_file.getOriginalFilename();
                if (check_file.isEmpty() || check_file_name == null || !check_file_name.endsWith(".zip")) {
                    return AjaxResult.error(ErrorCode.PARAMS_WRONG);
                }
                info.setExt_check(check_file_name);
            }
            if (val_file != null) {
                String val_file_name = val_file.getOriginalFilename();
                if (val_file.isEmpty() || val_file_name == null || !val_file_name.endsWith(".zip")) {
                    return AjaxResult.error(ErrorCode.PARAMS_WRONG);
                }
                info.setExt_val(val_file_name);
            }
        } else {
            return AjaxResult.error("不支持该转换类型:" + type);
        }

        ModelTrans mt = new ModelTrans();
        mt.setName(name);
        mt.setCreateman(createman);
        mt.setCreatetime(LocalDateTime.now());
        mt.setRemark(remark);
        mt.setStatus(CodeMap.MODEL_TRANS_STATUS_DEFAULT);
        mt.setType(type);
        mt.setWeights(weight_file_name);

        // 转换非空字段
        JSONConfig cfg = new JSONConfig();
        cfg.setIgnoreNullValue(true);
        mt.setParams(JSONUtil.toJsonStr(info, cfg));

        boolean flg = modelTransService.saveLink(mt, data_file, weight_file, check_file, val_file);
        return flg ? AjaxResult.success(mt) : AjaxResult.error();
    }

    @Operation(summary = "由模型训练任务创建", description = "由训练任务创建转换任务")
    @PostMapping("addByTask")
    public AjaxResult addByTask(Integer taskId, String name, String createman, String remark, String type,
            String params, MultipartFile check_file, MultipartFile val_file)
            throws IOException {

        TransParams info = JSONUtil.toBean(params, TransParams.class);
        // 通用参数校验
        if (taskId == null || StrUtil.isBlank(name) || StrUtil.isBlank(type)) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        // 检查taskId,获取模型和配置文件
        TrainTask task = trainTaskService.getById(taskId);
        if (task == null) {
            return AjaxResult.error("训练任务不存在");
        }
        String runName = task.getRun_name();
        if (StrUtil.isBlank(runName)) {
            return AjaxResult.error("训练结果不存在");
        }
        // pt文件
        Path weightPath = Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TRAIN_TASK, taskId.toString(),
                CodeMap.DIR_TRAIN_RUN, runName, "weights", "best.pt");
        if (!weightPath.toFile().exists()) {
            return AjaxResult.error("训练权重文件不存在:" + runName + "/weights/best.pt");
        }
        Path dataPath = Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TRAIN_TASK, taskId.toString(),
                CodeMap.DIR_TRAIN_FILE, "data.yaml");
        if (!weightPath.toFile().exists()) {
            return AjaxResult.error("模型配置文件不存在:data.yaml");
        }
        info.setData("data.yaml");
        if (type.equals(CodeMap.ALG_PT_ONNX)) {
            Integer imgsz = info.getImgsz();
            if (imgsz == null) {
                return AjaxResult.error(ErrorCode.PARAMS_WRONG);
            }
        } else if (type.contains(CodeMap.ALG_PT_RKNN)) {
            if (info.getModel_h() == null || info.getModel_w() == null
                    || StrUtil.isBlank(info.getType()) || StrUtil.isBlank(info.getChn())
                    || StrUtil.isBlank(info.getDate())) {
                return AjaxResult.error(ErrorCode.PARAMS_WRONG);
            }
            if (check_file != null) {
                String check_file_name = check_file.getOriginalFilename();
                if (check_file.isEmpty() || check_file_name == null || !check_file_name.endsWith(".zip")) {
                    return AjaxResult.error(ErrorCode.PARAMS_WRONG);
                }
                info.setExt_check(check_file_name);
            }
            if (val_file != null) {
                String val_file_name = val_file.getOriginalFilename();
                if (val_file.isEmpty() || val_file_name == null || !val_file_name.endsWith(".zip")) {
                    return AjaxResult.error(ErrorCode.PARAMS_WRONG);
                }
                info.setExt_val(val_file_name);
            }
        } else {
            return AjaxResult.error("不支持该转换类型:" + type);
        }

        ModelTrans mt = new ModelTrans();
        mt.setName(name);
        mt.setCreateman(createman);
        mt.setCreatetime(LocalDateTime.now());
        mt.setRemark(remark);
        mt.setStatus(CodeMap.MODEL_TRANS_STATUS_DEFAULT);
        mt.setType(type);
        mt.setWeights("train_" + taskId + "_" + runName + "_best.pt");

        // 转换非空字段
        JSONConfig cfg = new JSONConfig();
        cfg.setIgnoreNullValue(true);
        mt.setParams(JSONUtil.toJsonStr(info, cfg));

        boolean flg = modelTransService.saveLink2(mt, dataPath, weightPath, check_file, val_file);
        if (flg) {
            // 修改训练任务的trans_name
            TrainTask record = new TrainTask();
            record.setId(taskId);
            record.setTrans_name(runName);
            trainTaskService.updateById(record);
            return AjaxResult.success(mt);
        }
        return AjaxResult.error();
    }

    @Operation(summary = "修改", description = "修改")
    @PostMapping("update")
    public AjaxResult update(Integer id, String name, String createman, String remark, String params,
            MultipartFile weight_file, MultipartFile data_file, MultipartFile check_file, MultipartFile val_file)
            throws IOException {
        if (id == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }

        ModelTrans exist_mt = modelTransService.getById(id);
        if (exist_mt == null) {
            return AjaxResult.error("任务不存在");
        }
        // 新参数
        TransParams info = JSONUtil.toBean(params, TransParams.class);
        // 旧参数
        TransParams exist_info = JSONUtil.toBean(exist_mt.getParams(), TransParams.class);
        // 转换算法类型
        String type = exist_mt.getType();

        String weight_file_name = exist_mt.getWeights();
        if (weight_file != null) {
            weight_file_name = weight_file.getOriginalFilename();
            if (weight_file_name == null || !weight_file_name.endsWith(".pt")) {
                return AjaxResult.error(ErrorCode.PARAMS_WRONG);
            }
        }
        if (data_file != null) {
            String data_file_name = data_file.getOriginalFilename();
            if (data_file_name == null || !data_file_name.endsWith(".yaml")) {
                return AjaxResult.error(ErrorCode.PARAMS_WRONG);
            }
            info.setData(data_file_name);
        } else {
            info.setData(exist_info.getData());
        }
        if (type.equals(CodeMap.ALG_PT_ONNX)) {
            Integer imgsz = info.getImgsz();
            // onnx 参数校验
            if (imgsz == null) {
                return AjaxResult.error(ErrorCode.PARAMS_WRONG);
            }
        } else if (type.contains(CodeMap.ALG_PT_RKNN)) {
            if (info.getModel_h() == null || info.getModel_w() == null
                    || StrUtil.isBlank(info.getType()) || StrUtil.isBlank(info.getChn())
                    || StrUtil.isBlank(info.getDate())) {
                return AjaxResult.error(ErrorCode.PARAMS_WRONG);
            }
            if (check_file != null) {
                String check_file_name = check_file.getOriginalFilename();
                if (check_file.isEmpty() || check_file_name == null || !check_file_name.endsWith(".zip")) {
                    return AjaxResult.error(ErrorCode.PARAMS_WRONG);
                }
                info.setExt_check(check_file_name);
            }
            if (val_file != null) {
                String val_file_name = val_file.getOriginalFilename();
                if (val_file.isEmpty() || val_file_name == null || !val_file_name.endsWith(".zip")) {
                    return AjaxResult.error(ErrorCode.PARAMS_WRONG);
                }
                info.setExt_val(val_file_name);
            }
        } else {
            return AjaxResult.error("不支持该转换类型:" + type);
        }

        ModelTrans mt = new ModelTrans();
        mt.setId(exist_mt.getId());
        mt.setName(name);
        // mt.setCreateman(createman);
        // mt.setCreatetime(LocalDateTime.now());
        mt.setRemark(remark);
        mt.setType(type);
        // 不修改状态
        // mt.setStatus(CodeMap.MODEL_TRANS_STATUS_DEFAULT);
        mt.setWeights(weight_file_name);

        // 转换非空字段
        JSONConfig cfg = new JSONConfig();
        cfg.setIgnoreNullValue(true);
        mt.setParams(JSONUtil.toJsonStr(info, cfg));

        boolean flg = modelTransService.updateLink(mt, data_file, weight_file, check_file, val_file, exist_mt);
        return flg ? AjaxResult.success(mt) : AjaxResult.error();
    }

    @Operation(summary = "删除任务", description = "删除任务")
    @PostMapping("del")
    public AjaxResult delete(Integer id) {
        if (id == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        ModelTrans mt = modelTransService.getById(id);
        if (mt == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        if (CodeMap.MODEL_TRANS_STATUS_RUN == mt.getStatus()) {
            return AjaxResult.error("正在运行的任务不能删除");
        }
        if (modelTransService.removeById(id)) {
            try {
                FileUtil.del(Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_MODEL_TRANS, id.toString()));
            } catch (Exception e) {
                log.warn("del fail:{}/{}", CodeMap.DIR_MODEL_TRANS, id);
            }
            // 如果是训练任务创建的,还需要移除训练任务的trans_name
            String name = mt.getName();
            // train_14_exp001
            if (ReUtil.isMatch(CodeMap.RE_TRANS_NAME, name)) {
                String[] arr = name.split("_");
                LambdaUpdateWrapper<TrainTask> wrapper = new LambdaUpdateWrapper<>();
                wrapper.eq(TrainTask::getId, Integer.parseInt(arr[1])).eq(TrainTask::getRun_name, arr[2]);
                TrainTask task = new TrainTask();
                task.setTrans_name(name);
                boolean flg = trainTaskService.update(task, wrapper);
                log.info("remove transMode task:{},res:{}", name, flg);
            }

            return AjaxResult.success();
        }
        return AjaxResult.error();
    }

    @Operation(summary = "启动任务", description = "启动任务")
    @PostMapping("start")
    public AjaxResult start(Integer id) {
        if (id == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        ModelTrans mt = modelTransService.getById(id);
        if (mt == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        if (CodeMap.MODEL_TRANS_STATUS_RUN == mt.getStatus()) {
            return AjaxResult.error("任务正在进行中");
        }

        // 查看脚本是否存在
        LambdaQueryWrapper<TrainScript> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrainScript::getType, CodeMap.SCRIPT_TYPE_TRANS);
        wrapper.eq(TrainScript::getName, mt.getType());
        TrainScript script = trainScriptService.getOne(wrapper, false);
        if (script == null) {
            return AjaxResult.error("转换算法不存在:" + mt.getType());
        }

        ModelTrans record = new ModelTrans();
        record.setId(id);
        record.setStarttime(LocalDateTime.now());
        record.setStatus(CodeMap.MODEL_TRANS_STATUS_RUN);
        if (modelTransService.updateById(record)) {
            /** 启动转换任务 */
            modelTransService.startTask(mt, script);
            return AjaxResult.success();
        }
        return AjaxResult.error();
    }

    @Operation(summary = "查询校验/验证集", description = "查询校验/验证集")
    @PostMapping("calibrate")
    public AjaxResult getCalibrateList(String type) {
        // 只有两个类型允许
        if (!(StrUtil.equals(type, CodeMap.DIR_MODEL_CHECK) || StrUtil.equals(type, CodeMap.DIR_MODEL_VAL))) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        ArrayList<String> dirs = new ArrayList<>();
        Path basePath = Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_MODEL_CALIBRATE, type);

        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(basePath)) {
            for (Path path : directoryStream) {
                if (Files.isDirectory(path)) {
                    dirs.add(path.getFileName().toString());
                }
            }
        } catch (IOException ex) {
            log.error(ex.getMessage());
        }
        return AjaxResult.success(dirs);
    }
}
