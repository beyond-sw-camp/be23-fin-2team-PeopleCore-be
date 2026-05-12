package com.peoplecore.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* hr-service 사원 정보 변경 시 발행되는 이벤트.
 * 컨슈머(collaboration-service)는 empId 로 Redis 캐시(hr:emp:{empId}) 를 무효화한다. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class EmpUpdatedEvent {
    private Long empId;
}
