package com.lab.twin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 实验计划适用部番 lab_experiment_plan_dept */
@Data
@TableName("lab_experiment_plan_dept")
public class LabExperimentPlanDept {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String planId;
    private String registrationId;
    private String deptNumber;
    private LocalDateTime createdTime;
}
