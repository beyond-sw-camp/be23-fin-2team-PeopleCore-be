package com.peoplecore.employee.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class WorkforceSummaryDto {
    private int total;
    private int  hiredThisMonth;
    private int resignedThisMonth;
    private int contractExpiring;
}
