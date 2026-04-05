package com.peoplecore.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DeptUpdatedEvent {
/*hr에서 사원 정보 변경 이벤트 발생시 사용*/
    private Long deptId;
}
