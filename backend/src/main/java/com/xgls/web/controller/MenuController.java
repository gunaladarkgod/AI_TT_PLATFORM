package com.xgls.web.controller;

import java.util.ArrayList;
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
import com.xgls.web.entity.Menu;
import com.xgls.web.entity.RoleMenu;
import com.xgls.web.service.MenuService;
import com.xgls.web.service.RoleMenuService;
import com.xgls.web.utils.SessionUtil;
import com.xgls.web.vo.query.MenuQuery;

import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "菜单管理")
@RestController
@RequestMapping("/menu")
public class MenuController {
    @Autowired
    MenuService menuService;
    @Autowired
    RoleMenuService roleMenuService;

    @Operation(summary = "分页获取菜单列表", description = "分页获取菜单列表,支持名称检索")
    @PostMapping("list")
    public AjaxResult queryList(MenuQuery query) {
        Integer roleId = SessionUtil.getCurRoleId();
        if (roleId == null) {
            return AjaxResult.error(ErrorCode.AUTH_FAILED);
        }
        LambdaQueryWrapper<Menu> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(query.getName())) {
            wrapper.like(Menu::getName, query.getName());
        }
        if (!CodeMap.USER_TYPE_SYS.equals(roleId)) {
            // 查询role_menu表
            LambdaQueryWrapper<RoleMenu> wrapper2 = new LambdaQueryWrapper<>();
            wrapper2.eq(RoleMenu::getRole_id, roleId);
            List<Integer> menuIdList = roleMenuService.list(wrapper2).stream().map(item -> item.getMenu_id()).toList();
            if (menuIdList.isEmpty()) {
                return AjaxResult.success(new Page<>());
            }
            wrapper.in(Menu::getId, menuIdList);
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
        Page<Menu> page = new Page<>(current, size);

        /** 排序信息 */
        List<OrderItem> orders = query.getOrders();
        if (orders != null && !orders.isEmpty()) {
            page.addOrder(orders);
        } else {
            page.addOrder(OrderItem.asc("order_num"));
        }
        return AjaxResult.success(menuService.page(page, wrapper));
    }

    @Operation(summary = "获取全部菜单列表", description = "获取全部菜单列表,支持名称检索")
    @PostMapping("all")
    public AjaxResult queryAll(Menu query) {
        Integer roleId = SessionUtil.getCurRoleId();
        if (roleId == null) {
            return AjaxResult.error(ErrorCode.AUTH_FAILED);
        }
        LambdaQueryWrapper<Menu> wrapper = new LambdaQueryWrapper<>();

        if (StrUtil.isNotBlank(query.getName())) {
            wrapper.like(Menu::getName, query.getName());
        }
        if (!CodeMap.USER_TYPE_SYS.equals(roleId)) {
            // 查询role_menu表
            LambdaQueryWrapper<RoleMenu> wrapper2 = new LambdaQueryWrapper<>();
            wrapper2.eq(RoleMenu::getRole_id, roleId);
            List<Integer> menuIdList = roleMenuService.list(wrapper2).stream().map(item -> item.getMenu_id()).toList();
            if (menuIdList.isEmpty()) {
                return AjaxResult.success(new ArrayList<>(0));
            }
            wrapper.in(Menu::getId, menuIdList);
        }
        return AjaxResult.success(menuService.list(wrapper));
    }

    @Operation(summary = "添加菜单", description = "添加菜单")
    @PostMapping("add")
    public AjaxResult add(Menu record) {
        if (!SessionUtil.isSuperSys()) {
            return AjaxResult.error(ErrorCode.AUTH_FAILED);
        }
        String name = record.getName();
        String url = record.getUrl();
        if (StrUtil.isBlank(name) || StrUtil.isBlank(url)) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        LambdaQueryWrapper<Menu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Menu::getName, name);
        if (menuService.exists(wrapper)) {
            return AjaxResult.error(ErrorCode.MENU_NAME_EXIST);
        }
        wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Menu::getUrl, url);
        if (menuService.exists(wrapper)) {
            return AjaxResult.error(ErrorCode.MENU_URL_EXIST);
        }
        return menuService.save(record) ? AjaxResult.success() : AjaxResult.error();
    }

    @Operation(summary = "修改菜单", description = "修改菜单")
    @PostMapping("update")
    public AjaxResult update(Menu record) {
        if (!SessionUtil.isSuperSys()) {
            return AjaxResult.error(ErrorCode.AUTH_FAILED);
        }
        Integer id = record.getId();
        String name = record.getName();
        String url = record.getUrl();
        if (id == null || StrUtil.isBlank(name) || StrUtil.isBlank(url)) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        /** 先看是否存在 */
        Menu menu = menuService.getById(id);
        if (menu == null) {
            return AjaxResult.error(ErrorCode.MENU_NOT_EXIST);
        }

        LambdaQueryWrapper<Menu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Menu::getName, name).ne(Menu::getId, id);
        if (menuService.exists(wrapper)) {
            return AjaxResult.error(ErrorCode.MENU_NAME_EXIST);
        }
        wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Menu::getUrl, url).ne(Menu::getId, id);
        if (menuService.exists(wrapper)) {
            return AjaxResult.error(ErrorCode.MENU_URL_EXIST);
        }
        return menuService.updateById(record) ? AjaxResult.success() : AjaxResult.error();
    }

    @Operation(summary = "删除菜单", description = "删除菜单")
    @PostMapping("del")
    public AjaxResult del(@Parameter(description = "菜单Id") @RequestParam Integer id) {
        if (!SessionUtil.isSuperSys()) {
            return AjaxResult.error(ErrorCode.AUTH_FAILED);
        }
        return menuService.removeById(id) ? AjaxResult.success() : AjaxResult.error();
    }

    @Operation(summary = "获取指定角色的全部菜单列表", description = "获取指定角色的全部菜单列表")
    @PostMapping("role/list")
    public AjaxResult queryAllByRole(@Parameter(description = "角色Id") @RequestParam Integer role_id) {
        if (!SessionUtil.isSuperSys()) {
            return AjaxResult.error(ErrorCode.AUTH_FAILED);
        }
        LambdaQueryWrapper<Menu> wrapper = new LambdaQueryWrapper<>();
        if (!CodeMap.USER_TYPE_SYS.equals(role_id)) {
            // 查询role_menu表
            LambdaQueryWrapper<RoleMenu> wrapper2 = new LambdaQueryWrapper<>();
            wrapper2.eq(RoleMenu::getRole_id, role_id);
            List<Integer> menuIdList = roleMenuService.list(wrapper2).stream().map(item -> item.getMenu_id()).toList();
            if (menuIdList.isEmpty()) {
                return AjaxResult.success(new ArrayList<>());
            }
            wrapper.in(Menu::getId, menuIdList);
        }
        return AjaxResult.success(menuService.list(wrapper));
    }

    @Operation(summary = "设置角色-菜单关联信息", description = "设置角色-菜单关联信息")
    @PostMapping("role/save")
    public AjaxResult queryAllByRole(@Parameter(description = "角色Id") @RequestParam Integer role_id,
            @Parameter(description = "菜单Ids,逗号分隔") @RequestParam String menu_ids) {
        if (!SessionUtil.isSuperSys()) {
            return AjaxResult.error(ErrorCode.AUTH_FAILED);
        }
        if (role_id == null || role_id.equals(CodeMap.USER_TYPE_SYS)) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        if (!ReUtil.isMatch(CodeMap.RE_IDS, menu_ids)) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        return menuService.saveRoleMenus(role_id, menu_ids) ? AjaxResult.success() : AjaxResult.error();
    }
}
