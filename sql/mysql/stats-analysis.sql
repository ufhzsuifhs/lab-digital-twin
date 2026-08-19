-- ============================================================================
-- 智能实验室数字孪生驾驶舱 —— MySQL 统计 SQL（覆盖需求三~十一 + 首页指标）
-- 说明：口径见 docs/03-统计口径.md；枚举值为「约定」值，可据真实数据微调。
-- 机台粒度统一为 lab_machine_unit（机台），父类为 lab_machine。
-- ============================================================================

-- ============================================================================
-- 〇、首页六大指标（需求一）
-- ============================================================================
-- 1) 今日实验数量
SELECT COUNT(*) AS today_experiment_count
FROM lab_inspection_registration
WHERE inspection_date = CURDATE();

-- 2) 进行中实验 / 3) 完成实验（需求七口径复用）
SELECT
    SUM(CASE WHEN completion_status = '已完成' THEN 1 ELSE 0 END) AS completed_count,
    SUM(CASE WHEN completion_status NOT IN ('待测量','已完成','已取消') THEN 1 ELSE 0 END) AS running_count,
    COUNT(*) AS total_count
FROM lab_experiment_plan;

-- 4) 异常实验（今日存在 NG 记录）
SELECT COUNT(DISTINCT registration_id) AS abnormal_count
FROM lab_reliability_experiment_reg
WHERE date = CURDATE()
  AND (ng_count > 0 OR experiment_result IN ('NG','不合格'));

-- 5) 设备利用率（见 §三）
-- 6) 实验完成率（见 §七）

-- ============================================================================
-- 三、设备利用率分析
-- ============================================================================
-- 3.1 机台占用率（实时）：占用中机台数 / 启用机台总数
SELECT
    (SELECT COUNT(DISTINCT machine_id)
       FROM lab_machine_load_management
      WHERE endTime IS NULL OR endTime > NOW()) AS occupied_units,
    (SELECT COUNT(*) FROM lab_machine_unit WHERE enabled = 1) AS total_units,
    ROUND(
        (SELECT COUNT(DISTINCT machine_id) FROM lab_machine_load_management WHERE endTime IS NULL OR endTime > NOW())
        * 100.0 /
        NULLIF((SELECT COUNT(*) FROM lab_machine_unit WHERE enabled = 1), 0), 2
    ) AS utilization_pct;

-- 3.2 各机台占用情况（柱状图 + 排行）
SELECT
    u.id                      AS machine_unit_id,
    m.instrument_name         AS instrument_name,
    u.station_code            AS station_code,
    l.machine_name            AS machine_name,
    IFNULL(l.is_full, 0)      AS is_full,
    IFNULL(l.completed_count,0) AS completed_count,
    (IFNULL(l.is_full,0)*100 + IFNULL(l.completed_count,0)) AS load_score,
    l.endTime                 AS end_time,
    l.dept_number             AS dept_number
FROM lab_machine_unit u
LEFT JOIN lab_machine m          ON m.id = u.parent_machine_id
LEFT JOIN lab_machine_load_management l
       ON l.machine_id = u.id
      AND (l.endTime IS NULL OR l.endTime > NOW())
WHERE u.enabled = 1
ORDER BY load_score DESC;

-- 3.3 设备利用率趋势（日/周/月，基于机台绑定占用区间 lab_inspection_machine）
-- 日
SELECT DATE_FORMAT(im.start_time, '%Y-%m-%d') AS period,
       COUNT(DISTINCT im.machine_load_id)     AS occupied_slots,
       SUM(TIMESTAMPDIFF(HOUR, im.start_time, COALESCE(im.end_time, NOW()))) AS occupied_hours
FROM lab_inspection_machine im
WHERE im.start_time >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
GROUP BY period ORDER BY period;

-- 周
SELECT YEARWEEK(im.start_time, 3) AS period,
       COUNT(DISTINCT im.machine_load_id) AS occupied_slots
FROM lab_inspection_machine im
WHERE im.start_time >= DATE_SUB(CURDATE(), INTERVAL 12 WEEK)
GROUP BY period ORDER BY period;

-- 月
SELECT DATE_FORMAT(im.start_time, '%Y-%m') AS period,
       COUNT(DISTINCT im.machine_load_id)  AS occupied_slots
FROM lab_inspection_machine im
WHERE im.start_time >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
GROUP BY period ORDER BY period;

