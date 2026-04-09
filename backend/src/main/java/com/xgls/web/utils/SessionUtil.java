package com.xgls.web.utils;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;

import com.xgls.web.base.CodeMap;
import com.xgls.web.entity.User;

import cn.hutool.core.util.StrUtil;

public class SessionUtil {
    /** 系统管理员 */
    public static boolean isSuperSys() {
        Integer roleId = getCurRoleId();
        return roleId == null ? false : CodeMap.USER_TYPE_SYS.equals(roleId);
    }

    /** 系统管理员或者管理员 */
    public static boolean isAdminOrHeigh() {
        Integer roleId = getCurRoleId();
        return roleId == null ? false
                : (CodeMap.USER_TYPE_SYS.equals(roleId) || CodeMap.USER_TYPE_ADMIN.equals(roleId));
    }

    /** 具备管理权限或者就是用户自己 */
    public static boolean hasAdminOrSelf(String username) {
        User user = getCurUser();
        if (user == null || user.getType() == null) {
            return false;
        }
        Integer roleId = user.getType();
        return roleId == null ? false
                : (CodeMap.USER_TYPE_SYS.equals(roleId) || CodeMap.USER_TYPE_ADMIN.equals(roleId)
                        || StrUtil.equals(user.getUsername(), username));
    }

    /** 获取当前用户 */
    public static User getCurUser() {
        Subject currentUser = SecurityUtils.getSubject();
        Object o = currentUser.getSession().getAttribute(CodeMap.CUR_USER);
        return o == null ? null : (User) o;
    }

    public static Long getCurUserId() {
        Subject currentUser = SecurityUtils.getSubject();
        Object o = currentUser.getSession().getAttribute(CodeMap.CUR_USER);
        return o == null ? null : ((User) o).getId();
    }

    public static Integer getCurRoleId() {
        Subject currentUser = SecurityUtils.getSubject();
        Object o = currentUser.getSession().getAttribute(CodeMap.CUR_USER);
        return o == null ? null : ((User) o).getType();
    }
}
