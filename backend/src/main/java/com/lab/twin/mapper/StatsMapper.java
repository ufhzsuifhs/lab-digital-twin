package com.lab.twin.mapper;

import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 统计查询 Mapper：驾驶舱各分析模块的聚合 SQL（对应 sql/mysql/stats-analysis.sql）。
 * 全部返回 List&lt;Map&gt;，便于前端直接消费 JSON；聚合为只读场景，不走实体映射。
 */
public interface StatsMapper {

    // ============ 首页 ============
    @Select("SELECT COUNT(*) AS today_experiment_count FROM lab_inspection_registration WHERE inspection_date = CURDATE()")
    Map<String, Object> todayExperimentCount();

    @Select("""
            SELECT
                SUM(CASE WHEN completion_status = '已完成' THEN 1 ELSE 0 END) AS completed_count,
                SUM(CASE WHEN completion_status NOT IN ('待测量','已完成','已取消') THEN 1 ELSE 0 END) AS running_count,
                COUNT(*) AS total_count
            FROM lab_experiment_plan
            """)
    Map<String, Object> planStatusSummary();

    @Select("""
            SELECT COUNT(DISTINCT registration_id) AS abnormal_count
            FROM lab_reliability_experiment_reg
            WHERE date = CURDATE() AND (ng_count > 0 OR experiment_result IN ('NG','不合格'))
            """)
    Map<String, Object> todayAbnormalCount();

    @Select("""
            SELECT
                (SELECT COUNT(DISTINCT u.parent_machine_id)
                   FROM lab_machine_load_management l
                   JOIN lab_machine_unit u ON u.id = l.machine_id
                  WHERE l.endTime IS NULL OR l.endTime > NOW()) AS occupied_units,
                (SELECT COUNT(*) FROM lab_machine WHERE instrument_name <> '其他') AS total_units
            """)
    Map<String, Object> deviceUtilization();

    // ============ 数字孪生：设备列表（机台 + 当前负荷状态） ============
    @Select("""
            SELECT
                u.id AS machine_unit_id,
                m.instrument_name,
                u.station_code,
                u.unit_length, u.unit_width, u.unit_height,
                l.id AS load_id,
                IFNULL(l.machine_name, m.instrument_name) AS machine_name,
                IFNULL(l.is_full, 0) AS is_full,
                IFNULL(l.completed_count, 0) AS completed_count,
                IFNULL(l.confirm_count, 0) AS confirm_count,
                l.endTime AS end_time,
                l.dept_number,
                (IFNULL(l.is_full,0)*100 + IFNULL(l.completed_count,0)) AS load_score
            FROM lab_machine_unit u
            LEFT JOIN lab_machine m ON m.id = u.parent_machine_id
            LEFT JOIN lab_machine_load_management l
                   ON l.machine_id = u.id AND (l.endTime IS NULL OR l.endTime > NOW())
            WHERE u.enabled = 1
            ORDER BY u.sort_num, u.station_code
            """)
    List<Map<String, Object>> deviceListWithStatus();

    // ============ 三、设备利用率 ============
    @Select("""
            SELECT
                m.instrument_name,
                COUNT(l.id) AS load_records,
                SUM(IFNULL(l.is_full,0)) AS full_count,
                SUM(IFNULL(l.completed_count,0)) AS completed_count,
                (SUM(IFNULL(l.is_full,0))*100 + SUM(IFNULL(l.completed_count,0))) AS load_score
            FROM lab_machine m
            LEFT JOIN lab_machine_unit u ON u.parent_machine_id = m.id
            LEFT JOIN lab_machine_load_management l
                   ON l.machine_id = u.id AND (l.endTime IS NULL OR l.endTime > NOW())
            WHERE m.instrument_name <> '其他'
            GROUP BY m.instrument_name
            ORDER BY load_score DESC
            """)
    List<Map<String, Object>> machineOccupationRanking();

    @Select("""
            SELECT DATE_FORMAT(im.start_time, '%Y-%m-%d') AS period,
                   COUNT(DISTINCT im.machine_load_id) AS occupied_slots
            FROM lab_inspection_machine im
            WHERE im.start_time >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
            GROUP BY period ORDER BY period
            """)
    List<Map<String, Object>> utilizationTrendDaily();

