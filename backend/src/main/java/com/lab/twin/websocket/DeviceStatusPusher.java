package com.lab.twin.websocket;

import com.lab.twin.mapper.StatsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备状态推送任务：定时查询机台负荷状态并广播（无 CDC 时的实时方案）。
 * 仅当 lab.realtime.enabled=true 时启用，默认关闭避免无数据环境报错。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lab.realtime.enabled", havingValue = "true")
public class DeviceStatusPusher {

    private final StatsMapper statsMapper;
    private final RealtimePushService pushService;

    @Scheduled(fixedDelayString = "${lab.realtime.interval-ms:5000}")
    public void push() {
        try {
            List<Map<String, Object>> devices = statsMapper.deviceListWithStatus();
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "device-status");
            payload.put("timestamp", System.currentTimeMillis());
            payload.put("devices", devices);
            pushService.pushDeviceStatus(payload);
        } catch (Exception e) {
            log.warn("device status push failed: {}", e.getMessage());
        }
    }
}
