package com.lab.twin.controller;

import com.lab.twin.common.Result;
import com.lab.twin.service.RelationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 关系网络接口：/api/relation/graph（AntV G6 nodes + edges）
 */
@RestController
@RequestMapping("/api/relation")
@RequiredArgsConstructor
public class RelationController {

    private final RelationService relationService;

    @GetMapping("/graph")
    public Result<Map<String, Object>> graph() {
        return Result.ok(relationService.graph());
    }
}