    @Select("""
            SELECT YEARWEEK(im.start_time, 3) AS period,
                   COUNT(DISTINCT im.machine_load_id) AS occupied_slots
            FROM lab_inspection_machine im
            WHERE im.start_time >= DATE_SUB(CURDATE(), INTERVAL 12 WEEK)
            GROUP BY period ORDER BY period
            """)
    List<Map<String, Object>> utilizationTrendWeekly();

    @Select("""
            SELECT DATE_FORMAT(im.start_time, '%Y-%m') AS period,
                   COUNT(DISTINCT im.machine_load_id) AS occupied_slots
            FROM lab_inspection_machine im
            WHERE im.start_time >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
            GROUP BY period ORDER BY period
            """)
    List<Map<String, Object>> utilizationTrendMonthly();

    @Select("""
            SELECT m.instrument_name,
                   COUNT(l.id) AS load_records,
                   SUM(IFNULL(l.is_full,0)) AS full_count,
                   SUM(IFNULL(l.completed_count,0)) AS total_completed,
                   (SUM(IFNULL(l.is_full,0))*100 + SUM(IFNULL(l.completed_count,0)) + COUNT(l.id)*10) AS load_score
            FROM lab_machine m
            LEFT JOIN lab_machine_unit u ON u.parent_machine_id = m.id
            LEFT JOIN lab_machine_load_management l ON l.machine_id = u.id
            WHERE m.instrument_name <> '其他'
            GROUP BY m.instrument_name
            HAVING COUNT(l.id) > 0
            ORDER BY load_score DESC
            LIMIT 20
            """)
    List<Map<String, Object>> loadRanking();

    // ============ 四、事业部设备占用 ============
    @Select("""
            SELECT COALESCE(NULLIF(l.dept_number,''), '未归属') AS business_unit,
                   COUNT(DISTINCT l.machine_id) AS occupied_units,
                   SUM(IFNULL(l.completed_count,0)) AS completed_count
            FROM lab_machine_load_management l
            WHERE l.endTime IS NULL OR l.endTime > NOW()
            GROUP BY business_unit ORDER BY occupied_units DESC
            """)
    List<Map<String, Object>> businessUnitOccupation();

    @Select("""
            SELECT DATE_FORMAT(r.inspection_date, '%Y-%m') AS period, r.business_unit, COUNT(*) AS cnt
            FROM lab_inspection_registration r
            WHERE r.business_unit IS NOT NULL AND r.inspection_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
            GROUP BY period, r.business_unit ORDER BY period, cnt DESC
            """)
    List<Map<String, Object>> businessUnitTrend();

    // ============ 五、机种占用比例 ============
    @Select("""
            SELECT COALESCE(l.machine_model, '未知') AS machine_type,
                   COUNT(DISTINCT l.machine_id) AS occupied_units
            FROM lab_machine_load_management l
            WHERE l.endTime IS NULL OR l.endTime > NOW()
            GROUP BY machine_type ORDER BY occupied_units DESC LIMIT 10
            """)
    List<Map<String, Object>> machineTypeRatio();

    // ============ 六、实验申请分析 ============
    @Select("""
            SELECT DATE_FORMAT(inspection_date, '%Y-%m') AS period, COUNT(*) AS cnt
            FROM lab_inspection_registration
            WHERE inspection_date >= DATE_SUB(CURDATE(), INTERVAL 24 MONTH)
            GROUP BY period ORDER BY period
            """)
    List<Map<String, Object>> applicationTrendMonthly();

    @Select("""
            SELECT CONCAT(YEAR(inspection_date),'Q',QUARTER(inspection_date)) AS period, COUNT(*) AS cnt
            FROM lab_inspection_registration
            GROUP BY period ORDER BY period
            """)
    List<Map<String, Object>> applicationTrendQuarterly();

    @Select("""
            SELECT COALESCE(application_department,'未归属') AS dept, COUNT(*) AS cnt
            FROM lab_inspection_registration GROUP BY dept ORDER BY cnt DESC
            """)
    List<Map<String, Object>> deptApplicationCount();

