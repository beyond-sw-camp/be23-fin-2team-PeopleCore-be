package com.peoplecore.hrorder.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.employee.domain.Employee;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "hr_order")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class HrOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    @Column(name = "create_by", nullable = false)
    private Long createBy;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false)
    private OrderType orderType;

    @Column(name = "is_notified", nullable = false)
    private Boolean isNotified;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private OrderStatus status = OrderStatus.SCHEDULED;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "form_values", columnDefinition = "JSON")
    private String formValues;          // 동적 폼 입력값

    @Column(name = "form_snapshot", columnDefinition = "JSON")
    private String formSnapshot;        // 등록 시점 폼 설정 스냅샷

    @Column(name = "form_version")
    private Long formVersion;           // 폼 버전

    @Column(name = "deleted_at")
    private LocalDate deletedAt;

    public void updateOrder(OrderType orderType, LocalDate effectiveDate, String formValues){
        this.orderType = orderType; //발령유형
        this.effectiveDate = effectiveDate; //발령일
        this.formValues = formValues;//폼 입력값
    }

    public void softDelete(){
        this.deletedAt = LocalDate.now();
    }
//    삭제조회용
    public boolean isDeleted(){
        return this.deletedAt != null;
    }

//    상태 update(승인)
    public void updateStatus(OrderStatus status){
        this.status = status;
    }

//    통보
    public void markNotified(){
        this.isNotified = true;
        this.notifiedAt = LocalDateTime.now();
    }


}

