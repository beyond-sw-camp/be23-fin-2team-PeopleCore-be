package com.peoplecore.collaboration_service.approval.entity;


import com.peoplecore.common.entity.BaseTimeEntity;
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
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"seq_company_id", "seq_rule_id", "seq_reset_key"}))
public class ApprovalSeqCounter extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long seqCounterId;

    //    회사 id
    @Column(nullable = false)
    private UUID companyId;

    //    결재 번호 규칙
    @Column(nullable = false)
    private Long seqRuleId;

    //    리섹 주기별 키
    @Column(nullable = false, length = 20)
    private String seqResetKey;

    //    현재 일련 번호
    @Column(nullable = false)
    @Builder.Default
    private Integer seqCurrent = 0;

    //    낙관적 락 버전
    @Version
    @Builder.Default
    private Integer seqVersion = 1;

    //    채번 메서드
    public int nextSeq() {
        return ++this.seqCurrent;
    }

}
