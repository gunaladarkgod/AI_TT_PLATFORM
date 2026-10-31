package com.xgls.web.controller;

import com.xgls.web.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.Map;
import com.xgls.web.entity.User;
import com.xgls.web.utils.AuthenticatedUserUtil;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkspaceComponentControllerTest {
    @Test void loggedInNonAdminCannotDownload() {
        WorkspaceComponentService components = mock(WorkspaceComponentService.class);
        var controller = new WorkspaceComponentController(components, mock(RedisService.class), mock(UserService.class));
        try (var auth = mockStatic(AuthenticatedUserUtil.class)) {
            auth.when(() -> AuthenticatedUserUtil.current(any(), any(), any())).thenReturn(new User());
            assertFalse(controller.download(Map.of("id", "ultralytics", "engine", "ultralytics"), new MockHttpServletRequest()).isSuccess());
            assertFalse(controller.remove(Map.of("id", "ultralytics", "engine", "ultralytics"), new MockHttpServletRequest()).isSuccess());
            verifyNoInteractions(components);
        }
    }
    @Test void anonymousRequestsCannotInspectOrModifyWorkspace() {
        WorkspaceComponentService components = mock(WorkspaceComponentService.class);
        var controller = new WorkspaceComponentController(components, mock(RedisService.class), mock(UserService.class));
        var request = new MockHttpServletRequest();
        assertFalse(controller.list("ultralytics", request).isSuccess());
        assertFalse(controller.download(Map.of("id", "ultralytics", "engine", "ultralytics"), request).isSuccess());
        assertFalse(controller.remove(Map.of("id", "ultralytics", "engine", "ultralytics"), request).isSuccess());
        verifyNoInteractions(components);
    }
}
