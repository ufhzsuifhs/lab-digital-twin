-- ============================================================================
-- 智能实验室数字孪生驾驶舱 —— ClickHouse 分析模型 DDL
-- 目标：承接 MySQL 历史大数据，支持亿级聚合查询（日/周/月/季度趋势秒级响应）
-- 引擎选型：事实表 MergeTree + 分区按月；维度表 ReplacingMergeTree（SCD 缓慢变化维）
-- 分区键：toYYYYMM（按月）；排序键：高基数列 + 常用过滤列
-- ============================================================================

CREATE DATABASE IF NOT EXISTS lab_dw ENGINE = Atomic;

-- ----------------------------------------------------------------------------
-- 维度表 dim_department —— 部门 + 事业部（口径字典）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lab_dw.dim_department
(
    dept_id        String,            -- lab_department.id
    dept_name      String,            -- 部门名
    business_unit  String,            -- 事业部（来自送检/报价表冗余）
    enabled        UInt8,
    sign           Int8 DEFAULT 1,
    updated_time   DateTime,
    version        UInt64 DEFAULT toUnixTimestamp(now())
)
ENGINE = ReplacingMergeTree(version)
PARTITION BY tuple()
ORDER BY dept_id;

-- ----------------------------------------------------------------------------
-- 维度表 dim_machine —— 机器 + 机台（父/子扁平化）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lab_dw.dim_machine
(
    machine_id       String,          -- lab_machine_unit.id（机台ID，孪生/负荷粒度）
    parent_machine_id String,         -- lab_machine.id（父类机器）
    instrument_name  String,          -- 仪器名称
    station_code     String,          -- 机台编号
    machine_type     String,          -- 机种
    unit_length      Float64,         -- 腔体长 m
    unit_width       Float64,         -- 腔体宽 m
    unit_height      Float64,         -- 腔体高 m
    enabled          UInt8,
    sign             Int8 DEFAULT 1,
    updated_time     DateTime,
    version          UInt64 DEFAULT toUnixTimestamp(now())
)
ENGINE = ReplacingMergeTree(version)
ORDER BY machine_id;

-- ----------------------------------------------------------------------------
-- 维度表 dim_experiment_item —— 实验项目
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lab_dw.dim_experiment_item
(
    item_id      String,
    item_name    String,
    enabled      UInt8,
    sign         Int8 DEFAULT 1,
    updated_time DateTime,
    version      UInt64 DEFAULT toUnixTimestamp(now())
)
ENGINE = ReplacingMergeTree(version)
ORDER BY item_id;

-- ----------------------------------------------------------------------------
-- 维度表 dim_dqa_project —— DQA 项目清单
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lab_dw.dim_dqa_project
(
    project_id   String,
    project_name String,
    standard     String,
    enabled      UInt8,
    sign         Int8 DEFAULT 1,
    updated_time DateTime,
    version      UInt64 DEFAULT toUnixTimestamp(now())
)
ENGINE = ReplacingMergeTree(version)
ORDER BY project_id;

