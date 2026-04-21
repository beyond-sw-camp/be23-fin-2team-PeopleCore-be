package com.peoplecore.filevault.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 즐겨찾기 대상 유형.
 *
 * <p>JSON 직렬화 시 lowercase ({@code folder}, {@code file}) 로 노출하여
 * 프론트엔드 타입과 일치시킨다.</p>
 */
public enum FavoriteTargetType {
    FOLDER,
    FILE;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static FavoriteTargetType fromJson(String value) {
        if (value == null) return null;
        return FavoriteTargetType.valueOf(value.toUpperCase());
    }
}
