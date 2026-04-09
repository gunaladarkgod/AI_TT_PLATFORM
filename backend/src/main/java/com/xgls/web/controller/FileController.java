package com.xgls.web.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.xgls.web.base.AjaxResult;
import com.xgls.web.base.CodeMap;
import com.xgls.web.base.ErrorCode;
import com.xgls.web.service.EngineTaskService;
import com.xgls.web.utils.LogFileReader;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Tag(name = "文件管理")
@RestController
@RequestMapping("/files")
@Slf4j
public class FileController {
    @Value("${sys.root-upload}")
    String rootPath;
    @Value("${sys.root-logger}")
    String logPath;

    @Autowired
    EngineTaskService engineTaskService;

    /**
     * 查询本地目录下的文件及文件夹
     * 
     * @param base
     * @return
     */
    public AjaxResult getAllFiles(String base) {
        if (base == null || base.contains("..")) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        base = base.replace('\\', '/');
        Path basePath = Paths.get(base);
        if (!basePath.toFile().isDirectory()) {
            return AjaxResult.error("目录不存在");
        }
        ArrayList<String> dirs = new ArrayList<>();
        ArrayList<String> files = new ArrayList<>();
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(basePath)) {
            for (Path path : directoryStream) {
                if (Files.isDirectory(path)) {
                    dirs.add(path.getFileName().toString());
                } else if (Files.isRegularFile(path)) {
                    files.add(path.getFileName().toString());
                    if (files.size() > 499) {
                        break;
                    }
                }
            }
        } catch (IOException ex) {
            log.error(ex.getMessage());
        }

