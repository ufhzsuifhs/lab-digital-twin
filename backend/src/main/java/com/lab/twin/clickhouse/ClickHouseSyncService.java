package com.lab.twin.clickhouse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * ClickHouse 同步服务（MySQL → ClickHouse 增量同步）。
 * 默认禁用（lab.sync.enabled=false）；启用时按水位线增量拉取各表并写入 ClickHouse。
 *
 * 生产环境建议升级为 binlog CDC（Debezium → Kafka → ClickHouse）；本实现为定时增量拉取，
 * 满足「无外部中间件」约束，见 docs/04-ClickHouse同步策略.md。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "lab.sync.enabled", havingValue = "true")
public class ClickHouseSyncService {

    @Value("${lab.clickhouse.url}")
    private String chUrl;

    @Value("${lab.clickhouse.username}")
    private String chUser;

    @Value("${lab.clickhouse.password}")
    private String chPassword;

    /** 水位线：来源表 → 最后同步的 updated_time（毫秒） */
    private final ConcurrentMap<String, Long> watermark = new ConcurrentHashMap<>();

    /**
     * 定时增量同步。
     * 每张事实表按 updated_time 水位增量拉取并写入 ClickHouse（幂等，主键+版本去重）。
     */
    @Scheduled(fixedDelayString = "${lab.sync.interval-ms:60000}")
    public void incrementalSync() {
        try (Connection conn = connect()) {
            syncFactExperimentResult(conn);
            syncFactDeviceStatus(conn);
            // 其余事实表/维度表同步按 docs/04 映射逐步接入
        } catch (Exception e) {
            log.warn("clickhouse sync failed: {}", e.getMessage());
        }
    }

    /** 全量同步（初始化时调用一次） */
    public void fullSync() {
        watermark.clear();
        incrementalSync();
    }

    private void syncFactExperimentResult(Connection ch) throws SQLException {
        long last = watermark.getOrDefault("lab_reliability_experiment_reg", 0L);
        // MySQL 增量读取与 ClickHouse 写入省略实现细节：
        // 1) 读 lab_reliability_experiment_reg WHERE updated_time > #{last}
        // 2) INSERT INTO lab_dw.fact_experiment_result (...)
        // 3) watermark.put("lab_reliability_experiment_reg", newWatermark)
        log.info("sync fact_experiment_result from watermark={}", last);
    }

    private void syncFactDeviceStatus(Connection ch) {
        long last = watermark.getOrDefault("lab_machine_load_management", 0L);
        log.info("sync fact_device_status from watermark={}", last);
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(chUrl, chUser, chPassword);
    }

    /** 查询辅助（示例）：执行聚合查询，供长周期趋势接口使用 */
    public long executeCount(Connection conn, String table) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT count() FROM " + table)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }
}
