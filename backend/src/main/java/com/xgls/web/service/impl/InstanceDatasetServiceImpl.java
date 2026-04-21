package com.xgls.web.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xgls.web.entity.InstanceDataset;
import com.xgls.web.mapper.InstanceDatasetMapper;
import com.xgls.web.service.InstanceDatasetService;
import com.xgls.web.utils.InstanceDatasetPathUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class InstanceDatasetServiceImpl extends ServiceImpl<InstanceDatasetMapper, InstanceDataset> implements InstanceDatasetService {

    @Autowired
    private InstanceDatasetMapper instanceDatasetMapper;

    @Value("${sys.instancecfg.instancedata-root:/home/omen1/AI_TT_Platform/data/instance_dataset/}")
    private String instanceDataRoot;
    @Value("${sys.instancecfg.instancedata-mid-root:/home/omen1/AI_TT_Platform/data/instance_dataset_mid/}")
    private String instanceDataMidRoot;

    @Override
    public List<InstanceDataset> getAllInstanceDatasets() {
        return instanceDatasetMapper.selectList(new QueryWrapper<>());
    }

    @Override
    public List<String> listMmdetTrainableDatasetNames() {
        List<InstanceDataset> all = getAllInstanceDatasets();
        List<String> names = new ArrayList<>();
        for (InstanceDataset info : all) {
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
        InstanceDataset dataset = this.getById(id);
        if (dataset == null) {
            return false;
        }

        String rootPath = inferRootDirectory(dataset);
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
            Path finalRoot = Paths.get(instanceDataRoot).toAbsolutePath().normalize();
            if (target.startsWith(finalRoot)) {
                return true;
            }
            Path midRoot = Paths.get(instanceDataMidRoot).toAbsolutePath().normalize();
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
            if (path != null && !path.trim().isEmpty()) {
                Path p = Paths.get(path);
                if (Files.exists(p)) {
                    Path root = p.getParent() != null ? p.getParent().getParent() : null;
                    if (root != null) {
                        return root.toString();
                    }
                }
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
}