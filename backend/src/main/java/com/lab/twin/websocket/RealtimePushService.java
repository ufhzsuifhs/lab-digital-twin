package com.lab.twin.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * WebSocket 实时推送服务：向大屏广播设备状态与实验状态变更。
 * 频道：/topic/device-status、/topic/experiment-status。
 */
@Service
@RequiredArgsConstructor
public class RealtimePushService {

    public static final String TOPIC_DEVICE_STATUS = "/topic/device-status";
    public static final String TOPIC_EXPERIMENT_STATUS = "/topic/experiment-status";

    private final SimpMessagingTemplate messagingTemplate;

    /** 广播设备状态（数字孪生模型颜色随数据实时变化） */
    public void pushDeviceStatus(Map<String, Object> payload) {
        messagingTemplate.convertAndSend(TOPIC_DEVICE_STATUS, payload);
    }

    /** 广播实验状态变更（首页指标卡实时刷新） */
    public void pushExperimentStatus(Map<String, Object> payload) {
        messagingTemplate.convertAndSend(TOPIC_EXPERIMENT_STATUS, payload);
    }
}
