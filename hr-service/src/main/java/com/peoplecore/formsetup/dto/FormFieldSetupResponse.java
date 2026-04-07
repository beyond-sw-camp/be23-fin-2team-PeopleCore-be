package com.peoplecore.formsetup.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.formsetup.domain.FormFieldSetup;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormFieldSetupResponse {
    private Long id;
    private String formType;
    private String fieldKey;
    private String label;
    private String section;
    private String fieldType;
    private Boolean visible;
    private Boolean required;
    private Integer sortOrder;
    private List<String> options;
    private String autoFillFrom;

    private static final ObjectMapper mapper = new ObjectMapper();

//    db option문자열 ex.'["정규직","계약직"]' -> 프론트 List
    public static FormFieldSetupResponse from(FormFieldSetup entity) {
        List<String> optionList = null;
        if (entity.getOptions() != null && !entity.getOptions().isBlank()) {
            try { //json문자열 파싱 ex.정규직,계약직
                optionList = mapper.readValue(entity.getOptions(), new TypeReference<List<String>>() {});
            } catch (Exception e) {
                optionList = new ArrayList<>();
            }
        }

        return FormFieldSetupResponse.builder()
                .id(entity.getSetupId())
                .formType(entity.getFormType().name())
                .fieldKey(entity.getFieldKey())
                .label(entity.getLabel())
                .section(entity.getSection())
                .fieldType(entity.getFieldType().name())
                .visible(entity.getVisible())
                .required(entity.getRequired())
                .sortOrder(entity.getSortOrder())
                .options(optionList)
                .autoFillFrom(entity.getAutoFillFrom())
                .build();
    }
}
