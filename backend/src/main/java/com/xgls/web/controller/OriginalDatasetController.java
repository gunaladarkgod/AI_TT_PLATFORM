package com.xgls.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xgls.web.base.AjaxResult;
import com.xgls.web.entity.OriginalDataset;
import com.xgls.web.service.OriginalDatasetService;
import com.xgls.web.vo.dataset.MarkSubsetsReq;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

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
