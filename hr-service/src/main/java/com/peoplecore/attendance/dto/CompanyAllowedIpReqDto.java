package com.peoplecore.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CompanyAllowedIpReqDto {
    /* CIDR또는 단일 IP
     * 단일 IP입력 시 자동으로 /32로 보정  */
    @NotBlank(message = "IP 또는 CIDR 대역은 필수입니다.")
    @Size(max = 64)
    private String ipCidr;

    /*관리용 라벨 */
    @Size(max = 100)
    private String label;


    /*활성 여부 */
    private Boolean isActive;

}
