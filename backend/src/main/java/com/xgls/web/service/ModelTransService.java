package com.xgls.web.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xgls.web.base.CodeMap;
import com.xgls.web.entity.ModelTrans;
import com.xgls.web.entity.TrainScript;
import com.xgls.web.mapper.ModelTransMapper;
import com.xgls.web.utils.StreamGobbler;
import com.xgls.web.vo.TransParams;
import com.xgls.web.wscontroller.WsTransController;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ModelTransService extends ServiceImpl<ModelTransMapper, ModelTrans> {
    @Value("${sys.root-upload}")
    String rootPath;

    @Transactional
    public boolean saveLink(ModelTrans mt, MultipartFile data_file, MultipartFile weight_file, MultipartFile check_file,
            MultipartFile val_file)
            throws IOException {
        TransParams params = JSONUtil.toBean(mt.getParams(), TransParams.class);
        // 先插入,获取id
        if (baseMapper.insert(mt) <= 0) {
            return false;
        }
        Path basePath = Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_MODEL_TRANS, mt.getId().toString());
        // 先创建id目录
        Files.createDirectories(basePath);
        // 公共文件
        weight_file.transferTo(basePath.resolve(mt.getWeights()));
        data_file.transferTo(basePath.resolve(params.getData()));

        String type = mt.getType();
        if (type.contains(CodeMap.ALG_PT_RKNN)) {
            // 1.赋值基础校验图片集
            Path checkPath = basePath.resolve(CodeMap.DIR_MODEL_CHECK);
            Files.createDirectories(checkPath);
            String base_check = params.getBase_check().toLowerCase();
            FileUtil.copyContent(
                    Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_MODEL_CALIBRATE, CodeMap.DIR_MODEL_CHECK, base_check)
                            .toFile(),
                    checkPath.toFile(), true);
            // 2.赋值基础验证图片集
            Path valPath = basePath.resolve(CodeMap.DIR_MODEL_VAL);
            Files.createDirectories(checkPath);
            String base_val = params.getBase_val().toLowerCase();
            FileUtil.copyContent(
                    Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_MODEL_CALIBRATE, CodeMap.DIR_MODEL_VAL, base_val)
                            .toFile(),
                    valPath.toFile(), true);
            // 3.保存zip
            if (check_file != null) {
                // 保存
                Path checkZipPath = basePath.resolve(check_file.getOriginalFilename());
                check_file.transferTo(checkZipPath);
                // 解压
                ZipUtil.unzip(checkZipPath.toString(), checkPath.toString());
                // 不删除,需要保留,以后使用
                // Files.delete(zipPath);
            }
            if (val_file != null) {
                // 保存
                Path valZipPath = basePath.resolve(val_file.getOriginalFilename());
                val_file.transferTo(valZipPath);
                // 解压
                ZipUtil.unzip(valZipPath.toString(), valPath.toString());
                // 不删除,需要保留,以后使用
                // Files.delete(zipPath);
            }
        }

        return true;
    }

    @Transactional
    public boolean saveLink2(ModelTrans mt, Path dataPath, Path weightPath, MultipartFile check_file,
            MultipartFile val_file)
            throws IOException {
        TransParams params = JSONUtil.toBean(mt.getParams(), TransParams.class);
        // 先插入,获取id
        if (baseMapper.insert(mt) <= 0) {
            return false;
        }
        Path basePath = Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_MODEL_TRANS, mt.getId().toString());
        // 先创建id目录
        Files.createDirectories(basePath);
        // 公共文件
        FileUtil.copyContent(weightPath.toFile(), basePath.resolve(mt.getWeights()).toFile(), true);
        FileUtil.copyContent(dataPath.toFile(), basePath.resolve(params.getData()).toFile(), true);

        String type = mt.getType();
        if (type.contains(CodeMap.ALG_PT_RKNN)) {
            // 1.赋值基础校验图片集
            Path checkPath = basePath.resolve(CodeMap.DIR_MODEL_CHECK);
            Files.createDirectories(checkPath);
            String base_check = params.getBase_check().toLowerCase();
            FileUtil.copyContent(
                    Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_MODEL_CALIBRATE, CodeMap.DIR_MODEL_CHECK, base_check)
                            .toFile(),
                    checkPath.toFile(), true);
            // 2.赋值基础验证图片集
            Path valPath = basePath.resolve(CodeMap.DIR_MODEL_VAL);
            Files.createDirectories(checkPath);
            String base_val = params.getBase_val().toLowerCase();
            FileUtil.copyContent(
                    Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_MODEL_CALIBRATE, CodeMap.DIR_MODEL_VAL, base_val)
                            .toFile(),
                    valPath.toFile(), true);
            // 3.保存zip
            if (check_file != null) {
                // 保存
                Path checkZipPath = basePath.resolve(check_file.getOriginalFilename());
                check_file.transferTo(checkZipPath);
                // 解压
                ZipUtil.unzip(checkZipPath.toString(), checkPath.toString());
                // 不删除,需要保留,以后使用
                // Files.delete(zipPath);
            }
            if (val_file != null) {
                // 保存
                Path valZipPath = basePath.resolve(val_file.getOriginalFilename());
                val_file.transferTo(valZipPath);
                // 解压
                ZipUtil.unzip(valZipPath.toString(), valPath.toString());
                // 不删除,需要保留,以后使用
                // Files.delete(zipPath);
            }
        }

        return true;
    }

    @Transactional
    public boolean updateLink(ModelTrans mt, MultipartFile data_file, MultipartFile weight_file,
            MultipartFile check_file,
            MultipartFile val_file, ModelTrans old_mt)
            throws IOException {
        TransParams params = JSONUtil.toBean(mt.getParams(), TransParams.class);
        TransParams old_params = JSONUtil.toBean(old_mt.getParams(), TransParams.class);
        Path basePath = Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_MODEL_TRANS, mt.getId().toString());

        if (weight_file != null) {
            weight_file.transferTo(basePath.resolve(mt.getWeights()));
            // 删除旧的
            if (!StrUtil.equals(old_mt.getWeights(), mt.getWeights())) {
                try {
                    Files.delete(basePath.resolve(old_mt.getWeights()));
                } catch (Exception e) {
                }
            }
        }

        if (data_file != null) {
            data_file.transferTo(basePath.resolve(params.getData()));
            String old_file = old_params.getData();
            // 删除旧的
            if (StrUtil.isNotBlank(old_file) && !StrUtil.equals(old_file, params.getData())) {
                Files.delete(basePath.resolve(old_file));
            }
        }

        String type = mt.getType();
        if (type.contains(CodeMap.ALG_PT_RKNN)) {
            // 1.校验集 check
            if (!StrUtil.equalsAnyIgnoreCase(params.getBase_check(), old_params.getBase_check())
                    || check_file != null ||
                    (StrUtil.isBlank(params.getExt_check()) && StrUtil.isNotBlank(old_params.getExt_check()))) {
                Path checkPath = basePath.resolve(CodeMap.DIR_MODEL_CHECK);
                // 1.先清空
                FileUtil.del(checkPath);
                // 2.拷贝基础数据集
                String base_check = params.getBase_check().toLowerCase();
                FileUtil.copyContent(
                        Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_MODEL_CALIBRATE, CodeMap.DIR_MODEL_CHECK,
                                base_check)
                                .toFile(),
                        checkPath.toFile(), true);
                // 3.保存拓展数据集
                String ext_check = params.getExt_check();
                if (check_file != null) {
                    // 保存
                    check_file.transferTo(basePath.resolve(ext_check));
                }
                if (StrUtil.isNotBlank(ext_check)) {
                    Path extCheckPath = basePath.resolve(ext_check);
                    if (extCheckPath.toFile().exists()) {
                        ZipUtil.unzip(extCheckPath.toString(), checkPath.toString());
                    }
                }
                String old_ext_check = old_params.getExt_check();
                // 4.删除旧的zip包
                if (!StrUtil.equals(ext_check, old_ext_check) && StrUtil.isNotBlank(old_ext_check)) {
                    try {
                        Files.delete(basePath.resolve(old_ext_check));
                    } catch (Exception e) {
                    }
                }
            }
            // 2.验证集 val
            if (!StrUtil.equalsAnyIgnoreCase(params.getBase_val(), old_params.getBase_val()) || val_file != null ||
                    (StrUtil.isBlank(params.getExt_val()) && StrUtil.isNotBlank(old_params.getExt_val()))) {
                Path valPath = basePath.resolve(CodeMap.DIR_MODEL_VAL);
                // 1.先清空
                FileUtil.del(valPath);
                // 2.拷贝基础数据集
                String base_val = params.getBase_val().toLowerCase();
                FileUtil.copyContent(
                        Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_MODEL_CALIBRATE, CodeMap.DIR_MODEL_VAL, base_val)
                                .toFile(),
                        valPath.toFile(), true);
                // 3.保存拓展数据集
                String ext_val = params.getExt_val();
                if (val_file != null) {
                    // 保存
                    val_file.transferTo(basePath.resolve(ext_val));
                }
                if (StrUtil.isNotBlank(ext_val)) {
                    Path extValPath = basePath.resolve(ext_val);
                    if (extValPath.toFile().exists()) {
                        ZipUtil.unzip(extValPath.toString(), valPath.toString());
                    }
                }
                String old_ext_val = old_params.getExt_val();
                // 4.删除旧的zip包
                if (!StrUtil.equals(ext_val, old_ext_val) && StrUtil.isNotBlank(old_ext_val)) {
                    try {
                        Files.delete(basePath.resolve(old_ext_val));
                    } catch (Exception e) {
                    }
                }
            }
        }
        // 更新
        return baseMapper.updateById(mt) > 0;
    }

    @Async
    public void startTask(ModelTrans record, TrainScript script) {
        String type = record.getType();
        TransParams parmas = JSONUtil.toBean(record.getParams(), TransParams.class);

        ProcessBuilder pBuilder = null;
        String suff = script.getSuff();
        Path scriptPath = Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_SCRIPT, script.getId() + suff);

        boolean isLinux = StrUtil.equals(".sh", suff);
        // pt2onnx
        Path basePath = Path.of(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_MODEL_TRANS, record.getId().toString());
        if (StrUtil.equals(type, CodeMap.ALG_PT_ONNX)) {
            String imgsz = parmas.getImgsz().toString();
            pBuilder = isLinux
                    ? new ProcessBuilder(
                            "bash",
                            scriptPath.toString(), // 脚本地址 .sh
                            "--data", basePath.resolve(parmas.getData()).toString(),
                            "--weights", basePath.resolve(record.getWeights()).toString(),
                            "--imgsz", imgsz, imgsz,
                            "--include", "onnx")
                    : new ProcessBuilder(
                            "cmd.exe",
                            "/c",
                            scriptPath.toString(), // 脚本地址 .sh
                            "--data", basePath.resolve(parmas.getData()).toString(),
                            "--weights", basePath.resolve(record.getWeights()).toString(),
                            "--imgsz", imgsz, imgsz,
                            "--include", "onnx");

        } else if (type.contains(CodeMap.ALG_PT_RKNN)) {
            Integer quantise = 0;
            if (parmas.getQuantise() != null) {
                quantise = parmas.getQuantise();
            }
            pBuilder = isLinux
                    ? new ProcessBuilder(
                            "bash",
                            scriptPath.toString(), // 脚本地址 .sh
                            String.format("type=%s", parmas.getType()),
                            String.format("chn=%s", parmas.getChn()),
                            String.format("date=%s", parmas.getDate()),
                            String.format("quantise=%s", quantise),
                            String.format("data=%s", basePath.resolve(parmas.getData()).toString()),
                            String.format("weights=%s", basePath.resolve(record.getWeights()).toString()),
                            String.format("model_w=%s", parmas.getModel_w()),
                            String.format("mode_h=%s", parmas.getModel_h()))
                            // String.format("root_dir=%s", resPath.toString()),
                            // String.format("check_dir=%s", check_dir),
                            // String.format("val_dir=%s", val_dir))
                    : new ProcessBuilder(
                            "cmd.exe",
                            "/c",
                            scriptPath.toString(), // 脚本地址 .sh
                            String.format("type=%s", parmas.getType()),
                            String.format("chn=%s", parmas.getChn()),
                            String.format("date=%s", parmas.getDate()),
                            String.format("quantise=%s", quantise),
                            String.format("data=%s", basePath.resolve(parmas.getData()).toString()),
                            String.format("weights=%s", basePath.resolve(record.getWeights()).toString()),
                            String.format("model_w=%s", parmas.getModel_w()),
                            String.format("mode_h=%s", parmas.getModel_h()));
                            // String.format("root_dir=%s", resPath.toString()),
                            // String.format("check_dir=%s", check_dir),
                            // String.format("val_dir=%s", val_dir))
        }

        try {
            if (pBuilder != null) {
                Process cuProcess = pBuilder.start();
                StreamGobbler errorGobbler = new StreamGobbler(cuProcess.getErrorStream(),
                        CodeMap.SCRIPT_TYPE_TRANS);
                StreamGobbler outputGobbler = new StreamGobbler(cuProcess.getInputStream(),
                        CodeMap.SCRIPT_TYPE_TRANS);
                errorGobbler.start();
                outputGobbler.start();
                // 等待进程结束并获取退出码
                int exitCode = cuProcess.waitFor();
                if (exitCode == 0) {
                    // 正常退出
                    log.warn("trans finish success id:{}", record.getId());
                } else {
                    // 异常退出
                    log.warn("trans finish error id:{}", record.getId());
                }
            }
        } catch (IOException | InterruptedException e) {
            log.warn("trans id:[{}],err:{}", record.getId(), e.getMessage());
        } finally {
            // 需要新建对象,避免过程中被修改了
            ModelTrans mt = new ModelTrans();
            mt.setId(record.getId());
            mt.setEndtime(LocalDateTime.now());
            mt.setStatus(CodeMap.MODEL_TRANS_STATUS_FINISH);
            baseMapper.updateById(mt);

            JSONObject jo = new JSONObject();
            jo.set("id", mt.getId());
            jo.set("status", mt.getStatus());
            jo.set("endtime", mt.getEndtime());
            WsTransController.senMsgToAll(jo.toString());
        }

    }
}
