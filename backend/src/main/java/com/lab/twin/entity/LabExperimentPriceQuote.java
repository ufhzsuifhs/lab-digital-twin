package com.lab.twin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 实验项目估价报价主表 lab_experiment_price_quote */
@Data
@TableName("lab_experiment_price_quote")
public class LabExperimentPriceQuote {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    /** experiment_item | machine */
    private String quoteMode;
    private String oaNumber;
    private String machineType;
    private String deptNumber;
    /** 产品归属事业部 */
    private String businessUnit;
    private String applicant;
    private String departmentId;
    private String departmentName;
    private String contactPhone;
    private LocalDateTime inspectionTime;
    /** 是否供应商 1是 0否 */
    private Integer isSupplier;
    /** 合计实验价格(元) */
    private BigDecimal totalExperimentPrice;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
