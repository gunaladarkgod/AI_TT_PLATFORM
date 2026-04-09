package com.xgls.web.controller;

import com.xgls.web.common.preprocess_Result;
import com.xgls.web.entity.PreprocessScriptInfo;
import com.xgls.web.service.PreprocessScriptInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/preprocess")
public class PreprocessScriptUploadController {

    @Value("${sys.instancecfg.script-storage-dir:/home/omen1/AI_TT_Platform/data/preprocess_scripts/}")
    private String scriptStorageDir;

    @Autowired
    private PreprocessScriptInfoService preprocessScriptInfoService;

    @PostMapping("/upload")
    public preprocess_Result<?> uploadScript(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam("type") Integer type,
            @RequestParam("uploader") String uploader,
            @RequestParam(value = "paramSchema", defaultValue = "[]") String paramSchema) {

        // ========== 【1. 关键日志：确认请求到达】 ==========
        System.out.println("【后端】 收到上传请求！");
        System.out.println("【后端】name = " + name);
        System.out.println("【后端】type = " + type);
        System.out.println("【后端】uploader = " + uploader);
        System.out.println("【后端】paramSchema = " + paramSchema);
        System.out.println("【后端】文件名 = " + (file.isEmpty() ? "空" : file.getOriginalFilename()));
        // =================================================

        if (uploader == null || uploader.trim().isEmpty()) {
            System.out.println("【后端 uploader 为空");
            return preprocess_Result.error("请输入上传人");
        }
        String username = uploader.trim();

        if (file.isEmpty() || !file.getOriginalFilename().toLowerCase().endsWith(".py")) {
            System.out.println("【后端 文件为空或非 .py");
            return preprocess_Result.error("请上传 .py 脚本文件");
        }

        String safeFileName = UUID.randomUUID() + ".py";
        Path userScriptDir = Paths.get(scriptStorageDir, username);
        Path scriptPath = userScriptDir.resolve(safeFileName);

        try {
            Files.createDirectories(userScriptDir);
            file.transferTo(scriptPath.toFile());
            System.out.println("【后端】文件保存成功: " + scriptPath);
        } catch (IOException e) {
            System.err.println("【后端】文件保存失败: " + e.getMessage());
            e.printStackTrace();
            return preprocess_Result.error("保存脚本失败: " + e.getMessage());
        }

        if (!validatePythonSyntax(scriptPath.toString())) {
            try {
                Files.deleteIfExists(scriptPath);
            } catch (IOException ignored) {}
            System.err.println("【后端】Python 语法校验失败");
            return preprocess_Result.error("脚本 Python 语法错误");
        }

        PreprocessScriptInfo info = new PreprocessScriptInfo();
        info.setName(name);
        info.setType(type);
        info.setScript_path(scriptPath.toString());
        info.setParamSchema(paramSchema);
        info.setUploader(username);

        try {
            // ========== 【2. 关键日志：保存前】 ==========
            System.out.println("【后端】准备保存到数据库: " + info);
            preprocessScriptInfoService.save(info);
            // ========== 【3. 关键日志：保存后】 ==========
            System.out.println("【后端】数据库保存成功！scriptId = " + info.getId());
        } catch (Exception e) {
            System.err.println("【后端】数据库保存失败: " + e.getMessage());
            e.printStackTrace();
            return preprocess_Result.error("保存到数据库失败: " + e.getMessage());
        }

        return preprocess_Result.success(Map.of("scriptId", info.getId()));
    }

    private boolean validatePythonSyntax(String path) {
        try {
            // ========== 【4. 关键：使用 python3 而非 python】 ==========
            Process p = new ProcessBuilder("python3", "-m", "py_compile", path).start();
            boolean success = p.waitFor() == 0;
            System.out.println("【后端】 Python 语法校验结果: " + (success ? "通过" : "失败"));
            return success;
        } catch (Exception e) {
            System.err.println("【后端】Python 校验异常: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}