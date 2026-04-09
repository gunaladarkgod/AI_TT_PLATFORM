package com.xgls.web.utils;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MmdetConfigUtil {
    private MmdetConfigUtil() {}

    // =============== FS helpers ===============
    public static Path ensureRunDir(String rootPath, String runId) throws IOException {
        Path dir = Paths.get(rootPath, "modelcfg", runId);
        Files.createDirectories(dir);
        return dir;
    }

    public static String readString(MultipartFile f) throws IOException {
        return new String(f.getBytes(), StandardCharsets.UTF_8);
    }

    public static String readString(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    public static Path writeTextFile(Path dir, String fileName, String content) throws IOException {
        Path p = dir.resolve(fileName);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return p.toAbsolutePath();
    }

    // =============== Python literal helpers ===============
    public static String toPyRawLiteral(Path path) {
        String s = path.toString().replace("\\", "/").replace("'", "\\'");
        return "r'" + s + "'";
    }

    public static String toPyQuotedPath(String path) {
        String s = path.replace("\\", "/").replace("'", "\\'");
        return "'" + s + "'";
    }

    // =============== Regex helpers ===============
    public static String replaceFirst(String src, String regex, String replacement) {
        return src.replaceFirst(regex, replacement);
    }

    public static String replaceAll(String src, String regex, String replacement) {
        return src.replaceAll(regex, replacement);
    }

    /** 将第 orderIndex 个 key=xxx 替换为指定值（按出现顺序） */
    public static String replaceAssignmentByOrder(String src, String key, String replacement, int orderIndex) {
        Pattern p = Pattern.compile("(\\b" + Pattern.quote(key) + "\\s*=\\s*)([^,\\n]+)");
        Matcher m = p.matcher(src);
        StringBuffer sb = new StringBuffer();
        int idx = 0;
        while (m.find()) {
            if (idx == orderIndex) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + replacement));
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            }
            idx++;
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static String replaceQuotedAssignment(String src, String key, String quotedValue) {
        String regex = "(\\b" + Pattern.quote(key) + "\\s*=\\s*)'[^']*'";
        return src.replaceFirst(regex, "$1" + Matcher.quoteReplacement("'" + quotedValue.replace("'", "\\'") + "'"));
    }

    // =============== cfgFile.txt 解析/路径工具 ===============
    public static Map<String, String> parseCfgFileText(String txt) {
        Map<String, String> map = new HashMap<>();
        Pattern p = Pattern.compile("^\\s*([a-zA-Z0-9_]+)\\s*=\\s*([\"']?)(.*?)\\2\\s*$");
        String[] lines = txt.split("\\R");
        for (String line : lines) {
            Matcher m = p.matcher(line.trim());
            if (m.find()) {
                map.put(m.group(1).trim(), m.group(3).trim());
            }
        }
        return map;
    }

    /** 去注释/去引号/统一分隔符为 / */
    public static String sanitizePathValue(String v) {
        if (v == null) return "";
        String s = v.trim();
        int hash = s.indexOf('#');
        if (hash >= 0) s = s.substring(0, hash).trim();
        if ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\""))) {
            if (s.length() >= 2) s = s.substring(1, s.length() - 1);
        }
        return s.replace("\\", "/").trim();
    }

    public static boolean isAbsoluteLike(String p) {
        if (p == null) return false;
        String s = p.replace("\\", "/");
        return s.matches("^[A-Za-z]:/.*") || s.startsWith("/") || s.startsWith("\\\\");
    }

    // =============== runtime.py 细节处理 ===============
    public static Optional<Integer> readRuntimeCheckpointInterval(String runtimeTxt) {
        Matcher m = Pattern.compile("(?s)checkpoint\\s*=\\s*dict\\(.*?\\binterval\\s*=\\s*(\\d+)").matcher(runtimeTxt);
        if (m.find()) return Optional.of(Integer.parseInt(m.group(1)));
        return Optional.empty();
    }

    public static String forceSingleInterval(String txt, int interval) {
        txt = txt.replaceAll("(?s)(checkpoint\\s*=\\s*dict\\([^\\)]*?)\\binterval\\s*=\\s*[^,\\)]+\\s*,?\\s*", "$1");
        txt = txt.replaceAll(",\\s*,", ", ").replaceAll("\\(\\s*,", "(").replaceAll(",\\s*\\)", ")");
        return txt.replaceFirst("(?s)(checkpoint\\s*=\\s*dict\\()", "$1interval=" + interval + ", ");
    }

    public static String stripCreateSymlink(String txt) {
        txt = txt.replaceAll("(?s)(checkpoint\\s*=\\s*dict\\([^\\)]*?)\\bcreate_symlink\\s*=\\s*(True|False)\\s*,?\\s*", "$1");
        txt = txt.replaceAll(",\\s*,", ", ").replaceAll("\\(\\s*,", "(").replaceAll(",\\s*\\)", ")");
        return txt;
    }

    public static String forceSingleSaveBest(String txt, String metric) {
        txt = txt.replaceAll("(?s)(checkpoint\\s*=\\s*dict\\([^\\)]*?)\\bsave_best\\s*=\\s*'[^']*'\\s*,?\\s*", "$1");
        txt = txt.replaceAll(",\\s*,", ", ").replaceAll("\\(\\s*,", "(").replaceAll(",\\s*\\)", ")");
        return txt.replaceFirst("(?s)(checkpoint\\s*=\\s*dict\\()", "$1save_best='" + metric.replace("'", "\\'") + "', ");
    }

    // =============== backbone/init_cfg.checkpoint 注入 ===============
    public static String setBackboneInitCheckpoint(String txt, String quotedPath) {
        Pattern pb = Pattern.compile("backbone\\s*=\\s*dict\\(");
        Matcher mb = pb.matcher(txt);
        if (!mb.find()) return txt;

        int dictStart = mb.end() - 1; // at '('
        int i = dictStart + 1, depth = 1;
        boolean inSingle = false, inDouble = false;
        char prev = 0;

        while (i < txt.length()) {
            char c = txt.charAt(i);
            if (c == '\'' && !inDouble && prev != '\\') inSingle = !inSingle;
            else if (c == '"' && !inSingle && prev != '\\') inDouble = !inDouble;
            else if (!inSingle && !inDouble) {
                if (c == '(') depth++;
                else if (c == ')') { depth--; if (depth == 0) break; }
            }
            prev = c; i++;
        }
        if (i >= txt.length()) return txt;

        int dictEnd = i;
        String before = txt.substring(0, dictStart + 1);
        String body   = txt.substring(dictStart + 1, dictEnd);
        String after  = txt.substring(dictEnd);

        Pattern pInit = Pattern.compile("(?s)\\binit_cfg\\s*=\\s*dict\\((.*?)\\)");
        Matcher mi = pInit.matcher(body);
        String newBody;

        if (mi.find()) {
            String iBody = mi.group(1);
            Matcher mc = Pattern.compile("(checkpoint\\s*=\\s*)'[^']*'").matcher(iBody);
            String newIBody;
            if (mc.find()) {
                newIBody = mc.replaceFirst("$1" + Matcher.quoteReplacement(quotedPath));
            } else {
                String trimmed = iBody.trim();
                newIBody = trimmed.isEmpty() ? "checkpoint=" + quotedPath : "checkpoint=" + quotedPath + ", " + iBody;
            }
            String rebuiltInit = "init_cfg=dict(" + newIBody + ")";
            newBody = body.substring(0, mi.start()) + rebuiltInit + body.substring(mi.end());
        } else {
            String trimmed = body.trim();
            String sep = trimmed.isEmpty() ? "" : (trimmed.endsWith(",") ? " " : ", ");
            String injected = "init_cfg=dict(type='Pretrained', checkpoint=" + quotedPath + ")";
            newBody = body + sep + injected;
        }
        return before + newBody + after;
    }

    // =============== 文件复制/合并 ===============
    public static void copyConfigFilesToWorkDir(Path runDir, Path workDirPath) throws IOException {
        String[] configFiles = {"base_model.py", "data_pipeline.py", "train_opt.py", "default_runtime.py", "combined_base.py"};
        for (String file : configFiles) {
            Path source = runDir.resolve(file);
            if (Files.exists(source)) {
                Path target = workDirPath.resolve(file);
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        rewriteCombinedBaseToRelative(workDirPath);
    }

    public static void rewriteCombinedBaseToRelative(Path workDirPath) throws IOException {
        Path combinedBasePath = workDirPath.resolve("combined_base.py");
        if (!Files.exists(combinedBasePath)) return;
        String newContent = "_base_ = [\n" +
                "    './base_model.py',\n" +
                "    './data_pipeline.py',\n" +
                "    './train_opt.py',\n" +
                "    './default_runtime.py',\n" +
                "]\n";
        Files.writeString(combinedBasePath, newContent, StandardCharsets.UTF_8);
    }
    public static String replaceFirstDotAll(String text, String pattern, String replacement) {
        return Pattern.compile(pattern, Pattern.DOTALL).matcher(text).replaceFirst(replacement);
    }

    /**
     * 专为 ConvNeXt 处理：把 backbone 内的 init_cfg 整块覆盖，避免重复 checkpoint。
     * 目标形态：
     *   init_cfg=dict(type='Pretrained', checkpoint=<ckptPy>, prefix='backbone.')
     */
    public static String setConvNeXtCheckpoint(String text, String ckptPy) {
        String desired = "dict(type='Pretrained', checkpoint=" + ckptPy + ", prefix='backbone.')";
        // 1) 如果 backbone 里已有 init_cfg，整块替换
        String replaced = replaceFirstDotAll(
                text,
                "(backbone\\s*=\\s*dict\\([\\s\\S]*?init_cfg\\s*=\\s*)dict\\([\\s\\S]*?\\)", // 捕获到 init_cfg= 的 dict(...)
                "$1" + desired
        );
        if (!replaced.equals(text)) {
            return replaced;
        }
        // 2) 若 backbone 没有 init_cfg，则在 backbone=dict( ... ) 起始处注入
        return replaceFirstDotAll(
                text,
                "(backbone\\s*=\\s*dict\\()",
                "$1init_cfg=" + desired + ", "
        );
    }

}
