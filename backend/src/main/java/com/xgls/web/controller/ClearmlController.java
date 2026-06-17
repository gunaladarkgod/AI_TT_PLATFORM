package com.xgls.web.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xgls.web.base.AjaxResult;
import com.xgls.web.service.ClearmlApiService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "ClearML（dev）")
@RestController
@RequestMapping("/clearml")
@RequiredArgsConstructor
public class ClearmlController {

    private final ClearmlApiService clearmlApiService;

    @Operation(summary = "ClearML 连接概览（不含密钥）")
    @PostMapping("/status")
    public AjaxResult status() {
        return AjaxResult.success(clearmlApiService.statusSummary());
    }

    @Operation(summary = "诊断 auth.login（不写入 JWT 缓存；便于排查密钥）")
    @PostMapping("/probe")
    public AjaxResult probe() {
        return clearmlApiService.probeAuth();
    }

    @Operation(summary = "进行中 ClearML 实验（tasks.get_all status=in_progress）")
    @PostMapping("/tasks/active")
    public AjaxResult activeTasks(@RequestBody(required = false) Map<String, Object> body) {
        return clearmlApiService.fetchActiveTasks(body);
    }
}
