package com.xgls.web.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xgls.web.entity.ImgInfo;
import com.xgls.web.entity.InstanceDatasetMid;
import com.xgls.web.entity.OriginalDataset;
import com.xgls.web.entity.TaskDataset;
import com.xgls.web.mapper.TaskDatasetMapper;
import com.xgls.web.service.ImgInfoService;
import com.xgls.web.service.InstanceDatasetMidService;
import com.xgls.web.service.OriginalDataset1Service;
import com.xgls.web.service.TaskDataset1Service;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.time.format.DateTimeFormatter;


@Service
public class TaskDatasetServiceImpl extends ServiceImpl<TaskDatasetMapper, TaskDataset>
        implements TaskDataset1Service {
    @Autowired
    private OriginalDataset1Service originalDatasetService;

    @Autowired
    private ImgInfoService imgInfoService;

    @Autowired
    private InstanceDatasetMidService instanceDatasetMidService;

    @Value("${sys.instancecfg.instancedata-mid-root:/home/omen1/AI_TT_Platform/data/instance_dataset_mid/}")
    private String instanceDatasetMidRoot;

    // 内部类：带路径的图像信息
    @Data
    @AllArgsConstructor
    private static class ImageWithPath {
        private ImgInfo img;
        private Long projectId;   // 新增：用于生成文件名
        private String dataPath;
        private String annoPath;
    }

    public void processTestPlan(TaskDataset task, List<String> testPlan, List<String> trainOriginalIds, int index) {
        // 1. 解析 core_id（目标子集的原始数据集 ID）
        List<Long> coreIds = Arrays.stream(task.getCoreId().split("_"))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toList());

        // 2. 查询目标子集对应的原始数据集
        List<OriginalDataset> coreOriginals = originalDatasetService.listByIds(coreIds);

        // 3. 构建测试集
        List<ImageWithPath> testImages = new ArrayList<>();
        for (OriginalDataset orig : coreOriginals) {
            // 解析原始数据集的 task_id 列表（如 ["1", "2", "3", "4"]）
            String[] origTaskIds = orig.getTaskId().split("_");

            // 提取 testPlan 中属于该原始数据集的 taskId
            List<String> testTaskIds = new ArrayList<>();
            for (String testItem : testPlan) {
                // testItem = "1_1" → 我们只需要 taskId 部分 "1"
                String[] parts = testItem.split("_", 2);
                if (parts.length >= 2) {
                    String taskId = parts[1];
                    if (Arrays.asList(origTaskIds).contains(taskId)) {
                        testTaskIds.add(taskId);
                    }
                }
            }

            // 查询这些 taskId 对应的图像
            if (!testTaskIds.isEmpty()) {
                List<ImgInfo> imgs = imgInfoService.lambdaQuery()
                        .eq(ImgInfo::getProjectId, orig.getProjectId())
                        .in(ImgInfo::getTaskId, testTaskIds)
                        .list();
                for (ImgInfo img : imgs) {
                    testImages.add(new ImageWithPath(
                            img,
                            orig.getProjectId(),
                            task.getCoreDataPath(),
                            task.getCoreAnnoPath()
                    ));
                }
            }
        }

// 4.1 训练集 - 目标子集部分（来自 core）
        List<ImageWithPath> trainFromCore = new ArrayList<>();
        for (OriginalDataset orig : coreOriginals) {
            // 获取原始数据集的所有 task_id
            String[] allTaskIds = orig.getTaskId().split("_");

            // 提取 testPlan 中属于该原始数据集的 task_id
            Set<String> testTaskIdsInOrig = new HashSet<>();
            for (String testItem : testPlan) {
                // testItem = "1_1" → task_id = "1"
                String[] parts = testItem.split("_", 2);
                if (parts.length >= 2 && Arrays.asList(allTaskIds).contains(parts[1])) {
                    testTaskIdsInOrig.add(parts[1]);
                }
            }

            // 训练集 task_id = 所有 task_id - 测试集 task_id
            List<String> trainTaskIds = Arrays.stream(allTaskIds)
                    .filter(id -> !testTaskIdsInOrig.contains(id))
                    .collect(Collectors.toList());

            if (!trainTaskIds.isEmpty()) {
                List<ImgInfo> imgs = imgInfoService.lambdaQuery()
                        .eq(ImgInfo::getProjectId, orig.getProjectId())
                        .in(ImgInfo::getTaskId, trainTaskIds)
                        .list();
                for (ImgInfo img : imgs) {
                    trainFromCore.add(new ImageWithPath(img, orig.getProjectId(),
                            task.getCoreDataPath(), task.getCoreAnnoPath()));
                }
            }
        }

// 4.2 训练集 - 预训练子集部分（来自 sup）
        List<ImageWithPath> trainFromPretrain = new ArrayList<>();
        if (!trainOriginalIds.isEmpty()) {
            List<Long> trainOrigIds = trainOriginalIds.stream().map(Long::parseLong).collect(Collectors.toList());
            List<OriginalDataset> trainOriginals = originalDatasetService.listByIds(trainOrigIds);
            for (OriginalDataset orig : trainOriginals) {
                List<ImgInfo> imgs = imgInfoService.lambdaQuery()
                        .eq(ImgInfo::getProjectId, orig.getProjectId())
                        .list();
                for (ImgInfo img : imgs) {
                    trainFromPretrain.add(new ImageWithPath(img, orig.getProjectId(), task.getSupDataPath(), task.getSupAnnoPath()));
                }
            }
        }

        // 5. 生成路径
        String instanceName = generateUniqueInstanceName(task.getName(),index);
        String baseDir = Paths.get(instanceDatasetMidRoot.trim().replaceAll("/+$", ""), instanceName)
                .toString()
                .replace("\\", "/");

        String testImgPath = baseDir + "/test/images/";
        String trainImgPath = baseDir + "/train/images/";
        String testAnnoPath = baseDir + "/test/anno/";
        String trainAnnoPath = baseDir + "/train/anno/";

        // 6. 复制文件
        copyImages(testImages, testImgPath, testAnnoPath);
        copyImages(trainFromCore, trainImgPath, trainAnnoPath);
        copyImages(trainFromPretrain, trainImgPath, trainAnnoPath);

        // 7. 保存到 instance_dataset_mid
        InstanceDatasetMid instance = new InstanceDatasetMid();
        instance.setFatherName(task.getName());
        instance.setName(instanceName);

        // 继承任务数据集字段
        instance.setSensorType(task.getSensorType());
        instance.setTargetType(task.getTargetType());
        instance.setDataFormat(task.getDataFormat());

        // 图像数 = 测试集 + 训练集
        int totalImgNum = testImages.size() + trainFromCore.size() + trainFromPretrain.size();
        instance.setImgNum(totalImgNum);
        instance.setAnnoNum(totalImgNum); // 标注数 = 图像数

        // 类别信息继承目标子集（core）
        instance.setClassNum(task.getCoreClassNum());
        instance.setClassList(task.getCoreClassList());

        instance.setTestImagePath(testImgPath);
        instance.setTrainImagePath(trainImgPath);
        instance.setTestAnnoPath(testAnnoPath);
        instance.setTrainAnnoPath(trainAnnoPath);
        instance.setUsername("admin");
        instance.setCreatedTime(LocalDateTime.now());
        instanceDatasetMidService.save(instance);
    }

    // 生成唯一文件夹名
    private String generateUniqueInstanceName(String taskName,int index) {
        // 时间戳：yyyyMMddHHmmss
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        // 短 UUID（8位）
        //String random = UUID.randomUUID().toString().substring(0, 8);
        return taskName + "_" + timestamp + "_" + index;
    }

    private List<Long> queryImgIds(List<OriginalDataset> originals) {
        if (originals.isEmpty()) return new ArrayList<>();

        List<Long> imgIds = new ArrayList<>();
        for (OriginalDataset orig : originals) {
            List<Long> ids = imgInfoService.lambdaQuery()
                    .eq(ImgInfo::getProjectId, orig.getProjectId())
                    .eq(ImgInfo::getTaskId, orig.getTaskId())
                    .list()
                    .stream()
                    .map(ImgInfo::getId)
                    .collect(Collectors.toList());
            imgIds.addAll(ids);
        }
        return imgIds;
    }


    private void copyImages(List<ImageWithPath> images, String targetImgPath, String targetAnnoPath) {
        new File(targetImgPath).mkdirs();
        new File(targetAnnoPath).mkdirs();

        for (ImageWithPath item : images) {
            try {
                String srcImgFileName = item.getProjectId() + "_" + item.getImg().getImgName();
                Path srcImg = Paths.get(item.getDataPath(), srcImgFileName);
                System.out.println("源图像路径: " + srcImg.toAbsolutePath());
                System.out.println("源图像是否存在: " + Files.exists(srcImg));
                if (!Files.exists(srcImg)) {
                    log.warn("图像不存在，跳过: {}"+srcImg);

                    continue;
                }
                Path dstImg = Paths.get(targetImgPath, srcImgFileName);
                Files.copy(srcImg, dstImg, StandardCopyOption.REPLACE_EXISTING);

                String srcAnnoFileName = item.getProjectId() + "_" +
                        item.getImg().getImgName().replaceAll("\\.(jpg|png|jpeg)$", ".txt");
                Path srcAnno = Paths.get(item.getAnnoPath(), srcAnnoFileName);
                if (Files.exists(srcAnno)) {
                    Path dstAnno = Paths.get(targetAnnoPath, srcAnnoFileName);
                    Files.copy(srcAnno, dstAnno, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                System.err.println("复制文件失败: " + item.getImg() + ", error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}

