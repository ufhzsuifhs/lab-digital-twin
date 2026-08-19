package com.lab.twin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 机台负荷管理表 lab_machine_load_management */
@Data
@TableName("lab_machine_load_management")
public class LabMachineLoadManagement {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    /** 关联机台ID */
    private String machineId;
    /** 机种 */
    private String machineModel;
    /** 机台名称 */
    private String machineName;
    /** 是否已满（0未满、1已满） */
    private Integer isFull;
    /** 满载覆盖 0自动 1强制已满 2强制未满 */
    private Integer fullOverride;
    /** 叠层层数 */
    private Integer stackLayerCount;
    /** 手动设为已满时的实际占用(cm²) */
    private BigDecimal usedCm2AtFull;
    /** 结束时间（DDL 列为驼峰 endTime，非 end_time，需显式映射） */
    @TableField("endTime")
    private LocalDateTime endTime;
    /** 确认枚数 */
    private Integer confirmCount;
    /** 完成枚数 */
    private Integer completedCount;
    /** 部番 */
    private String deptNumber;
}
