package com.lab.twin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 实验计划表 lab_experiment_plan */
@Data
@TableName("lab_experiment_plan")
public class LabExperimentPlan {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    /** 送检单ID */
    private String registrationId;
    private String oaNumber;
    private String taskDetailId;
    /** 完成状态 */
    private String completionStatus;
    /** 机台ID */
    private String machineId;
    /** 实验项目ID */
    private String experimentItemId;
    private String dqaProjectId;
    private String testScopeId;
    private String feeId;
    private Integer feeQuantity;
    private String experimentConditionMethod;
    private String otherConditionGroupId;
    /** 条件模式 FLAT/CHAIN */
    private String conditionMode;
    private String currentStageId;
    private String chainStatus;
    private LocalDateTime nextTransitionAt;
    private Integer runtimeVersion;
    private Integer experimentQuantityPcs;
    private LocalDateTime experimentTime;
    /** 实验结果 */
    private String experimentResult;
    private String remark;
    private String ngSourcePlanId;
    private String sampleChangeContent;
    private String judgmentStandard;
    private String conditionPhotoUrls;
    private BigDecimal experimentCost;
    private Integer routineTest;
    /** 是否供应商 1是 0否 */
    private Integer isSupplier;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
