package com.lab.twin.controller;

import com.lab.twin.common.Result;
import com.lab.twin.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 首页驾驶舱接口：/api/dashboard/**
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /** 首页六大指标聚合 */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.ok(dashboardService.overview());
    }

    /** 数字孪生设备列表 */
    @GetMapping("/devices")
    public Result<List<Map<String, Object>>> devices() {
        return Result.ok(dashboardService.deviceList());
    }

    /** 单个设备详情 */
    @GetMapping("/device/{id}")
    public Result<Map<String, Object>> deviceDetail(@PathVariable("id") String id) {
        return Result.ok(dashboardService.deviceDetail(id));
    }
}
