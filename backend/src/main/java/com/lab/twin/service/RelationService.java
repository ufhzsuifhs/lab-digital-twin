package com.lab.twin.service;

import com.lab.twin.mapper.StatsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 关系网络服务：将扁平行数据构建为 AntV G6 图数据（nodes + edges）。
 * 节点：部门 / 事业部 / 机种 / 实验项目 / 设备；边：部门→项目→设备、事业部→部门。
 */
@Service
@RequiredArgsConstructor
public class RelationService {

    private final StatsMapper statsMapper;

    public Map<String, Object> graph() {
        List<Map<String, Object>> rows = statsMapper.relationEdges();

        Set<String> deptNodes = new LinkedHashSet<>();
        Set<String> bizNodes = new LinkedHashSet<>();
        Set<String> typeNodes = new LinkedHashSet<>();
        Set<String> itemNodes = new LinkedHashSet<>();
        Set<String> machineNodes = new LinkedHashSet<>();
        Set<String> deptToItem = new LinkedHashSet<>();
        Set<String> itemToMachine = new LinkedHashSet<>();
        Set<String> bizToDept = new LinkedHashSet<>();

        for (Map<String, Object> r : rows) {
            String dept = str(r.get("dept"));
            String biz = str(r.get("business_unit"));
            String type = str(r.get("machine_type"));
            String itemId = str(r.get("experiment_item_id"));
            String itemName = str(r.get("experiment_item_name"));
            String machineId = str(r.get("machine_id"));
            String station = str(r.get("station_code"));

            if (dept != null) deptNodes.add(dept);
            if (biz != null) bizNodes.add(biz);
            if (type != null) typeNodes.add(type);
            if (itemId != null) itemNodes.add(itemName != null ? itemName : itemId);
            if (machineId != null) machineNodes.add(station != null ? station : machineId);

            if (dept != null && (itemId != null)) deptToItem.add(dept + "|" + (itemName != null ? itemName : itemId));
            if (itemId != null && machineId != null) itemToMachine.add((itemName != null ? itemName : itemId) + "|" + (station != null ? station : machineId));
            if (biz != null && dept != null) bizToDept.add(biz + "|" + dept);
        }

        List<Map<String, Object>> nodes = new ArrayList<>();
        deptNodes.forEach(n -> nodes.add(node(n, n, "department")));
        bizNodes.forEach(n -> nodes.add(node(n, n, "business_unit")));
        typeNodes.forEach(n -> nodes.add(node(n, n, "machine_type")));
        itemNodes.forEach(n -> nodes.add(node(n, n, "experiment_item")));
        machineNodes.forEach(n -> nodes.add(node(n, n, "machine")));

        List<Map<String, Object>> edges = new ArrayList<>();
        int i = 0;
        for (String e : bizToDept) {
            String[] parts = e.split("\\|", 2);
            edges.add(edge("e" + (i++), parts[0], parts[1], "包含"));
        }
        for (String e : deptToItem) {
            String[] parts = e.split("\\|", 2);
            edges.add(edge("e" + (i++), parts[0], parts[1], "申请"));
        }
        for (String e : itemToMachine) {
            String[] parts = e.split("\\|", 2);
            edges.add(edge("e" + (i++), parts[0], parts[1], "使用"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        result.put("edges", edges);
        return result;
    }

    private String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    private Map<String, Object> node(String id, String label, String category) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", id);
        n.put("label", label);
        n.put("category", category);
        return n;
    }

    private Map<String, Object> edge(String id, String source, String target, String label) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("id", id);
        e.put("source", source);
        e.put("target", target);
        e.put("label", label);
        return e;
    }
}
