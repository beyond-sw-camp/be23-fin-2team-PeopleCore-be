package com.peoplecore.company.domain.copilot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class CopilotConfigJsonConverter implements AttributeConverter<CopilotConfig, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(CopilotConfig attribute) {
        CopilotConfig target = (attribute != null) ? attribute : CopilotConfig.defaults();
        try {
            return OBJECT_MAPPER.writeValueAsString(target);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize copilot config", e);
        }
    }

    @Override
    public CopilotConfig convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return CopilotConfig.defaults();
        try {
            return OBJECT_MAPPER.readValue(dbData, CopilotConfig.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize copilot config", e);
        }
    }
}
