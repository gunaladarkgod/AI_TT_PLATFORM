package com.xgls.web.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xgls.web.base.AjaxResult;
import com.xgls.web.base.CodeMap;
import com.xgls.web.base.ErrorCode;
import com.xgls.web.entity.ProfileTrain;
import com.xgls.web.service.ProfileTrainService;
import com.xgls.web.utils.SessionUtil;
import com.xgls.web.vo.query.ProfileTrainQuery;

import cn.hutool.core.util.StrUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "模型训练模板管理")
@RestController
@RequestMapping("/profile_train")
public class ProfileTrainController {
    @Autowired
    ProfileTrainService profileTrainService;

    @Operation(summary = "分页获取模板列表", description = "分页获取模板列表,支持名称检索")
    @PostMapping("list")
    public AjaxResult queryList(ProfileTrainQuery query) {
        LambdaQueryWrapper<ProfileTrain> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(query.getName())) {
            wrapper.like(ProfileTrain::getName, query.getName());
        }
        if (query.getAlg_id() != null) {
            wrapper.eq(ProfileTrain::getAlg_id, query.getAlg_id());
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
        Page<ProfileTrain> page = new Page<>(current, size);

        /** 排序信息 */
        List<OrderItem> orders = query.getOrders();
        if (orders != null && !orders.isEmpty()) {
            page.addOrder(orders);
        }
        return AjaxResult.success(profileTrainService.page(page, wrapper));
    }

    @Operation(summary = "获取全部训练模板列表", description = "获取全部训练模板列表,支持名称检索")
    @PostMapping("all")
    public AjaxResult queryAll(ProfileTrain query) {
        LambdaQueryWrapper<ProfileTrain> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(query.getName())) {
            wrapper.like(ProfileTrain::getName, query.getName());
        }
        if (query.getAlg_id() != null) {
            wrapper.eq(ProfileTrain::getAlg_id, query.getAlg_id());
        }
        return AjaxResult.success(profileTrainService.list(wrapper));
    }

    @Operation(summary = "根据id查询模板", description = "根据id查询模板")
    @PostMapping("id")
    public AjaxResult queryById(@Parameter(description = "模板Id") @RequestParam Integer id) {
        if (id == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        return AjaxResult.success(profileTrainService.getById(id));
    }

    @Operation(summary = "添加模板", description = "添加模板")
    @PostMapping("add")
    public AjaxResult add(ProfileTrain record) {
        if (!SessionUtil.isAdminOrHeigh()) {
            return AjaxResult.error(ErrorCode.AUTH_FAILED);
        }
        String name = record.getName();
        if (StrUtil.isBlank(name)) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        LambdaQueryWrapper<ProfileTrain> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProfileTrain::getName, name);
        if (profileTrainService.exists(wrapper)) {
            return AjaxResult.error(ErrorCode.NAME_HAS_EXIST);
        }

        return profileTrainService.save(record) ? AjaxResult.success() : AjaxResult.error();
    }

    @Operation(summary = "修改模板", description = "修改模板")
    @PostMapping("update")
    public AjaxResult update(ProfileTrain record) {
        if (!SessionUtil.isAdminOrHeigh()) {
            return AjaxResult.error(ErrorCode.AUTH_FAILED);
        }
        Integer id = record.getId();
        if (id == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        /** 先看是否存在 */
        ProfileTrain exist = profileTrainService.getById(id);
        if (exist == null) {
            return AjaxResult.error(ErrorCode.RECORD_NOT_EXIST);
        }
        if (record.getName() != null) {
            LambdaQueryWrapper<ProfileTrain> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ProfileTrain::getName, record.getName()).ne(ProfileTrain::getId, id);
            if (profileTrainService.exists(wrapper)) {
                return AjaxResult.error(ErrorCode.NAME_HAS_EXIST);
            }
        }

        return profileTrainService.updateById(record) ? AjaxResult.success() : AjaxResult.error();
    }

    @Operation(summary = "删除模板", description = "删除模板")
    @PostMapping("del")
    public AjaxResult del(@Parameter(description = "模板Id") @RequestParam Integer id) {
        if (!SessionUtil.isAdminOrHeigh()) {
            return AjaxResult.error(ErrorCode.AUTH_FAILED);
        }
        return profileTrainService.removeById(id) ? AjaxResult.success() : AjaxResult.error();
    }
}
