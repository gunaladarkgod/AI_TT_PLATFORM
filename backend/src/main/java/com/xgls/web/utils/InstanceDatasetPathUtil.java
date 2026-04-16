package com.xgls.web.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.xgls.web.entity.InstanceDataset;
import com.xgls.web.entity.InstanceDatasetMid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 中间实例数据集与最终实例数据集在磁盘上的路径解析与可用性校验。
 */
public final class InstanceDatasetPathUtil {

    private static final Logger log = LoggerFactory.getLogger(InstanceDatasetPathUtil.class);

    /** 导出会递归拷贝子目录，标注/图像可能在子文件夹内，需限制遍历深度避免误扫超大树。 */
    private static final int SOURCE_TREE_MAX_DEPTH = 24;

    private InstanceDatasetPathUtil() {}

    public record ResolvedInstanceDiskPaths(
            String trainImgPath,
            String testImgPath,
            String trainAnnoPath,
            String testAnnoPath) {}

    /**
     * 与训练打包 {@code buildDatasetCfg} 一致：解析最终实例数据集在磁盘上的四类目录。
     */
    public static ResolvedInstanceDiskPaths resolveTargetOrThrow(InstanceDataset info, String instanceDataRoot) {
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

    public static Optional<ResolvedInstanceDiskPaths> tryResolveTarget(InstanceDataset info, String instanceDataRoot) {
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
     * 训练集目录内至少有一张图、一个 DOTA txt（最终实例数据集打包口径）。
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
     * 中间实例数据集（instance_dataset_mid）：四类路径均为目录，且训练侧有图与标注。
     * 布局为 {@code .../train/images}、{@code .../train/anno} 等，与任务数据集导出一致。
     * 在子目录中查找图像与标注文件（与 {@link com.xgls.web.service.TaskDatasetDevService#copyDirContent} 保留层级一致）。
     * 标注接受常见 {@code *.txt}、{@code *.json}、{@code *.xml}。
     *
     * @param instanceDatasetMidRoot 可选；配置项 {@code sys.instancecfg.instancedata-mid-root}。
     *                                 当库中绝对路径在当前环境不存在时，会尝试 {@code root/name/train/images} 等标准导出布局。
     */
    public static boolean isSourceInstanceDatasetOnDisk(InstanceDatasetMid src) {
        return isSourceInstanceDatasetOnDisk(src, null);
    }

    public static boolean isSourceInstanceDatasetOnDisk(InstanceDatasetMid src, String instanceDatasetMidRoot) {
        if (src == null) {
            return false;
        }
        List<String[]> quads = new ArrayList<>();
        String tImg = MmdetConfigUtil.sanitizePathValue(src.getTrainImagePath());
        String tAnno = MmdetConfigUtil.sanitizePathValue(src.getTrainAnnoPath());
        String sImg = MmdetConfigUtil.sanitizePathValue(src.getTestImagePath());
        String sAnno = MmdetConfigUtil.sanitizePathValue(src.getTestAnnoPath());
        if (StrUtil.isNotBlank(tImg) && StrUtil.isNotBlank(tAnno) && StrUtil.isNotBlank(sImg) && StrUtil.isNotBlank(sAnno)) {
            quads.add(new String[] {tImg, sImg, tAnno, sAnno});
        }
        String midRoot = MmdetConfigUtil.sanitizePathValue(instanceDatasetMidRoot);
        if (StrUtil.isNotBlank(midRoot)) {
            Path root = Paths.get(midRoot.replaceAll("/+$", "")).normalize();
            for (String folder : distinctNonBlankFolderNames(src.getName(), src.getFatherName())) {
                Path base = root.resolve(folder).normalize();
                quads.add(new String[] {
                        toPosixPath(base.resolve("train").resolve("images")),
                        toPosixPath(base.resolve("test").resolve("images")),
                        toPosixPath(base.resolve("train").resolve("anno")),
                        toPosixPath(base.resolve("test").resolve("anno"))
                });
            }
        }
        for (String[] q : quads) {
            if (sourceLayoutUsableOnDisk(q)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> distinctNonBlankFolderNames(String name, String fatherName) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (StrUtil.isNotBlank(name)) {
            set.add(name.trim());
        }
        if (StrUtil.isNotBlank(fatherName)) {
            set.add(fatherName.trim());
        }
        return new ArrayList<>(set);
    }

    /** quad: trainImg, testImg, trainAnno, testAnno */
    private static boolean sourceLayoutUsableOnDisk(String[] quad) {
        if (quad == null || quad.length != 4) {
            return false;
        }
        if (!allLayoutDirsExist(quad[0], quad[1], quad[2], quad[3])) {
            return false;
        }
        try {
            Path trainImg = Paths.get(quad[0]).normalize();
            Path trainAnno = Paths.get(quad[2]).normalize();
            return hasImageFileDeep(trainImg) && hasMidExportLabelFileDeep(trainAnno);
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

    private static boolean hasImageFileDeep(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> walk = Files.walk(dir, SOURCE_TREE_MAX_DEPTH)) {
            return walk.filter(Files::isRegularFile).anyMatch(InstanceDatasetPathUtil::isImageFileName);
        }
    }

    private static boolean isImageFileName(Path file) {
        String n = file.getFileName().toString().toLowerCase();
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".bmp")
                || n.endsWith(".tif") || n.endsWith(".tiff");
    }

    /** 任务数据集导出的中间数据：训练标注目录树内至少有一个 txt / json / xml 文件。 */
    private static boolean hasMidExportLabelFileDeep(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> walk = Files.walk(dir, SOURCE_TREE_MAX_DEPTH)) {
            return walk.filter(Files::isRegularFile).anyMatch(InstanceDatasetPathUtil::isMidLabelFileName);
        }
    }

    private static boolean isMidLabelFileName(Path file) {
        String n = file.getFileName().toString().toLowerCase();
        return n.endsWith(".txt") || n.endsWith(".json") || n.endsWith(".xml");
    }
}
