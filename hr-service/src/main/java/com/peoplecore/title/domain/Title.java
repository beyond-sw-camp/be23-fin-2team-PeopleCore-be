package com.peoplecore.title.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "title")
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Title {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "title_id")
    private Long titleId;

    @Column(name = "title_name", nullable = false, unique = true)
    private String titleName;

    @Column(name = "title_code", nullable = false, unique = true)
    private String titleCode;
}
