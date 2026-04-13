package com.peoplecore.attendence.entity;

import com.peoplecore.company.domain.Company;
import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "company_allowed_ip",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_allowed_ip_company_cidr",
                columnNames = {"company_id", "ip_cidr"}
        )
)
@Getter
@Builder
public class CompanyAllowedIp extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*소속 회사 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /*허용 IP 또는  CIDR 대역
     *    * 단일 IP 는 "/32" 로 정규화해 저장 (예: 10.0.0.5 -> 10.0.0.5/32)
     * 대역은 "192.168.1.0/24" 형식.
     * */
    @Column(nullable = false, length = 64)
    private String ipCidr;

    /* 관리용 라벨
     * 인사 담당자 UI 식별용*/
    @Column(length = 100)
    private String label;

    /*활성 여부 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /*낙관적 락*/
    @Version
    private Long version;

    /**
     * 라벨/대역/활성 일괄 수정
     */
    public void update(String ipCidr, String label, Boolean isActive) {
        this.ipCidr = ipCidr;
        this.label = label;
        if (isActive != null) this.isActive = isActive;
    }

    /**
     * 활성/비활성 토글
     */
    public void toggleActive() {
        this.isActive = !this.isActive;
    }
}
