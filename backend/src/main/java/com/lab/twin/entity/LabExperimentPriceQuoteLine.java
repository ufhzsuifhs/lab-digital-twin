package com.lab.twin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 实验项目估价报价明细 lab_experiment_price_quote_line */
@Data
@TableName("lab_experiment_price_quote_line")
public class LabExperimentPriceQuoteLine {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String quoteId;
    private Integer sortNum;
    private String machineUnitId;
    private String stationCode;
    /** 仪器名称 */
    private String instrumentName;
    private String experimentItemId;
    private String experimentItemName;
    private String testScopeId;
    /** 测试范围 */
    private String experimentCondition;
    private String feeId;
    private String specificCondition;
    private BigDecimal hourlyRate;
    private String feeUnit;
    private Integer feeQuantity;
    private String feeQuantityLabel;
    /** 实验价格(元) */
    private BigDecimal experimentPrice;
    private String costFormula;
    private LocalDateTime createdTime;
}
