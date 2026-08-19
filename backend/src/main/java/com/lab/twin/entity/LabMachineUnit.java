package com.lab.twin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/** 机器信息表（子表·机台） lab_machine_unit */
@Data
@TableName("lab_machine_unit")
public class LabMachineUnit {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    /** 机器id(父类UUID) */
    private String parentMachineId;
    /** 机台编号 */
    private String stationCode;
    /** 1=启用 0=不启用 */
    private Integer enabled;
    /** 机台负荷展示排序 */
    private Integer sortNum;
    /** 腔体长(m) */
    private BigDecimal unitLength;
    /** 腔体宽(m) */
    private BigDecimal unitWidth;
    /** 腔体高(m) */
    private BigDecimal unitHeight;
    /** 机台体积 */
    private BigDecimal volume;
    /** 体积单位 m3/cm3/L */
    private String volumeUnit;
}
