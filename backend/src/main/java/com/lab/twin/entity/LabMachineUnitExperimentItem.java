package com.lab.twin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 机台可执行实验项目 lab_machine_unit_experiment_item（联合主键） */
@Data
@TableName("lab_machine_unit_experiment_item")
public class LabMachineUnitExperimentItem {
    /** 机台ID */
    private String machineUnitId;
    /** 实验项目ID */
    private String experimentItemId;
}
