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
 * 节点 ID 带类型前缀，避免部门名与机台编号撞号导致 G6 无法渲染。
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
        Set<String> typeToItem = new LinkedHashSet<>();

        for (Map<String, Object> r : rows) {
            String dept = str(r.get("dept"));
            String biz = str(r.get("business_unit"));
            String type = str(r.get("machine_type"));
            String itemId = str(r.get("experiment_item_id"));
            String itemName = str(r.get("experiment_item_name"));
            String machineId = str(r.get("machine_id"));
            String station = str(r.get("station_code"));
            String itemKey = itemName != null ? itemName : itemId;
            String machineKey = station != null ? station : machineId;

            if (dept != null) deptNodes.add(dept);
            if (biz != null) bizNodes.add(biz);
            if (type != null) typeNodes.add(type);
            if (itemKey != null) itemNodes.add(itemKey);
            if (machineKey != null) machineNodes.add(machineKey);

            if (dept != null && itemKey != null) deptToItem.add(dept + "|" + itemKey);
            if (itemKey != null && machineKey != null) itemToMachine.add(itemKey + "|" + machineKey);
            if (biz != null && dept != null) bizToDept.add(biz + "|" + dept);
            if (type != null && itemKey != null) typeToItem.add(type + "|" + itemKey);
        }

        List<Map<String, Object>> nodes = new ArrayList<>();
        deptNodes.forEach(n -> nodes.add(node("dept:" + n, n, "department")));
        bizNodes.forEach(n -> nodes.add(node("biz:" + n, n, "business_unit")));
        typeNodes.forEach(n -> nodes.add(node("type:" + n, n, "machine_type")));
        itemNodes.forEach(n -> nodes.add(node("item:" + n, n, "experiment_item")));
        machineNodes.forEach(n -> nodes.add(node("machine:" + n, n, "machine")));

        List<Map<String, Object>> edges = new ArrayList<>();
        int i = 0;
        for (String e : bizToDept) {
            String[] parts = e.split("\\|", 2);
            edges.add(edge("e" + (i++), "biz:" + parts[0], "dept:" + parts[1], "包含"));
        }
        for (String e : deptToItem) {
            String[] parts = e.split("\\|", 2);
            edges.add(edge("e" + (i++), "dept:" + parts[0], "item:" + parts[1], "申请"));
        }
        for (String e : itemToMachine) {
            String[] parts = e.split("\\|", 2);
            edges.add(edge("e" + (i++), "item:" + parts[0], "machine:" + parts[1], "使用"));
        }
        for (String e : typeToItem) {
            String[] parts = e.split("\\|", 2);
            edges.add(edge("e" + (i++), "type:" + parts[0], "item:" + parts[1], "机种"));
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
