package com.lab.twin.service;

import com.lab.twin.mapper.StatsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 首页驾驶舱服务：聚合六大指标 + 大屏扩展数据（趋势/设备状态分布/进行中列表/NG 告警）+ 数字孪生设备。
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final StatsMapper statsMapper;

    /** 首页运营大屏聚合数据 */
    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todayExperimentCount", statsMapper.todayExperimentCount().get("today_experiment_count"));
        result.put("planStatus", statsMapper.planStatusSummary());
        result.put("todayAbnormalCount", statsMapper.todayAbnormalCount().get("abnormal_count"));
        result.put("deviceUtilization", statsMapper.deviceUtilization());
        result.put("completionRate", statsMapper.completionRateOverall());
        // 大屏扩展
        result.put("experimentTrend", statsMapper.dailyExperimentTrend());
        result.put("deviceStatusDist", deviceStatusDist());
        result.put("runningList", statsMapper.runningExperimentList());
        result.put("recentNg", statsMapper.recentNgList());
        return result;
    }

    /** 设备状态分布：异常 / 满载 / 运行 / 空闲 四态 */
    private Map<String, Object> deviceStatusDist() {
        List<Map<String, Object>> devices = statsMapper.deviceListWithStatus();
        Set<String> errorMachines = new HashSet<>();
        for (Map<String, Object> ng : statsMapper.recentNgList()) {
            Object id = ng.get("ng_machine_id");
            if (id != null && !String.valueOf(id).isBlank()) {
                errorMachines.add(String.valueOf(id));
            }
        }
        int idle = 0, running = 0, full = 0, error = 0;
        for (Map<String, Object> d : devices) {
            String unitId = String.valueOf(d.get("machine_unit_id"));
            if (errorMachines.contains(unitId)) {
                error++;
            } else if ("1".equals(String.valueOf(d.get("is_full")))) {
                full++;
            } else if (d.get("load_id") != null) {
                running++;
            } else {
                idle++;
            }
        }
        Map<String, Object> dist = new LinkedHashMap<>();
        dist.put("idle", idle);
        dist.put("running", running);
        dist.put("full", full);
        dist.put("error", error);
        dist.put("total", devices.size());
        return dist;
    }

    /** 数字孪生设备列表（含当前负荷状态，供 Three.js 渲染） */
    public List<Map<String, Object>> deviceList() {
        return statsMapper.deviceListWithStatus();
    }

    /** 单个设备详情（点击弹层） */
    public Map<String, Object> deviceDetail(String machineUnitId) {
        return statsMapper.deviceDetail(machineUnitId);
    }
}
