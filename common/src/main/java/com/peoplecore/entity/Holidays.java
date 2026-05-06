package com.peoplecore.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder    //공휴일
@Table(
        name = "holidays",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_holiday_date_type_company",
                columnNames = {"date", "holiday_type", "company_id"}
        )
)
public class Holidays extends BaseTimeEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long holidayId;

    @Column(nullable = false)
    private LocalDate date;

    //    구분 - 법정공휴일, 사내일정
    private String holidayName;

    @Enumerated(EnumType.STRING)
    private HolidayType holidayType;

    private Boolean isRepeating;
    private UUID companyId;

    @Column(nullable = false)
    private Long empId;

    private Long empModifyId;

    public void update(LocalDate date, String holidayName, Boolean isRepeating, Long empModifyId) {
        this.date = date;
        this.holidayName = holidayName;
        this.isRepeating = isRepeating;
        this.empModifyId = empModifyId;
    }
}
