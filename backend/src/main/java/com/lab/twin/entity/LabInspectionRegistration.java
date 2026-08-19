package com.lab.twin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 送检登记表（申请入口） lab_inspection_registration */
@Data
@TableName("lab_inspection_registration")
public class LabInspectionRegistration {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    /** 送检人 */
    private String applicant;
    private String applicantContactPhone;
    /** 指定品质工程师 */
    private String designatedQe;
    /** 实验类型 */
    private String experimentType;
    /** 期望完成时间 */
    private LocalDateTime expectedCompletionTime;
    /** 完成状态 */
    private String completionStatus;
    private String receiveOpinion;
    /** 类别 */
    private String category;
    private String applicationCompany;
    /** 送检日期 */
    private LocalDate inspectionDate;
    /** 实验进度 */
    private String experimentProgress;
    private String experimentResponsiblePerson;
    /** 申请部门 */
    private String applicationDepartment;
    /** 机种 */
    private String machineType;
    /** 部番 */
    private String deptNumber;
    private Integer inspectionQuantity;
    private BigDecimal productLength;
    private BigDecimal productWidth;
    private BigDecimal productHeight;
    private String oaNumber;
    private String verificationPurpose;
    private String topFlag;
    private String remark;
    private Integer copyAsNew;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
    private Integer sortNum;
    /** 申请类型 */
    private String requestType;
    /** 是否供应商 1是 0否 */
    private Integer isSupplier;
    /** 事业部 */
    private String businessUnit;
}
