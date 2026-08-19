package com.lab.twin.service;

import com.lab.twin.mapper.StatsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 统计查询服务：转调 StatsMapper，按分析模块组织，供 Controller 直接消费。
 */
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final StatsMapper statsMapper;

    // ============ 三、设备利用率 ============
    public List<Map<String, Object>> machineOccupationRanking() {
        return statsMapper.machineOccupationRanking();
    }

    public Map<String, Object> deviceUtilization() {
        return statsMapper.deviceUtilization();
    }

    public List<Map<String, Object>> utilizationTrend(String granularity) {
        return switch (granularity == null ? "daily" : granularity) {
            case "weekly" -> statsMapper.utilizationTrendWeekly();
            case "monthly" -> statsMapper.utilizationTrendMonthly();
            default -> statsMapper.utilizationTrendDaily();
        };
    }

    public List<Map<String, Object>> loadRanking() {
        return statsMapper.loadRanking();
    }

    // ============ 四、事业部设备占用 ============
    public List<Map<String, Object>> businessUnitOccupation() {
        return statsMapper.businessUnitOccupation();
    }

    public List<Map<String, Object>> businessUnitTrend() {
        return statsMapper.businessUnitTrend();
    }

    // ============ 五、机种占用比例 ============
    public List<Map<String, Object>> machineTypeRatio() {
        return statsMapper.machineTypeRatio();
    }

    // ============ 六、实验申请分析 ============
    public List<Map<String, Object>> applicationTrend(String granularity) {
        return "quarterly".equals(granularity)
                ? statsMapper.applicationTrendQuarterly()
                : statsMapper.applicationTrendMonthly();
    }

    public List<Map<String, Object>> deptApplicationCount() {
        return statsMapper.deptApplicationCount();
    }

    public List<Map<String, Object>> requestTypeDistribution() {
        return statsMapper.requestTypeDistribution();
    }

    public List<Map<String, Object>> categoryDistribution() {
        return statsMapper.categoryDistribution();
    }

    public List<Map<String, Object>> supplierRatio() {
        return statsMapper.supplierRatio();
    }

    // ============ 七、完成率 ============
    public Map<String, Object> completionRateOverall() {
        return statsMapper.completionRateOverall();
    }

    public List<Map<String, Object>> completionTrendDaily() {
        return statsMapper.completionTrendDaily();
    }

    // ============ 八、异常实验（NG） ============
    public Map<String, Object> ngSummary() {
        return statsMapper.ngSummary();
    }

    public List<Map<String, Object>> ngTrendDaily() {
        return statsMapper.ngTrendDaily();
    }

    public List<Map<String, Object>> ngTopExperimentItem() {
        return statsMapper.ngTopExperimentItem();
    }

    public List<Map<String, Object>> ngTopMachineType() {
        return statsMapper.ngTopMachineType();
    }

    public List<Map<String, Object>> ngTopDevice() {
        return statsMapper.ngTopDevice();
    }

    public List<Map<String, Object>> ngInspectorDistribution() {
        return statsMapper.ngInspectorDistribution();
    }

    public List<Map<String, Object>> ngReasonAnalysis() {
        return statsMapper.ngReasonAnalysis();
    }

    // ============ 九、实验结果 ============
    public Map<String, Object> resultOkNg(String source) {
        return "dqa".equals(source) ? statsMapper.resultOkNgDqa() : statsMapper.resultOkNgReliability();
    }

    public List<Map<String, Object>> resultDistribution() {
        return statsMapper.resultDistribution();
    }

    // ============ 十、DQA 专项 ============
    public List<Map<String, Object>> dqaProjectRatio() {
        return statsMapper.dqaProjectRatio();
    }

    public List<Map<String, Object>> dqaPurposeTop() {
        return statsMapper.dqaPurposeTop();
    }

    public List<Map<String, Object>> dqaStageDistribution() {
        return statsMapper.dqaStageDistribution();
    }

    public List<Map<String, Object>> dqaResultDistribution() {
        return statsMapper.dqaResultDistribution();
    }

    public List<Map<String, Object>> dqaMonthTrend() {
        return statsMapper.dqaMonthTrend();
    }

    // ============ 十一、报价分析 ============
    public List<Map<String, Object>> quoteByDepartment() {
        return statsMapper.quoteByDepartment();
    }

    public List<Map<String, Object>> quoteByBusinessUnit() {
        return statsMapper.quoteByBusinessUnit();
    }

    public List<Map<String, Object>> quoteItemRanking() {
        return statsMapper.quoteItemRanking();
    }

    public List<Map<String, Object>> quoteInstrumentRanking() {
        return statsMapper.quoteInstrumentRanking();
    }

    public List<Map<String, Object>> quoteCostTrend() {
        return statsMapper.quoteCostTrend();
    }

    // ============ 十三、G6 关系网络 ============
    public List<Map<String, Object>> relationEdges() {
        return statsMapper.relationEdges();
    }
}
