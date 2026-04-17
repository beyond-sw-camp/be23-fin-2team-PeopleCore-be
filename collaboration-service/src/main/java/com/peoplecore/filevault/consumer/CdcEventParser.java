package com.peoplecore.filevault.consumer;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.UUID;

public class CdcEventParser {

    private CdcEventParser() {}

    public static UUID decodeUuid(String base64) {
        if (base64 == null) return null;
        byte[] bytes = Base64.getDecoder().decode(base64);
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        return new UUID(bb.getLong(), bb.getLong());
    }
}
