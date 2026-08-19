package com.lab.twin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 实验计划链式阶段 lab_experiment_plan_stage */
@Data
@TableName("lab_experiment_plan_stage")
public class LabExperimentPlanStage {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String planId;
    private Integer stageOrder;
    private String stageName;
    private Long durationSeconds;
    /** AUTO/MANUAL */
    private String progressionMode;
    /** PENDING/RUNNING/WAITING_MANUAL/COMPLETED */
    private String runtimeStatus;
    private LocalDateTime startedAt;
    private LocalDateTime dueAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
