package com.xgls.web.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xgls.web.dto.InstanceDatasetTrainingReadinessDto;
import com.xgls.web.entity.InstanceDataset;
import com.xgls.web.mapper.InstanceDatasetMapper;
import com.xgls.web.service.InstanceDatasetService;
import com.xgls.web.utils.InstanceDatasetPathUtil;
import com.xgls.web.utils.InstanceDatasetTrainTestRandomSplitUtil;
import com.xgls.web.utils.WorkspacePathUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class InstanceDatasetServiceImpl extends ServiceImpl<InstanceDatasetMapper, InstanceDataset> implements InstanceDatasetService {

    private static final Logger log = LoggerFactory.getLogger(InstanceDatasetServiceImpl.class);

    @Autowired
    private InstanceDatasetMapper instanceDatasetMapper;

    @Value("${sys.instancecfg.instancedata-root:data/instance_dataset/}")
    private String instanceDataRoot;

        private String instanceRootString() {
        return WorkspacePathUtil.instanceDatasetRoot().toAbsolutePath().normalize().toString();
    }

    private Path instanceRootPath() {
        return WorkspacePathUtil.instanceDatasetRoot().toAbsolutePath().normalize();
    }

    private Optional<Path> canonicalDatasetRoot(InstanceDataset dataset) {
        if (dataset == null || dataset.getName() == null || dataset.getName().isBlank()) {
            return Optional.empty();
        }
        Path root = instanceRootPath();
        String name = dataset.getName().trim();
        List<Path> candidates = new ArrayList<>();
        if (dataset.getFatherName() != null && !dataset.getFatherName().isBlank()) {
            candidates.add(root.resolve(InstanceDatasetPathUtil.safeFinalDatasetDirSegment(dataset.getFatherName())).resolve(name).normalize());
        }
        candidates.add(root.resolve(name).normalize());
        for (Path candidate : candidates) {
            if (isUsableInstanceDatasetDir(candidate)) {
                return Optional.of(candidate.toAbsolutePath().normalize());
            }
        }
        return Optional.empty();
    }

    private boolean isUsableInstanceDatasetDir(Path base) {
        if (base == null || !Files.isDirectory(base)) {
            return false;
        }
        Path images = base.resolve("images");
        Path annotations = base.resolve("annotations");
        return Files.isDirectory(images) && Files.isDirectory(annotations)
                && (Files.isDirectory(images.resolve("train")) || hasAnyRegularFile(images))
                && (Files.isDirectory(annotations.resolve("train")) || hasAnyRegularFile(annotations));
    }

    private boolean hasAnyRegularFile(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (var stream = Files.walk(dir, 3)) {
            return stream.anyMatch(Files::isRegularFile);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public List<InstanceDataset> getAllInstanceDatasets() {
        List<InstanceDataset> all = instanceDatasetMapper.selectList(new QueryWrapper<>());
        List<InstanceDataset> visible = new ArrayList<>();
        for (InstanceDataset dataset : all) {
            if (canonicalDatasetRoot(dataset).isPresent()) {
                visible.add(dataset);
            } else {
                log.debug("跳过未落在当前工作区 data/instance_dataset 下的实例数据集: id={}, father={}, name={}, trainImagePath={}",
                        dataset.getId(), dataset.getFatherName(), dataset.getName(), dataset.getTrainImagePath());
            }
        }
        return visible;
    }

    @Override
    public List<InstanceDatasetTrainingReadinessDto> listInstanceDatasetTrainingReadiness() {
        List<InstanceDataset> all = getAllInstanceDatasets();
        List<InstanceDatasetTrainingReadinessDto> out = new ArrayList<>();
        for (InstanceDataset info : all) {
            InstanceDatasetTrainingReadinessDto row = new InstanceDatasetTrainingReadinessDto();
            row.setId(info.getId());
            row.setName(info.getName());
            row.setFatherName(info.getFatherName());
            List<String> reasons = new ArrayList<>();
            if (info.getName() == null || info.getName().isBlank()) {
                reasons.add("实例数据集名称为空，无法作为训练集标识");
            }
            if (!InstanceDatasetPathUtil.hasMmdetTrainableClassList(info.getClassList())) {
                reasons.add("class_list 无效或为空，请维护有效的 JSON 类别（预处理后应写入非空 class_list）");
            }
            boolean pathResolveFailed = false;
            Optional<InstanceDatasetPathUtil.ResolvedInstanceDiskPaths> paths = Optional.empty();
            try {
                paths = InstanceDatasetPathUtil.tryResolveTargetEnsuringTestDirs(info, instanceRootString());
            } catch (IOException e) {
                pathResolveFailed = true;
                log.debug("解析实例数据集路径失败: name={}, err={}", info.getName(), e.toString());
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                reasons.add("解析磁盘路径失败: " + msg);
            }
            if (paths.isEmpty() && !pathResolveFailed) {
                reasons.add("无法定位实例数据目录，请确认预处理已落盘、根目录下存在 images/train 与 annotations/train，且与 father_name、name 一致");
            }
            if (paths.isPresent()) {
                InstanceDatasetPathUtil.ResolvedInstanceDiskPaths p = paths.get();
                if (!InstanceDatasetPathUtil.targetHasUsableTrainContent(p)) {
                    reasons.add("训练集目录中缺少有效图片与标注对（子目录内需至少一张图与一份 txt/json/xml 标注）");
                }
                int testImgN = InstanceDatasetTrainTestRandomSplitUtil.countImages(Path.of(p.testImgPath()));
                if (testImgN < 1) {
                    reasons.add("测试集无图片，请重新执行预处理并设置训测划分比例");
                }
                int testAnnoN = InstanceDatasetTrainTestRandomSplitUtil.countAnnoLabelFiles(Path.of(p.testAnnoPath()));
                if (testAnnoN < 1) {
                    reasons.add("测试集无标注，请重新执行预处理并检查图片与标注是否同名配对");
                }
            }
            row.setReasons(reasons);
            row.setQualified(reasons.isEmpty());
            out.add(row);
        }
        out.sort(Comparator.comparing(
                (InstanceDatasetTrainingReadinessDto d) -> Optional.ofNullable(d.getName()).orElse("").toLowerCase()));
        return out;
    }

    @Override
    public List<String> listMmdetTrainableDatasetNames() {
        List<InstanceDataset> all = getAllInstanceDatasets();
        List<String> names = new ArrayList<>();
        for (InstanceDataset info : all) {
            if (!InstanceDatasetPathUtil.hasMmdetTrainableClassList(info.getClassList())) {
                continue;
            }
            Optional<InstanceDatasetPathUtil.ResolvedInstanceDiskPaths> paths = Optional.empty();
            try {
                paths = InstanceDatasetPathUtil.tryResolveTargetEnsuringTestDirs(info, instanceRootString());
            } catch (IOException e) {
                log.debug("解析实例数据集路径失败，跳过: name={}, err={}", info.getName(), e.toString());
            }
            if (paths.isEmpty()) {
                continue;
            }
            if (!InstanceDatasetPathUtil.targetHasUsableTrainContent(paths.get())) {
                continue;
            }
            if (info.getName() != null && !info.getName().isBlank()) {
                names.add(info.getName());
            }
        }
        names.sort(Comparator.comparing(String::toLowerCase));
        return names;
    }

    @Override
    public InstanceDatasetTrainTestRandomSplitUtil.SplitResult splitInstanceDatasetRandomTrainTest(Long id, double trainRatio)
            throws java.io.IOException {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("无效的实例数据集 id");
        }
        InstanceDataset d = this.getById(id);
        if (d == null) {
            throw new IllegalArgumentException("未找到实例数据集: " + id);
        }
        InstanceDatasetTrainTestRandomSplitUtil.SplitResult result =
                InstanceDatasetTrainTestRandomSplitUtil.run(d, instanceRootString(), trainRatio);
        var paths = InstanceDatasetPathUtil.tryResolveTargetEnsuringTestDirs(d, instanceRootString());
        if (paths.isEmpty()) {
            return result;
        }
        var p = paths.get();
        int imgN = InstanceDatasetTrainTestRandomSplitUtil.countImages(Path.of(p.trainImgPath()))
                + InstanceDatasetTrainTestRandomSplitUtil.countImages(Path.of(p.testImgPath()));
        int annoN = InstanceDatasetTrainTestRandomSplitUtil.countAnnoLabelFiles(Path.of(p.trainAnnoPath()))
                + InstanceDatasetTrainTestRandomSplitUtil.countAnnoLabelFiles(Path.of(p.testAnnoPath()));
        d.setImgNum(imgN);
        d.setAnnoNum(annoN);
        this.updateById(d);
        return result;
    }
    @Override
    public String resolveInstanceDatasetRootPath(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("无效的实例数据集ID");
        }
        InstanceDataset dataset = this.getById(id);
        if (dataset == null) {
            throw new IllegalArgumentException("实例数据集不存在: " + id);
        }
        String rootPath = canonicalDatasetRoot(dataset)
                .map(Path::toString)
                .orElseGet(() -> inferRootDirectory(dataset));
        if (rootPath == null) {
            throw new IllegalStateException("未找到该实例数据集的本地目录，请确认目录已迁移到 data/instance_dataset/{任务数据集名称}/{实例数据集名称}");
        }
        Path root = Paths.get(rootPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("实例数据集目录不存在: " + root);
        }
        if (!isUnderAllowedRoots(root.toString())) {
            throw new IllegalArgumentException("非法路径，拒绝打开: " + root);
        }
        return root.toString();
    }

    @Override
    public String openInstanceDatasetPath(Long id) throws IOException {
        String rootPath = resolveInstanceDatasetRootPath(id);
        Path root = Paths.get(rootPath).toAbsolutePath().normalize();
        openDirectory(root);
        return root.toString();
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteInstanceDatasetById(Long id) {
        InstanceDataset dataset = this.getById(id);
        if (dataset == null) {
            return false;
        }

        String rootPath = canonicalDatasetRoot(dataset).map(Path::toString).orElseGet(() -> inferRootDirectory(dataset));
        if (rootPath != null) {
            if (!isUnderAllowedRoots(rootPath)) {
                throw new IllegalArgumentException("非法路径，拒绝删除: " + rootPath);
            }
            Path root = Paths.get(rootPath);
            if (Files.exists(root)) {
                try {
                    Files.walk(root)
                            .sorted((a, b) -> -a.compareTo(b))
                            .forEach(p -> {
                                try {
                                    Files.delete(p);
                                } catch (IOException e) {
                                    throw new RuntimeException("删除文件失败: " + p, e);
                                }
                            });
                } catch (IOException e) {
                    throw new RuntimeException("遍历大文件夹失败: " + root, e);
                }
            }
        } else {
            deleteIndividualPaths(dataset);
        }

        return this.removeById(id);
    }

    private boolean isUnderAllowedRoots(String rootPath) {
        try {
            Path target = Paths.get(rootPath).toAbsolutePath().normalize();
            Path finalRoot = WorkspacePathUtil.instanceDatasetRoot().toAbsolutePath().normalize();
            if (target.startsWith(finalRoot)) {
                return true;
            }
            Path midRoot = WorkspacePathUtil.instanceDatasetMidRoot().toAbsolutePath().normalize();
            return target.startsWith(midRoot);
        } catch (Exception e) {
            return false;
        }
    }

    private String inferRootDirectory(InstanceDataset dataset) {
        String[] candidatePaths = {
                dataset.getTrainImagePath(),
                dataset.getTrainAnnoPath(),
                dataset.getTestImagePath(),
                dataset.getTestAnnoPath()
        };

        for (String path : candidatePaths) {
            if (path == null || path.trim().isEmpty()) {
                continue;
            }
            Path p = Paths.get(path).toAbsolutePath().normalize();
            if (!Files.exists(p)) {
                continue;
            }
            String leaf = p.getFileName() == null ? "" : p.getFileName().toString().toLowerCase(Locale.ROOT);
            Path root = null;
            if ("train".equals(leaf) || "test".equals(leaf)) {
                root = p.getParent() != null ? p.getParent().getParent() : null;
            } else if ("images".equals(leaf) || "annotations".equals(leaf) || "anno".equals(leaf)) {
                root = p.getParent();
            }
            if (root != null) {
                return root.toString();
            }
        }
        return null;
    }

    private void deleteIndividualPaths(InstanceDataset dataset) {
        String[] paths = {
                dataset.getTrainImagePath(),
                dataset.getTrainAnnoPath(),
                dataset.getTestImagePath(),
                dataset.getTestAnnoPath()
        };

        for (String pathStr : paths) {
            if (pathStr != null && !pathStr.trim().isEmpty()) {
                Path path = Paths.get(pathStr);
                if (Files.exists(path)) {
                    try {
                        Files.walk(path)
                                .sorted((p1, p2) -> -p1.compareTo(p2))
                                .forEach(p -> {
                                    try {
                                        Files.delete(p);
                                    } catch (IOException e) {
                                        throw new RuntimeException("删除文件失败: " + p, e);
                                    }
                                });
                    } catch (IOException e) {
                        throw new RuntimeException("遍历目录失败: " + path, e);
                    }
                }
            }
        }
    }
    private void openDirectory(Path dir) throws IOException {
        Path normalized = dir.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            throw new IOException("目录不存在: " + normalized);
        }
        if (!GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            try {
                Desktop.getDesktop().open(normalized.toFile());
                return;
            } catch (IOException | UnsupportedOperationException ignored) {
                // Continue with OS-specific opener.
            }
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        ProcessBuilder builder;
        if (os.contains("win")) {
            builder = new ProcessBuilder("explorer.exe", "/e,", normalized.toString());
        } else if (os.contains("mac")) {
            builder = new ProcessBuilder("open", normalized.toString());
        } else {
            builder = new ProcessBuilder("xdg-open", normalized.toString());
        }
        builder.start();
    }
}
