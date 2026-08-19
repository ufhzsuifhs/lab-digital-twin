package com.lab.twin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** DQA项目清单 lab_dqa_project */
@Data
@TableName("lab_dqa_project")
public class LabDqaProject {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String projectName;
    private String standard;
    private Integer sortNum;
    private Integer enabled;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
