package com.xgls.web.utils;

import cn.hutool.core.util.StrUtil;
import com.xgls.web.entity.InstanceDataset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * 将最终实例数据集中本在 train 侧的图片/标注，按训测比随机拆分到 images/test 与 annotations/test
 *（先合并 test 回 train 再重划，以支持多次执行）。
 */
public final class InstanceDatasetTrainTestRandomSplitUtil {

    private static final int MAX_DEPTH = 32;
    private static final String[] IMG_EXT = {".jpg", ".jpeg", ".png", ".bmp", ".tif", ".tiff"};
    private static final String[] ANNO_EXT = {".txt", ".json", ".xml"};

    private InstanceDatasetTrainTestRandomSplitUtil() {}

    public record SplitResult(int trainImages, int testImages) {}

    public static SplitResult run(
            InstanceDataset dataset,
            String instanceDataRoot,
            double trainRatio) throws IOException {
        if (trainRatio < 0.01 || trainRatio > 0.99) {
            throw new IllegalArgumentException("训测比（训练集占比）须在 0.01～0.99 之间");
        }
        if (dataset == null || dataset.getId() == null) {
            throw new IllegalArgumentException("缺少实例数据集");
        }
        var pathsOpt = InstanceDatasetPathUtil.tryResolveTargetEnsuringTestDirs(dataset, instanceDataRoot);
        if (pathsOpt.isEmpty()) {
            throw new IllegalStateException(
                    "无法解析该实例数据集目录：请确认 instancedata-root 下存在 father_name/name/images/train 与 annotations/train（或 anno/train），或库中 train_image_path / train_anno_path 指向以 train 为末级的目录");
        }
        var p = pathsOpt.get();
        Path trainImg = Path.of(p.trainImgPath()).normalize();
        Path testImg = Path.of(p.testImgPath()).normalize();
        Path trainAnno = Path.of(p.trainAnnoPath()).normalize();
        Path testAnno = Path.of(p.testAnnoPath()).normalize();

        if (!Files.isDirectory(trainImg) || !Files.isDirectory(testImg)
                || !Files.isDirectory(trainAnno) || !Files.isDirectory(testAnno)) {
            throw new IllegalStateException("训练或测试的图像/标注目录不存在，无法划分");
        }

        mergeTestIntoTrain(testImg, trainImg);
        mergeTestIntoTrain(testAnno, trainAnno);

        List<Path> allImages = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(trainImg, MAX_DEPTH)) {
            walk.filter(Files::isRegularFile).filter(path -> isImageName(path.getFileName().toString()))
                    .forEach(allImages::add);
        }
        int n = allImages.size();
        if (n < 1) {
            throw new IllegalStateException("训练图像目录中未找到可划分的图片");
        }
        int trainKeep = (int) Math.round(n * trainRatio);
        if (trainKeep < 0) {
            trainKeep = 0;
        }
        if (trainKeep > n) {
            trainKeep = n;
        }
        if (trainKeep == n) {
            return new SplitResult(n, 0);
        }
        if (trainKeep == 0) {
            for (Path img : allImages) {
                movePair(trainImg, testImg, trainAnno, testAnno, img);
            }
            return new SplitResult(0, n);
        }
        List<Path> shuffled = new ArrayList<>(allImages);
        Collections.shuffle(shuffled, ThreadLocalRandom.current());
        List<Path> toTest = shuffled.subList(trainKeep, n);
        for (Path img : new ArrayList<>(toTest)) {
            movePair(trainImg, testImg, trainAnno, testAnno, img);
        }
        int inTrain = n - toTest.size();
        return new SplitResult(inTrain, toTest.size());
    }

    private static void mergeTestIntoTrain(Path testRoot, Path trainRoot) throws IOException {
        if (!Files.isDirectory(testRoot)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(testRoot, MAX_DEPTH)) {
            List<Path> files = walk.filter(Files::isRegularFile).sorted().toList();
            for (Path f : files) {
                if (f.equals(testRoot)) {
                    continue;
                }
                Path rel = testRoot.relativize(f);
                Path target = trainRoot.resolve(rel);
                Files.createDirectories(target.getParent());
                if (Files.exists(target)) {
                    Files.delete(f);
                } else {
                    Files.move(f, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void movePair(
            Path trainImgRoot, Path testImgRoot, Path trainAnnoRoot, Path testAnnoRoot, Path imagePath)
            throws IOException {
        Path relImg = trainImgRoot.relativize(imagePath);
        Path destImg = testImgRoot.resolve(relImg);
        Files.createDirectories(destImg.getParent());
        Files.move(imagePath, destImg, StandardCopyOption.REPLACE_EXISTING);
        findAndMoveAnno(trainAnnoRoot, testAnnoRoot, relImg);
    }

    private static void findAndMoveAnno(Path trainAnnoRoot, Path testAnnoRoot, Path relImg) throws IOException {
        String fileName = relImg.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
        Path parentRel = relImg.getParent() != null ? relImg.getParent() : Path.of("");
        for (String ext : ANNO_EXT) {
            Path src = trainAnnoRoot.resolve(parentRel).resolve(stem + ext).normalize();
            if (Files.isRegularFile(src)) {
                Path rel = trainAnnoRoot.relativize(src);
                Path dest = testAnnoRoot.resolve(rel);
                Files.createDirectories(dest.getParent());
                Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
                return;
            }
        }
    }

    private static boolean isImageName(String n) {
        if (StrUtil.isBlank(n)) {
            return false;
        }
        String lower = n.toLowerCase(Locale.ROOT);
        for (String e : IMG_EXT) {
            if (lower.endsWith(e)) {
                return true;
            }
        }
        return false;
    }

    public static int countImages(Path root) {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        try (Stream<Path> walk = Files.walk(root, MAX_DEPTH)) {
            return (int) walk.filter(Files::isRegularFile)
                    .filter(p -> isImageName(p.getFileName().toString()))
                    .count();
        } catch (IOException e) {
            return 0;
        }
    }

    public static int countAnnoLabelFiles(Path root) {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        try (Stream<Path> walk = Files.walk(root, MAX_DEPTH)) {
            return (int) walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        for (String e : ANNO_EXT) {
                            if (n.endsWith(e)) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .count();
        } catch (IOException e) {
            return 0;
        }
    }
}
