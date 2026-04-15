package com.xgls.web.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.xgls.web.entity.InstanceDataset;
import com.xgls.web.entity.InstanceDatasetinfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * 实例数据集（预处理结果 / 源）在磁盘上的路径解析与可用性校验。
 */
public final class InstanceDatasetPathUtil {

    private static final Logger log = LoggerFactory.getLogger(InstanceDatasetPathUtil.class);

    private InstanceDatasetPathUtil() {}

    public record ResolvedInstanceDiskPaths(
            String trainImgPath,
            String testImgPath,
            String trainAnnoPath,
            String testAnnoPath) {}

    /**
     * 与训练打包 {@code buildDatasetCfg} 一致：解析目标实例数据集在磁盘上的四类目录。
     */
    public static ResolvedInstanceDiskPaths resolveTargetOrThrow(InstanceDatasetinfo info, String instanceDataRoot) {
        Optional<ResolvedInstanceDiskPaths> o = tryResolveTarget(info, instanceDataRoot);
        if (o.isPresent()) {
            return o.get();
        }
        Path rootConfigured = Paths.get(instanceDataRoot.trim().replaceAll("/+$", "")).normalize();
        String tImg = normalizeStoredPath(info.getTrainImagePath(), rootConfigured);
        String name = info.getName();
        throw new IllegalStateException(String.format(
                "train_image_path 不是有效目录: %s；在配置根目录 %s 下也未找到 %s 的标准结构（images/train、images/test、annotations/train、annotations/test）。"
                        + " 请确认预处理已成功落盘、数据未被删除或移动，环境变量 INSTANCE_DATA_ROOT 与落盘时一致。",
                tImg, rootConfigured, StrUtil.blankToDefault(name, "(无 name)")));
    }

    public static Optional<ResolvedInstanceDiskPaths> tryResolveTarget(InstanceDatasetinfo info, String instanceDataRoot) {
        Path rootConfigured = Paths.get(instanceDataRoot.trim().replaceAll("/+$", "")).normalize();

        String tImg = normalizeStoredPath(info.getTrainImagePath(), rootConfigured);
        String sImg = normalizeStoredPath(info.getTestImagePath(), rootConfigured);
        String tAnno = normalizeStoredPath(info.getTrainAnnoPath(), rootConfigured);
        String sAnno = normalizeStoredPath(info.getTestAnnoPath(), rootConfigured);

        if (allLayoutDirsExist(tImg, sImg, tAnno, sAnno)) {
            return Optional.of(new ResolvedInstanceDiskPaths(tImg, sImg, tAnno, sAnno));
        }

        String name = info.getName();
        if (StrUtil.isBlank(name)) {
            return Optional.empty();
        }

        Path base = rootConfigured.resolve(name).normalize();
        String aTImg = toPosixPath(base.resolve("images").resolve("train"));
        String aSImg = toPosixPath(base.resolve("images").resolve("test"));
        String aTAnno = toPosixPath(base.resolve("annotations").resolve("train"));
        String aSAnno = toPosixPath(base.resolve("annotations").resolve("test"));

        if (allLayoutDirsExist(aTImg, aSImg, aTAnno, aSAnno)) {
            log.info("[instance-dataset] 库中路径无效，已按 instancedata-root + name 解析: name={}, base={}", name, base);
            return Optional.of(new ResolvedInstanceDiskPaths(aTImg, aSImg, aTAnno, aSAnno));
        }
        return Optional.empty();
    }

    /** MMDet 打包需要非空 JSONObject class_list（与 buildDatasetCfg 一致）。 */
    public static boolean hasMmdetTrainableClassList(String classList) {
        if (StrUtil.isBlank(classList) || !JSONUtil.isTypeJSONObject(classList)) {
            return false;
        }
        return !JSONUtil.parseObj(classList).keySet().isEmpty();
    }

    /**
     * 训练集目录内至少有一张图、一个 DOTA txt，避免下拉框里出现空壳数据集。
     */
    public static boolean targetHasUsableTrainContent(ResolvedInstanceDiskPaths p) {
        try {
            Path trainImg = Paths.get(p.trainImgPath()).normalize();
            Path trainAnno = Paths.get(p.trainAnnoPath()).normalize();
            return hasImageFile(trainImg) && hasTxtLabel(trainAnno);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 源实例数据集（instance_dataset）：四类路径均为目录，且训练侧有图与标注。
     * 布局为 {@code .../train/images}、{@code .../train/anno} 等，与任务数据集导出一致。
     */
    public static boolean isSourceInstanceDatasetOnDisk(InstanceDataset src) {
        String tImg = MmdetConfigUtil.sanitizePathValue(src.getTrainImagePath());
        String tAnno = MmdetConfigUtil.sanitizePathValue(src.getTrainAnnoPath());
        String sImg = MmdetConfigUtil.sanitizePathValue(src.getTestImagePath());
        String sAnno = MmdetConfigUtil.sanitizePathValue(src.getTestAnnoPath());
        if (StrUtil.isBlank(tImg) || StrUtil.isBlank(tAnno) || StrUtil.isBlank(sImg) || StrUtil.isBlank(sAnno)) {
            return false;
        }
        if (!allLayoutDirsExist(tImg, sImg, tAnno, sAnno)) {
            return false;
        }
        try {
            return hasImageFile(Paths.get(tImg)) && hasTxtLabel(Paths.get(tAnno));
        } catch (IOException e) {
            return false;
        }
    }

    private static String normalizeStoredPath(String raw, Path instanceRoot) {
        String s = MmdetConfigUtil.sanitizePathValue(raw);
        if (StrUtil.isBlank(s)) {
            return s;
        }
        if (MmdetConfigUtil.isAbsoluteLike(s)) {
            return s;
        }
        return toPosixPath(instanceRoot.resolve(s));
    }

    private static String toPosixPath(Path p) {
        return p.toAbsolutePath().normalize().toString().replace("\\", "/");
    }

    private static boolean allLayoutDirsExist(String trainImg, String testImg, String trainAnno, String testAnno) {
        if (StrUtil.isBlank(trainImg) || StrUtil.isBlank(testImg) || StrUtil.isBlank(trainAnno) || StrUtil.isBlank(testAnno)) {
            return false;
        }
        return Files.isDirectory(Paths.get(trainImg).normalize())
                && Files.isDirectory(Paths.get(testImg).normalize())
                && Files.isDirectory(Paths.get(trainAnno).normalize())
                && Files.isDirectory(Paths.get(testAnno).normalize());
    }

    private static boolean hasImageFile(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        String[] exts = {".jpg", ".jpeg", ".png", ".bmp", ".tif", ".tiff"};
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path f : ds) {
                if (!Files.isRegularFile(f)) {
                    continue;
                }
                String n = f.getFileName().toString().toLowerCase();
                for (String e : exts) {
                    if (n.endsWith(e)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasTxtLabel(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.txt")) {
            return ds.iterator().hasNext();
        }
    }
}
