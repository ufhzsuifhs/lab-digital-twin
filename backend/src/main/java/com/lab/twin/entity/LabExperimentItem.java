package com.lab.twin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 实验项目 lab_experiment_item */
@Data
@TableName("lab_experiment_item")
public class LabExperimentItem {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String itemName;
    private Integer sortNum;
    private Integer enabled;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
