package com.peoplecore.pay.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Table(name = "pay_item_histories") //급여항목이력
public class PayItemHistories {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long payItemHistoryId;

//    변경항목명
    @Column(nullable = false, length = 50)
    private String changedField;

    private String oldValue;
    private String newValue;

    @CreationTimestamp
    @Column(nullable = false)
    private LocalDateTime changedAt;

//    변경사유
    private String memo;

    @Column(nullable = false)
    private Long payItemId;

    @Column(nullable = false)
    private Long changedById;

    @Column(nullable = false)
    private UUID companyId;

}
