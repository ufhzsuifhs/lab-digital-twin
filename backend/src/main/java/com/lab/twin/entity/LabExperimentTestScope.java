package com.lab.twin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 实验测试范围 lab_experiment_test_scope */
@Data
@TableName("lab_experiment_test_scope")
public class LabExperimentTestScope {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String experimentItemId;
    private String experimentCondition;
    private Integer sortNum;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
