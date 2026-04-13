package com.peoplecore.attendance.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/* Attendence 복합 PK 클래스 */

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceId implements Serializable {

    /*근태 Id*/
    private Long attenId;

    /*근무 일자 (월별 파티션 키)*/
    private LocalDate attenWorkDate;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AttendanceId that)) return false;
        return Objects.equals(attenId, that.attenId)
                && Objects.equals(attenWorkDate, that.attenWorkDate);
    }
    @Override
    public int hashCode() {
        return Objects.hash(attenId, attenWorkDate);
    }
}

