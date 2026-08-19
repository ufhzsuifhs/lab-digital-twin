package com.lab.twin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 交接与完工记录表 lab_inspection_handover_log */
@Data
@TableName("lab_inspection_handover_log")
public class LabInspectionHandoverLog {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String registrationId;
    private String receiver;
    private LocalDateTime receiveDate;
}
