package com.xgls.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xgls.web.base.AjaxResult;
import com.xgls.web.entity.OriginalDataset;
import com.xgls.web.service.OriginalDatasetService;
import com.xgls.web.vo.dataset.MarkSubsetsReq;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/original-dataset")
public class OriginalDatasetController {

    private final OriginalDatasetService originalDatasetService;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 分页列表
     */
    @GetMapping
    public AjaxResult page(@RequestParam(required = false) Long projectId,
                           @RequestParam(required = false) Integer dataFormat,
                           @RequestParam(required = false) String keyword,
                           @RequestParam(defaultValue = "1") Integer page,
                           @RequestParam(defaultValue = "20") Integer size,
                           @RequestParam(defaultValue = "created_time") String sortBy,
                           @RequestParam(defaultValue = "desc") String order) {

        boolean desc = !"asc".equalsIgnoreCase(order);
        Page<OriginalDataset> p = originalDatasetService.pageList(
                projectId, dataFormat, keyword, page, size, sortBy, desc);

        Map<String, Object> payload = new HashMap<>();
        payload.put("items", p.getRecords());
        payload.put("total", p.getTotal());
        payload.put("page", p.getCurrent());
        payload.put("size", p.getSize());

        return AjaxResult.success(payload);
    }

    /** 详情 */
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable Long id) {
        OriginalDataset od = originalDatasetService.getById(id);
        return AjaxResult.success(od);
    }

    /** 不分页的全部（可选过滤） */
    @GetMapping("/all")
    public AjaxResult all(@RequestParam(required = false) Long projectId,
                          @RequestParam(required = false) Integer dataFormat) {
        return AjaxResult.success(originalDatasetService.listAll(projectId, dataFormat));
    }

    /** 外部导入记录列表（用于“原始数据集页”合并展示） */
    @GetMapping("/external")
    public AjaxResult listExternal() {
        return AjaxResult.success(originalDatasetService.listExternalDatasets());
    }

    /** 浏览可导入目录（文件浏览器） */
    @GetMapping("/external/browse")
    public AjaxResult browseExternal(@RequestParam(required = false) String base) {
        return originalDatasetService.browseExternalDirs(base);
    }

    /** 仅验证外部数据集路径，不写入记录 */
    @PostMapping("/external/validate")
    public AjaxResult validateExternal(@RequestBody Map<String, String> req) {
        String path = req != null ? req.get("path") : null;
        return originalDatasetService.validateExternalDatasetPath(path);
    }

    /** 本机目录选择器（同机部署场景） */
    @PostMapping("/external/pick-dir")
    public AjaxResult pickExternalDir() {
        return originalDatasetService.pickLocalDirectory();
    }

    /** 导入外部数据集（写入注册表） */
    @PostMapping("/external/import")
    public AjaxResult importExternal(@RequestBody Map<String, String> req) {
        String name = req != null ? req.get("name") : null;
        String path = req != null ? req.get("path") : null;
        return originalDatasetService.importExternalDataset(name, path);
    }

    /** 删除外部导入记录（仅删除记录，不删物理文件） */
    @PostMapping("/external/delete")
    public AjaxResult deleteExternal(@RequestBody Map<String, String> req) {
        String name = req != null ? req.get("name") : null;
        String path = req != null ? req.get("path") : null;
        return originalDatasetService.deleteExternalDatasetRecord(name, path);
    }

    /** 导入外部数据集（el 测试版：本地目录上传） */
    @PostMapping(value = "/external/import-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AjaxResult importExternalUpload(@RequestParam("name") String name,
                                           @RequestParam("files") List<MultipartFile> files,
                                           @RequestParam(value = "relPaths", required = false) List<String> relPaths) {
        return originalDatasetService.importExternalDatasetByUpload(name, files, relPaths);
    }

    /** 外部导入图片直出：基于传入数据集 path + img 相对路径 */
    @GetMapping("/external/image")
    public void externalImage(@RequestParam("path") String datasetPath,
                              @RequestParam("img") String relImgPath,
                              HttpServletResponse response) throws IOException {
        originalDatasetService.streamExternalImage(datasetPath, relImgPath, response);
    }

    /** 随机样例（默认 3 张，可排除上一批） */
    @PostMapping("/sample-random")
    public AjaxResult sampleRandom(@RequestBody Map<String, Object> req,
                                   HttpServletRequest request) {
        Long id = null;
        Object idObj = req != null ? req.get("id") : null;
        if (idObj instanceof Number) {
            id = ((Number) idObj).longValue();
        } else if (idObj != null) {
            try {
                id = Long.parseLong(String.valueOf(idObj));
            } catch (Exception ignore) {}
        }
        boolean isExternal = false;
        Object extObj = req != null ? req.get("isExternal") : null;
        if (extObj instanceof Boolean) {
            isExternal = (Boolean) extObj;
        } else if (extObj != null) {
            isExternal = "true".equalsIgnoreCase(String.valueOf(extObj));
        }
        String path = req != null ? String.valueOf(req.getOrDefault("path", "")) : "";

        List<String> exclude = new ArrayList<>();
        Object exObj = req != null ? req.get("exclude") : null;
        if (exObj instanceof List<?>) {
            for (Object o : (List<?>) exObj) {
                if (o != null) exclude.add(String.valueOf(o));
            }
        }
        return originalDatasetService.randomSampleImages(id, isExternal, path, exclude, 3, buildBaseUrl(request));
    }

    /* ====================== 批量标记子集 ====================== */
    @PostMapping("/mark-subsets")
    public AjaxResult markSubsets(@RequestBody MarkSubsetsReq req) {
        return originalDatasetService.markAndMaterializeTaskDataset(req);
    }

    /**
     * 预览：把 images 里的相对路径统一改为绝对 URL
     * 返回：{ code, msg, data: { datasetId, perLabel, items:[{label, images[], count}, ...] } }
     */
    @GetMapping("/{id}/preview")
    public AjaxResult preview(@PathVariable Long id,
                              @RequestParam(defaultValue = "3") Integer perLabel,
                              HttpServletRequest request) {
        AjaxResult res = originalDatasetService.previewSamples(id, perLabel);

        Object dataObj = res != null ? res.getData() : null;
        if (!(dataObj instanceof Map)) {
            // 保底：无法识别结构时直接返回原始结果，至少编译/运行不出错
            return res != null ? res : AjaxResult.success(
                    Map.of("datasetId", id, "perLabel", perLabel, "items", List.of())
            );
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) dataObj;

        // 处理 items -> 绝对 URL
        Object itemsObj = data.get("items");
        if (itemsObj instanceof List) {
            String base = buildBaseUrl(request);
            List<?> srcList = (List<?>) itemsObj;
            List<Map<String, Object>> fixed = new ArrayList<>(srcList.size());

            for (Object o : srcList) {
                if (!(o instanceof Map)) continue;

                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) o;
                Map<String, Object> row = new LinkedHashMap<>();

                Object label = m.getOrDefault("label", m.getOrDefault("className", m.get("name")));
                row.put("label", label != null ? label.toString() : "unknown");

                List<String> absUrls = new ArrayList<>();
                Object imgs = m.get("images");
                if (imgs instanceof List) {
                    for (Object u : (List<?>) imgs) {
                        if (u != null) absUrls.add(toAbsoluteUrl(base, u.toString()));
                    }
                }
                // 兼容 urls 字段
                Object urls = m.get("urls");
                if (absUrls.isEmpty() && urls instanceof List) {
                    for (Object u : (List<?>) urls) {
                        if (u != null) absUrls.add(toAbsoluteUrl(base, u.toString()));
                    }
                }
                row.put("images", absUrls);

                Object cnt = m.get("count");
                row.put("count", (cnt instanceof Number) ? ((Number) cnt).intValue() : absUrls.size());

                fixed.add(row);
            }
            data.put("items", fixed);
        }

        data.put("datasetId", id);
        data.put("perLabel", perLabel);

        // 重新包装成成功返回（避免直接操作原 AjaxResult 的内部结构）
        return AjaxResult.success(data);
    }

    /** 预览图片二进制直出：基于 original_dataset.data_path + img_name */
    @GetMapping("/{id}/image")
    public void previewImage(@PathVariable Long id,
                             @RequestParam("img") String imgName,
                             HttpServletResponse response) throws IOException {
        originalDatasetService.streamImage(id, imgName, response);
    }

    /* ====================== 工具方法：拼基础地址 & 绝对化 ====================== */

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

    /** 把相对路径转绝对；已是 http(s) 则原样返回 */
    private String toAbsoluteUrl(String base, String url) {
        if (url == null || url.isBlank()) return url;
        String low = url.toLowerCase(Locale.ROOT);
        if (low.startsWith("http://") || low.startsWith("https://")) return url;
        if (!url.startsWith("/")) url = "/" + url;
        return base + url;
    }
    @GetMapping("/{id}/objects")
    public AjaxResult objects(@PathVariable Long id,
                              @RequestParam("img") String imgName) {
        return originalDatasetService.getDotaObjects(id, imgName);
    }
}
