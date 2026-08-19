package com.lab.twin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 可靠性实验登记表 lab_reliability_experiment_reg */
@Data
@TableName("lab_reliability_experiment_reg")
public class LabReliabilityExperimentReg {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String registrationId;
    private String planId;
    /** NG数 */
    private Integer ngCount;
    /** 样品数 */
    private Integer sampleCount;
    /** 实验结果 */
    private String experimentResult;
    /** NG检验人员 */
    private String ngInspector;
    /** 指定机台(机台单元ID) */
    private String ngMachineId;
    private LocalDateTime ngOperateStart;
    private LocalDateTime ngOperateEnd;
    private BigDecimal duration;
    /** 月份 */
    private String month;
    /** 日期 */
    private LocalDate date;
    /** 机种 */
    private String machineModel;
    /** 事业部 */
    private String businessDept;
    private String entrustingDept;
    private String entruster;
    private String experimentCategory;
    private String experimentItemId;
    private String experimentCondition;
    /** 实验担当 */
    private String experimentAssignee;
    /** 机台编号 */
    private String machineNumber;
    private Integer pieceCount;
    private String remark;
    private String sampleChangeContent;
    /** 不良率(%) */
    private BigDecimal defectRate;
}
