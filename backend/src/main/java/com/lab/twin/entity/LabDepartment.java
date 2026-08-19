package com.lab.twin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 实验室部门主数据 lab_department */
@Data
@TableName("lab_department")
public class LabDepartment {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String deptName;
    private Integer sortNum;
    private Integer enabled;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
