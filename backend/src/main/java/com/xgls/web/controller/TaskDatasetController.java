package com.xgls.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xgls.web.base.AjaxResult;
import com.xgls.web.base.CodeMap;
import com.xgls.web.entity.TaskDataset;
import com.xgls.web.service.TaskDatasetService;
import com.xgls.web.vo.TaskDatasetParams;
import com.xgls.web.vo.dataset.TaskDatasetMergePretrainRequestVO;
import com.xgls.web.vo.dataset.TaskDatasetMergeTargetRequestVO;
import com.xgls.web.vo.dataset.TaskDatasetSubsetsInfoVO;
import com.xgls.web.vo.dataset.TaskDatasetUploadTemplateVO;
import cn.hutool.core.collection.CollUtil;
import com.xgls.web.vo.query.TaskDatasetQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.xgls.web.base.AjaxResult;
import com.xgls.web.service.TaskDatasetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.*;
import java.util.Optional;

/**
 * 任务数据集管理控制器
 */
@Tag(name = "任务数据集管理")
@RestController
@RequestMapping("/taskDataset")
public class TaskDatasetController {

    @Autowired
    private TaskDatasetService taskDatasetService;

    @Operation(summary = "分页获取任务数据集列表", description = "获取所有任务数据集列表")
    @PostMapping("queryTaskDatasetList")
    public AjaxResult queryList(TaskDatasetQuery query) {
        /** 分页信息 */
        Long current = query.getCurrent();
        Long size = query.getSize();
        if (current == null) {
            current = CodeMap.PAGE_NO_DEFAULT;
        }
        if (size == null) {
            size = CodeMap.PAGE_SIZE_DEFAULT;
        }


        LambdaQueryWrapper<TaskDataset> wrapper = new LambdaQueryWrapper<>();
        Page<TaskDataset> page = new Page<>(current, size);
        Page<TaskDataset> pageResult = taskDatasetService.page(page, wrapper);

        // 使用 IPage 接口类型接收转换结果
        IPage<TaskDatasetParams> resultPage = pageResult.convert(TaskDatasetParams::new);

        return AjaxResult.success(resultPage);
    }


    @Operation(summary = "删除任务数据列", description = "删除任务数据列")
    @PostMapping("del")
    public AjaxResult delete(@Parameter(description = "任务id") @RequestParam long id) {
        TaskDataset taskDataset = taskDatasetService.getById(id);
        if (taskDataset == null) {
            return AjaxResult.error("任务不存在");
        }
        taskDatasetService.removeById(id);
        return AjaxResult.success();
    }

    @Operation(summary = "获取子集信息", description = "获取任务数据集的子集信息")
    @GetMapping("/subsets-info")
    public AjaxResult getSubsetsInfo(
            @RequestParam(value = "task_dataset_id", required = false) Long taskDatasetId,
            @RequestParam(value = "is_pretrain", required = false) Boolean isPretrain) {
        try {
            TaskDatasetSubsetsInfoVO info = taskDatasetService.getSubsetsInfo(taskDatasetId, isPretrain);
            if (info == null) {
                return AjaxResult.success("暂无任务数据集");
            }
            return AjaxResult.success(info);
        } catch (Exception e) {
            return AjaxResult.error("获取子集信息失败: " + e.getMessage());
        }
    }

    @Operation(summary = "上传模板文件", description = "上传任务数据集模板文件")
    @PostMapping("/upload-template")
    public AjaxResult uploadTemplate(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return AjaxResult.error("上传文件不能为空");
            }

