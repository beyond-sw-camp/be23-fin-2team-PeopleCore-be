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
@Table(name = "pay_item_histories")
public class PayItemHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long payItemHistoryId;

    @Column(nullable = false)
    private Long payItemId;

    @Column(nullable = false, length = 50)
    private String changedField;

    private String oldValue;
    private String newValue;

    @CreationTimestamp
    @Column(nullable = false)
    private LocalDateTime changeAt;

    private String memo;

    @Column(nullable = false)
    private Long changedById;

    @Column(nullable = false)
    private UUID companyId;

}
