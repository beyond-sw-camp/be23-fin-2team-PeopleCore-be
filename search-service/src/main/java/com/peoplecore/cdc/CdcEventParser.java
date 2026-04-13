package com.peoplecore.cdc;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

/**
 * Debezium MySQL CDC 이벤트의 특수 타입 변환 유틸.
 * - company_id (BINARY(16) → Base64) → UUID
 * - io.debezium.time.Date (epoch days) → LocalDate
 * - io.debezium.time.MicroTimestamp (micros) → Instant
 */
public class CdcEventParser {

    private CdcEventParser() {}

    /**
     * BINARY(16) UUID를 Base64로 인코딩한 문자열을 UUID로 변환.
     * 예: "oAAAAQAAAAAAAAAAAAAAAQ==" → a0000001-0000-0000-0000-000000000001
     */
    public static String decodeUuid(String base64) {
        if (base64 == null) return null;
        byte[] bytes = Base64.getDecoder().decode(base64);
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        long high = bb.getLong();
        long low = bb.getLong();
        return new UUID(high, low).toString();
    }

    /**
     * epoch days (int) → LocalDate
     */
    public static LocalDate decodeDate(Number epochDays) {
        if (epochDays == null) return null;
        return LocalDate.ofEpochDay(epochDays.longValue());
    }

    /**
     * MicroTimestamp (micros since epoch) → Instant
     */
    public static Instant decodeMicroTimestamp(Number micros) {
        if (micros == null) return null;
        long value = micros.longValue();
        return Instant.ofEpochSecond(value / 1_000_000L, (value % 1_000_000L) * 1_000L);
    }

    /**
     * MicroTimestamp → ISO 문자열
     */
    public static String decodeMicroTimestampIso(Number micros) {
        Instant i = decodeMicroTimestamp(micros);
        return i == null ? null : i.toString();
    }
}
