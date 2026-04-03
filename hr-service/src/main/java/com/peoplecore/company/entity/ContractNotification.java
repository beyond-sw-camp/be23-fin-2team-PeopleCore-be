package com.peoplecore.company.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "contract_notification")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    private Long contractNotificationId;

    @Column(nullable = false)
    private UUID companyId;

    private LocalDateTime notifiedAt;
    private Integer daysBefore;


}
