# ClickHouse 数据同步策略

> 目标：把 MySQL 业务 OLTP 数据同步到 ClickHouse 分析库，支撑历史趋势与亿级聚合。事实表按月分区、维度表 ReplacingMergeTree，保证可重放、幂等、可回退。

## 1. 同步范围与映射

| ClickHouse 表 | 类型 | MySQL 来源 | 同步字段要点 |
|---|---|---|---|
| dim_department | 维度 | lab_department + lab_inspection_registration.business_unit | dept_name、business_unit |
| dim_machine | 维度 | lab_machine + lab_machine_unit | instrument_name、station_code、machine_type、腔体尺寸 |
| dim_experiment_item | 维度 | lab_experiment_item | item_name |
| dim_dqa_project | 维度 | lab_dqa_project | project_name、standard |
| fact_experiment_event | 事实 | lab_inspection_registration ⨝ lab_experiment_plan | 申请维度冗余 + 计划事件 |
| fact_device_status | 事实 | lab_machine_load_management | 机台负荷快照 |
| fact_experiment_stage_change | 事实 | lab_experiment_plan_stage | 阶段推进事件 |
| fact_experiment_result | 事实 | lab_reliability_experiment_reg + lab_dqa_optical_evaluation | source 区分两源 |

## 2. 同步方式

- **全量初始化**：首次建库后按主键全量拉取，写入 ClickHouse（维度表带 `version` 时间戳，事实表按分区写入）。
- **增量同步（水位线）**：以各表 `updated_time`（或 `created_time`）为水位，记录每个来源表最后同步时间 `last_sync_ts`，增量拉取 `updated_time > last_sync_ts` 的记录。
- **CDC 增强（可选）**：生产环境推荐基于 binlog（如 Debezium → Kafka → ClickHouse）实现秒级实时；本系统默认提供**定时增量任务**（Spring `@Scheduled`，默认 60s 间隔，可配置），不强制引入外部中间件。

## 3. 一致性保障

1. **幂等**：写入前按主键 + 版本去重；事实表以 `(主键, 事件时间)` 为幂等键。
2. **维度表 SCD**：ReplacingMergeTree + `version`（toUnixTimestamp(now())），旧版本行由 ClickHouse 后台合并淘汰，查询取最新（`FINAL` 或按 version 最大）。
3. **事实表**：MergeTree 追加写，不覆盖；聚合用物化视图（SummingMergeTree）做预聚合，避免每次全表扫描。
4. **失败重试**：同步任务记录水位与失败批次，支持断点续传；批次事务提交后再推进水位。

## 4. 调度与任务

- 任务：`SyncService`（Spring Boot）实现 `fullSync()`（初始化）与 `incrementalSync()`（增量）。
- 调度：`@Scheduled(fixedDelayString = "${lab.sync.interval-ms:60000}")` 定时增量；全量由启动开关或管理接口触发。
- 水位存储：MySQL `lab_sync_watermark` 表（`source_table`, `last_sync_ts`）或 Redis，二选一（默认 MySQL）。

## 5. 查询路由与回退

- 分析接口按 `dataSource` 路由：短周期（当日/近 7 日）走 MySQL，长周期（月/季/年、亿级聚合）走 ClickHouse。
- **回退**：ClickHouse 不可用或未配置时，接口自动回退 MySQL 等价 SQL，保证功能可用（降级不降服务）。

## 6. 性能设计（亿级）

- 分区键 `toYYYYMM(date)`：查询按月裁剪，避免全表扫描。
- 排序键覆盖高频过滤列（date、事业部、部门、机种、项目），命中主键索引。
- 物化视图预聚合（设备利用率日聚合、结果日聚合），趋势查询命中聚合表。
- 维度表用 `ReplacingMergeTree` + `LowCardinality` 字符串，降低存储与 join 成本。
