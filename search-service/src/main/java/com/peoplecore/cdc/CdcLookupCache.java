package com.peoplecore.cdc;

import org.springframework.stereotype.Component;

import java.util.Map;
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
}
