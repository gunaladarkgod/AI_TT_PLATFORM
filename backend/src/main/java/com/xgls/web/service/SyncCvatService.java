package com.xgls.web.service;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import com.xgls.web.base.CodeMap;
import com.xgls.web.entity.EngineLabel;
import com.xgls.web.entity.TrainLabel;
import com.xgls.web.utils.CvatApiUtil;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class SyncCvatService {
    private final EngineLabelService engineLabelService;
    private final TrainLabelService trainLabelService;

    /** 同步指定项目的标签到 engine_label，并联动 train_label 的增删 */
    public void syncLabels(Integer project_id) {
        ResponseEntity<String> res = CvatApiUtil.getLabelList(project_id);
        if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
            log.warn("syncLabels: request failed for project {}", project_id);
            return;
        }

        JSONObject body = new JSONObject(res.getBody());
        List<EngineLabel> labels_now = body.getBeanList("results", EngineLabel.class);
        // CVAT 返回里一般没有 project_id，这里补齐
        for (EngineLabel e : labels_now) e.setProject_id(project_id);

        // 取现有
        var w = new LambdaQueryWrapper<EngineLabel>()
                .eq(EngineLabel::getProject_id, project_id)
                .orderByAsc(EngineLabel::getId);
        List<EngineLabel> labels_exist = engineLabelService.list(w);

        // 不同则覆盖更新
        if (!compareList(labels_now, labels_exist)) {
            updateLabelList(project_id, labels_now);

            // 同步 TrainLabel（新增 & 清理“仅此项目使用且已删除”的标签）
            Set<String> nowSet = labels_now.stream().map(EngineLabel::getName).collect(Collectors.toSet());
            Set<String> oldSet = labels_exist.stream().map(EngineLabel::getName).collect(Collectors.toSet());
            Set<String> otherSet = new HashSet<>(engineLabelService.queryDistinctNames(project_id));

            // add
            List<TrainLabel> addLabels = nowSet.stream()
                    .filter(n -> !oldSet.contains(n) && !otherSet.contains(n))
                    .map(n -> {
                        TrainLabel t = new TrainLabel();
                        t.setName(n);
                        return t;
                    }).toList();
            if (!addLabels.isEmpty()) trainLabelService.saveBatch(addLabels);

            // remove（仅删除 merge=原始 且在其它项目也不再使用的）
            List<String> delNames = oldSet.stream()
                    .filter(o -> !nowSet.contains(o) && !otherSet.contains(o))
                    .toList();
            if (!delNames.isEmpty()) {
                var uw = new LambdaUpdateWrapper<TrainLabel>()
                        .eq(TrainLabel::getMerge, CodeMap.LABEL_IS_ORG)
                        .in(TrainLabel::getName, delNames);
                trainLabelService.remove(uw);
            }
        }
    }

    /** 比较两组标签是否完全一致（按 id 排序后逐项比较关键字段） */
    private boolean compareList(List<EngineLabel> now, List<EngineLabel> exist) {
        if (now.size() != exist.size()) return false;
        // 排序保证逐项比较
        Comparator<EngineLabel> cmp = Comparator.comparing(EngineLabel::getId, Comparator.nullsFirst(Integer::compareTo));
        now = now.stream().sorted(cmp).toList();
        exist = exist.stream().sorted(cmp).toList();
        for (int i = 0; i < now.size(); i++) {
            EngineLabel a = now.get(i), b = exist.get(i);
            if (!Objects.equals(a.getId(), b.getId())) return false;
            if (!StrUtil.equals(a.getName(), b.getName())) return false;
            if (!StrUtil.equals(a.getColor(), b.getColor())) return false;
            if (!StrUtil.equals(a.getType(), b.getType())) return false;
            if (!Objects.equals(a.getParent_id(), b.getParent_id())) return false;
            // task_id 不是关键字段，一般忽略
        }
        return true;
    }

    /** 用“全量覆盖”的方式更新某项目的 engine_label */
    @Transactional
    protected void updateLabelList(Integer project_id, List<EngineLabel> labels_now) {
        // 先删本项目全部旧数据
        engineLabelService.remove(new LambdaQueryWrapper<EngineLabel>()
                .eq(EngineLabel::getProject_id, project_id));
        // 补齐 project_id 并批量写入
        for (EngineLabel e : labels_now) e.setProject_id(project_id);
        if (!labels_now.isEmpty()) {
            engineLabelService.saveBatch(labels_now);
        }
    }
}
