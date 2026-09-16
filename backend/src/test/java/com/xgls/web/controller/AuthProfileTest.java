package com.xgls.web.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xgls.web.base.CodeMap;
import com.xgls.web.entity.User;
import com.xgls.web.service.RedisService;
import com.xgls.web.service.UserService;
import com.xgls.web.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthProfileTest {
    private AuthController controller() {
        AuthController controller = new AuthController();
        controller.userService = mock(UserService.class);
        controller.redisService = mock(RedisService.class);
        return controller;
    }

    @Test
    void missingAndRevokedTokensCannotReadOrSaveProfile() {
        AuthController controller = controller();
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertFalse(controller.profile(request).isSuccess());
        request.addHeader(CodeMap.X_ACCESS_TOKEN, "revoked");
        assertFalse(controller.saveProfile(request, "姓名", "", "", "").isSuccess());
        verifyNoInteractions(controller.userService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void profileUsesTokenIdentityAndSavesOnlyPersonalFields() throws Exception {
        // JWT 工具的默认密钥初始化依赖 Spring 配置，测试仅提供本地固定默认值。
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getProperty("sys.jwt-key", "xgls!213")).thenReturn("test-key");
            Class.forName(JwtUtils.class.getName());
        }
        AuthController controller = controller();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CodeMap.X_ACCESS_TOKEN, "session-token");
        request.addParameter("id", "999");
        request.addParameter("type", "1");
        User identity = new User(); identity.setId(7L);
        User current = new User(); current.setId(7L); current.setType(3);
        current.setStatus(CodeMap.USER_STATUS_OK); current.setPmd("secret-hash");
        when(controller.redisService.getJwtToken("session-token")).thenReturn("verified-token");
        when(controller.userService.getById(7L)).thenReturn(current);
        when(controller.userService.update(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<User> update = invocation.getArgument(0);
            String sql = ((com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>) update).getSqlSet();
            assertTrue(sql.contains("nickname"));
            assertFalse(sql.contains("type")); assertFalse(sql.contains("status")); assertFalse(sql.contains("pmd"));
            return true;
        });
        // Lambda 列名映射由 MyBatis 初始化。
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"), User.class);
        try (MockedStatic<JwtUtils> jwt = mockStatic(JwtUtils.class)) {
            jwt.when(() -> JwtUtils.verifyAndGetUser("verified-token")).thenReturn(identity);
            assertTrue(controller.saveProfile(request, " 人员姓名 ", "", "部门", "简介").isSuccess());
            User data = (User) controller.profile(request).getData();
            assertEquals(7L, data.getId()); assertEquals(3, data.getType()); assertNull(data.getPmd());
            current.setStatus(CodeMap.USER_STATUS_LOCK);
            assertFalse(controller.profile(request).isSuccess());
            assertFalse(controller.saveProfile(request, "姓名", "", "", "").isSuccess());
        }
        verify(controller.userService, never()).getById(999L);
    }
}
