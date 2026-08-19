package com.lab.twin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 实验分工明细表 lab_experiment_task_detail */
@Data
@TableName("lab_experiment_task_detail")
public class LabExperimentTaskDetail {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String registrationId;
    /** 机种（唯一） */
    private String machineType;
    /** 担当 */
    private String primaryAssignee;
    /** 第二代理人 */
    private String secondaryAssignee;
    private LocalDateTime createdTime;
}
