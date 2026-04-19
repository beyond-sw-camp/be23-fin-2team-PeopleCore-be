package com.peoplecore.filevault.audit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 감사 로그 대상 리소스 유형.
 */
public enum ResourceType {
    FOLDER,
    FILE;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static ResourceType fromJson(String value) {
        if (value == null) return null;
        return ResourceType.valueOf(value.toUpperCase());
    }
}
