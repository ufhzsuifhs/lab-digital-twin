package com.lab.twin.service;

import com.lab.twin.mapper.StatsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 分析引擎（内置统计算法，无外部依赖）。
 * 六项能力：设备压力预测、延期风险、设备瓶颈、实验效率、部门效率排行、资源推荐。
 */
@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private final StatsMapper statsMapper;

    /** 1. 设备资源预测：基于近 30 天占用趋势，线性回归外推未来 N 天，输出可能满载预警 */
    public Map<String, Object> devicePressureForecast(int days) {
        List<Map<String, Object>> history = statsMapper.deviceLoadDailyHistory();
        List<Double> series = new ArrayList<>();
        for (Map<String, Object> row : history) {
            Object v = row.get("occupied");
            series.add(v == null ? 0.0 : ((Number) v).doubleValue());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("history", history);
        result.put("forecast", linearForecast(series, days));
        // 满载预警：预测占用 >= 总机台数 80%
        Object totalUnitsObj = statsMapper.deviceUtilization().get("total_units");
        double totalUnits = totalUnitsObj == null ? 0 : ((Number) totalUnitsObj).doubleValue();
        List<String> warnings = new ArrayList<>();
        if (totalUnits > 0) {
            List<Map<String, Object>> forecast = (List<Map<String, Object>>) result.get("forecast");
            for (Map<String, Object> f : forecast) {
                double v = ((Number) f.get("value")).doubleValue();
                if (v >= totalUnits * 0.8) {
                    warnings.add(f.get("date") + " 预计占用 " + v + " 台，接近满载(" + totalUnits + "台)");
                }
            }
        }
        result.put("fullLoadWarnings", warnings);
        return result;
    }

    /** 2. 实验延期风险：已过 due_at 且未完成的阶段 + 风险评分 */
    public Map<String, Object> delayRiskForecast() {
        List<Map<String, Object>> overdue = statsMapper.overdueStages();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overdueCount", overdue.size());
        result.put("overdueStages", overdue);
        result.put("riskNote", "基于阶段 due_at 已过且未完成判定；结合设备负荷与历史完成情况可进一步加权");
        return result;
    }

    /** 3. 设备瓶颈分析：负载排行前列 + 关联 NG 命中，识别影响整体效率的设备 */
    public Map<String, Object> bottleneckAnalysis() {
        List<Map<String, Object>> loadRanking = statsMapper.loadRanking();
        List<Map<String, Object>> ngTopDevice = statsMapper.ngTopDevice();
        Map<String, Object> ngByStation = new HashMap<>();
        for (Map<String, Object> row : ngTopDevice) {
            ngByStation.put(String.valueOf(row.get("station_code")), row.get("ng_count"));
        }
        List<Map<String, Object>> bottlenecks = new ArrayList<>();
        for (Map<String, Object> row : loadRanking) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("instrument_name", row.get("instrument_name"));
            item.put("station_code", row.get("station_code"));
            item.put("load_score", row.get("load_score"));
            item.put("ng_count", ngByStation.getOrDefault(String.valueOf(row.get("station_code")), 0));
            bottlenecks.add(item);
            if (bottlenecks.size() >= 10) {
                break;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bottlenecks", bottlenecks);
        result.put("note", "按负荷强度降序，叠加 NG 命中数，排名靠前即为影响整体效率的瓶颈设备");
        return result;
    }

    /** 4. 实验效率分析：平均/最长/最短实验周期 */
    public Map<String, Object> efficiencyAnalysis() {
        return statsMapper.experimentDurationStats();
    }

    /** 5. 部门实验效率排行：申请数/完成数/平均耗时/NG 比例 */
    public List<Map<String, Object>> deptEfficiencyRanking() {
        List<Map<String, Object>> list = statsMapper.deptEfficiency();
        for (Map<String, Object> row : list) {
            Object applyObj = row.get("apply_count");
            Object ngObj = row.get("ng_count");
            double apply = applyObj == null ? 0 : ((Number) applyObj).doubleValue();
            double ng = ngObj == null ? 0 : ((Number) ngObj).doubleValue();
            row.put("ng_rate_pct", apply > 0 ? Math.round(ng / apply * 10000) / 100.0 : 0.0);
        }
        return list;
    }

    /** 6. 实验资源推荐：输入实验项目 → 推荐设备/预计周期/历史成功率 */
    public List<Map<String, Object>> resourceRecommend(String experimentItemId) {
        List<Map<String, Object>> machines = statsMapper.recommendMachines(experimentItemId);
        for (Map<String, Object> m : machines) {
            Object histObj = m.get("history_count");
            Object okObj = m.get("ok_count");
            double hist = histObj == null ? 0 : ((Number) histObj).doubleValue();
            double ok = okObj == null ? 0 : ((Number) okObj).doubleValue();
            m.put("success_rate_pct", hist > 0 ? Math.round(ok / hist * 10000) / 100.0 : null);
            m.put("expected_duration", m.get("avg_duration"));
        }
        return machines;
    }

    /** 简单线性回归外推（最小二乘） */
    private List<Map<String, Object>> linearForecast(List<Double> series, int days) {
        List<Map<String, Object>> forecast = new ArrayList<>();
        int n = series.size();
        if (n < 2) {
            double last = n == 0 ? 0 : series.get(n - 1);
            for (int k = 1; k <= days; k++) {
                forecast.add(forecastPoint(LocalDate.now().plusDays(k), last));
            }
            return forecast;
        }
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (int i = 0; i < n; i++) {
            double y = series.get(i);
            sumX += i;
            sumY += y;
            sumXY += i * y;
            sumXX += (double) i * i;
        }
        double denom = n * sumXX - sumX * sumX;
        double slope = denom == 0 ? 0 : (n * sumXY - sumX * sumY) / denom;
        double intercept = (sumY - slope * sumX) / n;
        for (int k = 1; k <= days; k++) {
            double v = intercept + slope * (n - 1 + k);
            forecast.add(forecastPoint(LocalDate.now().plusDays(k), Math.max(0, v)));
        }
        return forecast;
    }

    private Map<String, Object> forecastPoint(LocalDate date, double value) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("date", date.toString());
        p.put("value", Math.round(value * 100) / 100.0);
        return p;
    }
}
