package com.lab.twin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** DQA光学评价表 lab_dqa_optical_evaluation */
@Data
@TableName("lab_dqa_optical_evaluation")
public class LabDqaOpticalEvaluation {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    /** 送检单ID */
    private String registrationId;
    private String planId;
    private String month;
    private String deptNumber;
    private String machineType;
    /** 评价目的 */
    private String evaluationPurpose;
    private String entrustingDept;
    /** 评价阶段 */
    private String evaluationStage;
    private String experimentItemId;
    /** DQA项目清单ID */
    private String dqaProjectId;
    /** 评价结果：合格、不合格 */
    private String evaluationResult;
    /** 备注（原因） */
    private String remark;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
