package com.peoplecore.formsetup.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "form_field_setup",
       uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "form_type", "field_key"}))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormFieldSetup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "setup_id")
    private Long setupId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "form_type", nullable = false, length = 30)
    private FormType formType;

    @Column(name = "field_key", nullable = false, length = 50)
    private String fieldKey;

    @Column(name = "label", nullable = false, length = 50)
    private String label;

    @Column(name = "section", nullable = false, length = 30)
    private String section;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 20)
    private FieldType fieldType;

    @Column(name = "visible", nullable = false)
    @Builder.Default
    private Boolean visible = true;

    @Column(name = "required", nullable = false)
    @Builder.Default
    private Boolean required = false;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "options", length = 500)
    private String options;

    @Column(name = "auto_fill_from", length = 50)
    private String autoFillFrom;

    public void update(String label, String section, FieldType fieldType, Boolean visible,
                       Boolean required, Integer sortOrder, String options, String autoFillFrom) {
        this.label = label;
        this.section =section;
        this.fieldType = fieldType;
        this.visible = visible;
        this.required = required;
        this.sortOrder = sortOrder;
        this.options = options;
        this.autoFillFrom = autoFillFrom;
    }
}
