package com.peoplecore.company.domain.copilot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Map;

@Converter
public class GlossaryJsonConverter implements AttributeConverter<Map<String, String>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> TYPE_REF = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, String> attribute) {
        if (attribute == null) return "{}";
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize glossary", e);
        }
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return Map.of();
        try {
            return OBJECT_MAPPER.readValue(dbData, TYPE_REF);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize glossary", e);
        }
    }
}
