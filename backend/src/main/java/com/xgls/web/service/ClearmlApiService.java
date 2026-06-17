package com.xgls.web.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.xgls.web.base.AjaxResult;
import com.xgls.web.config.ClearmlProperties;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ClearML REST：Basic（access:secret）→ JWT → tasks.get_all。
 *
 * <p>Secret 策略（依次尝试）：配置的 secret → 空密码（仅 Access Key）→ 将 Access Key 再当作 Secret（少数环境容错）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClearmlApiService {

    private static final String UA = "AI-TT-Platform-ClearML/1.0";

    private final ClearmlProperties props;

    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /** JWT 缓存（内存）；失效或 401 时刷新 */
    private volatile String cachedJwt;

    private volatile long jwtExpiresAtMs;

    private static final long JWT_CACHE_MS = 45 * 60 * 1000L;

    public Map<String, Object> statusSummary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", props.isEnabled());
        m.put("configured", StrUtil.isNotBlank(props.getAccessKey()));
        m.put("apiHost", props.getApiHost());
        m.put("webHost", props.getWebHost());
        m.put("apiVersion", props.getApiVersion());
        return m;
    }

    /**
     * 验证 auth.login；成功后写入 JWT 缓存，便于紧接着调用 tasks 列表不再重复登录。
     */
    public AjaxResult probeAuth() {
        if (!props.isEnabled()) {
            return AjaxResult.error("ClearML 未启用（sys.clearml.enabled=false）");
        }
        if (StrUtil.isBlank(props.getAccessKey())) {
            return AjaxResult.error("未配置 CLEARML_API_ACCESS_KEY / sys.clearml.access-key");
        }
        try {
            String jwt = loginFreshJwt();
            synchronized (this) {
                cachedJwt = jwt;
                jwtExpiresAtMs = System.currentTimeMillis() + JWT_CACHE_MS;
            }
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("loginOk", true);
            ok.put(
                    "hint",
                    "REST auth.login 已成功。若列表仍为空，说明当前没有 status=in_progress 的任务。");
            return AjaxResult.success(ok);
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("loginOk", false);
            err.put(
                    "hint",
                    "请在工作区点击「+ Create new credentials」，弹窗会同时给出 Access Key 与 Secret Key（Secret 通常只显示一次），填入 env 后再试。");
            err.put("detail", e.getMessage());
            return AjaxResult.error(e.getMessage(), err);
        }
    }

    /** 依次尝试的 Secret 列表（含空串与 access 副本）。 */
    private List<String> secretStrategies() {
        String access = props.getAccessKey();
        List<String> tries = new ArrayList<>();
        String cfg = StrUtil.nullToEmpty(props.getSecretKey());
        if (StrUtil.isNotBlank(cfg)) {
            tries.add(cfg);
        }
        tries.add("");
        if (!access.equals(cfg)) {
            tries.add(access);
        }
        return tries;
    }

    public AjaxResult fetchActiveTasks(Map<String, Object> body) {
        if (!props.isEnabled()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("tasks", List.of());
            empty.put(
                    "hint",
                    "ClearML 未启用：请设置 CLEARML_ENABLED=true 或 sys.clearml.enabled=true。");
            return AjaxResult.success(empty);
        }
        if (StrUtil.isBlank(props.getAccessKey())) {
            return AjaxResult.error("ClearML 未配置访问密钥（CLEARML_API_ACCESS_KEY）");
        }
        int pageSize = props.getActiveTasksPageSize();
        if (body != null && body.get("page_size") instanceof Number n) {
            pageSize = Math.min(200, Math.max(1, n.intValue()));
        }
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("status", List.of("in_progress"));
        req.put("page_size", pageSize);
        req.put("page", 0);
        try {
            String jsonBody = JSONUtil.toJsonStr(req);
            String resp = postApi("tasks.get_all", jsonBody, true);
            JSONObject root = JSONUtil.parseObj(resp);
            ensureMetaOk(root);
            JSONArray tasksArr = extractTasksArray(root);
            List<Map<String, Object>> out = new ArrayList<>();
            String filter = StrUtil.trimToEmpty(props.getProjectNameContains());
            for (int i = 0; i < tasksArr.size(); i++) {
                JSONObject t = tasksArr.getJSONObject(i);
                if (t == null) {
                    continue;
                }
                String projectName = resolveProjectName(t);
                if (StrUtil.isNotBlank(filter)
                        && (projectName == null || !projectName.contains(filter))) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                String id = t.getStr("id");
                row.put("id", id);
                row.put("name", t.getStr("name"));
                row.put("projectName", projectName);
                row.put("projectId", resolveProjectId(t));
                row.put("status", String.valueOf(t.get("status")));
                row.put("consoleUrl", buildConsoleUrl(id, resolveProjectId(t)));
                out.add(row);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tasks", out);
            data.put("filteredByProject", StrUtil.isNotBlank(filter) ? filter : null);
            if (out.isEmpty()) {
                data.put(
                        "hint",
                        "此处仅列出 ClearML 上 status=in_progress 的任务；训练若已结束则列表为空。"
                                + " 请到 "
                                + StrUtil.blankToDefault(props.getWebHost(), "https://app.clear.ml")
                                + " 打开对应项目（默认 AI-TT-Platform）查看历史实验。"
                                + " 若从未配置密钥，请填写 backend/env.clearml.local 并重启后端。");
            }
            return AjaxResult.success(data);
        } catch (Exception e) {
            log.warn("ClearML tasks.get_all failed: {}", e.getMessage());
            return AjaxResult.error("ClearML 请求失败: " + e.getMessage());
        }
    }

    private static JSONArray extractTasksArray(JSONObject root) {
        if (root == null) {
            return new JSONArray();
        }
        JSONObject data = root.getJSONObject("data");
        if (data != null && data.containsKey("tasks")) {
            return data.getJSONArray("tasks");
        }
        return root.getJSONArray("tasks");
    }

    private static void ensureMetaOk(JSONObject root) {
        JSONObject meta = root.getJSONObject("meta");
        if (meta == null) {
            return;
        }
        int code = meta.getInt("result_code", 200);
        if (code != 200) {
            throw new IllegalStateException(meta.getStr("result_msg", "ClearML API error " + code));
        }
    }

    private String resolveProjectId(JSONObject task) {
        Object proj = task.get("project");
        if (proj instanceof JSONObject jo) {
            return jo.getStr("id");
        }
        if (proj instanceof String s) {
            return s;
        }
        return task.getStr("project");
    }

    private String resolveProjectName(JSONObject task) {
        Object proj = task.get("project");
        if (proj instanceof JSONObject jo) {
            return jo.getStr("name");
        }
        return null;
    }

    private String buildConsoleUrl(String taskId, String projectId) {
        String web = trimTrailingSlash(props.getWebHost());
        if (StrUtil.isNotBlank(projectId)) {
            return web + "/projects/" + projectId + "/tasks/" + taskId;
        }
        return web + "/tasks/" + taskId + "/execution";
    }

    private static String trimTrailingSlash(String s) {
        if (s == null) {
            return "";
        }
        String x = s.trim();
        while (x.endsWith("/")) {
            x = x.substring(0, x.length() - 1);
        }
        return x;
    }

    private String postApi(String action, String jsonBody, boolean allowRetry401) throws Exception {
        String jwt = getJwt();
        String url = buildActionUrl(action);
        HttpRequest.Builder b =
                jsonPost(url, jsonBody)
                        .header("Authorization", "Bearer " + jwt);
        HttpResponse<String> resp =
                httpClient.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() == 401 && allowRetry401) {
            synchronized (this) {
                cachedJwt = null;
                jwtExpiresAtMs = 0;
            }
            jwt = getJwt();
            b = jsonPost(url, jsonBody).header("Authorization", "Bearer " + jwt);
            resp = httpClient.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
        String body = resp.body();
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException(
                    "tasks "
                            + action
                            + " HTTP "
                            + resp.statusCode()
                            + ": "
                            + summarizeClearmlBody(body));
        }
        return body;
    }

    private static HttpRequest.Builder jsonPost(String url, String jsonBody) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .header("User-Agent", UA)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
    }

    private String getJwt() throws Exception {
        String jwt = cachedJwt;
        if (jwt != null && System.currentTimeMillis() < jwtExpiresAtMs - 60_000L) {
            return jwt;
        }
        synchronized (this) {
            jwt = cachedJwt;
            if (jwt != null && System.currentTimeMillis() < jwtExpiresAtMs - 60_000L) {
                return jwt;
            }
            jwt = loginFreshJwt();
            cachedJwt = jwt;
            jwtExpiresAtMs = System.currentTimeMillis() + JWT_CACHE_MS;
            return jwt;
        }
    }

    /** 换新 JWT（多种 Secret 策略）。 */
    private String loginFreshJwt() throws Exception {
        Exception last = null;
        int idx = 0;
        for (String secret : secretStrategies()) {
            idx++;
            try {
                return executeLogin(secret);
            } catch (Exception e) {
                last = e;
                log.warn("ClearML auth.login strategy #{} failed: {}", idx, e.getMessage());
            }
        }
        if (last != null) {
            throw last;
        }
        throw new IllegalStateException("ClearML auth.login 失败：未尝试任何策略");
    }

    private String executeLogin(String secret) throws Exception {
        String loginUrl = buildActionUrl("auth.login");
        String access = props.getAccessKey();
        String basic =
                Base64.getEncoder()
                        .encodeToString(
                                (access + ":" + secret).getBytes(StandardCharsets.UTF_8));
        HttpRequest req =
                HttpRequest.newBuilder()
                        .uri(URI.create(loginUrl))
                        .timeout(Duration.ofSeconds(30))
                        .header("Authorization", "Basic " + basic)
                        .header("Content-Type", "application/json")
                        .header("User-Agent", UA)
                        .POST(HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8))
                        .build();
        HttpResponse<String> resp =
                httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = resp.body();
        JSONObject root;
        try {
            root = JSONUtil.parseObj(body);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "auth.login 响应非 JSON，HTTP "
                            + resp.statusCode()
                            + ": "
                            + StrUtil.maxLength(body, 280));
        }
        JSONObject meta = root.getJSONObject("meta");
        if (meta != null) {
            int rc = meta.getInt("result_code", resp.statusCode() == 200 ? 200 : -1);
            if (rc != 200) {
                throw new IllegalStateException(
                        meta.getStr("result_msg", "ClearML result_code=" + rc));
            }
        } else if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException(
                    "auth.login HTTP " + resp.statusCode() + ": " + StrUtil.maxLength(body, 400));
        }
        String token = root.getByPath("data.token", String.class);
        if (StrUtil.isBlank(token)) {
            throw new IllegalStateException(
                    "auth.login 缺少 data.token：" + summarizeClearmlBody(body));
        }
        return token;
    }

    private static String summarizeClearmlBody(String body) {
        try {
            JSONObject o = JSONUtil.parseObj(body);
            JSONObject meta = o.getJSONObject("meta");
            if (meta != null) {
                int rc = meta.getInt("result_code", -1);
                String msg = meta.getStr("result_msg");
                return "meta result_code=" + rc + " msg=" + msg;
            }
        } catch (Exception ignored) {
        }
        return StrUtil.maxLength(body, 400);
    }

    private String buildActionUrl(String action) {
        String host = trimTrailingSlash(props.getApiHost());
        String ver = props.getApiVersion().trim();
        if (StrUtil.isBlank(ver)) {
            return host + "/" + action;
        }
        // 托管 SaaS（api.clear.ml）需使用 /v2.23/ 形式；错误使用 /v2_23/ 会返回纯文本 401「Missing authentication headers」且无法解析 JSON
        String seg = (ver.startsWith("v") || ver.startsWith("V")) ? ver : "v" + ver;
        seg = seg.replace('_', '.');
        return host + "/" + seg + "/" + action;
    }
}
