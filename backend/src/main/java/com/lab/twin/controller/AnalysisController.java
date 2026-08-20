package com.lab.twin.controller;

import com.lab.twin.common.Result;
import com.lab.twin.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 分析模块接口：/api/analysis/**
 * 覆盖需求三~十一 + 关系网络数据。
 */
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final StatisticsService statisticsService;

    // ============ 三、设备利用率 ============
    @GetMapping("/device/occupation")
    public Result<List<Map<String, Object>>> machineOccupationRanking() {
        return Result.ok(statisticsService.machineOccupationRanking());
    }

    @GetMapping("/device/utilization")
    public Result<Map<String, Object>> deviceUtilization() {
        return Result.ok(statisticsService.deviceUtilization());
    }

    @GetMapping("/device/utilization-trend")
    public Result<List<Map<String, Object>>> utilizationTrend(@RequestParam(defaultValue = "daily") String granularity) {
        return Result.ok(statisticsService.utilizationTrend(granularity));
    }

    @GetMapping("/device/load-ranking")
    public Result<List<Map<String, Object>>> loadRanking() {
        return Result.ok(statisticsService.loadRanking());
    }

    // ============ 四、事业部设备占用 ============
    @GetMapping("/business-unit/occupation")
    public Result<List<Map<String, Object>>> businessUnitOccupation() {
        return Result.ok(statisticsService.businessUnitOccupation());
    }

    @GetMapping("/business-unit/trend")
    public Result<List<Map<String, Object>>> businessUnitTrend() {
        return Result.ok(statisticsService.businessUnitTrend());
    }

    // ============ 五、机种占用比例 ============
    @GetMapping("/machine-type/ratio")
    public Result<List<Map<String, Object>>> machineTypeRatio() {
        return Result.ok(statisticsService.machineTypeRatio());
    }

    // ============ 六、实验申请分析 ============
    @GetMapping("/application/trend")
    public Result<List<Map<String, Object>>> applicationTrend(@RequestParam(defaultValue = "monthly") String granularity) {
        return Result.ok(statisticsService.applicationTrend(granularity));
    }

    @GetMapping("/application/dept")
    public Result<List<Map<String, Object>>> deptApplicationCount() {
        return Result.ok(statisticsService.deptApplicationCount());
    }

    @GetMapping("/application/request-type")
    public Result<List<Map<String, Object>>> requestTypeDistribution() {
        return Result.ok(statisticsService.requestTypeDistribution());
    }

    @GetMapping("/application/category")
    public Result<List<Map<String, Object>>> categoryDistribution() {
        return Result.ok(statisticsService.categoryDistribution());
    }

    @GetMapping("/application/supplier")
    public Result<List<Map<String, Object>>> supplierRatio() {
        return Result.ok(statisticsService.supplierRatio());
    }

    // ============ 七、完成率 ============
    @GetMapping("/completion/overall")
    public Result<Map<String, Object>> completionRateOverall() {
        return Result.ok(statisticsService.completionRateOverall());
    }

    @GetMapping("/completion/trend")
    public Result<List<Map<String, Object>>> completionTrendDaily() {
        return Result.ok(statisticsService.completionTrendDaily());
    }

    // ============ 八、异常实验（NG） ============
    @GetMapping("/abnormal/summary")
    public Result<Map<String, Object>> ngSummary() {
        return Result.ok(statisticsService.ngSummary());
    }

    @GetMapping("/abnormal/trend")
    public Result<List<Map<String, Object>>> ngTrendDaily() {
        return Result.ok(statisticsService.ngTrendDaily());
    }

    @GetMapping("/abnormal/top-item")
    public Result<List<Map<String, Object>>> ngTopExperimentItem() {
        return Result.ok(statisticsService.ngTopExperimentItem());
    }

    @GetMapping("/abnormal/top-machine-type")
    public Result<List<Map<String, Object>>> ngTopMachineType() {
        return Result.ok(statisticsService.ngTopMachineType());
    }

    @GetMapping("/abnormal/top-device")
    public Result<List<Map<String, Object>>> ngTopDevice() {
        return Result.ok(statisticsService.ngTopDevice());
    }

    @GetMapping("/abnormal/inspector")
    public Result<List<Map<String, Object>>> ngInspectorDistribution() {
        return Result.ok(statisticsService.ngInspectorDistribution());
    }

    @GetMapping("/abnormal/reason")
    public Result<List<Map<String, Object>>> ngReasonAnalysis() {
        return Result.ok(statisticsService.ngReasonAnalysis());
    }

    // ============ 九、实验结果 ============
    @GetMapping("/result/okng")
    public Result<List<Map<String, Object>>> resultOkNg(@RequestParam(defaultValue = "reliability") String source) {
        return Result.ok(statisticsService.resultOkNg(source));
    }

    @GetMapping("/result/distribution")
    public Result<List<Map<String, Object>>> resultDistribution() {
        return Result.ok(statisticsService.resultDistribution());
    }

    // ============ 十、DQA 专项 ============
    @GetMapping("/dqa/project-ratio")
    public Result<List<Map<String, Object>>> dqaProjectRatio() {
        return Result.ok(statisticsService.dqaProjectRatio());
    }

    @GetMapping("/dqa/purpose")
    public Result<List<Map<String, Object>>> dqaPurposeTop() {
        return Result.ok(statisticsService.dqaPurposeTop());
    }

    @GetMapping("/dqa/stage")
    public Result<List<Map<String, Object>>> dqaStageDistribution() {
        return Result.ok(statisticsService.dqaStageDistribution());
    }

    @GetMapping("/dqa/result")
    public Result<List<Map<String, Object>>> dqaResultDistribution() {
        return Result.ok(statisticsService.dqaResultDistribution());
    }

    @GetMapping("/dqa/month-trend")
    public Result<List<Map<String, Object>>> dqaMonthTrend() {
        return Result.ok(statisticsService.dqaMonthTrend());
    }

    // ============ 十一、报价分析 ============
    @GetMapping("/quote/department")
    public Result<List<Map<String, Object>>> quoteByDepartment() {
        return Result.ok(statisticsService.quoteByDepartment());
    }

    @GetMapping("/quote/business-unit")
    public Result<List<Map<String, Object>>> quoteByBusinessUnit() {
        return Result.ok(statisticsService.quoteByBusinessUnit());
    }

    @GetMapping("/quote/item-ranking")
    public Result<List<Map<String, Object>>> quoteItemRanking() {
        return Result.ok(statisticsService.quoteItemRanking());
    }

    @GetMapping("/quote/instrument-ranking")
    public Result<List<Map<String, Object>>> quoteInstrumentRanking() {
        return Result.ok(statisticsService.quoteInstrumentRanking());
    }

    @GetMapping("/quote/cost-trend")
    public Result<List<Map<String, Object>>> quoteCostTrend() {
        return Result.ok(statisticsService.quoteCostTrend());
    }

    // ============ 十三、关系网络 ============
    @GetMapping("/relation/edges")
    public Result<List<Map<String, Object>>> relationEdges() {
        return Result.ok(statisticsService.relationEdges());
    }
}
