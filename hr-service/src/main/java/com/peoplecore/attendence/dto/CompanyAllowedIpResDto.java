package com.peoplecore.attendence.dto;

import com.peoplecore.attendence.entity.CompanyAllowedIp;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyAllowedIpResDto {

    private Long id;
    /*저장된 CIdr*/
    private String ipCidr;

    /*관리 라벨 */
    private String label;

    /*활성 여부 */
    private Boolean isActive;

    /*생성 일시 */
    private LocalDateTime createdAt;
    /*수정 일시 */
    private LocalDateTime updateAt;

    public static CompanyAllowedIpResDto fromEntity(CompanyAllowedIp e) {
        return CompanyAllowedIpResDto.builder()
                .id(e.getId())
                .ipCidr(e.getIpCidr())
                .label(e.getLabel())
                .isActive(e.getIsActive())
                .createdAt(e.getCreatedAt())
                .updateAt(e.getUpdatedAt())
                .build();
    }

}
