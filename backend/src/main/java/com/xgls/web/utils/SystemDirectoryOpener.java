package com.xgls.web.utils;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** 平台内所有“打开路径”操作共用的系统文件浏览器启动器。 */
public final class SystemDirectoryOpener {
    private SystemDirectoryOpener() {
    }

    public static Path openDirectory(Path directory) throws IOException {
        Path normalized = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            throw new IOException("目录不存在: " + normalized);
        }
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                && !GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            try {
                Desktop.getDesktop().open(normalized.toFile());
                return normalized;
            } catch (IOException | UnsupportedOperationException ignored) {
                // Windows 下 Desktop.open 不可用时继续使用 Explorer。
            }
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        ProcessBuilder builder;
        if (os.contains("win")) {
            // ProcessBuilder 直接创建 explorer.exe 在隐藏的 Java 后端中可能不会激活已有资源管理器。
            // 用 Windows ShellExecute（PowerShell Start-Process）打开，和用户手动双击文件夹一致。
            String escapedPath = normalized.toString().replace("'", "''");
            String command = "Start-Process -FilePath 'explorer.exe' -ArgumentList @('/e,', '" + escapedPath
                    + "') -ErrorAction Stop";
            builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", command);
        } else if (os.contains("mac")) {
            builder = new ProcessBuilder("open", normalized.toString());
        } else {
            builder = new ProcessBuilder("xdg-open", normalized.toString());
        }
        try {
            Process process = builder.redirectErrorStream(true).start();
            if (os.contains("win")) {
                boolean completed = process.waitFor(8, TimeUnit.SECONDS);
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (!completed) {
                    process.destroyForcibly();
                    throw new IOException("Windows 文件浏览器启动超时；目录=" + normalized + "；命令=" + commandForLog(builder));
                }
                if (process.exitValue() != 0) {
                    throw new IOException("Windows 文件浏览器启动失败；退出码=" + process.exitValue()
                            + "；目录=" + normalized + "；命令=" + commandForLog(builder)
                            + (output.isBlank() ? "" : "；系统输出=" + output));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("等待系统文件浏览器返回时被中断；目录=" + normalized, e);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("调用系统文件浏览器异常；目录=" + normalized + "；命令=" + commandForLog(builder)
                    + "；原因=" + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
        return normalized;
    }

    private static String commandForLog(ProcessBuilder builder) {
        return String.join(" ", builder.command());
    }
}