-- 3.4 设备空闲率
SELECT ROUND(100 - (
    SELECT COUNT(DISTINCT machine_id) FROM lab_machine_load_management WHERE endTime IS NULL OR endTime > NOW()
) * 100.0 / NULLIF((SELECT COUNT(*) FROM lab_machine_unit WHERE enabled = 1), 0), 2) AS idle_rate_pct;

-- 3.5 设备负载排行
SELECT m.instrument_name AS instrument_name,
       u.station_code   AS station_code,
       COUNT(l.id)      AS load_records,
       SUM(IFNULL(l.is_full,0))         AS full_count,
       SUM(IFNULL(l.completed_count,0)) AS total_completed,
       (SUM(IFNULL(l.is_full,0))*100 + SUM(IFNULL(l.completed_count,0))) AS load_score
FROM lab_machine_unit u
JOIN lab_machine m ON m.id = u.parent_machine_id
LEFT JOIN lab_machine_load_management l ON l.machine_id = u.id
GROUP BY m.instrument_name, u.station_code
ORDER BY load_score DESC
LIMIT 20;

-- ============================================================================
-- 四、事业部设备占用分析
-- ============================================================================
-- 4.1 各事业部设备资源占用（饼图 + 排行）—— 按机台负荷的部番/事业部
SELECT COALESCE(NULLIF(l.dept_number,''), '未归属') AS business_unit,
       COUNT(DISTINCT l.machine_id)               AS occupied_units,
       SUM(IFNULL(l.completed_count,0))           AS completed_count
FROM lab_machine_load_management l
WHERE l.endTime IS NULL OR l.endTime > NOW()
GROUP BY business_unit
ORDER BY occupied_units DESC;

-- 4.2 事业部占用趋势（按送检登记的事业部，月）
SELECT DATE_FORMAT(r.inspection_date, '%Y-%m') AS period,
       r.business_unit,
       COUNT(*) AS cnt
FROM lab_inspection_registration r
WHERE r.business_unit IS NOT NULL
  AND r.inspection_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
GROUP BY period, r.business_unit
ORDER BY period, cnt DESC;

-- ============================================================================
-- 五、机种占用比例（TOP10）
-- ============================================================================
SELECT COALESCE(r.machine_type, l.machine_model, '未知') AS machine_type,
       COUNT(DISTINCT l.machine_id)                     AS occupied_units,
       ROUND(COUNT(DISTINCT l.machine_id) * 100.0 /
             NULLIF((SELECT COUNT(DISTINCT machine_id) FROM lab_machine_load_management WHERE endTime IS NULL OR endTime > NOW()), 0), 2) AS pct
FROM lab_machine_load_management l
LEFT JOIN lab_inspection_registration r ON r.machine_type = l.machine_model
WHERE l.endTime IS NULL OR l.endTime > NOW()
GROUP BY machine_type
ORDER BY occupied_units DESC
LIMIT 10;

-- ============================================================================
-- 六、实验申请分析（lab_inspection_registration）
-- ============================================================================
-- 6.1 申请数量趋势（月 / 季度）
SELECT DATE_FORMAT(inspection_date, '%Y-%m') AS period, COUNT(*) AS cnt
FROM lab_inspection_registration
WHERE inspection_date >= DATE_SUB(CURDATE(), INTERVAL 24 MONTH)
GROUP BY period ORDER BY period;

SELECT CONCAT(YEAR(inspection_date),'Q',QUARTER(inspection_date)) AS period, COUNT(*) AS cnt
FROM lab_inspection_registration
GROUP BY period ORDER BY period;

-- 6.2 各部门申请量
SELECT COALESCE(application_department,'未归属') AS dept, COUNT(*) AS cnt
FROM lab_inspection_registration
GROUP BY dept ORDER BY cnt DESC;

-- 6.3 申请类型分布（request_type）
SELECT COALESCE(request_type,'未分类') AS request_type, COUNT(*) AS cnt
FROM lab_inspection_registration
GROUP BY request_type ORDER BY cnt DESC;

-- 6.4 实验类别分布（category）
SELECT COALESCE(category,'未分类') AS category, COUNT(*) AS cnt
FROM lab_inspection_registration
GROUP BY category ORDER BY cnt DESC;

-- 6.5 供应商实验占比（is_supplier）
SELECT is_supplier,
       COUNT(*) AS cnt,
       ROUND(COUNT(*) * 100.0 / NULLIF((SELECT COUNT(*) FROM lab_inspection_registration),0),2) AS pct