    @Select("""
            SELECT COALESCE(request_type,'未分类') AS request_type, COUNT(*) AS cnt
            FROM lab_inspection_registration GROUP BY request_type ORDER BY cnt DESC
            """)
    List<Map<String, Object>> requestTypeDistribution();

    @Select("""
            SELECT COALESCE(category,'未分类') AS category, COUNT(*) AS cnt
            FROM lab_inspection_registration GROUP BY category ORDER BY cnt DESC
            """)
    List<Map<String, Object>> categoryDistribution();

    @Select("""
            SELECT is_supplier, COUNT(*) AS cnt
            FROM lab_inspection_registration GROUP BY is_supplier
            """)
    List<Map<String, Object>> supplierRatio();

    // ============ 七、完成率 ============
    @Select("""
            SELECT
                SUM(CASE WHEN completion_status = '已完成' THEN 1 ELSE 0 END) AS completed_count,
                COUNT(*) AS total_count,
                ROUND(SUM(CASE WHEN completion_status = '已完成' THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*),0), 2) AS completion_rate_pct
            FROM lab_experiment_plan
            """)
    Map<String, Object> completionRateOverall();

    @Select("""
            SELECT DATE(updated_time) AS d,
                   SUM(CASE WHEN completion_status = '已完成' THEN 1 ELSE 0 END) AS completed,
                   COUNT(*) AS total,
                   ROUND(SUM(CASE WHEN completion_status = '已完成' THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*),0), 2) AS rate_pct
            FROM lab_experiment_plan
            WHERE updated_time >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
            GROUP BY d ORDER BY d
            """)
    List<Map<String, Object>> completionTrendDaily();

    // ============ 八、异常实验（NG） ============
    @Select("""
            SELECT IFNULL(SUM(ng_count),0) AS ng_total,
                   IFNULL(SUM(sample_count),0) AS sample_total,
                   ROUND(IFNULL(SUM(ng_count),0) * 100.0 / NULLIF(IFNULL(SUM(sample_count),0),0), 2) AS ng_rate_pct
            FROM lab_reliability_experiment_reg
            """)
    Map<String, Object> ngSummary();

    @Select("""
            SELECT date AS d, COUNT(*) AS ng_records, IFNULL(SUM(ng_count),0) AS ng_pieces
            FROM lab_reliability_experiment_reg
            WHERE date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
            GROUP BY d ORDER BY d
            """)
    List<Map<String, Object>> ngTrendDaily();

    @Select("""
            SELECT e.item_name AS experiment_item, SUM(IFNULL(r.ng_count,0)) AS ng_count
            FROM lab_reliability_experiment_reg r
            LEFT JOIN lab_experiment_item e ON e.id = r.experiment_item_id
            GROUP BY e.item_name ORDER BY ng_count DESC LIMIT 10
            """)
    List<Map<String, Object>> ngTopExperimentItem();

    @Select("""
            SELECT machine_model, SUM(IFNULL(ng_count,0)) AS ng_count
            FROM lab_reliability_experiment_reg
            GROUP BY machine_model ORDER BY ng_count DESC LIMIT 10
            """)
    List<Map<String, Object>> ngTopMachineType();

    @Select("""
            SELECT u.station_code, m.instrument_name, SUM(IFNULL(r.ng_count,0)) AS ng_count
            FROM lab_reliability_experiment_reg r
            LEFT JOIN lab_machine_unit u ON u.id = r.ng_machine_id
            LEFT JOIN lab_machine m ON m.id = u.parent_machine_id
            GROUP BY u.station_code, m.instrument_name ORDER BY ng_count DESC LIMIT 10
            """)
    List<Map<String, Object>> ngTopDevice();

    @Select("""
            SELECT COALESCE(ng_inspector,'未记录') AS inspector, SUM(IFNULL(ng_count,0)) AS ng_count
            FROM lab_reliability_experiment_reg
            GROUP BY inspector ORDER BY ng_count DESC LIMIT 20
            """)
    List<Map<String, Object>> ngInspectorDistribution();

    @Select("""
            SELECT COALESCE(NULLIF(remark,''), experiment_result, '未记录') AS ng_reason, COUNT(*) AS cnt
            FROM lab_reliability_experiment_reg
            WHERE ng_count > 0 OR experiment_result IN ('NG','不合格')
            GROUP BY ng_reason ORDER BY cnt DESC LIMIT 20
            """)
    List<Map<String, Object>> ngReasonAnalysis();