        JSONObject json = new JSONObject();
        json.set("dirs", dirs);
        json.set("files", files);
        json.set("base", base);
        return AjaxResult.success(json);
    }

    /**
     * 查询子目录的列表,不包含文件
     * 
     * @param base
     * @return
     */
    public AjaxResult getDirs(String base) {
        if (base == null || base.contains("..")) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        base = base.replace('\\', '/');
        Path basePath = Paths.get(base);
        if (!basePath.toFile().isDirectory()) {
            return AjaxResult.error("目录不存在");
        }
        ArrayList<String> dirs = new ArrayList<>();
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(basePath)) {
            for (Path path : directoryStream) {
                if (Files.isDirectory(path)) {
                    dirs.add(path.getFileName().toString());
                }
            }
        } catch (IOException ex) {
            log.error(ex.getMessage());
        }

        JSONObject json = new JSONObject();
        json.set("dirs", dirs);
        json.set("base", base);
        return AjaxResult.success(json);
    }

    /**
     * 查询目录下文件的列表,不包含目录
     * 
     * @param base
     * @return
     */
    public AjaxResult getFiles(String base) {
        if (base == null || base.contains("..")) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        base = base.replace('\\', '/');
        Path basePath = Paths.get(base);
        if (!basePath.toFile().isDirectory()) {
            return AjaxResult.error("目录不存在");
        }
        ArrayList<String> files = new ArrayList<>();
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(basePath)) {
            for (Path path : directoryStream) {
                if (Files.isRegularFile(path)) {
                    files.add(path.getFileName().toString());
                    if (files.size() > 499) {
                        break;
                    }
                }
            }
        } catch (IOException ex) {
            log.error(ex.getMessage());
        }
        JSONObject json = new JSONObject();
        json.set("files", files);
        json.set("base", base);
        return AjaxResult.success(json);
    }

    @PostMapping("/list")
    public AjaxResult queryFiles(String type, String base) {
        if (StrUtil.isBlank(type)) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        switch (type) {
            case CodeMap.DIR_CVAT_TASK:
            case CodeMap.DIR_TRAIN_TASK:
            case CodeMap.DIR_MODEL_TRANS:
            case CodeMap.DIR_MODEL_CALIBRATE:
                return getAllFiles(rootPath + "/" + CodeMap.DIR_SRC + "/" + type + "/" + base);
            default:
                return AjaxResult.error();
        }
    }

    @PostMapping("/dir/del")
    public AjaxResult delDir(String type, String base) {
        if (type == null || base == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        // 允许删除的条件
        if ((type.equals(CodeMap.DIR_MODEL_CALIBRATE) && ReUtil.isMatch(CodeMap.RE_PATH, base))
                || (type.equals(CodeMap.DIR_TRAIN_TASK)
                        && ReUtil.isMatch("^[0-9]+(/run/exp[0-9]*|/val/.+|/predict/.+)$", base))
                || (type.equals(CodeMap.DIR_CVAT_TASK)
                        && ReUtil.isMatch("^[0-9]+/[0-9]+/data_trans(/.*)?$", base))) {

            Path path = Paths.get(rootPath, CodeMap.DIR_SRC, type, base);
            if (FileUtil.del(path)) {
                // 根目录要保存
                if (ReUtil.isMatch(CodeMap.RE_EMPTY_PATH, base)) {
                    FileUtil.mkdir(Paths.get(rootPath, CodeMap.DIR_SRC, type));
                }
                return AjaxResult.success();
            }
            return AjaxResult.error();
        }

        return AjaxResult.error(ErrorCode.PERMISSION_DENIED);
    }

    @PostMapping("/file/del")
    public AjaxResult delFile(String type, String base, String name) {
        if (StrUtil.isBlank(type) || base == null || StrUtil.isBlank(name)) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        if (name.contains("/") || name.contains("\\")) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        // 允许删除的条件
        if ((type.equals(CodeMap.DIR_MODEL_CALIBRATE) || type.equals(CodeMap.DIR_MODEL_TRANS))
                && ReUtil.isMatch(CodeMap.RE_PATH, base)) {
            Path path = Paths.get(rootPath, CodeMap.DIR_SRC, type, base, name);
            if (path.toFile().isFile()) {
                try {
                    Files.delete(path);
                    return AjaxResult.success();
                } catch (IOException e) {
                    return AjaxResult.error();
                }
            }
        }

        return AjaxResult.error(ErrorCode.PERMISSION_DENIED);
    }

    @PostMapping("/dir/add")
    public AjaxResult addDir(String type, String base, String name) {
        if (StrUtil.isBlank(type) || StrUtil.isBlank(base) || StrUtil.isBlank(name)) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        if ((type.equals(CodeMap.DIR_MODEL_CALIBRATE)
                && ReUtil.isMatch(CodeMap.RE_PATH, base)
                && ReUtil.isMatch(CodeMap.RE_SN, name))) {
            Path path = Paths.get(rootPath, CodeMap.DIR_SRC, type, base, name);
            log.info("create dir:{}", path.toString());
            if (path.toFile().exists()) {
                return AjaxResult.error("目录已存在");
            }
            try {
                Files.createDirectory(path);
                return AjaxResult.success();
            } catch (Exception e) {
                e.printStackTrace();
                return AjaxResult.error(e.getMessage());
            }
        }
        return AjaxResult.error(ErrorCode.PERMISSION_DENIED);
    }

    @PostMapping("/file/add")
    public AjaxResult addFiles(String type, String base, MultipartFile file) {
        if (file == null || base == null || type == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        if (ReUtil.isMatch(CodeMap.RE_PATH, base)
                && (type.equals(CodeMap.DIR_MODEL_CALIBRATE)
                        || type.equals(CodeMap.DIR_MODEL_TRANS)
                        || type.equals(CodeMap.DIR_TRAIN_TASK))) {
            try {
                file.transferTo(Path.of(rootPath, CodeMap.DIR_SRC, type, base, file.getOriginalFilename()));
                return AjaxResult.success();
            } catch (IllegalStateException | IOException e) {
                return AjaxResult.error(e.getMessage());
            }
        }

        return AjaxResult.error(ErrorCode.PERMISSION_DENIED);
    }

    @PostMapping("log")
    public AjaxResult getLog(Integer lines, String type) {
        // 限制要访问的日志类别
        if (!(StrUtil.equals(type, CodeMap.SCRIPT_TYPE_TRAIN) // 训练
                || StrUtil.equals(type, CodeMap.SCRIPT_TYPE_TRANS)// 转换
                || StrUtil.equals(type, CodeMap.SCRIPT_TYPE_VAL)// 验证
                || StrUtil.equals(type, CodeMap.SCRIPT_TYPE_PREDICT)// 预测
                || StrUtil.equals(type, CodeMap.SCRIPT_TYPE_DATA)// 数据集转换
                || StrUtil.equals(type, "main"))) {// 主日志
            return AjaxResult.success(new ArrayList<>());
        }
        if (lines == null || lines <= 0) {
            lines = 30;
        } else if (lines > 100) {
            lines = 100;
        }
        Path path = Path.of(logPath, type, type + ".log");
        try {
            return AjaxResult.success(LogFileReader.getLastLines(path.toString(), lines));
        } catch (IOException e) {
            return AjaxResult.success(new ArrayList<>());
        }
    }

    @PostMapping("/dir/download")
    public void downloadFiles(String type, String base, HttpServletResponse response) throws IOException {
        if (!StrUtil.equals(type, CodeMap.DIR_TRAIN_TASK)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        Path directoryPath = Path.of(rootPath, CodeMap.DIR_SRC, type, base);
        // 检查目录是否存在
        if (Files.notExists(directoryPath) || !Files.isDirectory(directoryPath)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // 设置响应类型为二进制流
        response.setContentType("application/zip");

        // 根据目录的名称生成下载文件名
        String result = base.replace('/', '_').replace('\\', '_') + ".zip";
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result + "\"");

        try (ZipOutputStream zipOut = new ZipOutputStream(response.getOutputStream())) {
            Files.newDirectoryStream(directoryPath, path -> Files.isRegularFile(path)).forEach(file -> {
                try (InputStream inputStream = Files.newInputStream(file)) {
                    ZipEntry zipEntry = new ZipEntry(file.getFileName().toString());
                    zipOut.putNextEntry(zipEntry);
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = inputStream.read(buffer)) >= 0) {
                        zipOut.write(buffer, 0, length);
                    }
                    zipOut.closeEntry();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }

}
