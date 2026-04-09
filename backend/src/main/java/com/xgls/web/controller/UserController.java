package com.xgls.web.controller;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xgls.web.base.AjaxResult;
import com.xgls.web.base.CodeMap;
import com.xgls.web.base.ErrorCode;
import com.xgls.web.entity.User;
import com.xgls.web.service.RedisService;
import com.xgls.web.service.UserService;
import com.xgls.web.utils.JwtUtils;
import com.xgls.web.vo.query.UserQuery;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    UserService userService;
    @Autowired
    RedisService redisService;


    @Operation(summary = "获取用户列表", description = "获取用户列表")
    @PostMapping("list")
    public AjaxResult queryList(UserQuery query) {
        /** 查询条件 */
        LambdaQueryWrapper<User> wrapper = Wrappers.lambdaQuery(User.class);

        /** 用户名 模糊检索 */
        if (StrUtil.isNotBlank(query.getUsername())) {
            wrapper.like(User::getUsername, query.getUsername());
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
        Page<User> page = new Page<>(current, size);

        /** 排序信息 */
        List<OrderItem> orders = query.getOrders();
        if (orders != null && !orders.isEmpty()) {
            page.addOrder(orders);
        }
        Page<User> res = userService.page(page, wrapper);
        List<User> list = res.getRecords();
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setPmd(null);
        }

        return AjaxResult.success(res);
    }

    @Operation(summary = "添加用户", description = "生成默认密码")
    @PostMapping("add")
    public AjaxResult add(User user) {
        String username = StrUtil.trim(user.getUsername());// 剔除空格
        if (StrUtil.isBlank(username)) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        user.setUsername(username);
        if (CodeMap.USER_STATUS_LOCK != user.getStatus()) {// 默认激活
            user.setStatus(CodeMap.USER_STATUS_OK);
        }
        LambdaQueryWrapper<User> warp = new QueryWrapper<User>().lambda().eq(User::getUsername, username);
        if (userService.count(warp) > 0) {
            return AjaxResult.error(ErrorCode.ACCOUNT_HAS_EXIST);
        }

        user.setId(null);
        user.setAddtime(DateTime.now().toString());
        if (StrUtil.isBlank(user.getNickname())) {
            user.setNickname(username);
        }

        // 加默认后缀
        String pwd = SecureUtil.md5(username + CodeMap.PWD_SUFFIX_DEFAULT);
        // 加盐
        pwd = SecureUtil.md5(pwd + CodeMap.XGLS);
        user.setPmd(pwd);

        return userService.save(user) ? AjaxResult.success() : AjaxResult.error();
    }

    @Operation(summary = "修改用户信息", description = "修改用户信息")
    @PostMapping("update")
    public AjaxResult update(User user) {
        // id 必须有
        Long id = user.getId();
        if (id == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        // 查询现有的user信息
        User exist = userService.getById(id);
        if (exist == null) {
            return AjaxResult.error(ErrorCode.USER_NOT_EXIST);
        }
        // username 不允许修改
        // pmd 不允许修改
        // addtime 不允许修改
        user.setUsername(null);
        user.setPmd(null);
        user.setAddtime(null);

        if (userService.updateById(user)) {
            // 查看type是否发生改变,如果改变,更新jwt
            Integer type = user.getType();
            if (type != null && !type.equals(exist.getType())) {
                Set<Object> set = redisService.getUserJwt(id);
                if (set != null && !set.isEmpty()) {
                    user.setUsername(exist.getUsername());
                    String newToken = JwtUtils.generateTokenForUser(user);
                    for (Object item : set) {
                        redisService.updateJwtToken(item.toString(), newToken);
                    }
                }
            }
            return AjaxResult.success();
        } else {
            return AjaxResult.error();
        }
    }

    @Operation(summary = "删除用户", description = "删除用户")
    @PostMapping("del")
    public AjaxResult del(Long id) {
        if (id == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }

        if (userService.removeById(id)) {
            // 同时删除所有的用户相关jwt
            Set<Object> set = redisService.getUserJwt(id);
            if (set != null && !set.isEmpty()) {
                for (Object item : set) {
                    String token = item.toString();
                    redisService.removeJwtToken(token);
                }
                redisService.delUserAllJwt(id);
            }
            return AjaxResult.success();
        }

        return AjaxResult.error();
    }

    @Operation(summary = "修改密码")
    @PostMapping("pwd")
    public AjaxResult updatePwd(String username, String pmd1, String pmd2, HttpServletRequest request) {
        if (StrUtil.isBlank(username) || StrUtil.isBlank(pmd1) || StrUtil.isBlank(pmd2)) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        String jwt = request.getHeader(CodeMap.X_ACCESS_TOKEN);
        User user = JwtUtils.verifyAndGetUser(jwt);
        if (user != null && username.equals(user.getUsername())) {
            // 可以修改
            pmd1 = SecureUtil.md5(pmd1 + CodeMap.XGLS);
            pmd2 = SecureUtil.md5(pmd2 + CodeMap.XGLS);
            LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(User::getId, user.getId()).eq(User::getUsername, username).eq(User::getPmd, pmd1);
            wrapper.set(User::getPmd, pmd2);
            return userService.update(wrapper) ? AjaxResult.success() : AjaxResult.error();
        }
        return AjaxResult.error(ErrorCode.AUTH_FAILED);
    }

    @Operation(summary = "修改密码")
    @PostMapping("reset")
    public AjaxResult resetPwd(Long id) {

        if (id == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        User record = userService.getById(id);
        if (record == null) {
            return AjaxResult.error(ErrorCode.ACCOUNT_NOT_EXIST);
        }
        String pwd = SecureUtil.md5(record.getUsername() + CodeMap.PWD_SUFFIX_DEFAULT);
        pwd = SecureUtil.md5(pwd + CodeMap.XGLS);
        record.setPmd(pwd);
        return userService.updateById(record) ? AjaxResult.success() : AjaxResult.error();
    }

}