    // ============ 九、实验结果 ============
    @Select("""
            SELECT
                SUM(CASE WHEN UPPER(experiment_result) IN ('OK','合格') THEN 1 ELSE 0 END) AS ok_count,
                SUM(CASE WHEN UPPER(experiment_result) IN ('NG','不合格') THEN 1 ELSE 0 END) AS ng_count,
                ROUND(SUM(CASE WHEN UPPER(experiment_result) IN ('OK','合格') THEN 1 ELSE 0 END) * 100.0 /
                      NULLIF(SUM(CASE WHEN UPPER(experiment_result) IN ('OK','合格','NG','不合格') THEN 1 ELSE 0 END),0), 2) AS pass_rate_pct
            FROM lab_reliability_experiment_reg
            WHERE experiment_result IS NOT NULL
            """)
    Map<String, Object> resultOkNgReliability();

    @Select("""
            SELECT
                SUM(CASE WHEN evaluation_result = '合格' THEN 1 ELSE 0 END) AS ok_count,
                SUM(CASE WHEN evaluation_result = '不合格' THEN 1 ELSE 0 END) AS ng_count,
                ROUND(SUM(CASE WHEN evaluation_result = '合格' THEN 1 ELSE 0 END) * 100.0 /
                      NULLIF(SUM(CASE WHEN evaluation_result IN ('合格','不合格') THEN 1 ELSE 0 END),0), 2) AS pass_rate_pct
            FROM lab_dqa_optical_evaluation
            WHERE evaluation_result IS NOT NULL
            """)
    Map<String, Object> resultOkNgDqa();

    @Select("""
            SELECT UPPER(experiment_result) AS result, COUNT(*) AS cnt
            FROM lab_reliability_experiment_reg
            WHERE experiment_result IS NOT NULL GROUP BY result
            """)
    List<Map<String, Object>> resultDistribution();

    // ============ 十、DQA 专项 ============
    @Select("""
            SELECT p.project_name AS project, COUNT(*) AS cnt
            FROM lab_dqa_optical_evaluation e
            LEFT JOIN lab_dqa_project p ON p.id = e.dqa_project_id
            GROUP BY p.project_name ORDER BY cnt DESC
            """)
    List<Map<String, Object>> dqaProjectRatio();

    @Select("""
            SELECT COALESCE(evaluation_purpose,'未记录') AS purpose, COUNT(*) AS cnt
            FROM lab_dqa_optical_evaluation
            GROUP BY purpose ORDER BY cnt DESC LIMIT 10
            """)
    List<Map<String, Object>> dqaPurposeTop();

    @Select("""
            SELECT COALESCE(evaluation_stage,'未记录') AS stage, COUNT(*) AS cnt
            FROM lab_dqa_optical_evaluation
            GROUP BY stage ORDER BY cnt DESC
            """)
    List<Map<String, Object>> dqaStageDistribution();

    @Select("""
            SELECT COALESCE(evaluation_result,'未记录') AS result, COUNT(*) AS cnt
            FROM lab_dqa_optical_evaluation
            GROUP BY result
            """)
    List<Map<String, Object>> dqaResultDistribution();

    @Select("""
            SELECT month AS period, COUNT(*) AS cnt
            FROM lab_dqa_optical_evaluation
            WHERE month IS NOT NULL GROUP BY month ORDER BY month
            """)
    List<Map<String, Object>> dqaMonthTrend();

    // ============ 十一、报价分析 ============
    @Select("""
            SELECT COALESCE(q.department_name,'未归属') AS dept,
                   SUM(IFNULL(q.total_experiment_price,0)) AS total_price
            FROM lab_experiment_price_quote q
            GROUP BY dept ORDER BY total_price DESC
            """)
    List<Map<String, Object>> quoteByDepartment();

    @Select("""
            SELECT COALESCE(q.business_unit,'未归属') AS business_unit,
                   SUM(IFNULL(q.total_experiment_price,0)) AS total_price
            FROM lab_experiment_price_quote q
            GROUP BY business_unit ORDER BY total_price DESC
            """)
    List<Map<String, Object>> quoteByBusinessUnit();

