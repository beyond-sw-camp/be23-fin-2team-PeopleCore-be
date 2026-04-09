package com.peoplecore.approval.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "emp_id", "doc_id"}))
public class PersonalFolderDocument extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private Long empId;

    @Column(nullable = false)
    private Long docId;

    @Column(nullable = false)
    private Long personalFolderId;

    public void moveToFolder(Long personalFolderId) {
        this.personalFolderId = personalFolderId;
    }

    public void transferTo(Long targetEmpId) {
        this.empId = targetEmpId;
    }
}
