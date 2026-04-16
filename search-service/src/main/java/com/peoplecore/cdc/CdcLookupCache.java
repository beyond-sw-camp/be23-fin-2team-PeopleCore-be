package com.peoplecore.cdc;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JOIN 문제 해결용 메모리 캐시.
 * Debezium은 FK만 전달하므로 dept/grade/title 이름을 별도 구독하여 여기 보관.
 */
@Component
public class CdcLookupCache {

    private final Map<Long, String> deptNames = new ConcurrentHashMap<>();
    private final Map<Long, String> gradeNames = new ConcurrentHashMap<>();
    private final Map<Long, String> titleNames = new ConcurrentHashMap<>();

    public void putDept(Long id, String name) {
        if (id != null && name != null) deptNames.put(id, name);
    }

    public void removeDept(Long id) {
        if (id != null) deptNames.remove(id);
    }

    public String getDeptName(Long id) {
        return id == null ? null : deptNames.get(id);
    }

    public void putGrade(Long id, String name) {
        if (id != null && name != null) gradeNames.put(id, name);
    }

    public String getGradeName(Long id) {
        return id == null ? null : gradeNames.get(id);
    }

    public void putTitle(Long id, String name) {
        if (id != null && name != null) titleNames.put(id, name);
    }

    public String getTitleName(Long id) {
        return id == null ? null : titleNames.get(id);
    }

    private final Map<Long, Map<Long, Long>> lineEmpIdsByDoc = new ConcurrentHashMap<>();

    public void putLine(Long docId, Long lineId, Long empId) {
        if (docId == null || lineId == null || empId == null) return;
        lineEmpIdsByDoc.computeIfAbsent(docId, k -> new ConcurrentHashMap<>()).put(lineId, empId);
    }

    public void removeLine(Long docId, Long lineId) {
        if (docId == null || lineId == null) return;
        Map<Long, Long> lines = lineEmpIdsByDoc.get(docId);
        if (lines != null) {
            lines.remove(lineId);
            if (lines.isEmpty()) lineEmpIdsByDoc.remove(docId);
        }
    }

    public void removeDoc(Long docId) {
        if (docId != null) lineEmpIdsByDoc.remove(docId);
    }

    public Set<Long> getLineEmpIds(Long docId) {
        if (docId == null) return Collections.emptySet();
        Map<Long, Long> lines = lineEmpIdsByDoc.get(docId);
        if (lines == null) return Collections.emptySet();
        return new HashSet<>(lines.values());
    }
}
