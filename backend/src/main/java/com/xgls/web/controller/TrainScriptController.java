package com.xgls.web.controller;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xgls.web.base.AjaxResult;
import com.xgls.web.base.CodeMap;
import com.xgls.web.base.ErrorCode;
import com.xgls.web.entity.TrainScript;
import com.xgls.web.service.TrainScriptService;
import com.xgls.web.vo.query.TrainScriptQuery;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Tag(name = "算法管理")
@RestController
@RequestMapping("/trainScript")
@Slf4j
public class TrainScriptController {
    @Value("${sys.conda}")
    String condaPath;
    @Value("${sys.root-upload}")
    String rootPath;
    @Autowired
    TrainScriptService trainScriptService;

    @Operation(summary = "分页获取列表", description = "分页获取列表")
    @PostMapping("list")
    public AjaxResult queryList(TrainScriptQuery query) {
        /** 查询条件 */
        LambdaQueryWrapper<TrainScript> wrapper = Wrappers.lambdaQuery(TrainScript.class);
        if (query.getId() != null) {
            wrapper.eq(TrainScript::getId, query.getId());
        }
        if (StrUtil.isNotBlank(query.getType())) {
            wrapper.eq(TrainScript::getType, query.getType());
        }
        /** 用户名 模糊检索 */
        if (StrUtil.isNotBlank(query.getName())) {
            wrapper.like(TrainScript::getName, query.getName());
        }

        /** 分页信息 */
        Long current = query.getCurrent();
        Long size = query.getSize();
        if (current == null) {
            current = CodeMap.PAGE_NO_DEFAULT;
        }
        if (size == null) {
            size = CodeMap.PAGE_SIZE_DEFAULT;
        }
        Page<TrainScript> page = new Page<>(current, size);
        Page<TrainScript> res = trainScriptService.page(page, wrapper);
        return AjaxResult.success(res);
    }

    @Operation(summary = "获取全部", description = "获取全部")
    @PostMapping("all")
    public AjaxResult queryAll(TrainScriptQuery query) {
        /** 查询条件 */
        LambdaQueryWrapper<TrainScript> wrapper = Wrappers.lambdaQuery(TrainScript.class);
        if (StrUtil.isNotBlank(query.getType())) {
            wrapper.eq(TrainScript::getType, query.getType());
        }
        /** 用户名 模糊检索 */
        if (StrUtil.isNotBlank(query.getName())) {
            wrapper.like(TrainScript::getName, query.getName());
        }
        return AjaxResult.success(trainScriptService.list(wrapper));
    }

    @Operation(summary = "添加", description = "添加")
    @PostMapping("add")
    public AjaxResult add(TrainScript record) throws IOException {

        if (StrUtil.isBlank(record.getName())
                || StrUtil.isBlank(record.getEnv()) || StrUtil.isBlank(record.getCmd())
                || StrUtil.isBlank(record.getMain()) || StrUtil.isBlank(record.getType())) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        String cfg = record.getCfg();
        if (StrUtil.isBlank(cfg) || !JSONUtil.isTypeJSON(cfg)) {
            record.setCfg(null);
        }
        String name = record.getName().trim();
        record.setName(name);
        LambdaQueryWrapper<TrainScript> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrainScript::getName, name);
        if (trainScriptService.getOne(wrapper, false) != null) {
            return AjaxResult.error("算法名称已经存在");
        }

        record.setId(null);
        record.setUptime(LocalDateTime.now());
        record.setSuff(FileUtil.isWindows() ? ".bat" : ".sh");
        return trainScriptService.saveLink(record) ? AjaxResult.success() : AjaxResult.error();
    }

    @Operation(summary = "修改", description = "修改")
    @PostMapping("update")
    public AjaxResult update(TrainScript record) throws IOException {
        Integer id = record.getId();
        if (id == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        String cfg = record.getCfg();
        if (StrUtil.isBlank(cfg) || !JSONUtil.isTypeJSON(cfg)) {
            record.setCfg(null);
        }
        record.setSuff(FileUtil.isWindows() ? ".bat" : ".sh");
        return trainScriptService.updateByIdLink(record) ? AjaxResult.success() : AjaxResult.error();
    }

    @Operation(summary = "删除", description = "删除")
    @PostMapping("del")
    public AjaxResult del(Integer id) {
        if (id == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        TrainScript tf = trainScriptService.getById(id);
        if (tf == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        boolean flg = trainScriptService.removeById(id);

        if (flg) {
            try {
                FileUtil.del(
                        Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_SCRIPT, id + tf.getSuff()));
            } catch (Exception e) {
            }
            return AjaxResult.success();
        }
        return AjaxResult.error();
    }
}
