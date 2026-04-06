package com.peoplecore.approval.entity;


import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
public class PersonalApprovalFolder extends BaseTimeEntity {

        /** 개인 문서함 id */
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long personalFolderId;

        /** 회사 id */
        @Column(nullable = false)
        private UUID companyId;

        /** 소유자 사원 id */
        @Column(nullable = false)
        private Long empId;

        /** 문서함 이름 */
        @Column(nullable = false, length = 100)
        private String folderName;

        /** 정렬 순서 */
        @Column(nullable = false)
        @Builder.Default
        private Integer sortOrder = 0;

        /** 사용 여부 */
        @Column(nullable = false)
        @Builder.Default
        private Boolean isActive = true;

        /** 이름 수정 */
        public void updateName(String folderName) {
            this.folderName = folderName;
        }

        /** 순서 변경 */
        public void updateSortOrder(Integer sortOrder) {
            this.sortOrder = sortOrder;
        }

        /** 이관 (소유자 변경) */
        public void transferTo(Long targetEmpId) {
            this.empId = targetEmpId;
        }
}
