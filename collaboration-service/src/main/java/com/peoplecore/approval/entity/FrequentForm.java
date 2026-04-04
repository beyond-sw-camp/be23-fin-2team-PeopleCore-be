package com.peoplecore.approval.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "emp_id", "form_id"}))
public class FrequentForm extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long frequentFormId;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private Long empId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_id", nullable = false)
    private ApprovalForm form;
}
