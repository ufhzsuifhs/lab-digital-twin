package com.lab.twin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 送检机台绑定表 lab_inspection_machine */
@Data
@TableName("lab_inspection_machine")
public class LabInspectionMachine {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String registrationId;
    private String planId;
    /** 机台负荷ID */
    private String machineLoadId;
    private String machineModel;
    private String deptNumber;
    private Integer completedCount;
    private LocalDateTime endTime;
    private LocalDateTime startTime;
    /** 是否取出 0否 1是 */
    private Integer isPickedUp;
    private LocalDateTime pickupTime;
    private String pickupBy;
    private Integer allowStacking;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
