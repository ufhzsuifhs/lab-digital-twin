package com.lab.twin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 实验计划其他条件 lab_experiment_plan_other_condition */
@Data
@TableName("lab_experiment_plan_other_condition")
public class LabExperimentPlanOtherCondition {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String groupId;
    private String condKey;
    private String condValue;
    private BigDecimal durationValue;
    /** hour/minute/day */
    private String durationUnit;
    private Integer parallelGroup;
    private Integer sortNum;
    private LocalDateTime createdTime;
}
