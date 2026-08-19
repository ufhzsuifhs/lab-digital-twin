package com.lab.twin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 机器表（父表） lab_machine */
@Data
@TableName("lab_machine")
public class LabMachine {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    /** 仪器名称 */
    private String instrumentName;
    /** 数量 */
    private Integer quantity;
    /** 设备能力 */
    private String deviceCapability;
}