    @Select("""
            SELECT l.experiment_item_name AS item,
                   SUM(IFNULL(l.experiment_price,0)) AS total_price,
                   COUNT(*) AS line_cnt
            FROM lab_experiment_price_quote_line l
            GROUP BY l.experiment_item_name ORDER BY total_price DESC LIMIT 20
            """)
    List<Map<String, Object>> quoteItemRanking();

    @Select("""
            SELECT COALESCE(l.instrument_name,'未记录') AS instrument,
                   SUM(IFNULL(l.experiment_price,0)) AS total_income
            FROM lab_experiment_price_quote_line l
            GROUP BY instrument ORDER BY total_income DESC LIMIT 20
            """)
    List<Map<String, Object>> quoteInstrumentRanking();

    @Select("""
            SELECT DATE_FORMAT(q.inspection_time, '%Y-%m') AS period,
                   SUM(IFNULL(q.total_experiment_price,0)) AS total_price
            FROM lab_experiment_price_quote q
            WHERE q.inspection_time IS NOT NULL
            GROUP BY period ORDER BY period
            """)
    List<Map<String, Object>> quoteCostTrend();

    // ============ G6 关系网络 ============
    @Select("""
            SELECT r.id AS registration_id, r.application_department AS dept, r.business_unit,
                   r.machine_type, r.dept_number,
                   p.id AS plan_id, p.experiment_item_id, p.machine_id,
                   e.item_name AS experiment_item_name,
                   u.station_code AS station_code
            FROM lab_inspection_registration r
            LEFT JOIN lab_experiment_plan p ON p.registration_id = r.id
            LEFT JOIN lab_experiment_item e ON e.id = p.experiment_item_id
            LEFT JOIN lab_machine_unit u ON u.id = p.machine_id
            LIMIT 500
            """)
    List<Map<String, Object>> relationEdges();

    // ============ 设备详情（数字孪生点击弹层） ============
    @Select("""
            SELECT
                u.id AS machine_unit_id, u.station_code, u.unit_length, u.unit_width, u.unit_height,
                m.instrument_name,
                l.machine_name, l.is_full, l.full_override, l.completed_count, l.confirm_count,
                l.endTime AS expected_finish, l.dept_number,
                reg.application_department, reg.business_unit, reg.applicant,
                p.id AS plan_id, p.completion_status,
                e.item_name AS current_experiment
            FROM lab_machine_unit u
            LEFT JOIN lab_machine m ON m.id = u.parent_machine_id
            LEFT JOIN lab_machine_load_management l
                   ON l.machine_id = u.id AND (l.endTime IS NULL OR l.endTime > NOW())
            LEFT JOIN lab_inspection_machine im ON im.machine_load_id = l.id
            LEFT JOIN lab_inspection_registration reg ON reg.id = im.registration_id
            LEFT JOIN lab_experiment_plan p ON p.id = im.plan_id
            LEFT JOIN lab_experiment_item e ON e.id = p.experiment_item_id
            WHERE u.id = #{machineUnitId}
            """)
    Map<String, Object> deviceDetail(@org.apache.ibatis.annotations.Param("machineUnitId") String machineUnitId);

    // ============ AI 模块支撑查询 ============
    /** 近 30 天每日占用机台数（设备压力预测输入） */
    @Select("""
            SELECT DATE(start_time) AS d, COUNT(DISTINCT machine_load_id) AS occupied
            FROM lab_inspection_machine
            WHERE start_time >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
            GROUP BY d ORDER BY d
            """)
    List<Map<String, Object>> deviceLoadDailyHistory();

    /** 延期阶段（due_at 已过且未完成） */
    @Select("""
            SELECT s.plan_id, s.stage_name, s.due_at, s.runtime_status,
                   p.experiment_item_id, e.item_name
            FROM lab_experiment_plan_stage s
            JOIN lab_experiment_plan p ON p.id = s.plan_id
            LEFT JOIN lab_experiment_item e ON e.id = p.experiment_item_id
            WHERE s.due_at < NOW() AND s.runtime_status NOT IN ('COMPLETED')
            ORDER BY s.due_at LIMIT 100
            """)
    List<Map<String, Object>> overdueStages();

