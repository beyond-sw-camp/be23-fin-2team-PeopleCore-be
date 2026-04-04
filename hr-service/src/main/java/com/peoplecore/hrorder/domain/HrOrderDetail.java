package com.peoplecore.hrorder.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "인사발령 상세")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class HrOrderDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_detail_id")
    private Long orderDetailId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private OrderDetailTargetType targetType;

    @Column(name = "before_id", nullable = false)
    private Long beforeId;

    @Column(name = "after_id", nullable = false)
    private Long afterId;
}
