package com.lab.twin.controller;

import com.lab.twin.common.Result;
import com.lab.twin.service.AiAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI 预测分析接口：/api/ai/**
 * 六项能力：设备压力预测、延期风险、设备瓶颈、实验效率、部门效率排行、资源推荐。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiAnalysisService aiAnalysisService;

    /** 设备资源预测（未来 N 天压力 + 满载预警） */
    @GetMapping("/device-pressure")
    public Result<Map<String, Object>> devicePressure(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(aiAnalysisService.devicePressureForecast(days));
    }

    /** 实验延期风险预测 */
    @GetMapping("/delay-risk")
    public Result<Map<String, Object>> delayRisk() {
        return Result.ok(aiAnalysisService.delayRiskForecast());
    }

    /** 设备瓶颈分析 */
    @GetMapping("/bottleneck")
    public Result<Map<String, Object>> bottleneck() {
        return Result.ok(aiAnalysisService.bottleneckAnalysis());
    }

    /** 实验效率分析（平均/最长/最短周期） */
    @GetMapping("/efficiency")
    public Result<Map<String, Object>> efficiency() {
        return Result.ok(aiAnalysisService.efficiencyAnalysis());
    }

    /** 部门实验效率排行 */
    @GetMapping("/dept-efficiency")
    public Result<List<Map<String, Object>>> deptEfficiency() {
        return Result.ok(aiAnalysisService.deptEfficiencyRanking());
    }

    /** 实验资源推荐（输入实验项目ID） */
    @GetMapping("/resource-recommend")
    public Result<List<Map<String, Object>>> resourceRecommend(@RequestParam String experimentItemId) {
        return Result.ok(aiAnalysisService.resourceRecommend(experimentItemId));
    }
}
