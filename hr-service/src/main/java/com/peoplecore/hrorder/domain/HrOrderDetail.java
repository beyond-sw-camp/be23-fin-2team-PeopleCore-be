package com.peoplecore.hrorder.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "hr_order_detail")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class HrOrderDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_detail_id")
    private Long orderDetailId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private HrOrder hrOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private OrderDetailTargetType targetType;

    @Column(name = "before_id", nullable = false)
    private Long beforeId;

    @Column(name = "after_id", nullable = false)
    private Long afterId;
}
