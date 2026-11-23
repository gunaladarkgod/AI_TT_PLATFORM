package com.xgls.web.utils;

import com.xgls.web.base.CodeMap;
import com.xgls.web.entity.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticatedUserUtilTest {
    @Test
    void researchComponentPermissionMatchesMaintainerRoles() {
        assertFalse(AuthenticatedUserUtil.canManageResearchComponents(null));
        assertTrue(AuthenticatedUserUtil.canManageResearchComponents(user(CodeMap.USER_TYPE_SYS)));
        assertTrue(AuthenticatedUserUtil.canManageResearchComponents(user(CodeMap.USER_TYPE_ADMIN)));
        assertTrue(AuthenticatedUserUtil.canManageResearchComponents(user(CodeMap.USER_TYPE_IMPROVEMENT)));
        assertFalse(AuthenticatedUserUtil.canManageResearchComponents(user(CodeMap.USER_TYPE_OTHER)));
    }

    private User user(Integer type) {
        User user = new User();
        user.setType(type);
        return user;
    }
}
