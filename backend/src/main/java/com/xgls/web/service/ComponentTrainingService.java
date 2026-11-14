package com.xgls.web.service;

import cn.hutool.json.*;
import com.xgls.web.utils.WorkspacePathUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Reviewed platform adapters. Downloaded repository contents never define commands. */
@Service
public class ComponentTrainingService {
    private final AlgorithmComponentService components;
    private final Path root;
    @Autowired
    public ComponentTrainingService(AlgorithmComponentService components) {
        this(components, WorkspacePathUtil.workspaceRoot());
    }
    ComponentTrainingService(AlgorithmComponentService components, Path root) {
        this.components = components; this.root = root.toAbsolutePath().normalize();
    }
    public List<JSONObject> list(String direction) {
        try {
            List<JSONObject> result = new ArrayList<>();
            var adapters = JSONUtil.parseArray(Files.readString(root.resolve("research-registry/training-adapters.json")));
            for (String kind : List.of("baseline", "algorithm")) {
                Object rows = components.list(kind, direction).get("components");
                if (!(rows instanceof Collection<?> entries)) continue;
                for (Object entry : entries) {
                    JSONObject component = JSONUtil.parseObj(entry);
                    if (!"downloaded".equals(component.getStr("localStatus"))) continue;
                    JSONObject adapter = null;
                    for (Object candidate : adapters) {
                        JSONObject definition = JSONUtil.parseObj(candidate);
                        if (kind.equals(definition.getStr("kind")) && component.getStr("id").equals(definition.getStr("id"))) { adapter = definition; break; }
                    }
                    if (adapter == null) {
                        JSONObject unavailable = new JSONObject(component);
                        unavailable.set("name", component.getStr("name") + ("baseline".equals(kind) ? "（baseline）" : ""));
                        unavailable.set("valid", false); unavailable.set("blockers", List.of("尚未配置审核后的运行适配器"));
                        unavailable.set("data_format", component.getStr("dataFormat"));
                        result.add(unavailable); continue;
                    }
                    Path path = root.resolve(component.getStr("path")).normalize();
                    List<String> errors = new ArrayList<>();
                    if (!path.toRealPath().startsWith(root.resolve("local-components").toRealPath())) errors.add("组件路径超出许可目录");
                    if (!Files.isRegularFile(path.resolve("ultralytics/__init__.py"))) errors.add("缺少算法源码");
                    if (adapter.getStr("model").endsWith(".yaml") && !Files.isRegularFile(path.resolve(adapter.getStr("model")))) errors.add("缺少模型配置");
                    String commit = revision(path);
                    Process diff = new ProcessBuilder("git", "-c", "safe.directory=" + path.toString().replace("\\", "/"), "diff", "--quiet", "HEAD", "--").directory(path.toFile()).start();
                    if (!diff.waitFor(5, TimeUnit.SECONDS)) { diff.destroyForcibly(); errors.add("组件变更检查超时"); }
                    else if (diff.exitValue() != 0) errors.add("组件含未提交源码变更，请先提交后使用");
                    if (commit.isBlank()) errors.add("无法读取组件版本");
                    adapter.set("component_commit", commit);
                    adapter.set("adapter_digest", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                            .digest(Files.readAllBytes(root.resolve("research-registry/training-adapters.json")))));
                    adapter.set("valid", errors.isEmpty());
                    adapter.set("blockers", errors);
                    result.add(adapter);
                }
            }
            return result;
        } catch (Exception e) { throw new IllegalArgumentException("训练方案读取失败：" + e.getMessage(), e); }
    }
    public JSONObject require(String id, String direction) {
        JSONObject item = list(direction).stream().filter(row -> id.equals(row.getStr("id"))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("方案尚未下载或不属于所选方向，请刷新算法列表"));
        if (!item.getBool("valid", false)) throw new IllegalArgumentException("方案不可用：" + item.get("blockers"));
        return item;
    }
    public void validateParameters(JSONObject adapter, JSONObject params) {
        JSONObject values = params.getJSONObject("ultralytics_parameters");
        if (values == null) values = new JSONObject();
        Set<String> keys = new HashSet<>();
        for (Object raw : adapter.getJSONArray("parameters")) {
            JSONObject rule = JSONUtil.parseObj(raw);
            String key = rule.getStr("key"); keys.add(key);
            Object value = values.getOrDefault(key, rule.get("default"));
            String type = rule.getStr("type");
            if ("integer".equals(type) || "number".equals(type)) {
                if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                    || number.doubleValue() < rule.getDouble("min") || number.doubleValue() > rule.getDouble("max")
                    || ("integer".equals(type) && number.doubleValue() != Math.rint(number.doubleValue())))
                    throw new IllegalArgumentException("训练参数类型或范围错误：" + key);
            } else if ("boolean".equals(type) && !(value instanceof Boolean)
                    || "string".equals(type) && (!(value instanceof String) || ((String)value).length() > 100)) {
                throw new IllegalArgumentException("训练参数类型错误：" + key);
            }
            values.set(key, value);
        }
        if (!keys.containsAll(values.keySet())) throw new IllegalArgumentException("包含未声明的训练参数");
        params.set("ultralytics_parameters", values);
    }
    private String revision(Path path) throws Exception {
        Process p = new ProcessBuilder("git", "-c", "safe.directory=" + path.toString().replace("\\", "/"), "rev-parse", "HEAD").directory(path.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        if (!p.waitFor(5, TimeUnit.SECONDS)) { p.destroyForcibly(); return ""; }
        return p.exitValue() == 0 ? new String(p.getInputStream().readAllBytes()).trim() : "";
    }
}
