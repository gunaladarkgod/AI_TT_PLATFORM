package com.xgls.web.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xgls.web.entity.InstanceDataset;
import com.xgls.web.entity.InstanceDatasetMid;
import com.xgls.web.mapper.InstanceDatasetMapper;
import com.xgls.web.mapper.InstanceDatasetMidMapper;
import com.xgls.web.service.InstanceDatasetMidService;
import com.xgls.web.utils.InstanceDatasetPathUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class InstanceDatasetMidServiceImpl extends ServiceImpl<InstanceDatasetMidMapper, InstanceDatasetMid>
        implements InstanceDatasetMidService {

    @Autowired
    private InstanceDatasetMidMapper instanceDatasetMidMapper;

    @Autowired
    private InstanceDatasetMapper instanceDatasetMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${sys.instancecfg.instancedata-mid-root:/home/omen1/AI_TT_Platform/data/instance_dataset_mid/}")
    private String instanceDatasetMidRoot;

    /**
     * 列举预处理源数据集：优先 {@code instance_dataset_mid}，合并「仅中间导出」形态的 {@code instance_dataset} 行，
     * 并对磁盘上已存在标准导出目录但库中无记录的情况自动补库（与任务数据集导出目录结构一致）。
     */
    @Override
    public List<InstanceDatasetMid> getAllInstanceDatasets() {
        if (tableExists("instance_dataset_mid")) {
            materializeExportFoldersFromDisk();
        }
        Map<String, InstanceDatasetMid> merged = new LinkedHashMap<>();
        if (tableExists("instance_dataset_mid")) {
            for (InstanceDatasetMid m : instanceDatasetMidMapper.selectList(null)) {
                merged.putIfAbsent(midMergeKey(m), m);
            }
        }
        if (tableExists("instance_dataset")) {
            for (InstanceDataset d : instanceDatasetMapper.selectList(null)) {
                if (!isMidLikeLegacyInstanceDataset(d)) {
                    continue;
                }
                InstanceDatasetMid m = copyToMid(d);
                merged.putIfAbsent(midMergeKey(m), m);
            }
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 扫描中间导出根目录：对符合 train/test 结构且含训练样本的文件夹，若 mid 表无同名记录则插入一行，
     * 解决「磁盘已有导出、库无记录」导致预处理页无选项的问题。
     */
    private void materializeExportFoldersFromDisk() {
        String raw = StrUtil.trimToEmpty(instanceDatasetMidRoot);
        if (StrUtil.isBlank(raw)) {
            return;
        }
        Path root = Paths.get(raw.replaceAll("/+$", "")).normalize();
        if (!Files.isDirectory(root)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path child : stream) {
                if (!Files.isDirectory(child)) {
                    continue;
                }
                String folder = child.getFileName().toString();
                if (".".equals(folder) || "..".equals(folder)) {
                    continue;
                }
                if (midRowExistsForFolderName(folder)) {
                    continue;
                }
                InstanceDatasetMid probe = buildProbeFromExportFolder(child, folder);
                if (!InstanceDatasetPathUtil.isSourceInstanceDatasetOnDisk(probe, instanceDatasetMidRoot)) {
                    continue;
                }
                probe.setUsername(StrUtil.blankToDefault(probe.getUsername(), "磁盘同步"));
                probe.setCreatedTime(LocalDateTime.now());
                probe.setUpdatedTime(LocalDateTime.now());
                try {
                    this.save(probe);
                    log.info("[instance-mid] 已从磁盘补全中间集记录: name={}, trainImg={}", folder, probe.getTrainImagePath());
                } catch (Exception e) {
                    log.warn("[instance-mid] 磁盘补全写入失败 folder={}: {}", folder, e.toString());
                }
            }
        } catch (Exception e) {
            log.warn("[instance-mid] 扫描中间导出目录失败: {}", e.toString());
        }
    }

    private boolean midRowExistsForFolderName(String folder) {
        Long c1 = instanceDatasetMidMapper.selectCount(
                new LambdaQueryWrapper<InstanceDatasetMid>().eq(InstanceDatasetMid::getName, folder));
        Long c2 = instanceDatasetMidMapper.selectCount(
                new LambdaQueryWrapper<InstanceDatasetMid>().eq(InstanceDatasetMid::getFatherName, folder));
        return (c1 != null && c1 > 0) || (c2 != null && c2 > 0);
    }

    private static InstanceDatasetMid buildProbeFromExportFolder(Path base, String folder) {
        InstanceDatasetMid mid = new InstanceDatasetMid();
        mid.setFatherName(folder);
        mid.setName(folder);
        mid.setSensorType("外部");
        mid.setTargetType("复合");
        mid.setDataFormat(0);
        mid.setClassList("{}");
        mid.setClassNum(0);
        mid.setImgNum(0);
        mid.setAnnoNum(0);
        mid.setTrainImagePath(pathWithTrailingSlash(base.resolve("train").resolve("images")));
        mid.setTrainAnnoPath(pathWithTrailingSlash(base.resolve("train").resolve("anno")));
        mid.setTestImagePath(pathWithTrailingSlash(base.resolve("test").resolve("images")));
        mid.setTestAnnoPath(pathWithTrailingSlash(base.resolve("test").resolve("anno")));
        return mid;
    }

    private static String pathWithTrailingSlash(Path p) {
        String s = p.toAbsolutePath().normalize().toString().replace("\\", "/");
        return s.endsWith("/") ? s : s + "/";
    }

    private static String midMergeKey(InstanceDatasetMid m) {
        String t = StrUtil.trimToEmpty(m.getTrainImagePath()).replace("\\", "/").replaceAll("/+$", "");
        if (StrUtil.isNotBlank(t)) {
            return t.toLowerCase();
        }
        return "n:" + StrUtil.blankToDefault(m.getName(), "") + "|f:" + StrUtil.blankToDefault(m.getFatherName(), "");
    }

    /**
     * 历史上 {@link com.xgls.web.service.TaskDatasetDevService#saveMidRecord} 在无 mid 表时写入 {@code instance_dataset}；
     * 预处理后最终集通常带 config_list / param_schema，据此区分。
     */
    static boolean isMidLikeLegacyInstanceDataset(InstanceDataset d) {
        if (d == null) {
            return false;
        }
        String cfg = StrUtil.trimToEmpty(d.getConfigList());
        if (StrUtil.isNotBlank(cfg) && (cfg.startsWith("[") || cfg.contains("\"order\""))) {
            return false;
        }
        String ps = StrUtil.trimToEmpty(d.getParamSchema());
        if (StrUtil.isNotBlank(ps) && !"{}".equals(ps) && !"null".equalsIgnoreCase(ps)) {
            return false;
        }
        return StrUtil.isNotBlank(d.getTrainImagePath()) && StrUtil.isNotBlank(d.getTrainAnnoPath())
                && StrUtil.isNotBlank(d.getTestImagePath()) && StrUtil.isNotBlank(d.getTestAnnoPath());
    }

    private static InstanceDatasetMid copyToMid(InstanceDataset src) {
        InstanceDatasetMid mid = new InstanceDatasetMid();
        BeanUtils.copyProperties(src, mid);
        return mid;
    }

    private boolean tableExists(String tableName) {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                    Integer.class,
                    tableName);
            return cnt != null && cnt > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
