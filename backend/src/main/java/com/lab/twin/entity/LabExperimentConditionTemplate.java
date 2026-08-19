package com.lab.twin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 实验条件模板（多版本） lab_experiment_condition_template */
@Data
@TableName("lab_experiment_condition_template")
public class LabExperimentConditionTemplate {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String experimentItemId;
    private String deptNumber;
    private Integer version;
    private String versionName;
    /** 条件方案 JSON */
    private String schemeJson;
    private String remark;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
