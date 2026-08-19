package com.lab.twin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 智能实验室数字孪生运营驾驶舱 —— 后端入口。
 * 无登录模块，默认直达驾驶舱首页；聚焦数据展示 / 分析 / 数字孪生 / 智能统计。
 */
@SpringBootApplication
@MapperScan("com.lab.twin.mapper")
@EnableScheduling
public class LabTwinApplication {

    public static void main(String[] args) {
        SpringApplication.run(LabTwinApplication.class, args);
    }
}
