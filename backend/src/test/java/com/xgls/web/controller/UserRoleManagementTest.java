package com.xgls.web.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.xgls.web.base.CodeMap;
import com.xgls.web.entity.User;
import com.xgls.web.service.RedisService;
import com.xgls.web.service.UserService;
import com.xgls.web.utils.JwtUtils;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

@SuppressWarnings("unchecked")
class UserRoleManagementTest {
    AuthController controller;
    MockHttpServletRequest request;
    User admin, target;
    MockedStatic<JwtUtils> jwt;

    @BeforeAll
    static void initialize() throws Exception {
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getProperty("sys.jwt-key", "xgls!213")).thenReturn("test-key");
            Class.forName(JwtUtils.class.getName());
        }
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "roles-test"), User.class);
    }

    @BeforeEach
    void setup() {
        controller = new AuthController();
        controller.userService = mock(UserService.class);
        controller.redisService = mock(RedisService.class);
        request = new MockHttpServletRequest();
        request.addHeader(CodeMap.X_ACCESS_TOKEN, "session");
        admin = user(1L, 1); target = user(2L, 3);
        User claims = user(1L, 1);
        jwt = mockStatic(JwtUtils.class);
        jwt.when(() -> JwtUtils.verifyAndGetUser("real-token")).thenReturn(claims);
        when(controller.redisService.getJwtToken("session")).thenReturn("real-token");
        when(controller.userService.getById(1L)).thenReturn(admin);
        when(controller.userService.getById(2L)).thenReturn(target);
    }

    @AfterEach void cleanup() { jwt.close(); }

    static User user(Long id, int type) {
        User user = new User(); user.setId(id); user.setType(type); user.setStatus(CodeMap.USER_STATUS_OK);
        return user;
    }

    @Test void onlyCurrentDatabaseAdminCanReadOrChangeRoles() {
        for (int type : new int[]{2, 3, 4}) {
            admin.setType(type); // JWT 中仍为管理员，不可据此获得权限。
            assertFalse(controller.userRoles(request).isSuccess());
            assertFalse(controller.changeUserRole(request, 2L, 1).isSuccess());
        }
        admin.setType(1); admin.setStatus(CodeMap.USER_STATUS_LOCK);
        assertFalse(controller.changeUserRole(request, 2L, 4).isSuccess());
        when(controller.redisService.getJwtToken("session")).thenReturn(null);
        assertFalse(controller.userRoles(request).isSuccess());
        verify(controller.userService, never()).update(any(Wrapper.class));
    }

    @Test void invalidSelfAndMissingTargetsCannotBeChanged() {
        for (Integer type : new Integer[]{null, 0, 5}) assertFalse(controller.changeUserRole(request, 2L, type).isSuccess());
        assertFalse(controller.changeUserRole(request, null, 4).isSuccess());
        assertFalse(controller.changeUserRole(request, 1L, 3).isSuccess());
        assertFalse(controller.changeUserRole(request, 999L, 4).isSuccess());
        verify(controller.userService, never()).update(any(Wrapper.class));
    }

    @Test void roleUpdateOnlyWritesTypeAndRevokesAllTargetSessions() {
        when(controller.userService.update(any(Wrapper.class))).thenAnswer(invocation -> {
            LambdaUpdateWrapper<User> update = invocation.getArgument(0);
            String sql = update.getSqlSet();
            assertTrue(sql.contains("type"));
            assertFalse(sql.contains("pmd")); assertFalse(sql.contains("status")); assertFalse(sql.contains("nickname"));
            assertTrue(update.getParamNameValuePairs().containsValue(4));
            return true;
        });
        when(controller.redisService.getUserJwt(2L)).thenReturn(Set.of("old-a", "old-b"));
        assertTrue(controller.changeUserRole(request, 2L, 4).isSuccess());
        verify(controller.redisService).removeJwtToken("old-a"); verify(controller.redisService).removeJwtToken("old-b");
        verify(controller.redisService).delUserAllJwt(2L);
        verify(controller.redisService, never()).delUserAllJwt(1L);
    }

    @Test void unchangedOrFailedUpdatesDoNotRevokeSessions() {
        assertTrue(controller.changeUserRole(request, 2L, 3).isSuccess());
        when(controller.userService.update(any(Wrapper.class))).thenReturn(false);
        assertFalse(controller.changeUserRole(request, 2L, 4).isSuccess());
        verify(controller.redisService, never()).getUserJwt(2L);
        verify(controller.redisService, never()).delUserAllJwt(2L);
    }

    @Test void userListSelectsNoCredentialsOrPrivateContactFields() {
        when(controller.userService.list(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<User> query = invocation.getArgument(0);
            assertFalse(query.getSqlSelect().contains("pmd")); assertFalse(query.getSqlSelect().contains("phone"));
            assertTrue(query.getSqlSelect().contains("part"));
            return List.of(target);
        });
        assertTrue(controller.userRoles(request).isSuccess());
    }

    @Test void legacyUpdateCannotBypassRoleManagementAndNonAdminCannotCreateRoles() {
        UserController legacy = new UserController();
        legacy.userService = controller.userService; legacy.redisService = controller.redisService;
        User payload = user(2L, 1);
        assertFalse(legacy.update(payload).isSuccess());
        admin.setType(4);
        assertFalse(legacy.add(payload, request).isSuccess());
        verify(controller.userService, never()).updateById(any(User.class));
        verify(controller.userService, never()).save(any(User.class));
    }

    @Test void ordinaryProfileUpdateCannotWriteBackStaleRole() {
        UserController legacy = new UserController();
        legacy.userService = controller.userService; legacy.redisService = controller.redisService;
        User payload = user(2L, 3); payload.setNickname("姓名");
        when(controller.userService.updateById(any(User.class))).thenAnswer(invocation -> {
            User update = invocation.getArgument(0);
            assertNull(update.getType());
            assertEquals("姓名", update.getNickname());
            return true;
        });
        assertTrue(legacy.update(payload).isSuccess());
    }
}
