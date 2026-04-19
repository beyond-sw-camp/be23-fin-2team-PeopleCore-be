package com.peoplecore.filevault.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 파일함 활동 이력 행동 유형.
 *
 * <p>JSON 직렬화 시 lowercase ({@code create_folder} 등) 로 노출하여
 * 프론트엔드 타입과 일치시킨다.</p>
 */
public enum ActivityAction {
    CREATE_FOLDER,
    DELETE_FOLDER,
    UPLOAD,
    DELETE,
    RENAME,
    DOWNLOAD,
    RESTORE,
    PERMANENT_DELETE;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static ActivityAction fromJson(String value) {
        if (value == null) return null;
        return ActivityAction.valueOf(value.toUpperCase());
    }
}
