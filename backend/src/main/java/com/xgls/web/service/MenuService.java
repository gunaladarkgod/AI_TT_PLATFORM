package com.xgls.web.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xgls.web.entity.Menu;
import com.xgls.web.entity.RoleMenu;
import com.xgls.web.mapper.MenuMapper;
import com.xgls.web.mapper.RoleMenuMapper;
import com.xgls.web.service.MenuService;

@Service
public class MenuService extends ServiceImpl<MenuMapper, Menu> {
    @Autowired
    RoleMenuMapper roleMenuMapper;

    @Transactional(rollbackFor = Exception.class)
    public boolean saveRoleMenus(Integer role_id, String menu_ids) {
        /** 1.先查询现有菜单 */
        LambdaQueryWrapper<RoleMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RoleMenu::getRole_id, role_id);
        List<RoleMenu> existList = roleMenuMapper.selectList(wrapper);
        Set<Integer> existMap = new HashSet<>();
        existList.forEach(item -> {
            existMap.add(item.getMenu_id());
        });

        /** 2.插入新增的菜单 */
        String[] arr = menu_ids.split(",");
        Set<Integer> menuSet = new HashSet<>();
        for (int i = 0; i < arr.length; i++) {
            Integer menu_id = Integer.parseInt(arr[i]);
            if (!existMap.contains(menu_id)) {// 不在现有id中,代表要新增
                RoleMenu roleMenu = new RoleMenu();
                roleMenu.setRole_id(role_id);
                roleMenu.setMenu_id(menu_id);
                if (roleMenuMapper.insert(roleMenu) == 0) {
                    throw new RuntimeException();
                }
            }
            menuSet.add(menu_id);
        }
        /** 3. 删除不在菜单 */
        List<Long> delList = new ArrayList<>();
        for (RoleMenu item : existList) {
            if (!menuSet.contains(item.getMenu_id())) {
                delList.add(item.getId());
            }
        }
        if (!delList.isEmpty()) {
            roleMenuMapper.deleteBatchIds(delList);
        }
        return true;
    }

}
