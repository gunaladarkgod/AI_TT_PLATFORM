package com.xgls.web.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xgls.web.base.AjaxResult;
import com.xgls.web.base.CodeMap;
import com.xgls.web.base.ErrorCode;
import com.xgls.web.entity.TrainLabel;
import com.xgls.web.service.EngineLabelService;
import com.xgls.web.service.TrainLabelService;
import com.xgls.web.vo.query.TrainLabelQuery;

import cn.hutool.core.util.StrUtil;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "训练标签集管理")
@RestController
@RequestMapping("/trainLabel")
public class TrainLabelController {
    @Autowired
    TrainLabelService trainLabelService;
    @Autowired
    EngineLabelService engineLabelService;

    @PostMapping("all")
    public AjaxResult queryAll(TrainLabelQuery query) {
        String nameLike = query.getName();
        LambdaQueryWrapper<TrainLabel> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(nameLike)) {
            wrapper.like(TrainLabel::getName, nameLike);
        }
        String nickLike = query.getNick();
        if (StrUtil.isNotBlank(nickLike)) {
            wrapper.like(TrainLabel::getNick, nickLike);
        }
        Integer merge = query.getMerge();
        if (merge != null && merge >= 0) {
            wrapper.eq(TrainLabel::getMerge, merge);
        }
        return AjaxResult.success(trainLabelService.list(wrapper));
    }

    @PostMapping("add")
    public AjaxResult add(TrainLabel record) {
        String name = record.getName();
        Integer merge = record.getMerge();
        String children = record.getChildren();
        if (StrUtil.isBlank(name) || merge != CodeMap.LABEL_IS_MERAGE || StrUtil.isBlank(children)) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        record.setId(null);
        LambdaQueryWrapper<TrainLabel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrainLabel::getName, name);
        if (trainLabelService.getOne(wrapper, false) != null) {
            return AjaxResult.error("名称已存在");
        }
        return trainLabelService.save(record) ? AjaxResult.success() : AjaxResult.error();
    }

    @PostMapping("update")
    public AjaxResult update(Integer id, String nick, String children) {
        if (id == null) {
            return AjaxResult.error();
        }
        TrainLabel record = new TrainLabel();
        record.setId(id);
        record.setNick(nick == null ? "" : nick);
        record.setChildren(children);
        return trainLabelService.updateById(record) ? AjaxResult.success() : AjaxResult.error();
    }

    @PostMapping("del")
    public AjaxResult delete(Integer id) {
        if (id == null) {
            return AjaxResult.error();
        }
        return trainLabelService.removeById(id) ? AjaxResult.success() : AjaxResult.error();
    }

    @PostMapping("sync/all")
    public AjaxResult syncAll() {
        List<TrainLabel> list = engineLabelService.queryNewLabels();
        if (!list.isEmpty()) {
            return trainLabelService.saveBatch(list) ? AjaxResult.success() : AjaxResult.error();
        }
        return AjaxResult.success();
    }

}