            TaskDatasetUploadTemplateVO result = taskDatasetService.uploadTemplate(file);
            // 使用单参数success方法，将消息和数据合并到一个对象中
            Map<String, Object> response = new HashMap<>();
            response.put("message", "模板上传成功");
            response.put("data", result);
            return AjaxResult.success(response);
        } catch (IllegalArgumentException e) {
            return AjaxResult.error(e.getMessage());
        } catch (IOException e) {
            return AjaxResult.error("模板上传失败: " + e.getMessage());
        } catch (Exception e) {
            return AjaxResult.error("系统错误: " + e.getMessage());
        }
    }

    @Operation(summary = "合并目标子集", description = "合并选定的核心子集到目标数据集")
    @PostMapping("/merge-target")
    public AjaxResult mergeTarget(@RequestBody TaskDatasetMergeTargetRequestVO request) {
        try {
            // 参数验证
            if (request == null) {
                return AjaxResult.error("请求参数不能为空");
            }

            if (CollUtil.isEmpty(request.getSelectedCoreSubsetIds())) {
                return AjaxResult.error("请选择要合并的核心子集");
            }

            if (request.getTaskDatasetName() == null || request.getTaskDatasetName().trim().isEmpty()) {
                return AjaxResult.error("任务数据集名称不能为空");
            }

            // 记录请求参数
            System.out.println("=== 合并目标子集请求参数 ===");
            System.out.println("任务数据集名称: " + request.getTaskDatasetName());
            System.out.println("传感器类型: " + request.getSensorType());
            System.out.println("目标类型: " + request.getTargetType());
            System.out.println("核心子集ID: " + request.getSelectedCoreSubsetIds());
            System.out.println("用户名: " + request.getUsername());
            System.out.println("标签映射: " + (request.getLabelMapping() != null ? request.getLabelMapping().size() : 0));

            // 调用服务层
            Map<String, Object> result = taskDatasetService.updateTargetSubsets(request);

            if (result.containsKey("error")) {
                return AjaxResult.error(result.get("error").toString());
            }

            // 使用单参数success方法，将消息和数据合并到一个对象中
            Map<String, Object> response = new HashMap<>();
            response.put("message", "目标子集合并成功");
            response.put("data", result);
            return AjaxResult.success(response);
        } catch (IllegalArgumentException e) {
            return AjaxResult.error(e.getMessage());
        } catch (IOException e) {
            return AjaxResult.error("合并目标子集失败: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return AjaxResult.error("系统错误: " + e.getMessage());
        }
    }

    @Operation(summary = "合并预训练子集", description = "合并选定的辅助子集到预训练数据集")
    @PostMapping("/merge-pretrain")
    public AjaxResult mergePretrain(@RequestBody TaskDatasetMergePretrainRequestVO request) {
        try {
            // 参数验证
            if (request == null) {
                return AjaxResult.error("请求参数不能为空");
            }

            if (CollUtil.isEmpty(request.getSelectedAuxiliarySubsetIds())) {
                return AjaxResult.error("请选择需要合并的辅助子集");
            }

            // 记录请求参数
            System.out.println("=== 合并预训练子集请求参数 ===");
            System.out.println("任务数据集ID: " + request.getTaskDatasetId());
            System.out.println("辅助子集ID: " + request.getSelectedAuxiliarySubsetIds());
            System.out.println("用户名: " + request.getUsername());
            System.out.println("标签映射: " + (request.getLabelMapping() != null ? request.getLabelMapping().size() : 0));

            // 调用服务层
            Map<String, Object> result = taskDatasetService.updatePretrainSubsets(request);

            if (result.containsKey("error")) {
                return AjaxResult.error(result.get("error").toString());
            }

            // 使用单参数success方法，将消息和数据合并到一个对象中
            Map<String, Object> response = new HashMap<>();
            response.put("message", "预训练子集合并成功");
            response.put("data", result);
            return AjaxResult.success(response);
        } catch (IllegalArgumentException e) {
            return AjaxResult.error(e.getMessage());
        } catch (IOException e) {
            return AjaxResult.error("合并预训练子集失败: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return AjaxResult.error("系统错误: " + e.getMessage());
        }
    }

    @Operation(summary = "获取最新任务数据集", description = "获取最新的任务数据集信息")
    @GetMapping("/latest")
    public AjaxResult getLatestTaskDataset() {
        try {
            TaskDataset latestDataset = taskDatasetService.getLatestTaskDataset();
            if (latestDataset == null) {
                return AjaxResult.success("暂无任务数据集");
            }
            return AjaxResult.success(latestDataset);
        } catch (Exception e) {
            return AjaxResult.error("获取最新任务数据集失败: " + e.getMessage());
        }
    }

    @Operation(summary = "根据ID获取任务数据集", description = "根据ID获取任务数据集详情")
    @GetMapping("/{id}")
    public AjaxResult getTaskDatasetById(@PathVariable Long id) {
        try {
            TaskDataset taskDataset = taskDatasetService.getById(id);
            if (taskDataset == null) {
                return AjaxResult.error("任务数据集不存在");
            }
            return AjaxResult.success(taskDataset);
        } catch (Exception e) {
            return AjaxResult.error("获取任务数据集失败: " + e.getMessage());
        }
    }

    @Operation(summary = "删除任务数据集", description = "根据ID删除任务数据集")
    @DeleteMapping("/{id}")
    public AjaxResult deleteTaskDataset(@PathVariable Long id) {
        try {
            boolean success = taskDatasetService.removeById(id);
            if (success) {
                return AjaxResult.success("删除成功");
            } else {
                return AjaxResult.error("删除失败，任务数据集可能不存在");
            }
        } catch (Exception e) {
            return AjaxResult.error("删除失败: " + e.getMessage());
        }
    }

    // ===================== 新增：子集图片预览 =====================

    @Operation(summary = "预览任务数据集子集图片", description = "按类别返回若干示例图片 URL")
    @GetMapping("/{id}/subset-preview")
    public AjaxResult previewSubset(@PathVariable Long id,
                                    @RequestParam("subset") String subset,
                                    @RequestParam(value = "perLabel", defaultValue = "3") Integer perLabel,
                                    HttpServletRequest request) {
        try {
            String baseUrl = buildBaseUrl(request);
            return taskDatasetService.previewSubset(id, subset, perLabel, baseUrl);
        } catch (Exception e) {
            return AjaxResult.error("获取子集预览失败: " + e.getMessage());
        }
    }

    @Operation(summary = "任务数据集子集图片直出", description = "根据 subset 和图片名输出二进制图片")
    @GetMapping("/{id}/subset-image")
    public void subsetImage(@PathVariable Long id,
                            @RequestParam("subset") String subset,
                            @RequestParam("img") String imgName,
                            HttpServletResponse response) throws IOException {
        taskDatasetService.streamSubsetImage(id, subset, imgName, response);
    }

    /** 生成当前请求的基础地址（支持反向代理） */
    private String buildBaseUrl(HttpServletRequest req) {
        String scheme = firstNonBlank(req.getHeader("X-Forwarded-Proto"), req.getScheme());
        String host   = firstNonBlank(req.getHeader("X-Forwarded-Host"),  req.getHeader("Host"));
        String portH  = req.getHeader("X-Forwarded-Port");
        String prefix = Optional.ofNullable(req.getHeader("X-Forwarded-Prefix")).orElse("");

        if (host == null || host.isBlank()) {
            host = req.getServerName();
            int port = req.getServerPort();
            if (!("http".equalsIgnoreCase(scheme) && port == 80) &&
                    !("https".equalsIgnoreCase(scheme) && port == 443)) {
                host = host + ":" + port;
            }
        } else if (!host.contains(":")) {
            if (portH != null && !portH.isBlank()) {
                if (!("http".equalsIgnoreCase(scheme) && "80".equals(portH)) &&
                        !("https".equalsIgnoreCase(scheme) && "443".equals(portH))) {
                    host = host + ":" + portH;
                }
            }
        }

        if (!prefix.isEmpty() && prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return scheme + "://" + host + prefix;
    }

    private String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    /**
     * 子集图片的 DOTA 标注查询接口
     * 前端调用：GET /taskDataset/{id}/subset-objects?subset=core&img=1
     *
     * subset: core / sup
     * img   : 前端 URL 里的 img 参数（例如 1、10、100 或 1.jpg）
     */
    @GetMapping("/{id}/subset-objects")
    public AjaxResult subsetObjects(@PathVariable("id") Long id,
                                    @RequestParam("subset") String subset,
                                    @RequestParam("img") String img) {
        return taskDatasetService.getSubsetDotaObjects(id, subset, img);
    }
}