-- ----------------------------------------------------------------------------
-- 事实表 fact_experiment_event —— 实验事件（送检登记 + 计划）
-- 一行 = 一个实验计划事件（申请维度冗余其上，便于按部门/机种/事业部/供应商下钻）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lab_dw.fact_experiment_event
(
    event_time          DateTime,       -- 事件时间（送检/更新时间）
    event_date          Date,           -- 事件日期（分区用）
    registration_id     String,         -- 送检单ID
    plan_id             String,         -- 实验计划ID
    oa_number           String,
    -- 维度外键（联 dim_*）
    dept_id             String,
    business_unit       String,
    machine_type        String,
    experiment_item_id  String,
    dqa_project_id      String,
    machine_id          String,         -- 绑定机台（lab_machine_unit.id）
    -- 业务属性
    request_type        String,         -- 申请类型
    category            String,         -- 类别
    is_supplier         UInt8,          -- 是否供应商
    completion_status   String,         -- 完成状态
    experiment_result   String,         -- 实验结果
    experiment_cost     Decimal(14,2),  -- 实验费用
    experiment_quantity Int32,          -- 数量 pcs
    -- 指标
    cnt                 UInt32 DEFAULT 1 -- 计数
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(event_date)
ORDER BY (event_date, business_unit, dept_id, machine_type, experiment_item_id, registration_id)
TTL event_date + INTERVAL 3 YEAR;

-- ----------------------------------------------------------------------------
-- 事实表 fact_device_status —— 设备状态快照（机台负荷）
-- 每行 = 一次机台负荷状态快照（含占用强度，供利用率/负载排行聚合）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lab_dw.fact_device_status
(
    snapshot_time     DateTime,         -- 快照时间
    snapshot_date     Date,             -- 快照日期（分区）
    machine_id        String,           -- 机台ID（联 dim_machine）
    station_code      String,
    machine_model     String,           -- 机种
    is_full           UInt8,            -- 是否已满
    full_override     UInt8,
    stack_layer_count Int32,
    confirm_count     Int32,
    completed_count   Int32,
    dept_number       String,           -- 部番
    end_time          DateTime,         -- 结束时间
    load_score        Float64,          -- 负荷强度 = is_full*100 + completed_count
    occupied          UInt8 DEFAULT 0   -- 是否占用中（endTime IS NULL 或 > snapshot_time）
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(snapshot_date)
ORDER BY (snapshot_date, machine_id, dept_number)
TTL snapshot_date + INTERVAL 2 YEAR;

-- ----------------------------------------------------------------------------
-- 事实表 fact_experiment_stage_change —— 实验阶段变化（链式阶段推进）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lab_dw.fact_experiment_stage_change
(
    change_time      DateTime,
    change_date      Date,
    plan_id          String,
    stage_id         String,
    stage_order      Int32,
    stage_name       String,
    progression_mode String,            -- AUTO/MANUAL
    runtime_status   String,            -- PENDING/RUNNING/WAITING_MANUAL/COMPLETED
    duration_seconds Int64,
    started_at       DateTime,
    due_at           DateTime,
    completed_at     DateTime,
    delayed          UInt8 DEFAULT 0    -- 是否延期（completed_at > due_at）
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(change_date)
ORDER BY (change_date, plan_id, stage_order);

-- ----------------------------------------------------------------------------
-- 事实表 fact_experiment_result —— 实验结果（可靠性 NG + DQA 评价）
-- 统一结果事实，source 区分来源；供 NG/合格率/结果分布聚合
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lab_dw.fact_experiment_result
(
    result_time       DateTime,
    result_date       Date,
    source            LowCardinality(String),  -- 'reliability' | 'dqa'
    registration_id   String,
    plan_id           String,
    experiment_item_id String,
    dqa_project_id    String,
    machine_id        String,           -- ng_machine_id / 机台
    machine_type      String,
    business_dept     String,           -- 事业部
    entrusting_dept   String,           -- 委托部门
    ng_inspector      String,           -- NG 检验人员
    experiment_assignee String,         -- 实验担当
    ng_count          Int32 DEFAULT 0,
    sample_count      Int32 DEFAULT 0,
    defect_rate       Float64,          -- 不良率 %
    result_value      LowCardinality(String),  -- OK / NG / 合格 / 不合格
    evaluation_purpose String,          -- DQA 评价目的
    evaluation_stage  String,           -- DQA 评价阶段
    remark            String            -- 原因
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(result_date)
ORDER BY (result_date, source, business_dept, machine_type, experiment_item_id, result_value)
TTL result_date + INTERVAL 3 YEAR;

-- ----------------------------------------------------------------------------
-- 物化视图（示例）：设备利用率日聚合，供趋势秒级查询
-- ----------------------------------------------------------------------------
CREATE MATERIALIZED VIEW IF NOT EXISTS lab_dw.mv_device_util_daily
ENGINE = SummingMergeTree
PARTITION BY toYYYYMM(day)
ORDER BY (day, machine_type, dept_number)
AS
SELECT
    toDate(snapshot_time) AS day,
    machine_type,
    dept_number,
    count()              AS snapshot_cnt,
    sum(occupied)        AS occupied_cnt,
    sum(is_full)         AS full_cnt
FROM lab_dw.fact_device_status
GROUP BY day, machine_type, dept_number;

-- ----------------------------------------------------------------------------
-- 物化视图（示例）：实验结果日聚合（OK/NG/合格率）
-- ----------------------------------------------------------------------------
CREATE MATERIALIZED VIEW IF NOT EXISTS lab_dw.mv_result_daily
ENGINE = SummingMergeTree
PARTITION BY toYYYYMM(day)
ORDER BY (day, source, business_dept, machine_type, result_value)
AS
SELECT
    result_date           AS day,
    source,
    business_dept,
    machine_type,
    result_value,
    sum(ng_count)         AS sum_ng,
    sum(sample_count)     AS sum_sample,
    count()               AS cnt
FROM lab_dw.fact_experiment_result
GROUP BY day, source, business_dept, machine_type, result_value;
