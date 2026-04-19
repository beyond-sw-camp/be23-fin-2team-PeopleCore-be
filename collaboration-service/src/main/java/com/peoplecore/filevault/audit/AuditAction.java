package com.peoplecore.filevault.audit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 파일함 감사 로그 행동 유형.
 *
 * <p>JSON 직렬화 시 lowercase 로 노출. 폴더/파일/다운로드 단위로 상세 분류한다.
 * 기존 {@link com.peoplecore.filevault.entity.ActivityAction} 보다 더 세분화되어 있으며,
 * FE 호환을 위해 활동 이력 응답으로 변환할 때는 더 큰 단위로 묶어 제공한다.</p>
 */
public enum AuditAction {
    CREATE_FOLDER,
    RENAME_FOLDER,
    MOVE_FOLDER,
    SOFT_DELETE_FOLDER,
    RESTORE_FOLDER,
    PERMANENT_DELETE_FOLDER,
    UPLOAD_FILE,
    RENAME_FILE,
    MOVE_FILE,
    SOFT_DELETE_FILE,
    RESTORE_FILE,
    PERMANENT_DELETE_FILE,
    DOWNLOAD_FILE;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static AuditAction fromJson(String value) {
        if (value == null) return null;
        return AuditAction.valueOf(value.toUpperCase());
    }
}
