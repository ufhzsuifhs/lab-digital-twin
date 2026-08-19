package com.lab.twin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 实验项目费用 lab_experiment_item_fee */
@Data
@TableName("lab_experiment_item_fee")
public class LabExperimentItemFee {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String testScopeId;
    private String specificCondition;
    private BigDecimal startupFee;
    private String feeUnit;
    private Integer enabled;
    private BigDecimal hourlyRate;
    private BigDecimal thirdPartyReferencePrice;
    private Integer sortNum;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