FROM lab_inspection_registration
GROUP BY is_supplier;

-- ============================================================================
-- 七、完成率分析（lab_experiment_plan.completion_status）
-- ============================================================================
-- 7.1 整体完成率（仪表盘）
SELECT
    SUM(CASE WHEN completion_status = '已完成' THEN 1 ELSE 0 END) AS completed_count,
    COUNT(*) AS total_count,
    ROUND(SUM(CASE WHEN completion_status = '已完成' THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*),0), 2) AS completion_rate_pct
FROM lab_experiment_plan;

-- 7.2 每日完成率变化（按 updated_time）
SELECT DATE(updated_time) AS d,
       SUM(CASE WHEN completion_status = '已完成' THEN 1 ELSE 0 END) AS completed,
       COUNT(*) AS total,
       ROUND(SUM(CASE WHEN completion_status = '已完成' THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*),0), 2) AS rate_pct
FROM lab_experiment_plan
WHERE updated_time >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
GROUP BY d ORDER BY d;

-- ============================================================================
-- 八、异常实验分析（lab_reliability_experiment_reg）
-- ============================================================================
-- 8.1 NG 数量 / NG 比例 / 异常趋势（日）
SELECT IFNULL(SUM(ng_count),0)                AS ng_total,
       IFNULL(SUM(sample_count),0)            AS sample_total,
       ROUND(IFNULL(SUM(ng_count),0) * 100.0 / NULLIF(IFNULL(SUM(sample_count),0),0), 2) AS ng_rate_pct
FROM lab_reliability_experiment_reg;

SELECT date AS d, COUNT(*) AS ng_records, IFNULL(SUM(ng_count),0) AS ng_pieces
FROM lab_reliability_experiment_reg
WHERE date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
GROUP BY d ORDER BY d;

-- 8.2 NG TOP 实验项目
SELECT e.item_name AS experiment_item, SUM(IFNULL(r.ng_count,0)) AS ng_count
FROM lab_reliability_experiment_reg r
LEFT JOIN lab_experiment_item e ON e.id = r.experiment_item_id
GROUP BY e.item_name ORDER BY ng_count DESC LIMIT 10;

-- 8.3 NG TOP 机种
SELECT machine_model, SUM(IFNULL(ng_count,0)) AS ng_count
FROM lab_reliability_experiment_reg
GROUP BY machine_model ORDER BY ng_count DESC LIMIT 10;

-- 8.4 NG TOP 设备（ng_machine_id → lab_machine_unit）
SELECT u.station_code, m.instrument_name, SUM(IFNULL(r.ng_count,0)) AS ng_count
FROM lab_reliability_experiment_reg r
LEFT JOIN lab_machine_unit u ON u.id = r.ng_machine_id
LEFT JOIN lab_machine m ON m.id = u.parent_machine_id
GROUP BY u.station_code, m.instrument_name ORDER BY ng_count DESC LIMIT 10;

-- 8.5 NG 责任人员分布
SELECT COALESCE(ng_inspector,'未记录') AS inspector, SUM(IFNULL(ng_count,0)) AS ng_count
FROM lab_reliability_experiment_reg
GROUP BY inspector ORDER BY ng_count DESC LIMIT 20;

-- 8.6 NG 原因分析（remark / experiment_result 作为原因维度）
SELECT COALESCE(NULLIF(remark,''), experiment_result, '未记录') AS ng_reason, COUNT(*) AS cnt
FROM lab_reliability_experiment_reg
WHERE ng_count > 0 OR experiment_result IN ('NG','不合格')
GROUP BY ng_reason ORDER BY cnt DESC LIMIT 20;

-- ============================================================================
-- 九、实验结果分析（申请单 / DQA 单 分别统计，只统计已有结果）
-- ============================================================================
-- 9.1 实验申请单 OK/NG 与合格率
SELECT
    SUM(CASE WHEN UPPER(experiment_result) IN ('OK','合格') THEN 1 ELSE 0 END) AS ok_count,
    SUM(CASE WHEN UPPER(experiment_result) IN ('NG','不合格') THEN 1 ELSE 0 END) AS ng_count,
    ROUND(SUM(CASE WHEN UPPER(experiment_result) IN ('OK','合格') THEN 1 ELSE 0 END) * 100.0 /
          NULLIF(SUM(CASE WHEN UPPER(experiment_result) IN ('OK','合格','NG','不合格') THEN 1 ELSE 0 END),0), 2) AS pass_rate_pct
