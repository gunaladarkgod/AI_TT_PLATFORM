package com.xgls.web.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

public class CocoJsonMerger {

    /**
     * 合并 COCO 格式的 JSON 文件：将 images 和 annotations 追加到 train.json 中
     * 支持超大文件，使用流式读取，几乎不占内存
     *
     * @param trainJsonPath       主文件路径（提前写好 info、categories 等，不包含末尾 }）
     * @param imagesJsonPath      包含 JSON 数组的一整行的 images.json
     * @param annotationsJsonPath 包含 JSON 数组的一整行的 annotations.json
     * @throws IOException 读写异常
     */
    public static void merge(Path trainJsonPath, Path lablePath, Path imagesJsonPath, Path annotationsJsonPath)
            throws IOException {
        // 1. 写入 "lables":
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(trainJsonPath.toFile(), true))) {
            writer.write("{\n\"categories\": ");
        }
        copyFileAsRaw(lablePath, trainJsonPath);

        // 2. 写入 "images":
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(trainJsonPath.toFile(), true))) {
            writer.write(",\n\"images\": ");
        }
        copyFileAsRaw(imagesJsonPath, trainJsonPath);

        // 3. 写入 "annotations":
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(trainJsonPath.toFile(), true))) {
            writer.write(",\n\"annotations\": ");
        }
        copyFileAsRaw(annotationsJsonPath, trainJsonPath);

        // 4. 写入最后的大括号闭合 JSON
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(trainJsonPath.toFile(), true))) {
            writer.write("\n}\n");
        }
    }

    /**
     * 将一个文件内容（如 JSON 数组）原样拷贝到目标文件末尾，支持大文件
     */
    private static void copyFileAsRaw(Path source, Path target) throws IOException {
        try (
                BufferedInputStream in = new BufferedInputStream(new FileInputStream(source.toFile()));
                BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(target.toFile(), true)) // 追加模式
        ) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        }
    }
}