    /** 实验耗时统计（平均/最长/最短周期） */
    @Select("""
            SELECT AVG(duration) AS avg_duration, MAX(duration) AS max_duration,
                   MIN(duration) AS min_duration, COUNT(*) AS cnt
            FROM lab_reliability_experiment_reg WHERE duration IS NOT NULL
            """)
    Map<String, Object> experimentDurationStats();

    /** 部门实验效率（申请/完成/平均耗时/NG 比例） */
    @Select("""
            SELECT COALESCE(r.application_department,'未归属') AS dept,
                   COUNT(DISTINCT r.id) AS apply_count,
                   SUM(CASE WHEN p.completion_status = '已完成' THEN 1 ELSE 0 END) AS completed_count,
                   AVG(reg.duration) AS avg_duration,
                   SUM(CASE WHEN reg.ng_count > 0 THEN 1 ELSE 0 END) AS ng_count
            FROM lab_inspection_registration r
            LEFT JOIN lab_experiment_plan p ON p.registration_id = r.id
            LEFT JOIN lab_reliability_experiment_reg reg ON reg.registration_id = r.id
            GROUP BY dept ORDER BY apply_count DESC
            """)
    List<Map<String, Object>> deptEfficiency();

    /** 机台推荐：可执行指定项目的机台 + 历史成功率/平均周期 */
    @Select("""
            SELECT u.id AS machine_unit_id, u.station_code, m.instrument_name,
                   COUNT(reg.id) AS history_count,
                   SUM(CASE WHEN UPPER(reg.experiment_result) IN ('OK','合格') THEN 1 ELSE 0 END) AS ok_count,
                   AVG(reg.duration) AS avg_duration
            FROM lab_machine_unit_experiment_item mei
            JOIN lab_machine_unit u ON u.id = mei.machine_unit_id
            JOIN lab_machine m ON m.id = u.parent_machine_id
            LEFT JOIN lab_reliability_experiment_reg reg ON reg.ng_machine_id = u.id
            WHERE mei.experiment_item_id = #{itemId} AND u.enabled = 1
            GROUP BY u.id, u.station_code, m.instrument_name
            ORDER BY history_count DESC
            """)
    List<Map<String, Object>> recommendMachines(@org.apache.ibatis.annotations.Param("itemId") String itemId);

    // ============ 首页大屏支撑查询 ============
    /** 近 30 天每日实验申请量（趋势折线） */
    @Select("""
            SELECT DATE(inspection_date) AS d, COUNT(*) AS cnt
            FROM lab_inspection_registration
            WHERE inspection_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
            GROUP BY d ORDER BY d
            """)
    List<Map<String, Object>> dailyExperimentTrend();

    /** 进行中实验列表（join 项目/机台/部门） */
    @Select("""
            SELECT p.id AS plan_id, e.item_name, m.instrument_name, u.station_code,
                   COALESCE(r.application_department,'未归属') AS dept,
                   COALESCE(r.business_unit,'') AS business_unit,
                   p.updated_time
            FROM lab_experiment_plan p
            LEFT JOIN lab_experiment_item e ON e.id = p.experiment_item_id
            LEFT JOIN lab_machine_unit u ON u.id = p.machine_id
            LEFT JOIN lab_machine m ON m.id = u.parent_machine_id
            LEFT JOIN lab_inspection_registration r ON r.id = p.registration_id
            WHERE p.completion_status NOT IN ('待测量','已完成','已取消')
            ORDER BY p.updated_time DESC
            LIMIT 20
            """)
    List<Map<String, Object>> runningExperimentList();

    /** 最近 NG 记录（含机台ID，用于设备异常判定 + 告警条） */
    @Select("""
            SELECT r.date, r.ng_machine_id, e.item_name, r.machine_model,
                   r.ng_count, r.ng_inspector
            FROM lab_reliability_experiment_reg r
            LEFT JOIN lab_experiment_item e ON e.id = r.experiment_item_id
            WHERE r.ng_count > 0 OR r.experiment_result IN ('NG','不合格')
            ORDER BY r.date DESC, r.id DESC
            LIMIT 15
            """)
    List<Map<String, Object>> recentNgList();
}