FROM lab_reliability_experiment_reg
WHERE experiment_result IS NOT NULL;

-- 9.2 DQA 申请单 OK/NG 与合格率（evaluation_result：合格/不合格）
SELECT
    SUM(CASE WHEN evaluation_result = '合格' THEN 1 ELSE 0 END) AS ok_count,
    SUM(CASE WHEN evaluation_result = '不合格' THEN 1 ELSE 0 END) AS ng_count,
    ROUND(SUM(CASE WHEN evaluation_result = '合格' THEN 1 ELSE 0 END) * 100.0 /
          NULLIF(SUM(CASE WHEN evaluation_result IN ('合格','不合格') THEN 1 ELSE 0 END),0), 2) AS pass_rate_pct
FROM lab_dqa_optical_evaluation
WHERE evaluation_result IS NOT NULL;

-- 9.3 结果分布（OK/NG 饼图）
SELECT UPPER(experiment_result) AS result, COUNT(*) AS cnt
FROM lab_reliability_experiment_reg
WHERE experiment_result IS NOT NULL
GROUP BY result;

-- ============================================================================
-- 十、DQA 专项分析（lab_dqa_optical_evaluation + lab_dqa_project）
-- ============================================================================
-- 10.1 DQA 项目占比（dqa_project_id）
SELECT p.project_name AS project, COUNT(*) AS cnt,
       ROUND(COUNT(*) * 100.0 / NULLIF((SELECT COUNT(*) FROM lab_dqa_optical_evaluation),0),2) AS pct
FROM lab_dqa_optical_evaluation e
LEFT JOIN lab_dqa_project p ON p.id = e.dqa_project_id
GROUP BY p.project_name ORDER BY cnt DESC;

-- 10.2 评价目的 TOP（evaluation_purpose）
SELECT COALESCE(evaluation_purpose,'未记录') AS purpose, COUNT(*) AS cnt
FROM lab_dqa_optical_evaluation
GROUP BY purpose ORDER BY cnt DESC LIMIT 10;

-- 10.3 评价阶段分布（evaluation_stage）
SELECT COALESCE(evaluation_stage,'未记录') AS stage, COUNT(*) AS cnt
FROM lab_dqa_optical_evaluation
GROUP BY stage ORDER BY cnt DESC;

-- 10.4 评价结果分布（evaluation_result）
SELECT COALESCE(evaluation_result,'未记录') AS result, COUNT(*) AS cnt
FROM lab_dqa_optical_evaluation
GROUP BY result;

-- 10.5 月份趋势
SELECT month AS period, COUNT(*) AS cnt
FROM lab_dqa_optical_evaluation
WHERE month IS NOT NULL
GROUP BY month ORDER BY month;

-- ============================================================================
-- 十一、报价分析（lab_experiment_price_quote + line）
-- ============================================================================
-- 11.1 各部门报价金额（department_name）
SELECT COALESCE(q.department_name,'未归属') AS dept,
       SUM(IFNULL(q.total_experiment_price,0)) AS total_price
FROM lab_experiment_price_quote q
GROUP BY dept ORDER BY total_price DESC;

-- 11.2 事业部报价金额（business_unit）
SELECT COALESCE(q.business_unit,'未归属') AS business_unit,
       SUM(IFNULL(q.total_experiment_price,0)) AS total_price
FROM lab_experiment_price_quote q
GROUP BY business_unit ORDER BY total_price DESC;

-- 11.3 实验项目收费排行（experiment_item_name）
SELECT l.experiment_item_name AS item,
       SUM(IFNULL(l.experiment_price,0)) AS total_price,
       COUNT(*) AS line_cnt
FROM lab_experiment_price_quote_line l
GROUP BY l.experiment_item_name ORDER BY total_price DESC LIMIT 20;

-- 11.4 设备实验收入排行（instrument_name）
SELECT COALESCE(l.instrument_name,'未记录') AS instrument,
       SUM(IFNULL(l.experiment_price,0)) AS total_income
FROM lab_experiment_price_quote_line l
GROUP BY instrument ORDER BY total_income DESC LIMIT 20;

-- 11.5 实验成本趋势（月，以报价金额近似）
SELECT DATE_FORMAT(q.inspection_time, '%Y-%m') AS period,
       SUM(IFNULL(q.total_experiment_price,0)) AS total_price
FROM lab_experiment_price_quote q
WHERE q.inspection_time IS NOT NULL
GROUP BY period ORDER BY period;
