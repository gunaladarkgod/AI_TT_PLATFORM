// com.xgls.web.service.impl.InstanceDatasetinfoServiceImpl.java
package com.xgls.web.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xgls.web.entity.InstanceDatasetinfo;
import com.xgls.web.mapper.InstanceDatasetinfoMapper;
import com.xgls.web.service.InstanceDatasetinfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xgls.web.utils.InstanceDatasetPathUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class InstanceDatasetinfoServiceImpl
        extends ServiceImpl<InstanceDatasetinfoMapper, InstanceDatasetinfo>
        implements InstanceDatasetinfoService {

    @Autowired
    private InstanceDatasetinfoMapper instanceDatasetinfoMapper;

    // ✅ 定义实例数据集根目录（必须与你实际路径一致！ 硬编码
//    private static final String INSTANCE_DATA_ROOT = "/home/cs303-1/AI_TT_Platform/data/instance_dataset/";

    // 改为（由配置注入）：
    @Value("${sys.instancecfg.instancedata-root:/home/omen1/AI_TT_Platform/data/instance_dataset/}")
    private String instanceDataRoot;

    @Override
    public List<InstanceDatasetinfo> getAllInstanceDatasets() {
        // 查询所有实例数据集
        return instanceDatasetinfoMapper.selectList(new QueryWrapper<>());
    }

    @Override
    public List<String> listMmdetTrainableDatasetNames() {
        List<InstanceDatasetinfo> all = getAllInstanceDatasets();
        List<String> names = new ArrayList<>();
        for (InstanceDatasetinfo info : all) {
            if (!InstanceDatasetPathUtil.hasMmdetTrainableClassList(info.getClassList())) {
                continue;
            }
            Optional<InstanceDatasetPathUtil.ResolvedInstanceDiskPaths> paths =
                    InstanceDatasetPathUtil.tryResolveTarget(info, instanceDataRoot);
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
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteInstanceDatasetById(Long id) {
        // 1. 查询数据库中是否存在该记录
        InstanceDatasetinfo dataset = this.getById(id);
        if (dataset == null) {
            return false;
        }

        // 2. 尝试推断大文件夹根目录（从任意非空路径）
        String rootPath = inferRootDirectory(dataset);

        if (rootPath != null) {
            // 3. 安全校验：确保路径在合法根目录下
            if (!rootPath.startsWith(instanceDataRoot)) {
                throw new IllegalArgumentException("非法路径，拒绝删除: " + rootPath);
            }

            // 4. 删除整个大文件夹
            Path root = Paths.get(rootPath);
            if (Files.exists(root)) {
                try {
                    Files.walk(root)
                            .sorted((a, b) -> -a.compareTo(b)) // 先删子文件，再删父目录
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
            // 5. 退化方案：逐个删除四个子目录（兼容旧数据）
            deleteIndividualPaths(dataset);
        }

        // 6. 删除数据库记录
        return this.removeById(id);
    }

    /**
     * 从数据集路径推断大文件夹根目录
     * 示例:
     *   输入: /.../demoXXX/images/train/
     *   输出: /.../demoXXX
     */
    private String inferRootDirectory(InstanceDatasetinfo dataset) {
        String[] candidatePaths = {
                dataset.getTrainImagePath(),
                dataset.getTrainAnnoPath(),
                dataset.getTestImagePath(),
                dataset.getTestAnnoPath()
        };

        for (String path : candidatePaths) {
            if (path != null && !path.trim().isEmpty()) {
                Path p = Paths.get(path);
                if (Files.exists(p)) {
                    // images/train/ 的父目录是 demoXXX，再父目录是 instance_dataset
                    // 所以 getParent().getParent() 就是 demoXXX
                    Path root = p.getParent().getParent();
                    if (root != null) {
                        return root.toString();
                    }
                }
            }
        }
        return null; // 无法推断
    }

    /**
     * 退化方案：逐个删除四个子目录（兼容不符合新结构的旧数据）
     */
    private void deleteIndividualPaths(InstanceDatasetinfo dataset) {
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
}