package com.xgls.web.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xgls.web.base.AjaxResult;
import com.xgls.web.base.CodeMap;
import com.xgls.web.base.ErrorCode;
import com.xgls.web.entity.UserProject;
import com.xgls.web.service.UserProjectService;
import com.xgls.web.utils.SessionUtil;

import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "用户-项目管理")
@RestController
@RequestMapping("/userProject")
public class UserProjectController {
    @Autowired
    UserProjectService userProjectService;

    @Operation(summary = "获取用户关联的项目列表", description = "获取用户关联的项目列表")
    @PostMapping("query")
    public AjaxResult queryAll(@Parameter(description = "用户id,不允许为空") Integer user_id) {
        if (user_id == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        if (!SessionUtil.isAdminOrHeigh()) {
            return AjaxResult.error(ErrorCode.AUTH_FAILED);
        }
        LambdaQueryWrapper<UserProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserProject::getUser_id, user_id);
        return AjaxResult.success(userProjectService.list(wrapper));
    }

    @Operation(summary = "保存用户-项目关联关系", description = "全量更新")
    @PostMapping("save")
    public AjaxResult save(@Parameter(description = "用户id", required = true) @RequestParam Integer user_id,
            @Parameter(description = "项目ids,使用逗号分割,空代表没有") @RequestParam String project_ids) {
        if (user_id == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        if (!SessionUtil.isAdminOrHeigh()) {
            return AjaxResult.error(ErrorCode.AUTH_FAILED);
        }
        // ids的格式
        if (StrUtil.isNotEmpty(project_ids) && !ReUtil.isMatch(CodeMap.RE_IDS, project_ids)) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        /** 先删除全部的user_id */
        LambdaUpdateWrapper<UserProject> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserProject::getUser_id, user_id);
        userProjectService.remove(wrapper);
        /** 再添加 */
        if (StrUtil.isNotEmpty(project_ids)) {
            String[] ids = project_ids.split(",");
            List<UserProject> arr = new ArrayList<>();
            for (int i = 0; i < ids.length; i++) {
                UserProject up = new UserProject();
                up.setUser_id(user_id);
                up.setProject_id(Integer.parseInt(ids[i]));
                arr.add(up);
            }
            if (userProjectService.saveBatch(arr)) {
                return AjaxResult.success();
            } else {
                return AjaxResult.error();
            }
        }
        return AjaxResult.success();
    }
}
