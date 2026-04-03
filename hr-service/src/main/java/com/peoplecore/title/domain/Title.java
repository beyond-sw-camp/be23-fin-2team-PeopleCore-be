package com.peoplecore.title.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "title")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Title {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "title_id")
    private Long id;
}
