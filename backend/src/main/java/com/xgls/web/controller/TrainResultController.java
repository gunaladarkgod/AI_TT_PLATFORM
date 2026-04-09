package com.xgls.web.controller;

import java.util.List;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xgls.web.base.CodeMap;
import com.xgls.web.entity.TrainScript;
import com.xgls.web.entity.TrainTask;
import com.xgls.web.vo.query.TrainTaskQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xgls.web.base.AjaxResult;
import com.xgls.web.entity.TrainResult;
import com.xgls.web.service.TrainResultService;

import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "训练结果管理")
@RestController
@RequestMapping("/trainResult")
public class TrainResultController {

    @Autowired
    private TrainResultService trainResultService;

    @PostMapping("all")
    public AjaxResult queryAll(TrainTaskQuery query) {
        LambdaQueryWrapper<TrainResult> wrapper = new LambdaQueryWrapper<>();
        /** 分页信息 */
        Long current = query.getCurrent();
        Long size = query.getSize();
        if (current == null) {
            current = CodeMap.PAGE_NO_DEFAULT;
        }
        if (size == null) {
            size = CodeMap.PAGE_SIZE_DEFAULT;
        }
        Page<TrainResult> page = new Page<>(current, size);
        return AjaxResult.success(trainResultService.page(page, wrapper));
    }
}
