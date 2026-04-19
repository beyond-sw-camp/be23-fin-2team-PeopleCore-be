package com.peoplecore.filevault.audit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 감사 로그를 발생시킨 주체의 출처.
 *
 * <ul>
 *   <li>{@link #USER} : HTTP 요청 (X-User-Id 등 헤더 기반)</li>
 *   <li>{@link #SYSTEM} : 내부 자동 작업 (스케줄러 등)</li>
 *   <li>{@link #CDC} : Kafka CDC 이벤트로 인한 자동 생성 (부서 기본 폴더 등)</li>
 * </ul>
 */
public enum ActorSource {
    USER,
    SYSTEM,
    CDC;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static ActorSource fromJson(String value) {
        if (value == null) return null;
        return ActorSource.valueOf(value.toUpperCase());
    }
}
