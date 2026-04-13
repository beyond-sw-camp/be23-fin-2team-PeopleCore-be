package com.peoplecore.attendence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;


/*CommuteRecord 복합 PK 클래스
 * JPA @IDCLASS  규ㅇ약
 * -필드명/타입이 엔티티의 @Id 필드와 정확히 일치해야 함
 * Serializable 구현 필수
 * equals/hashCode 필수 (1,2차 캐시. 영속성 컨텍스트 식별에 사용 )
 *
 * ex) {@code commuteRecordRepository.findById(new CommuteRecordId(id,date))}} */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommuteRecordId implements Serializable {

    /*출퇴근 기록 Id */
    private Long comRecId;

    /*근무일자 (월별 파티션 키) */
    private LocalDate workDate;

    /* 캐싱된 곳에서 꺼내와 비교할 때 주소로 비교가 아닌 필드값을 기준으로 비교하게 오버라이딩 */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommuteRecordId that)) return false;
        return Objects.equals(comRecId, that.comRecId) && Objects.equals(workDate, that.getWorkDate());
    }

    /*hashCpde로 버킷 찾기 -> 해당 버킷 내에서 equals로 비교 */
    @Override
    public int hashCode() {
        /* 두 필드로 해시값 만듦. equals에서 비교하는 필드와 반드시 같은 필드를 써야 함 */
        return Objects.hash(comRecId, workDate);
    }

}
